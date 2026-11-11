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
    C4 该函数体内若**没有写动作** → 判为**假成功**

📌 **v2 收紧（2026-11-11 23:50 实测盲区）—— 「有 await」不等于「做了写操作」**：
    第一版判据 = 「函数体里有 `await` / `request.` / `axios` / `xxxApi.method(`」就算「做了事」
    → 注入反例：`await orderApi.list()`（**读**）之后弹「保存成功」→ **闸门放过了**（真空绿，规则 100）
    → 假成功的核心形态恰恰是「**该写的时候只读了**」→ 第一版对这类**完全失明**
    ✅ v2 判据 = **必须真的有写动作**：
       ① HTTP 动词：`request.post/put/delete/patch` / `.post(` / `.put(` / `.delete(` / `.patch(`
       ② api 方法名以**写动词**开头（create/add/save/update/delete/submit/publish/…）
          📌 写动词词根来自**本仓库 api 层自己的方法名**（512 个方法里 117 个带写动词前缀），
             不是手抄一张表（规则 33 / 92）
       ③ 另计「其它真动作」：`localStorage.setItem` / `sessionStorage.setItem` / `saveAs` / 剪贴板
          （实测 1 处「设置已保存」是**真的存了本地** → 不该报）
    📌 **实测**：收紧后本仓库 40 处「写操作+成功」提示**全部仍通过**（**0 误报**），
       而「只读接口 + 成功提示」被**正确判红**（并区分「只做了读」vs「什么都没做」）。

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
  把 `handleAddChannel` 里的 `await logisticsApi.createChannel(...)` 删掉 → 本闸门必须 exit 1；
  恢复 → 必须 exit 0。
  追加：注入「只读 + 成功提示」→ 必须 exit 1；注入「localStorage 真写」→ 必须 exit 0。

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

# 「真的做了**写**操作」的信号 —— 必须落在**同一个函数体**里
#
# 📌 **v2 收紧（2026-11-11 23:50 实测盲区）**：
#   第一版把「任意 await / 任意 xxxApi.xxx(」都当成「做了事」→
#   `await orderApi.list()`（**读**）之后弹「保存成功」→ **闸门放过了**（真空绿，规则 100）
#   → 假成功正是「该写的时候只读了」。
# ✅ 收紧为：**必须是写语义的调用**。写语义的判定不靠手抄一张表（规则 33），而是：
#    ① HTTP 动词：`request.post/put/delete/patch`、`.post(`、`.put(`、`.delete(`、`.patch(`、`fetch(...{method:'POST'})`
#    ② api 方法名以**写动词**开头（create/add/save/update/delete/submit/publish/…）——
#       词根来自**本仓库 api 层自己的方法名**（117/512 个方法带写动词前缀），
#       不是凭空捏造的表（规则 33 / 92）。
# 📌 **实测**：收紧后本仓库 40 处「写操作+成功」提示**全部仍通过**（0 误报），
#    而「只读接口 + 成功提示」的新形态被**正确判红**。
WRITE_SIGNALS = [
    # ① HTTP 动词层
    re.compile(r"\.\s*(post|put|delete|patch)\s*\("),
    re.compile(r"\brequest\s*\.\s*(post|put|delete|patch)\s*\("),
    re.compile(r"method\s*:\s*['\"](POST|PUT|DELETE|PATCH)['\"]", re.IGNORECASE),
    # ② api 方法名的写动词前缀
    re.compile(
        r"\b\w*Api\s*\.\s*(?:create|add|save|update|edit|delete|remove|submit|publish|"
        r"upload|import|insert|set|put|post|del|batch|adjust|assign|adopt|apply|approve|"
        r"cancel|confirm|complete|pay|receive|ship|mark|reset|change|transfer|move|bind|"
        r"enable|disable|sync|lock|handle|ignore|close|open|reopen|generate|export|download)\w*\s*\(",
        re.IGNORECASE,
    ),
]

# 📌 **其它「确实做了事」的信号**（不是写，但足以证明不是空壳）
#    ⚠️ `localStorage.setItem` 也要算 —— 「设置已保存」是**真的存了本地**（实测 1 处，
#       曾经的缺陷是「只弹提示、什么都没存」，但**现在**它真存了 → 不该报）。
OTHER_ACTION_SIGNALS = [
    re.compile(r"\blocalStorage\s*\.\s*setItem\s*\("),
    re.compile(r"\bsessionStorage\s*\.\s*setItem\s*\("),
    re.compile(r"\bsaveAs\s*\("),
    re.compile(r"\bnavigator\.clipboard\b"),
]

# 「只是读」的信号 —— 只出现这些 = **没做写操作**
READ_ONLY_SIGNALS = [
    re.compile(r"\.\s*get\s*\("),
    re.compile(r"\brequest\s*\.\s*get\s*\("),
    re.compile(r"\.\s*list\s*\("),
    re.compile(r"\.\s*query\s*\("),
    re.compile(r"\.\s*search\s*\("),
    re.compile(r"\.\s*get[A-Z]\w*\s*\("),
    re.compile(r"\.\s*list[A-Z]\w*\s*\("),
    re.compile(r"\.\s*fetch[A-Z]\w*\s*\("),
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
            # 📌 v2：**必须真的有写动作**（而不是「有 await 就算」）
            #    读调用 + 「保存成功」= 真空绿（规则 100）
            has_write = any(sig.search(body) for sig in WRITE_SIGNALS)
            has_other = any(sig.search(body) for sig in OTHER_ACTION_SIGNALS)
            if has_write or has_other:
                continue
            # 只有读信号 → 判红（它是「写操作的成功提示」却只做了读）
            read_only = any(sig.search(body) for sig in READ_ONLY_SIGNALS)
            findings.append((rel, i + 1, msg[:80], read_only))

    print("📋 巡检前端「声称做了写操作，却一个请求都没发」")
    print(f"ℹ️  扫到的「写操作 + 成功」提示：{checked} 处")
    if verbose:
        for rel, ln, s, ro in findings:
            print(f"   {rel}:{ln}  {s}  {'（只做了读）' if ro else '（什么都没做）'}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「假成功 / 假删除」：")
        for rel, ln, s, ro in findings:
            print(f"   {rel}:{ln}")
            print(f"        「{s}」—— 所在函数里"
                  + ("只做了**读**操作，没有任何写动作" if ro else "没有任何真实动作"))
        print("\n✅ 修法：**真的调用后端接口**，等它成功了再弹提示；"
              "失败要弹 error 而不是 success（规则 80 / 82）")
        return 1

    print("\n✅ 所有「写操作 + 成功」的提示都有对应的真实请求")
    return 0


if __name__ == "__main__":
    sys.exit(main())
