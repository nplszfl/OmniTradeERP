#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「提交信息里不能出现工作流命令字面量」的闸门 —— 规则 138 的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过，2026-11-12 06:00）：

    commit `cd749be` 的提交信息里有一句：
        「测试 **1032** 全绿，`##[error]`=0。」          ← **字面量 `##[error]`**
    → `docker buildx build` 生成的 **provenance JSON** 里带着
      `github_event_payload.commits[].message`（**提交信息原文**）
    → 这份 JSON 被打进 CI 日志
    → 其中一行**正好以 `##[error]` 开头**（JSON 里的换行 / `\\r` 让它落到行首）
    → **GitHub 的日志解析器把它当成「工作流错误命令」**
    → 一次**全绿**的 CI，在 GitHub 页面上显示 **96 条红色 error 注解**，
       工具 `ci-log-stats.py` 也据此报「`##[error]` 计数 = 96」
    = **假的「服务端故障」**（规则 28 / 113 的同族：**指标在报警，但事情没坏**）
    📌 更糟：这类噪声会让「CI 红了」这条信号**彻底失去可信度**（告警疲劳）。

📌 **为什么不能靠「改掉 provenance」来根治**：
    provenance 是**供应链证明**，push 路径上有价值；关掉它属于用安全换观感。
    → ✅ 正确做法：**别让提交信息里出现工作流命令字面量**（本闸门），
       provenance 保持开启。

📌 **判据（等价写法必须一起认 —— 规则 100 / 117）**：
    | 形式 | 说明 |
    |---|---|
    | `##[error]` `##[warning]` `##[notice]` `##[debug]` | 旧式命令语法（**本项目踩的就是它**）|
    | `::error::` `::error file=x::` `::warning::` … | 新式命令语法 |
    → 大小写不敏感；**出现在提交信息的任何位置都算**（因为 JSON 渲染可能把它挪到行首）。

📌 **豁免**：确实要写这个字面量时（例如「修复 `##[error]` 计数」这种提交），
    在提交信息里加一行 `[wf-cmd-ok]` → 本闸门跳过该提交（并在输出里说明）。

📌 **查哪些提交**（两种来源，CI 里用第 1 种）：
    1. **`GITHUB_EVENT_PATH`**（push / pull_request 事件 JSON）→
       `commits[].message` + `head_commit.message` + `pull_request.title/body`
       → **这正是会被 provenance JSON 带进日志的那些文本**，最准。
       📌 与 git 深度无关（`actions/checkout` 默认 `fetch-depth: 1` 只有 1 个 commit，
       但事件的 `commits[]` 仍然是**全部**）。
    2. **git log**（本地跑时用）→ 默认查最近一次提交；`--base <sha>` 查区间；
       `--all` 查全部历史。

用法：
    python3 scripts/check-commit-message-workflow-commands.py            # 巡检（有问题 → exit 1）
    python3 scripts/check-commit-message-workflow-commands.py --verbose  # 打印每条判定细节
    python3 scripts/check-commit-message-workflow-commands.py --all      # 全历史体检
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys

# ── 工作流命令字面量 ───────────────────────────────────────────────
# 📌 旧式 `##[error]` 与新式 `::error::` 是**两套语法**，必须一起认（规则 100）
LEGACY_CMD_RE = re.compile(r"##\[(?:error|warning|notice|debug)\]", re.IGNORECASE)
MODERN_CMD_RE = re.compile(r"::(?:error|warning|notice|debug)(?:::|\s)", re.IGNORECASE)

EXEMPT_TOKEN = "[wf-cmd-ok]"

# 📌 **豁免必须「独占一行」** ——
#    否则「文档里提到了这个标记」也会被当成豁免 → **自己的说明文字把自己豁免了** → 假绿。
#    （实测踩过：闸门自己的提交信息里写了「豁免：写一行 `[wf-cmd-ok]`」→ 它把自己放过了。）
EXEMPT_RE = re.compile(r"^\s*" + re.escape(EXEMPT_TOKEN) + r"\s*$", re.MULTILINE)

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def git(*args: str) -> str:
    r = subprocess.run(
        ["git", "-C", REPO_ROOT, *args],
        capture_output=True, text=True, timeout=120,
    )
    return r.stdout if r.returncode == 0 else ""


def find_commands(text: str) -> list[str]:
    """返回文本里命中的工作流命令字面量（去重、保序）。"""
    hits: list[str] = []
    for m in LEGACY_CMD_RE.finditer(text):
        hits.append(m.group(0))
    for m in MODERN_CMD_RE.finditer(text):
        hits.append(m.group(0))
    out: list[str] = []
    for h in hits:
        if h not in out:
            out.append(h)
    return out


# ── 来源 1：事件负载（CI 里最准）────────────────────────────────────
def collect_from_event() -> list[tuple[str, str]]:
    """→ [(来源标签, 文本)]"""
    path = os.environ.get("GITHUB_EVENT_PATH")
    if not path or not os.path.exists(path):
        return []
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        return []

    out: list[tuple[str, str]] = []
    for c in (data.get("commits") or []):
        msg = (c or {}).get("message") or ""
        sha = ((c or {}).get("id") or "")[:8]
        if msg:
            out.append((f"commit {sha}" if sha else "commit", msg))
    hc = data.get("head_commit") or {}
    if hc.get("message"):
        out.append(("head_commit", hc["message"]))
    pr = data.get("pull_request") or {}
    if pr.get("title"):
        out.append(("pull_request.title", pr["title"]))
    if pr.get("body"):
        out.append(("pull_request.body", pr["body"]))
    return out


# ── 来源 2：git log（本地跑）─────────────────────────────────────────
def collect_from_git(base: str | None, all_history: bool) -> list[tuple[str, str]]:
    if all_history:
        raw = git("log", "--all", "--format=%H%x1f%B%x1e")
    elif base:
        raw = git("log", f"{base}..HEAD", "--format=%H%x1f%B%x1e")
    else:
        raw = git("log", "-n", "1", "--format=%H%x1f%B%x1e")
    out: list[tuple[str, str]] = []
    for rec in raw.split("\x1e"):
        rec = rec.strip("\n")
        if not rec or "\x1f" not in rec:
            continue
        sha, msg = rec.split("\x1f", 1)
        out.append((f"commit {sha[:8]}", msg))
    return out


def main() -> int:
    verbose = "--verbose" in sys.argv
    all_history = "--all" in sys.argv
    base = None
    if "--base" in sys.argv:
        i = sys.argv.index("--base")
        if i + 1 < len(sys.argv):
            base = sys.argv[i + 1]

    from_event = collect_from_event()
    items = list(from_event)
    source = "事件负载 GITHUB_EVENT_PATH" if from_event else ""
    # 📌 **只在「没有事件负载」时才回退到本地 git** ——
    #    否则两处会**各自独立报红**，一条消息被重复判、且事件级的豁免会被 git 级覆盖
    #    （实测踩过：事件里已豁免，但 git HEAD 那条仍报红 → 看起来像「豁免没用」）。
    #    📌 **为什么仍需要这个回退**：`pull_request` 事件的 payload **没有 `commits[]`**
    #    （只有 head sha），而 `actions/checkout` 默认 `fetch-depth: 1` → 本地也只有 1 个 commit
    #    → 两者合起来才能覆盖 push / PR 两种事件。
    if not items:
        head_msg = git("log", "-n", "1", "--format=%B")
        if head_msg.strip():
            items.append(("git HEAD", head_msg))
            source = "本地 git HEAD（事件负载无 commits[]）"
    if not items:
        items = collect_from_git(base, all_history)
        source = "git log" + ("（全部历史）" if all_history else (f"（{base}..HEAD）" if base else "（最近 1 个提交）"))

    print("📋 巡检「提交信息里不得出现工作流命令字面量」")
    print(f"ℹ️  来源：{source}")
    print(f"ℹ️  待查文本：{len(items)} 份")

    # 📌 **待查文本 == 0 → 不能报绿** ——
    #    「一条都没查到」和「都干净」在输出上都长得一样（规则 43 的变体）。
    #    实测踩过：把脚本拷到仓库外跑 → REPO_ROOT 指错 → 待查 0 份 → 报「✅ 全部干净」
    #    → **真空绿**。✅ 这里必须直接判红。
    if not items:
        print("\n❌ **一条提交信息都没取到** —— 无法判定。")
        print("   可能原因：")
        print("     ① 不在 git 仓库里 / 仓库浅克隆且事件负载里没有 `commits[]`")
        print("     ② `GITHUB_EVENT_PATH` 不存在或格式不对")
        print("   📌 **「没查到」≠「没问题」** —— 闸门要么查，要么失败（规则 43）。")
        return 1

    findings = []
    exempt = []
    for label, text in items:
        if EXEMPT_RE.search(text):
            exempt.append(label)
            continue
        cmds = find_commands(text)
        if cmds:
            findings.append((label, cmds, text))

    if verbose:
        for label, cmds, _ in findings:
            print(f"   ❌ {label}: {cmds}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处提交信息含工作流命令字面量：")
        for label, cmds, text in findings:
            print(f"   {label}: {', '.join(cmds)}")
            for line in text.split("\n"):
                if find_commands(line):
                    print(f"        {line.strip()[:150]}")
        print("\n📌 **为什么这是真问题**：")
        print("   docker buildx 的 provenance JSON 里带着 `github_event_payload.commits[].message`")
        print("   （**提交信息原文**）→ 它被打进 CI 日志 → 其中一行以 `##[error]` 开头")
        print("   → **GitHub 把它当成工作流错误命令** → 全绿的 CI 在页面上显示一片红。")
        print("\n✅ 修法（二选一）：")
        print("   ① **改写措辞** —— 描述这个字面量而不写它本身")
        print('      （例：「工作流 error 注解」而不是「`##[error]`」）')
        print(f"   ② **显式豁免** —— 在提交信息里加一行 `{EXEMPT_TOKEN}`")
        return 1

    if exempt:
        print(f"\nℹ️  有 {len(exempt)} 份文本带 `{EXEMPT_TOKEN}` 豁免标记 → 已跳过")
    print("\n✅ 所有提交信息都不含工作流命令字面量（CI 日志不会被误判成错误）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
