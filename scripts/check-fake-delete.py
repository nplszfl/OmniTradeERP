#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「假删除」巡检：**点了删除、弹了「已删除」，但后端数据一条没动**。

## 为什么需要这个脚本

2026-11-04 抓到的两个真缺陷（前端比后端更容易藏）：

```js
// erp-web/src/views/report/index.vue
async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除 ${row.name}？`, '提示', { type: 'warning' })
  list.value = list.value.filter(r => r.id !== row.id)   // ← 只改本地数组
  ElMessage.success('已删除')                             // ← 骗人
}
```

→ **后端一个 DELETE 都没发** → 刷新页面，东西**原样回来**。
→ 用户以为删掉了 → 该做的后续动作（释放库存、通知对方、停用账号）**全都没发生**。
→ 这是「界面在骗人」的**最恶劣形态**：不是数字对不上，是**操作根本没生效**。

同一个缺陷也出现在 `views/tenant/index.vue` ——
而且更讽刺：**后端 `TenantListController` 本来就有 `DELETE /{id}`**，
只是前端**从来没调过**，自己写了个假删除。

📌 为什么单测/现有闸门都发现不了：
- 前端没有「点击删除 → 断言后端收到 DELETE」的测试；
- `check-dead-endpoints.py` 只查「接口有没有调用方」，
  而这里**恰恰是「前端不调后端」** → 它看不见；
- `check-frontend-fake-fallback.py` 查的是 **catch 块里的假数据**，
  这两个缺陷**不在 catch 里**（在 `try` 成功的路径上！）→ 它也看不见。

📌 **规律**：每个闸门都有自己负责的「形态」。
→ 「假删除」是**成功路径**上的谎言，和「假数据兜底」是**失败路径**上的谎言，
   **两者要分开查**。

## 判据

找**删除类动作函数**（函数名含 delete / remove / del / destroy / 删），
检查其函数体：

- ❌ 命中 → **只改本地状态**（`.value = ....filter` / `.value.splice` /
  `.value = ....filter`）**且没有调后端**（没有 `xxxApi.xxx(` 形式的调用）
  → 报红

排除：
- 函数体里有 `await` 的后端调用（`xxxApi.` / `request.`）→ 合法
- 没有本地状态变更（纯导航 / 纯提示）→ 合法

## 用法

    python3 scripts/check-fake-delete.py
    python3 scripts/check-fake-delete.py --strict

退出码：
    0 = 没有假删除
    1 = 有（fatal —— 操作没生效却显示成功）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_SRC = ROOT / "erp-web/src"

# 删除类动作函数：名字里含 delete/remove/del/destroy
FUNC_RE = re.compile(
    r"(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{",
    re.IGNORECASE,
)
DELETE_NAME_RE = re.compile(r"(delete|remove|del|destroy|drop|trash)", re.IGNORECASE)

# 本地状态变更
LOCAL_MUTATE_RES = [
    re.compile(r"\.value\s*=\s*[\w.]+\.value\.filter\("),
    re.compile(r"\.value\.splice\("),
    re.compile(r"\.value\s*=\s*[\w.]+\.value\.filter"),
    re.compile(r"\.value\s*=\s*.*\.filter\("),
]

# 调后端
API_CALL_RE = re.compile(r"\b[A-Za-z_$][\w$]*Api\s*\.\s*\w+\s*\(")
REQUEST_CALL_RE = re.compile(r"\brequest\s*\.\s*(get|post|put|delete|patch)\s*\(")


def extract_body(src, start):
    """从 '{' 后开始配平取出函数体"""
    i = start
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
    return src[start:j - 1]


def main():
    strict = "--strict" in sys.argv

    files = sorted(list(WEB_SRC.rglob("*.vue")) + list(WEB_SRC.rglob("*.ts")))
    problems = []

    for f in files:
        try:
            src = f.read_text(encoding="utf-8")
        except Exception:
            continue
        for m in FUNC_RE.finditer(src):
            name = m.group(1)
            if not DELETE_NAME_RE.search(name):
                continue
            body = extract_body(src, m.end() - 1)
            line = src[:m.start()].count("\n") + 1

            mutates_local = any(r.search(body) for r in LOCAL_MUTATE_RES)
            calls_api = bool(API_CALL_RE.search(body) or REQUEST_CALL_RE.search(body))
            shows_success = bool(re.search(r"ElMessage\.success", body))

            if mutates_local and not calls_api:
                problems.append((f.relative_to(ROOT), line, name, shows_success))

    print("=" * 72)
    print("「假删除」巡检（点了删除不能只改本地数组）")
    print("=" * 72)
    print(f"扫描文件：{len(files)} 个")
    print()

    if problems:
        print(f"🔴 发现 {len(problems)} 处「假删除」：\n")
        for rel, line, name, success in problems:
            print(f"  ❌ {rel}:{line}  {name}()")
            print(f"       只改本地数组（filter/splice），**没调后端**")
            if success:
                print(f"       ⚠️ 还弹了 ElMessage.success → **明确在骗人**")
            print(f"       → 用户以为删掉了，**其实数据一条没动**")
            print(f"       → 刷新页面原样回来 → 后续动作（释放库存/停用/通知）全都没发生")
            print(f"       → 正确做法：**真调后端 delete 接口**，失败就**不弹成功**")
            print()
    else:
        print("✅ 没发现「假删除」")
        print()

    print("=" * 72)
    print(f"结论：假删除 {len(problems)} 处")
    print("=" * 72)

    if problems and strict:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
