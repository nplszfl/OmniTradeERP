#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""LLM **降级不许撒谎**的闸门 —— 规则 19 / 56 的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过的坑，见 MEMORY.md 规则 19）：

真实调用 LLM 失败（404 / 超时 / 鉴权错）→ catch → **静默**返回一段 canned 文本，
而响应里的 ``model`` 字段**仍然写着** ``deepseek-chat`` →
调用方**完全无法分辨真假** → **AI 从来没真的工作过，而界面看着完全正常。**

2026-11-04 修过一次（加 ``mock`` / ``mockReason`` 标志，``model`` 在降级时改写成 ``mock``，
模拟回复**不编造 token 用量**）—— 但**那只是「修好了」，不是「不会再坏」**。
⚠️ 没有任何闸门守着它 → 下一个人重构 ``AIAssistantServiceImpl`` 时，
把 ``setModel(MOCK_MODEL_NAME)`` 挪走 / 把 ``if (!answer.mock())`` 守卫删掉，
**四道已有闸门全绿，而 AI 又一次在骗人**（规则 29：闸门不跑 = 不存在；规则 51：手写结论会静默过期）。

📌 **判据设计**（对着「代码实际怎么写」来，不做泛扫）：

| 断言 | 内容 | 防的是 |
|---|---|---|
| **A1** | 带 ``model`` 字段的**响应 DTO** 必须同时声明 ``mock`` + ``mockReason`` | 调用方**拿不到**降级的可见性 |
| **A2** | 用到 ``setMock(`` 的服务必须存在 ``MOCK_MODEL… = "mock"`` 常量 | 没有「mock 这个 model 名」的统一事实来源 |
| **A3** | 每个 ``if (…mock())`` **降级分支**内必须同时有 ``setMockReason(`` + ``setModel(``（实参为 mock 标记） | **原始缺陷**：降级时 ``model`` 仍写真实模型名 |
| **A4** | 每个 ``setMock(`` 调用点必须在 12 行内被**降级处理**（``if (…mock())``）或本身是**透传**（``x.getMock()``） | 设了标志却没人处理 = 降级不可见 |
| **A5** | 出现 token 统计（``setPromptTokens`` / ``setTotalTokens``）的文件必须存在 ``if (!…mock())`` 守卫 | **原始缺陷**：模拟回复也按「长度/4」编造 token 数（假事实） |

📌 **判据只管 main 源码里的「LLM 降级契约」，不扫测试代码**（测试里 mock 仓库是另一回事）。

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
① 删掉降级分支里的 ``setModel(MOCK_MODEL_NAME)`` → 本闸门必须 exit 1；
② 删掉 ``if (!answer.mock())`` 守卫 → 本闸门必须 exit 1。

用法：
    python3 scripts/check-llm-degradation-honesty.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-llm-degradation-honesty.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_ROOT = os.path.join(REPO_ROOT, "erp-ai-assistant-service", "src", "main", "java")

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# ── 判据用的正则（都刻意收窄，避免假阳性）────────────────────────────
# 响应 DTO 里「model 字段」的声明：必须是 model 本身，不能是 modelType / modelName
# ⚠️ 字段可能带初值（`private Boolean mock = false;`）→ 声明正则必须容许 `= …`
DTO_MODEL_FIELD = re.compile(r"private\s+(?:String|Boolean)\s+model\s*(?:=[^;]*)?;")
DTO_MOCK_FIELD = re.compile(r"private\s+Boolean\s+mock\s*(?:=[^;]*)?;")
DTO_MOCK_REASON_FIELD = re.compile(r"private\s+String\s+mockReason\s*(?:=[^;]*)?;")

# mock 标记常量：MOCK_MODEL_NAME = "mock"（或 MOCK_MODEL / MOCK_MODEL_MARK 等）
MOCK_CONST = re.compile(r"MOCK_MODEL\w*\s*=\s*\"mock\"")

# 降级分支：if (answer.mock()) / if (x.isMock()) …
MOCK_BRANCH = re.compile(r"if\s*\(\s*[\w.]*\.mock\(\s*\)\s*\)")
MOCK_BRANCH_IS = re.compile(r"if\s*\(\s*[\w.]*\.isMock\(\s*\)\s*\)")
# 📌 **等价写法必须一起认**（规则 100/117 —— 只认一种写法 = 双向失明）
#    2026-11-14 14:56 实测**假红**：真实代码写的是
#        if (MOCK_MODEL_NAME.equals(row.getModel())) { out.setMock(TRUE); out.setMockReason(...); }
#    —— 这是**语义完全正确**的降级处理（model 就是 "mock"），
#    但上面两条正则都认不出 → A4 判成「setMock 没有对应降级处理」→ **假红**。
#    ⚠️ **假红比假绿危险**（规则 99）：会逼下一个人去「放松闸门」而不是补等价写法。
#    → 所以这里**扩充**认得的写法，绝不**放宽**判据。
#    ① `if (x.getMock())`        —— 与 .mock() 同义
#    ② `if (MOCK_MODEL_NAME.equals(...))` —— 用 mock 常量判断
#    ③ `if (....equals(MOCK_MODEL_NAME))` —— 同上，反过来写
#    ⚠️ **取反**（`if (!x.mock())` / `if (!MOCK...equals(...))`）是「**真实模型**」那条路，
#    **不算**降级分支 → 用 (?!\s*!) 排除。
MOCK_BRANCH_GET = re.compile(r"if\s*\(\s*(?!\s*!)[\w.]*\.getMock\(\s*\)\s*\)")
MOCK_BRANCH_CONST = re.compile(r"if\s*\(\s*(?!\s*!)[\w.]*MOCK\w*\s*\.\s*equals\s*\(")
MOCK_BRANCH_CONST_REV = re.compile(r"if\s*\(\s*(?!\s*!)[\w.]*\.\s*equals\s*\(\s*[\w.]*MOCK\w*\s*\)")

SET_MOCK = re.compile(r"\.setMock\s*\(")
SET_MOCK_REASON = re.compile(r"\.setMockReason\s*\(")
SET_MODEL = re.compile(r"\.setModel\s*\(\s*([\w.()]+|\"[^\"]*\")")
TOKEN_STAT = re.compile(r"\.(?:setPromptTokens|setCompletionTokens|setTotalTokens)\s*\(")
MOCK_GUARD = re.compile(r"if\s*\(\s*!\s*[\w.]*\.mock\(\s*\)\s*\)")

# 📌 **流式（Flux）形态的等价写法**（规则 100：同一个语义有几种写法？我认了几种？）
#    非流式：降级靠 DTO 的 mock / mockReason / model 三个字段表达；
#    流式：没有「每条 chunk 带 model」的 DTO → 降级靠**在流里先推一条说明**表达。
#    → 流式分支不要求 setModel（chunk 里根本没有 model 字段），但**必须把原因推出来**。
STREAM_EMIT = re.compile(r"emitter\.next\s*\(")
STREAM_REASON = re.compile(r"\.reason\(\)")

# 透传形态：out.setMock(response.getMock()) —— 只是复制标志，不负责写原因
PASSTHROUGH_MOCK = re.compile(r"\.setMock\s*\(\s*[\w.]+\.getMock\(\s*\)\s*\)")


class Finding:
    def __init__(self, rule: str, path: str, line: int, msg: str):
        self.rule = rule
        self.path = path
        self.line = line
        self.msg = msg

    def __str__(self) -> str:
        return f"[{self.rule}] {self.path}:{self.line} — {self.msg}"


def java_files():
    """全仓 main 源码里的 .java（不含测试）。"""
    out = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/java" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def rel(path: str) -> str:
    return os.path.relpath(path, REPO_ROOT)


def service_of(path: str) -> str:
    """从文件路径反推所属 maven 模块名。"""
    r = rel(path).split(os.sep)
    return r[0] if r else "?"


def block_after(lines, start_idx):
    """返回 if 条件所在行之后、与之花括号配平的块内容（不含条件行本身）。

    找不到配平花括号时返回 None（判据放弃该处 —— 宁可漏报不可假红，规则 99）。
    """
    depth = 0
    started = False
    chunk = []
    for i in range(start_idx, len(lines)):
        line = lines[i]
        chunk.append((i + 1, line))
        for ch in line:
            if ch == "{":
                depth += 1
                started = True
            elif ch == "}":
                depth -= 1
                if started and depth == 0:
                    return chunk
    return None


def check_dto_contract(files, findings):
    """A1：带 model 字段的响应 DTO 必须同时有 mock + mockReason。"""
    for path in files:
        r = rel(path)
        if os.sep + "dto" + os.sep not in path:
            continue
        base = os.path.basename(path)
        if "Response" not in base and "Result" not in base and "Reply" not in base:
            continue
        text = open(path, encoding="utf-8").read()
        if not DTO_MODEL_FIELD.search(text):
            continue
        if not DTO_MOCK_FIELD.search(text):
            findings.append(Finding(
                "A1", r, 1,
                "响应 DTO 有 model 字段却没有 mock 字段 → 调用方分辨不出这是不是真 AI 回的"))
        if not DTO_MOCK_REASON_FIELD.search(text):
            findings.append(Finding(
                "A1", r, 1,
                "响应 DTO 有 model 字段却没有 mockReason 字段 → 降级原因传不到调用方"))


def check_mock_constant(files, findings):
    """A2：用到 setMock( 的服务必须有 MOCK_MODEL… = \"mock\" 常量。"""
    by_service = {}
    for path in files:
        text = open(path, encoding="utf-8").read()
        if SET_MOCK.search(text):
            by_service.setdefault(service_of(path), []).append(path)
    for svc, paths in by_service.items():
        found = False
        for path in files:
            if service_of(path) != svc:
                continue
            text = open(path, encoding="utf-8").read()
            if MOCK_CONST.search(text):
                found = True
                break
        if not found:
            findings.append(Finding(
                "A2", rel(paths[0]), 1,
                f"服务 {svc} 用了 setMock( 但没有 MOCK_MODEL… = \"mock\" 常量 → "
                f"「降级时 model 该写成什么」没有统一事实来源"))


def check_degradation_branch(files, findings):
    """A3：每个 if (…mock()) 降级分支内必须写原因 + 把 model 改成 mock 标记。"""
    for path in files:
        text = open(path, encoding="utf-8").read()
        lines = text.splitlines()
        for idx, line in enumerate(lines):
            if not (MOCK_BRANCH.search(line) or MOCK_BRANCH_IS.search(line)):
                continue
            chunk = block_after(lines, idx)
            if chunk is None:
                continue
            body = "\n".join(l for _, l in chunk)
            # 📌 **流式形态**（规则 100）：降级靠「先推一条带原因的说明」表达，
            #    没有 model 字段可改 → 只要求**把原因推出来**。
            if STREAM_EMIT.search(body):
                if not STREAM_REASON.search(body):
                    findings.append(Finding(
                        "A3", rel(path), idx + 1,
                        "流式降级分支里没有把降级原因推给调用方 → "
                        "调用方会以为这是真 AI 流式输出（规则 19）"))
                continue
            if not SET_MOCK_REASON.search(body):
                findings.append(Finding(
                    "A3", rel(path), idx + 1,
                    "降级分支里没有 setMockReason( → 降级原因丢失，调用方看不见"))
            m = SET_MODEL.search(body)
            if not m:
                findings.append(Finding(
                    "A3", rel(path), idx + 1,
                    "降级分支里没有 setModel( → model 仍然是真实模型名，"
                    "**调用方会以为真调通了**（规则 19 的原始缺陷）"))
            elif "mock" not in m.group(1).lower():
                findings.append(Finding(
                    "A3", rel(path), idx + 1,
                    f"降级分支里 setModel({m.group(1)}) 的实参不是 mock 标记 → "
                    f"降级时 model 仍写真实模型名（规则 19 的原始缺陷）"))


def text_offset_of_line(text, idx):
    """第 idx 行的起始字符偏移（lines 是 text.splitlines() 的结果）。"""
    off = 0
    for i in range(idx):
        off += len(text.splitlines()[i]) + 1
    return off


def enclosing_block_head(text, pos):
    """
    返回**真正包含** pos 的那个块的开括号之前的文本（块头）。
    按括号配平往前找 —— 不能靠「往前看 N 行」（那会把上一个已结束的块当成守卫 → 假绿）。
    """
    depth = 0
    i = pos - 1
    while i >= 0:
        c = text[i]
        if c == '}':
            depth += 1
        elif c == '{':
            if depth == 0:
                j = i - 1
                while j >= 0 and text[j] not in ';}{':
                    j -= 1
                return text[j + 1:i]
            depth -= 1
        i -= 1
    return ""


def is_mock_guarded_block(head):
    """
    块头是不是一个「mock 判断」的 if —— 即 `if (…mock… ) {` 开头的块。
    ⚠️ 取反的 `if (!x.mock())` 是「**真实模型**」那条路，**不算**降级分支。
    """
    if not head:
        return False
    # 📌 块头里可能夹着**注释行**（如「// 📌 model == \"mock\" → …」在 if 上面）
    #    → 必须先剥注释，否则 `h.startswith(\"if\")` 会因为注释开头而落空。
    h = strip_comments(head).strip()
    if not h.startswith("if"):
        return False
    cond = h[2:].strip()
    if cond.startswith("("):
        cond = cond[1:]
    # 取反 → 不是降级分支
    if cond.lstrip().startswith("!"):
        return False
    return bool(
        MOCK_BRANCH.match(h) or MOCK_BRANCH_IS.match(h) or MOCK_BRANCH_GET.match(h)
        or MOCK_BRANCH_CONST.match(h) or MOCK_BRANCH_CONST_REV.match(h)
    )


def strip_comments(text):
    """剥掉块注释与行注释（只用于判据里的局部文本）。"""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return text


def check_mock_flag_handled(files, findings):
    """A4：每个 setMock( 调用点必须被降级处理，或本身是透传。"""
    for path in files:
        text = open(path, encoding="utf-8").read()
        lines = text.splitlines()
        for idx, line in enumerate(lines):
            if not SET_MOCK.search(line):
                continue
            if PASSTHROUGH_MOCK.search(line):
                continue
            window = "\n".join(lines[idx:idx + 13])
            # 📌 **向前找**（原有）：`setMock(x.mock()); if (x.mock()) { … }` —— 守卫在**后面**
            if (MOCK_BRANCH.search(window) or MOCK_BRANCH_IS.search(window)
                    or MOCK_BRANCH_GET.search(window)
                    or MOCK_BRANCH_CONST.search(window)
                    or MOCK_BRANCH_CONST_REV.search(window)):
                continue
            # 📌 **向后找**（2026-11-14 14:56 补）：`if (MOCK_MODEL_NAME.equals(x.getModel())) {`
            #    —— 守卫在**前面**，且 setMock 就在这个 if 块**里面**。
            #    ⚠️ 不能简单把窗口往前扩 N 行：那样会把「上一个已经结束的块里的
            #    if (…mock())」误当成本行的守卫 → **假绿**。
            #    ✅ 所以必须**按括号配平**找到**真正包含本行的那一个块**，再看它的块头。
            head = enclosing_block_head(text, text_offset_of_line(text, idx))
            if is_mock_guarded_block(head):
                continue
            findings.append(Finding(
                "A4", rel(path), idx + 1,
                "setMock( 之后没有对应的 if (…mock()) 降级处理 → "
                "标志设了但没人据此写原因 / 改 model（降级不可见）"))


def check_token_not_faked(files, findings):
    """A5：token 统计必须被 mock 守卫（模拟回复不得编造 token 用量）。"""
    for path in files:
        text = open(path, encoding="utf-8").read()
        if not TOKEN_STAT.search(text):
            continue
        if not MOCK_GUARD.search(text):
            findings.append(Finding(
                "A5", rel(path), 1,
                "有 token 统计却没有 if (!…mock()) 守卫 → "
                "**模拟回复也会编造 token 用量**（那是假事实，规则 19）"))


def main() -> int:
    verbose = "--verbose" in sys.argv
    files = java_files()
    findings = []

    check_dto_contract(files, findings)
    check_mock_constant(files, findings)
    check_degradation_branch(files, findings)
    check_mock_flag_handled(files, findings)
    check_token_not_faked(files, findings)

    if findings:
        print("❌ LLM 降级诚实性闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print(f"  {f}")
        print(
            "\n📌 降级必须**可被看见**（规则 19）：\n"
            "   · 响应里 model 要写成 mock（不是真实模型名）\n"
            "   · 要带 mockReason 说明为什么降级\n"
            "   · 模拟回复**不得编造 token 用量**（那是假事实）\n"
            "   · 原始缺陷：静默降级 + model 仍写 deepseek-chat → AI 从来没真工作过，界面却完全正常"
        )
        return 1

    if verbose:
        print("✅ LLM 降级诚实性：%d 个 main 源文件全部符合契约" % len(files))
    else:
        print("✅ LLM 降级诚实性：%d 个 main 源文件全部符合契约（A1-A5）" % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
