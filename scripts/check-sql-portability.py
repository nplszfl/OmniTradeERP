#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「SQL 方言可移植性」巡检：**裸 SQL 里不许出现 H2 不认的 MySQL 方言**。

## 为什么需要这个脚本

2026-11-05 实测抓到：`erp-analytics-service` 的看板 SQL 用了三种 MySQL 方言
（`DATE_FORMAT` / `INTERVAL 1 DAY` / `DATE_SUB(... WEEKDAY ...)`），
**H2（即使 MODE=MySQL）不认** → 整条 SQL 抛异常 → 被 `catch` 吞掉 → 只能 `return List.of()`。

结果是：**看板上的「今日/昨日/本月销售额」「按周营收趋势」「今日订单数」恒为 0 / 恒为空**，
页面上却**完全看不出来**（只有一条 WARN 日志）。

🔴 为什么四道闸门都没发现：
1. **单测碰不到** —— `DashboardServiceTest` 把 Repository **mock 掉了**，SQL 根本没执行；
2. **CI 没有 MySQL service** —— 也没有真库可跑；
3. **生产的 MySQL 上它是好的** —— 语法在 MySQL 8 完全合法；
4. **没有任何闸门查「这条 SQL 在别的库上能不能跑」**。

→ 于是形成一条**永远不会被验证的路径**：SQL 写错 → 只有生产能发现 → 只有 WARN 日志。

📌 **同一个坑本项目已经踩过一次**（规则 17）：
`erp-inventory-service` 的 `InventoryNameResolver` 里留着当时的注释 ——
「时间下界用参数传，不要写 DATE_SUB(NOW(), INTERVAL 30 DAY)」。

## 本脚本查什么

扫全部 `**/src/main/java/**/*.java`（**不含 test**）里的**裸 SQL 字符串**，
命中下列 **H2 实测不认**的构造 → **exit 1**。

| 构造 | H2 MODE=MySQL | 替代写法 |
|---|---|---|
| `DATE_SUB(` / `DATE_ADD(` | ❌ Function not found | `DATEADD('DAY', n, ts)`（H2）/ 时间边界用参数 |
| `DATE_FORMAT(` | ❌ Function not found | 用 `LocalDate`/`LocalDateTime` 参数切时间窗 |
| `WEEKDAY(` | ❌ Function not found | Java 侧 `date.with(previousOrSame(MONDAY))` |
| `INTERVAL n UNIT`（算术式） | ❌ Syntax error | `LocalDateTime` 参数 |
| `DATEDIFF(a, b)`（两参 MySQL 形） | ❌ 第一个参数被当单位 | `DATEDIFF(DAY, a, b)` / `TIMESTAMPDIFF(DAY, a, b)` |
| `FIND_IN_SET(` / `STR_TO_DATE(` / `JSON_EXTRACT(` / `SUBSTRING_INDEX(` | ❌ Function not found | 各自有标准替代 |

⚠️ **已实测确认 H2 是支持**的（所以**不报**，避免假红）：
`IFNULL` / `GROUP_CONCAT` / `TIMESTAMPDIFF` / `CAST(x AS CHAR)` / `LAST_INSERT_ID` /
`CONVERT` / `RAND` / `NOW` / `CURDATE` / `YEAR|MONTH|DAY` / `UNIX_TIMESTAMP` /
`FROM_UNIXTIME` / `DATE()` / `HOUR()` / `LIMIT o, n` / `ON DUPLICATE KEY UPDATE` / `REPLACE INTO`。

## 两个必须处理的技术细节（不处理就会假绿/假红）

1. **必须先剥掉 Java 注释** —— 否则 `InventoryNameResolver` 里那段
   「不要写 DATE_SUB(NOW(), INTERVAL 30 DAY)」的**注释**会被当成真代码 → 假红。
2. **必须把相邻字符串字面量拼起来再看** —— 真实代码长这样：
   ```java
   "SELECT DATE_SUB(DATE(o.create_time), "
        + "INTERVAL WEEKDAY(o.create_time) DAY) AS wk, "
   ```
   `INTERVAL` 和 `WEEKDAY` 分在两个字面量里，**不拼接就一个都检测不到**。

## 逃生舱

确实需要 MySQL 专有 SQL 时，在那一行加注释 `// sql-portability: allow` → 该行跳过。
（用了就要在注释里说明「为什么只能在 MySQL 上跑」。）

## 用法

    python3 scripts/check-sql-portability.py

退出码：
    0 = 没有 MySQL 专有方言
    1 = 有（fatal —— 这些 SQL 在 H2 上必然抛异常，然后被 catch 吞掉）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ── H2 实测不认的构造（括号里的说明是替代写法）──────────────────────────────
BANNED = [
    ("DATE_SUB(", "`DATE_SUB(x, INTERVAL n DAY)` —— MySQL 专有"),
    ("DATE_ADD(", "`DATE_ADD(x, INTERVAL n DAY)` —— MySQL 专有"),
    ("DATE_FORMAT(", "`DATE_FORMAT(x, '%Y-%m-%d')` —— MySQL 专有"),
    ("WEEKDAY(", "`WEEKDAY(x)` —— MySQL 专有（周一起始）"),
    ("FIND_IN_SET(", "`FIND_IN_SET(a, b)` —— MySQL 专有"),
    ("STR_TO_DATE(", "`STR_TO_DATE(s, fmt)` —— MySQL 专有"),
    ("JSON_EXTRACT(", "`JSON_EXTRACT(col, '$.k')` —— MySQL 专有"),
    ("SUBSTRING_INDEX(", "`SUBSTRING_INDEX(s, sep, n)` —— MySQL 专有"),
]
BANNED_RE = [(name, re.compile(re.escape(name), re.IGNORECASE)) for name, _ in BANNED]

# `INTERVAL 30 DAY` 这种算术式
INTERVAL_RE = re.compile(
    r"INTERVAL\s+(?:\d+|'[^']*'|\?)\s+(?:MICROSECOND|SECOND|MINUTE|HOUR|DAY|WEEK|MONTH|QUARTER|YEAR)\b",
    re.IGNORECASE)

# `DATEDIFF(a, b)` 的 MySQL 两参形 —— 第一个参数会被 H2 当成「单位」
DATEDIFF_RE = re.compile(
    r"DATEDIFF\s*\(\s*(?!(?:SECOND|MINUTE|HOUR|DAY|WEEK|MONTH|QUARTER|YEAR)\b)[^,()]+,",
    re.IGNORECASE)

# 看起来像 SQL 的字面量才查（避免误伤日志文案、注释里的举例）
# ⚠️ 判据必须**够宽**：真实代码里有很多「SQL 片段」字面量，
#    比如 "DATE(create_time) = CURDATE() - INTERVAL 1 DAY" ——
#    它没有 SELECT/FROM，但**确实是要发给数据库的 SQL 片段**。
#    早期版本用「必须含 SELECT/FROM」过滤 → 这类片段**全被漏掉**（反向验证时才发现）。
SQLISH_RE = re.compile(
    r"\b(?:SELECT|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM|FROM\s+\w+|WHERE\s|CREATE\s+TABLE|ALTER\s+TABLE"
    r"|GROUP\s+BY|ORDER\s+BY|JOIN\b|VALUES\b|CURDATE|NOW\s*\(|DATE\s*\(|DATEADD|COALESCE|SUM\s*\(|COUNT\s*\(|AVG\s*\()",
    re.IGNORECASE)

# 📌 **SQL 片段特征**（2026-11-09 实测补的漏洞）——
#    上面那个 SQLISH_RE 过滤有个**真实盲区**：
#    一个「不带 SELECT/WHERE/CURDATE 等关键字」的 WHERE 片段，
#    只要**被当成参数传给拼 SQL 的辅助方法**，就会**完全逃过** SQLISH_RE。
#
#    实测反例（注入后闸门**没报红**）：
#        sumOrders("DATE_FORMAT(create_time, '%Y-%m') = '2026-11'")
#        → 辅助方法体内是 `"SELECT ... WHERE " + where + " AND deleted = 0"`
#        → 片段最终**确实**进了 SQL → 生产 MySQL 合法 / H2 抛异常 → 被 catch 吞掉
#        → 看板「本月销售额」恒为 0（**同一个坑规则 17**）。
#
#    → 判据从「字面量自己像不像完整 SQL」改成两段式：
#       ① 含**被禁构造**（DATE_FORMAT( / DATE_SUB( / INTERVAL n DAY …）
#       ② 且带**SQL 片段特征**（snake_case 列名 / 比较运算符 / AND / OR / BETWEEN / LIKE）
#    → 「日志文案 / 英文说明」不含列名和比较符 → 不会误伤；
#       「WHERE 片段」两者都占 → 抓得到。
FRAGMENT_RE = re.compile(
    r"\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b"      # snake_case 列名：create_time / order_amount
    r"|=\s*'|\bAND\b|\bOR\b|\bBETWEEN\b|\bLIKE\b|>=|<=|<>|!=|\bIN\s*\(",
    re.IGNORECASE)

# 被禁构造的「存在性」判定（字面量里有没有命中任何一条禁令）
BANNED_ANY_RE = ([pat for _, pat in BANNED_RE]
                 + [INTERVAL_RE, DATEDIFF_RE])

STR_RE = re.compile(r'"(?:\\.|[^"\\])*"', re.DOTALL)
# ⚠️ 必须能匹配**单个**字面量（`()` 前缀不能是 `+`）——
#    否则 "DATE(create_time) = CURDATE()" 这种独立片段永远不会被扫到。
CHAIN_RE = re.compile(r'(?:"(?:\\.|[^"\\])*"\s*\+\s*)*"(?:\\.|[^"\\])*"', re.DOTALL)
LINE_COMMENT_RE = re.compile(r"//[^\n]*")
BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)

ALLOW_MARK = "sql-portability: allow"


def strip_comments(text: str) -> str:
    """把注释内容换成空格（保留换行与长度）→ 偏移量不变，行号可算。"""
    def blank(m):
        return re.sub(r"[^\n]", " ", m.group(0))
    text = BLOCK_COMMENT_RE.sub(blank, text)
    text = LINE_COMMENT_RE.sub(blank, text)
    return text


def unquote(literal: str) -> str:
    body = literal[1:-1]
    return re.sub(r"\\(.)", r"\1", body)


def java_files():
    # ⚠️ 两个 glob 会重叠（`*-service` 与 `erp-*/src/main/java/**`）→ 必须去重，
    #    否则同一处缺陷会被**报两遍**（数字虚高会让人不信闸门）。
    seen = set()
    for f in sorted(ROOT.glob("erp-*/src/main/java/**/*.java")):
        if "src/main/java" not in str(f):
            continue
        if f in seen:
            continue
        seen.add(f)
        yield f


def main() -> int:
    problems = []
    scanned = 0
    hit_names = {}
    hit_ctx = {}

    for f in java_files():
        raw = f.read_text(encoding="utf-8", errors="replace")
        scanned += 1
        code = strip_comments(raw)

        # 逃生舱：带标记的行直接跳过（标记在**原始文本**里，注释已剥但标记本身在注释里 → 用 raw 查）
        allowed_lines = {i + 1 for i, ln in enumerate(raw.splitlines()) if ALLOW_MARK in ln}

        # ⚠️ finditer 会把同一条拼接链匹配两次（一次从第一个字面量起、一次从第二个起）
        #    → 手工推进扫描位置，避免**同一处缺陷被报两遍**（数字虚高会让人不信闸门）。
        pos = 0
        while pos < len(code):
            chain = CHAIN_RE.search(code, pos)
            if chain is None:
                break
            pos = chain.end()
            parts = [unquote(p.group(0)) for p in STR_RE.finditer(chain.group(0))]
            content = "".join(parts)
            # 📌 **两段式判定**（见 FRAGMENT_RE 处的说明）：
            #    ① 完整 SQL（含 SELECT/WHERE/CURDATE…）→ 直接查
            #    ② SQL **片段**（无关键字，但含被禁构造 + 列名/比较符）→ 也查
            #       → 这一支是 2026-11-09 补上的真实盲区。
            if not SQLISH_RE.search(content):
                if not any(pat.search(content) for pat in BANNED_ANY_RE):
                    continue
                if not FRAGMENT_RE.search(content):
                    continue

            start_line = code.count("\n", 0, chain.start()) + 1
            if start_line in allowed_lines:
                continue

            for name, pat in BANNED_RE:
                for hit in pat.finditer(content):
                    line = start_line + content[:hit.start()].count("\n")
                    if line in allowed_lines:
                        continue
                    key = (f, line)
                    hit_names.setdefault(key, set()).add(name)
                    hit_ctx.setdefault(key, content[max(0, hit.start() - 40):hit.start() + 60])
            for pat, label in ((INTERVAL_RE, "INTERVAL n UNIT"), (DATEDIFF_RE, "DATEDIFF(a, b)")):
                for hit in pat.finditer(content):
                    line = start_line + content[:hit.start()].count("\n")
                    if line in allowed_lines:
                        continue
                    key = (f, line)
                    hit_names.setdefault(key, set()).add(label)
                    hit_ctx.setdefault(key, content[max(0, hit.start() - 40):hit.start() + 60])

    # 📌 **按行去重**（2026-11-09 修）——
    #    同一行同时命中 `DATE_SUB(` 和 `WEEKDAY(` 时，
    #    旧版报「N 处」而实际只有「N **行**」有问题 → **数字虚高会让人不信闸门**。
    #    → 现在按 (文件, 行) 合并，该行的命中构造用 ` + ` 列出。
    for key in sorted(hit_names, key=lambda k: (str(k[0]), k[1])):
        f, line = key
        problems.append((f, line, " + ".join(sorted(hit_names[key])), hit_ctx[key]))

    print("=" * 72)
    print("SQL 方言可移植性巡检（MySQL 专有方言 → H2 会抛异常 → 被 catch 吞掉）")
    print("=" * 72)
    print(f"扫描 Java 文件：{scanned} 个")

    if problems:
        print()
        print(f"❌ 发现 {len(problems)} 行 MySQL 专有方言：")
        for f, line, name, ctx in problems:
            rel = f.relative_to(ROOT) if f.is_relative_to(ROOT) else f
            ctx = " ".join(ctx.split())
            print(f"  · {rel}:{line}  {name}")
            print(f"      …{ctx}…")
        print()
        print("📌 修法：时间边界一律用 LocalDate/LocalDateTime **参数**传；")
        print("   周起始等换算放到 Java 侧做纯函数（可单测）。")
        print(f"   确实需要 MySQL 专有 SQL → 在该行加 `// {ALLOW_MARK}` 并说明原因。")
        print()
        print("=" * 72)
        print(f"结论：{len(problems)} 行待修")
        print("=" * 72)
        return 1

    print()
    print("✅ 没有发现 MySQL 专有方言（裸 SQL 在 H2 上可执行）")
    print()
    print("=" * 72)
    print("结论：0 处待修")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    sys.exit(main())
