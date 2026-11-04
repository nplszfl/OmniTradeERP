#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「前端假数据兜底」巡检：**接口失败时不能拿编出来的数据冒充真实数据**。

## 为什么需要这个脚本

2026-11-04 在 AI 模块随手扫 `catch` 块，逮到 3 个**真缺陷**
（都是「接口失败 → 页面看着有数据，实则全是假的」）：

```js
// views/purchase/index.vue
} catch {
  // mock
  list.value = [
    { id: 1, poNo: 'PO-2026-0001', supplierName: 'AcmeSound', totalAmount: 38400.00, ... },
    ...6 条...
  ]
}
```

→ 后端 500 时，采购列表**看着有 6 张单子**，
   PO 编号、供应商、金额、日期一应俱全，**用户分辨不出来**。
   → 用户会以为这些单子真的存在 → 照着审批/收货 → **真出事**。

同样的模式还有：
- `views/ai/Anomaly.vue` → 6 条假异常订单 + **写死的 KPI**（`total: 1247 / rate: 3.93`）
- `views/ai/Recommend.vue` → 5 条假选品推荐（连「搜索量上涨 320%」都编好了）

📌 更讽刺的是：**项目里已经有正确的范式了**，
`tenant/index.vue` / `InventoryPredict.vue` / `Pricing.vue` / `Dashboard.vue`
早就改成「失败就如实置空 + 报错」，
**只是有 3 个页面被漏掉了** —— 说明「靠人记得改」这条路走不通。

📌 后端那个方向的老教训（规则 12）在这里**同样成立**：
`catch` 空 / 吞异常 / 填假数据 = **静默失败**。
而且前端更隐蔽 —— 后端返回 500 至少还有日志和监控，
前端兜底后**界面上完全看不出出过错**。

## 本脚本查什么

扫 `erp-web/src/**` 的 `catch` 块，找出**把编出来的对象/数组赋给状态变量**的地方：

- 判据：catch 块里出现 `xxx.value = [ { ... } ]` 或 `xxx.value = { ... }`
  且**内容非空**、**含对象字面量**（`{...}`）
  → 就是「兜底假数据」

排除项：
- `= []` / `= {}` → **诚实的空状态**，不算缺陷
- `= null` / `= undefined` → 同上
- catch 体里只有 `ElMessage.error(...)` → 正确范式

## 用法

    python3 scripts/check-frontend-fake-fallback.py
    python3 scripts/check-frontend-fake-fallback.py --strict

退出码：
    0 = 没有假数据兜底
    1 = 有（fatal —— 界面在骗人）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_SRC = ROOT / "erp-web/src"

# catch 块
CATCH_RE = re.compile(r"\}?\s*catch\s*(\([^)]*\))?\s*\{")

# 赋值：xxx.value = [ 或 { 或 xxx = [
ASSIGN_RE = re.compile(r"(?:const|let|var)?\s*([A-Za-z_$][\w$]*(?:\.[\w$]+)*)\s*=\s*([\[{])")


def extract_catch_blocks(src):
    """→ [(起始行号, catch 体)]"""
    out = []
    for m in CATCH_RE.finditer(src):
        i = m.end()
        depth = 1
        j = i
        while j < len(src) and depth:
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
            j += 1
        body = src[i:j - 1]
        line = src[:m.start()].count("\n") + 1
        out.append((line, body))
    return out


def find_fake_fallbacks(body):
    """catch 体里的「兜底假数据」赋值 → [(变量名, 假数据行数)]"""
    hits = []
    for m in ASSIGN_RE.finditer(body):
        var, opener = m.group(1), m.group(2)
        closer = "]" if opener == "[" else "}"
        # 配平扫描出字面量内容
        depth = 1
        p = m.end()
        instr = False
        quote = ""
        while p < len(body) and depth:
            ch = body[p]
            if instr:
                if ch == "\\" and p + 1 < len(body):
                    p += 2
                    continue
                if ch == quote:
                    instr = False
            else:
                if ch in "'\"`":
                    instr = True
                    quote = ch
                elif ch == opener:
                    depth += 1
                elif ch == closer:
                    depth -= 1
            p += 1
        content = body[m.end():p - 1].strip()
        if not content:
            continue  # 空数组/空对象 → 诚实置空
        # 含对象字面量 → 假数据行
        rows = len(re.findall(r"\{[^{}]*\}", content))
        if rows:
            hits.append((var, rows))
    return hits


def main():
    strict = "--strict" in sys.argv

    files = sorted(list(WEB_SRC.rglob("*.vue")) + list(WEB_SRC.rglob("*.ts")))
    problems = []

    for f in files:
        try:
            src = f.read_text(encoding="utf-8")
        except Exception:
            continue
        for line, body in extract_catch_blocks(src):
            hits = find_fake_fallbacks(body)
            if hits:
                rel = f.relative_to(ROOT)
                for var, rows in hits:
                    problems.append((rel, line, var, rows))

    print("=" * 72)
    print("「前端假数据兜底」巡检（接口失败不许拿编的数据冒充真数据）")
    print("=" * 72)
    print(f"扫描文件：{len(files)} 个")
    print()

    if problems:
        print(f"🔴 发现 {len(problems)} 处「catch 里填假数据」：\n")
        for rel, line, var, rows in problems:
            print(f"  ❌ {rel}:{line}")
            print(f"       兜底赋值 : {var} = [ {{...}} × {rows} ]")
            print(f"       → 接口失败时页面**看着有数据**，实则全是编的")
            print(f"       → 用户分辨不出来 → 可能照着假数据做决策（规则 12 / 19）")
            print(f"       → 正确做法：**如实置空 + ElMessage.error(...)**")
            print()
    else:
        print("✅ 没发现「catch 里填假数据」")
        print()

    print("=" * 72)
    print(f"结论：假数据兜底 {len(problems)} 处")
    print("=" * 72)

    if problems and strict:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
