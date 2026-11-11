#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「声称做了写操作，却一个请求都没发」的闸门 —— 前端「假成功 / 假删除」的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过的坑，2026-11-11）：

    erp-web/src/views/logistics/Logistics.vue
    const handleAddChannel = async () => {
      try {
        ElMessage.success('渠道添加成功')   // ← 只弹提示
        showChannelDialog.value = false
        loadChannels()                       // ← 重新拉列表（渠道并不会出现）
      } catch (error) { ... }
    }

    → 用户点「添加渠道」→ 弹「渠道添加成功」→ 弹窗关闭 → 列表刷新 → **渠道根本没进去**
    → **后端连 `POST /logistics/channel` 这个端点都不存在**
    = **假成功**（规则 80 / 82）。

📌 **判据为什么是「窄语义」而不是「扫全部成功提示」**（规则 42 / 55 / 96 / 99）：
  第一版扫「所有 success/info 提示 + 往上 25 行有没有请求」→ **误报 2 处**：
    - `已选择 {渠道}，运费 $x`  → 它**真的**记录了 `selectedRate.value = rate`（局部状态变更）
    - `补货功能需要跳转到补货页面` → 它**本来就**是提示，不是「我做了某事」
  更严重的是：把「Vue 状态赋值」也算成「做了事」→ **原缺陷会被放过**
  （假 handler 里也有 `showChannelDialog.value = false`）→ **假绿**。
  ✅ 所以判据改成**窄语义**：只查**声称做了写操作**的成功提示
     （添加/创建/新建/保存/更新/修改/删除/提交/发布…成功），且要求**同一个函数体**里有真实动作。

📌 **判据**：
    C1 扫 `erp-web/src` 的 `.vue` / `.ts` / `.js`
    C2 取每条 `ElMessage.success(...)` / `$message.success(...)` / `ElMessage.info(...)`
       中**文本**匹配「写操作 + 成功」的（`(添加|创建|新建|保存|更新|修改|删除|移除|提交|发布|启用|停用).{0,6}成功`）
    C3 定位**所在函数体**（按花括号配平找最近的 `=> {` / `function xxx(`）
    C4 该函数体内若**没有** `await` / `request.` / `axios` / `xxxApi.method(` /
       `.post(` / `.put(` / `.delete(` / `.patch(` / `fetch(` → 判为**假成功**

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
  把 `handleAddChannel` 里的 `await logisticsApi.createChannel(...)` 删掉 → 本闸门必须 exit 1；
  恢复 → 必须 exit 0。

用法：
    python3 scripts/check-frontend-fake-success.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-frontend-fake-success.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_SRC = os.path.join(REPO_ROOT, "erp-web", "src")

SKIP_DIRS = {"node_modules", "dist", ".git"}

SUCCESS_RE = re.compile(
    r"(?:ElMessage|\$message|this\.\$message)\s*\.\s*(?:success|info)\s*\("
)

# 📌 「声称做了写操作」——窄语义，避免把「已选择」「需要跳转」这类误判成假成功
WRITE_CLAIM_RE = re.compile(
    r"(添加|创建|新建|保存|更新|修改|删除|移除|提交|发布|启用|停用|导入|上传)"
    r".{0,6}(成功|完成)"
)

# 「真的做了某事」的信号 —— 必须落在**同一个函数体**里
ACTION_SIGNALS = [
    re.compile(r"\bawait\b"),
    re.compile(r"\brequest\s*\."),
    re.compile(r"\baxios\b"),
    re.compile(r"\b\w*Api\s*\.\s*\w+\s*\("),
    re.compile(r"\.\s*(post|put|delete|patch)\s*\("),
    re.compile(r"\bfetch\s*\("),
    re.compile(r"\bsaveAs\s*\("),
    re.compile(r"\bnavigator\.clipboard\b"),
]

# 函数起点：箭头函数 / function 声明
FUNC_START_RE = re.compile(r"(?:const|let|var|async function|function)\s+\w+\s*(?:=\s*(?:async\s*)?)?\(?[^{;]{0,120}?=>\s*\{|function\s+\w+\s*\([^)]*\)\s*\{")
FUNC_DECL_RE = re.compile(r"(?:public|private|protected)?\s*(?:async\s+)?\w+\s*\([^)]*\)\s*(?::\s*[^{]+)?\{")
ARROW_RE = re.compile(r"=>\s*\{")


def iter_source_files():
    if not os.path.isdir(WEB_SRC):
        return
    for dirpath, dirnames, filenames in os.walk(WEB_SRC):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith((".vue", ".ts", ".js")):
                yield os.path.join(dirpath, fn)


def match_brace(text: str, open_idx: int) -> int:
    """返回与 open_idx 处 '{' 配对的 '}' 下标。"""
    depth = 0
    i = open_idx
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text) - 1


def enclosing_function(text: str, idx: int):
    """返回包含 idx 的最近函数体 (start, end) —— 找不到返回 None。"""
    best = None
    for m in ARROW_RE.finditer(text, 0, idx):
        brace = text.index("{", m.start())
        end = match_brace(text, brace)
        if brace <= idx <= end:
            if best is None or brace > best[0]:
                best = (brace, end)
    if best:
        return best
    for m in FUNC_DECL_RE.finditer(text, 0, idx):
        brace = text.index("{", m.start())
        end = match_brace(text, brace)
        if brace <= idx <= end:
            if best is None or brace > best[0]:
                best = (brace, end)
    return best


def extract_message(line: str) -> str:
    m = re.search(r"(?:success|info)\s*\(\s*(['\"`])(.+?)\1", line)
    return m.group(2) if m else ""


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []
    checked = 0

    for path in iter_source_files():
        try:
            text = open(path, encoding="utf-8").read()
        except Exception:
            continue
        rel = os.path.relpath(path, REPO_ROOT)
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if not SUCCESS_RE.search(line):
                continue
            msg = extract_message(line)
            if not msg or not WRITE_CLAIM_RE.search(msg):
                continue
            checked += 1
            fn = enclosing_function(text, text[:sum(len(x) + 1 for x in lines[:i])].rfind("\n") + 1)
            if fn is None:
                # 找不到函数体 → 无法判定，不报（避免把顶层代码误判）
                continue
            body = text[fn[0]:fn[1]]
            if any(sig.search(body) for sig in ACTION_SIGNALS):
                continue
            findings.append((rel, i + 1, msg[:80]))

    print("📋 巡检前端「声称做了写操作，却一个请求都没发」")
    print(f"ℹ️  扫到的「写操作 + 成功」提示：{checked} 处")
    if verbose:
        for rel, ln, s in findings:
            print(f"   {rel}:{ln}  {s}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「假成功 / 假删除」：")
        for rel, ln, s in findings:
            print(f"   {rel}:{ln}")
            print(f"        「{s}」—— 所在函数里没有任何 await / API 调用")
        print("\n✅ 修法：**真的调用后端接口**，等它成功了再弹提示；"
              "失败要弹 error 而不是 success（规则 80 / 82）")
        return 1

    print("\n✅ 所有「写操作 + 成功」的提示都有对应的真实请求")
    return 0


if __name__ == "__main__":
    sys.exit(main())
