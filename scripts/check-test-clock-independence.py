#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「测试与时钟无关」巡检：**用 `now()` 派生的数据不许做子日位移**（除非该模块不按天分桶）。

## 为什么需要这个脚本

2026-11-10 00:16 实测抓到：`erp-analytics-service` 的
`AnalyticsRepositoryH2Test` **在本地 `mvn test` 下红**，而 **CI 是绿的**。

根因是一颗**时间炸弹测试**：

```java
LocalDateTime now = LocalDateTime.now();
insertOrder(1L, "SO-T-1", ..., now);            // 今天
insertOrder(2L, "SO-T-2", ..., now.minusHours(1)); // 「也以为是今天」
```

`AnalyticsRepository` 的口径是**按天分桶**：
`create_time >= day.atStartOfDay() AND create_time < day.plusDays(1).atStartOfDay()`

→ 在 **00:00~00:59** 之间跑，`now.minusHours(1)` 会落到**昨天 23:xx**
→ 「今日销售额」少 200、「按小时聚合」少 1 单 → **单测直接红**。

🔴 为什么 CI 没抓到：
- 这个测试是 **23:54** 提交的，CI 也在那个点跑 → **全绿**；
- → **「CI 绿」只是「跑的那一刻恰好不是 0 点」的运气**，不是「测试与时钟无关」的证明；
- → 每晚 00:00~00:59 它就会红一次，而且**只有 1/24 的时间窗口**。

📌 **这类缺陷特别阴险**：它不会一直坏，它会**每天坏一小时**，然后在白天自己"好了"。

## 本脚本查什么

扫全部 `**/src/test/**/*.java`：

| 判据 | 说明 |
|---|---|
| ① **子日位移** | `now()`（或由 `now()` 赋值的别名变量）后面跟了 **小时/分钟/秒/毫秒/纳秒** 的加减 |
| ② **该模块按天分桶** | 同一模块的**生产代码**里出现 `LocalDate.now()` / `atStartOfDay(` / `withDayOfMonth(` / `TemporalAdjusters.previousOrSame(DayOfWeek` |

**① + ② 同时成立** → **exit 1**。

### 为什么要 ② 这个条件（否则会假红）

单看 ① 会把一堆**安全**的测试也报出来 —— 它们只是拿 `now() ± 1h` 当**移动基准**
跟另一个 `now()` 比（两边一起动，永远一致），**根本不涉及天**。

实测本仓库里有 3 处这种安全写法（`PlatformSyncRegistryTest` /
`LiveStreamServiceTest` / `SupplierEvaluationServiceTest`）——
它们所在的 3 个模块的**生产代码里没有任何按天分桶** → 不报。

→ 也就是说：**只有「用 `now()` 派生数据 + 生产侧按天分桶」这个组合才是真雷**。

## 怎么改（闸门给出的建议）

**把锚点固定到一个确定的时刻**，别用 `now()`：

```java
// ❌ 时间炸弹：0 点后 now.minusHours(1) 会跨到昨天
LocalDateTime now = LocalDateTime.now();
insertOrder(2L, ..., now.minusHours(1));

// ✅ 锚点固定在正午：无论几点跑，11:00 / 12:00 都稳稳落在今天
private LocalDateTime todayAnchor;   // = LocalDate.now().atTime(12, 0)
insertOrder(2L, ..., todayAnchor.minusHours(1));
```

⚠️ 生产侧的 SQL 按**日期区间** `[dayStart, nextDayStart)` 过滤（不是 `create_time <= now`），
所以中午这个锚点**即便「还没到」也不会被漏掉**。

📌 改完必须**反向验证**：把种子改回 `now()` → 闸门必须报红 → 还原 → 变绿。

## 已知覆盖边界（诚实说明，别当成「全查到了」）

- 只查 **Java** 测试；其它语言的测试不管。
- 只查 **子日位移**；「按天位移 + 跨月」这类**跨月边界**问题不在本脚本范围
  （例如「本月」的期望值写死成 350.00，到每月 1 号就会错 —— 那个要靠断言自己算）。
- ② 的判据是**模块级**的：如果某模块的生产代码把按天分桶放在**别的模块**里，本脚本看不到。
"""

import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── ① 子日位移的两种写法 ────────────────────────────────────────────────
# A) 直接链式：now().minusHours(  /  now() . plusMinutes( ...
SUBDAY_OPS = "Hours|Minutes|Seconds|Millis|Nanos"
DIRECT_RE = re.compile(
    r"\.now\(\)\s*\.\s*(?:minus|plus)(?:" + SUBDAY_OPS + r")\s*\(",
    re.MULTILINE,
)
# B) 别名：先把 now() 赋给某个变量，再对那个变量做子日位移
ALIAS_DECL_RE = re.compile(
    r"\b([A-Za-z_]\w*)\s*=\s*[\w.]*\.now\(\)\s*;", re.MULTILINE
)
ALIAS_USE_RE = re.compile(
    r"\b([A-Za-z_]\w*)\s*\.\s*(?:minus|plus)(?:" + SUBDAY_OPS + r")\s*\(",
)

# ── ② 按天分桶（只看生产代码） ───────────────────────────────────────────
DAY_BUCKET_RE = re.compile(
    r"LocalDate\s*\.\s*now\s*\(\s*\)"
    r"|\.atStartOfDay\s*\("
    r"|\.withDayOfMonth\s*\("
    r"|TemporalAdjusters\s*\.\s*previousOrSame\s*\("
    r"|DayOfWeek\s*\."
)

# ⚠️ 这里**不能**排除 `now` 这种名字！
# 反向验证时实测踩过：注入 `LocalDateTime now = LocalDateTime.now();` + `now.minusHours(1)`
# —— 变量名恰好叫 `now`，被过滤掉 → **闸门全绿** → 差点误判「闸门失灵」。
# → 变量名**不作为豁免依据**：只要「某个变量来自 now()」且「它被做了子日位移」，就报。
#   （安全性由 ②「该模块生产代码是否按天分桶」保证，不需要靠变量名兜底。）
SAFE_ALIAS = set()


def java_files(root):
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            if fn.endswith(".java"):
                yield os.path.join(dirpath, fn)


def read(path):
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        return fh.read()


def strip_comments(text):
    """把注释（行注释 + 块注释 + javadoc）抹成空格，**保留行数与列宽**。

    为什么必须做：反向验证时实测抓到 —— 闸门把
    「注释里写的 {@code now.minusHours(1)}」也报了出来 →
    **6 处里只有 1 处是真代码** → 噪声会让闸门报告失真（规则 96 的教训）。
    """
    out = []
    i = 0
    n = len(text)
    state = None  # None | 'line' | 'block' | 'str' | 'chr'
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state is None:
            if c == "/" and nxt == "/":
                state = "line"
                out.append("  ")
                i += 2
                continue
            if c == "/" and nxt == "*":
                state = "block"
                out.append("  ")
                i += 2
                continue
            if c == '"':
                state = "str"
            elif c == "'":
                state = "chr"
            out.append(c)
            i += 1
            continue
        if state == "line":
            if c == "\n":
                state = None
                out.append("\n")
            else:
                out.append(" ")
            i += 1
            continue
        if state == "block":
            if c == "*" and nxt == "/":
                state = None
                out.append("  ")
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        # 字符串 / 字符字面量：**内容抹成空格**（只留引号）——
        #   字符串里的 "now.minusHours(1)" 是给人看的说明文字，不是代码。
        if state == "str":
            if c == "\\" and nxt:
                out.append(" ")
                out.append(" ")
                i += 2
                continue
            if c == '"':
                out.append('"')
                state = None
                i += 1
                continue
            out.append("\n" if c == "\n" else " ")   # 📌 保留换行 → 行号不错位
            i += 1
            continue
        if state == "chr":
            if c == "\\" and nxt:
                out.append(" ")
                out.append(" ")
                i += 2
                continue
            if c == "'":
                out.append("'")
                state = None
                i += 1
                continue
            out.append(" ")
            i += 1
            continue
    return "".join(out)


def module_of(path):
    """<repo>/<module>/src/test/... → <module>"""
    rel = os.path.relpath(path, ROOT)
    parts = rel.split(os.sep)
    if len(parts) >= 2:
        return parts[0]
    return ""


def main():
    modules = sorted(
        d for d in os.listdir(ROOT)
        if os.path.isdir(os.path.join(ROOT, d))
        and os.path.isdir(os.path.join(ROOT, d, "src"))
    )

    # ② 先算出「哪些模块的生产代码按天分桶」
    day_bucket_modules = set()
    for mod in modules:
        main_dir = os.path.join(ROOT, mod, "src", "main")
        if not os.path.isdir(main_dir):
            continue
        for f in java_files(main_dir):
            if DAY_BUCKET_RE.search(strip_comments(read(f))):
                day_bucket_modules.add(mod)
                break

    hits = []          # (file, line, snippet, reason)
    scanned = 0

    for mod in modules:
        test_dir = os.path.join(ROOT, mod, "src", "test")
        if not os.path.isdir(test_dir):
            continue
        for path in java_files(test_dir):
            scanned += 1
            raw = read(path)
            text = strip_comments(raw)   # 📌 注释/字符串里的写法不算代码（否则报告全是噪声）
            raw_lines = raw.splitlines()
            lines = text.splitlines()
            reasons = []

            # A) 直接链式
            for m in DIRECT_RE.finditer(text):
                line_no = text.count("\n", 0, m.start()) + 1
                reasons.append((line_no, raw_lines[line_no - 1].strip() if line_no <= len(raw_lines) else ""))

            # B) 别名
            aliases = {a for a in ALIAS_DECL_RE.findall(text) if a not in SAFE_ALIAS}
            for alias in aliases:
                for m in ALIAS_USE_RE.finditer(text):
                    if m.group(1) != alias:
                        continue
                    line_no = text.count("\n", 0, m.start()) + 1
                    reasons.append((line_no, raw_lines[line_no - 1].strip() if line_no <= len(raw_lines) else ""))

            if not reasons:
                continue

            # ② 该模块生产代码不按天分桶 → 这一处是「移动基准」用法，不是雷
            if mod not in day_bucket_modules:
                continue

            for line_no, snippet in reasons:
                hits.append((path, line_no, snippet, mod))

    if hits:
        print("🔴 「测试与时钟无关」巡检：**发现按天分桶模块里的时间炸弹测试**\n")
        for path, line_no, snippet, mod in hits:
            rel = os.path.relpath(path, ROOT)
            print(f"  ❌ {rel}:{line_no}")
            print(f"     {snippet}")
        print(
            "\n📌 **这是 2026-11-10 00:16 实测抓到的真缺陷**："
            "\n   `now().minusHours(1)` 在 **00:00~00:59** 之间会跨到昨天 →"
            "\n   而生产侧 `AnalyticsRepository` 是**按天分桶**的"
            "（`create_time >= day.atStartOfDay() AND < day.plusDays(1).atStartOfDay()`）"
            "\n   → 「今日销售额」「按小时聚合」的期望值当场少一截 → **单测红**。"
            "\n   提交于 23:54、CI 也在 23:54 跑 → **CI 全绿** → 靠运气藏了 4 天。"
        )
        print(
            "\n🔧 **怎么改**：把锚点固定到一个确定时刻，别用 `now()`："
            "\n     private LocalDateTime todayAnchor;   // = LocalDate.now().atTime(12, 0)"
            "\n     insertOrder(..., todayAnchor.minusHours(1));   // 11:00，稳稳落在今天"
            "\n   （生产侧按**日期区间**过滤，不是 `create_time <= now` → 中午锚点不会被漏掉。）"
        )
        print(
            "\n⚠️ **改完必须反向验证**：把种子改回 `now()` → 本闸门必须报红 → 还原 → 变绿。"
        )
        print(f"\n命中 {len(hits)} 处。")
        return 1

    print(
        "✅ 「测试与时钟无关」巡检通过："
        f"已扫 {scanned} 个测试文件；"
        f"按天分桶的模块（{'、'.join(sorted(day_bucket_modules)) or '无'}）"
        "里没有「now() + 子日位移」的写法。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
