#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「假成功」巡检：**弹了「已xxx」，但后端一个请求都没发**。

## 为什么需要这个脚本

2026-11-04 在前端扫「`ElMessage.success` 但函数体里没有后端调用」，逮到 **8 处真缺陷**：

```js
// views/purchase/index.vue（approve / receive / cancel 三个都是）
row.status = 'APPROVED'
ElMessage.success('已审核')          // ← 后端一个 DELETE 都没发

// views/ai/Anomaly.vue
async function runAll() {
  ElMessage.success('已对所有 30 单未审核订单重新检测')   // ← 连「30」都是写死的
}

// views/ai/Pricing.vue
function apply(row: any) { row.applied = true; ElMessage.success(`已采纳 ${row.sku} 的建议价`) }

// views/ai/InventoryPredict.vue
function createPO(row: any) { ElMessage.success(`已基于 AI 建议创建 ... 的采购单`) }
```

→ 后端 500 / 接口不存在时，**用户看到的却是「成功」**。
→ 用户以为动作生效了 → 后续动作（收货入库、释放库存、通知对方）**全都没发生**。

📌 **讽刺的是：这些后端接口全都存在**（`/purchase/orders/{id}/approve`、
`/pricing/apply/{sku}`、`/v1/anomaly/{id}/confirm` …），
**只是前端从来没调过**，自己写了个「假成功」。

📌 这和 `check-fake-delete.py` 的关系：
- 假删除 = **成功路径**上的谎言，且**改了本地状态**；
- 假成功 = **更宽的**版本，不一定改本地状态（`runAll()` 连本地状态都没改）。
→ 两道闸门各管一摊，缺一不可。

## 判据

找**函数**（含 `async function`），其函数体里：

- 有 `ElMessage.success(...)` **且**
- **没有**任何后端调用（`xxxApi.yyy(` / `request.get|post|put|delete(`）
  **且**
- **没有**调用本文件里的其它函数（有些是 `refresh()` → `load()` → API，
  直接看 refresh 的函数体会**误报**）

→ 报红

### 允许的「纯本地成功」

下面这些**不需要后端**，弹成功是合理的：

- 剪贴板（`navigator.clipboard`）
- 本地下载（`URL.createObjectURL` / `a.click()`）
- 打开新窗口（`window.open`）
- 打印（`window.print`）
- 纯 UI 状态切换（展开/收起/选中/复制文案…）

## 用法

    python3 scripts/check-fake-success.py
    python3 scripts/check-fake-success.py --strict

退出码：
    0 = 没有假成功
    1 = 有（fatal —— 界面在骗人）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_SRC = ROOT / "erp-web/src"

FUNC_RE = re.compile(
    r"(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{")
API_CALL_RE = re.compile(r"\b[A-Za-z_$][\w$]*Api\s*\.\s*[\w$]+\s*\(")
REQUEST_RE = re.compile(r"\brequest\s*\.\s*(get|post|put|delete|patch)\s*\(")
SUCCESS_RE = re.compile(r"ElMessage\s*\.\s*success\s*\(")
LOCAL_CALL_RE = re.compile(r"(?<![\w.$])([A-Za-z_$][\w$]*)\s*\(")

# 纯本地、不需要后端的成功场景
LOCAL_ONLY_RE = re.compile(
    r"navigator\s*\.\s*clipboard"
    r"|URL\s*\.\s*createObjectURL"
    r"|window\s*\.\s*open"
    r"|window\s*\.\s*print"
    r"|document\s*\.\s*execCommand"
)

# 这些函数名本身就是纯本地动作
LOCAL_ONLY_NAMES = re.compile(
    r"^(copy|clip|export|download|print|preview|open|close|toggle|expand|"
    r"collapse|select|focus|scroll|copyText|handleCopy)$", re.IGNORECASE)


def extract_body(src, brace_pos):
    """从 '{' 处开始配平取出函数体"""
    i = brace_pos + 1
    depth = 1
    j = i
    instr = False
    quote = ""
    while j < len(src) and depth:
        ch = src[j]
        if instr:
            if ch == "\\" and j + 1 < len(src):
                j += 2
                continue
            if ch == quote:
                instr = False
        else:
            if ch in "'\"`":
                instr = True
                quote = ch
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
        j += 1
    return src[i:j - 1]


def collect_functions(src):
    """{函数名: (起始行, 函数体)}"""
    out = {}
    for m in FUNC_RE.finditer(src):
        name = m.group(1)
        body = extract_body(src, m.end() - 1)
        line = src[:m.start()].count("\n") + 1
        # 同名函数保留第一个
        out.setdefault(name, (line, body))
    return out


def has_backend_call(body):
    return bool(API_CALL_RE.search(body) or REQUEST_RE.search(body))


def calls_local_function(body, funcs, depth=0):
    """函数体里是否调用了本文件的其它函数（最多追 2 层）"""
    if depth > 2:
        return False
    for m in LOCAL_CALL_RE.finditer(body):
        name = m.group(1)
        if name in ("if", "for", "while", "switch", "catch", "return", "function",
                    "typeof", "await", "String", "Number", "Boolean", "Array",
                    "Object", "JSON", "Math", "Date", "Promise", "console",
                    "Error", "parseInt", "parseFloat", "setTimeout", "encodeURIComponent"):
            continue
        if name in funcs:
            return True
            # 继续追一层
        sub_body = funcs[name][1]
        if has_backend_call(sub_body):
            return True
        if calls_local_function(sub_body, funcs, depth + 1):
            return True
    return False


def main():
    strict = "--strict" in sys.argv

    files = sorted(list(WEB_SRC.rglob("*.vue")) + list(WEB_SRC.rglob("*.ts")))
    problems = []

    for f in files:
        try:
            src = f.read_text(encoding="utf-8")
        except Exception:
            continue
        funcs = collect_functions(src)
        for name, (line, body) in funcs.items():
            if not SUCCESS_RE.search(body):
                continue
            if LOCAL_ONLY_RE.search(body):
                continue
            if LOCAL_ONLY_NAMES.match(name):
                continue
            if has_backend_call(body):
                continue
            if calls_local_function(body, funcs):
                continue
            problems.append((f.relative_to(ROOT), line, name))

    print("=" * 72)
    print("「假成功」巡检（弹了「已xxx」就必须真有后端调用）")
    print("=" * 72)
    print(f"扫描文件：{len(files)} 个")
    print()

    if problems:
        print(f"🔴 发现 {len(problems)} 处「假成功」：\n")
        for rel, line, name in problems:
            print(f"  ❌ {rel}:{line}  {name}()")
            print(f"       弹了 ElMessage.success，但**没有后端调用**")
            print(f"       → 用户以为动作生效了，**其实什么都没发生**")
            print(f"       → 后续动作（入库/释放库存/落库/通知）全都没发生（规则 51 / 72）")
            print(f"       → 正确做法：**真调后端**，失败就**不弹成功**")
            print()
    else:
        print("✅ 没发现「假成功」")
        print()

    print("=" * 72)
    print(f"结论：假成功 {len(problems)} 处")
    print("=" * 72)

    if problems and strict:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
