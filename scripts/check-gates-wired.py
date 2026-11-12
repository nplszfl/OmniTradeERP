#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「闸门不跑 = 不存在」的闸门 —— 规则 29 的守门人。

📌 **为什么需要这道闸门**：

规则 29 说：**「闸门不跑 = 不存在」** —— 巡检脚本写了但没人跑，等于没写。
而「没人跑」有**两种**形态：

| 形态 | 后果 | 本项目真实发作 |
|---|---|---|
| **A** | 脚本**建好了**，但**没接进任何 workflow** | ✅ 真实发生过：第 42 道闸门（`check-internal-detail-leak.py`）建好后忘了注册 → 守护「SQL 栈泄露」的那道防线**根本没上线** |
| **B** | 脚本接进了 workflow，但**那个 job 从不触发**（分支写错 / 被 `if` 跳过） | ✅ 真实发生过：workflow 写 `on: [main, develop]` → 三个 workflow **一次都没被触发过** |

📌 **本闸门查 A（注册完整性）**：
**每一个 `scripts/check-*.py` 都必须至少被一个 workflow 的 `run:` 命令引用**。

📌 **为什么不查 B（触发完整性）**：
那要看 GitHub 的**实际运行记录**（哪个 run 跑了哪些脚本）→ 需要联网调 API，
且**本地跑不了** → 不适合做巡检脚本。
→ B 由 **CI 自己的日志** + 人工/heartbeat 核验（每轮用 `gh api` 对账「磁盘 N ↔ CI N」）。

📌 **判据（窄）**：

| 断言 | 内容 |
|---|---|
| **A** | `scripts/` 下每个 `check-*.py`，都必须出现在至少一个 `.github/workflows/*.yml` 的文本里 |

📌 **为什么用「文本里出现过」而不是「解析出真实的 run 命令」**：
后者要处理 YAML 的 `run: \|` 多行块、变量拼接、`${{ }}` 插值 → 判据复杂且容易假红。
→ 「**在 workflow 文件里被提到**」是一个**必要条件**（连提都没提 → 肯定没跑），
   它**不能**证明真跑了 —— 但真跑由 CI 日志对账兜底。
→ 📌 **这是「必要不充分」的判据，方向必须选对**：
   宁可**漏报**（真跑了但文本没提）也**不要误报**（没跑却报红）→ 误报会逼人放松闸门（规则 99）。

📌 **反向验证**（规则 40 / 75）：
① 新建一个 `scripts/check-not-wired-xxx.py`（不接进 workflow）→ A 报红；
② 把它接进任一 workflow → 报红消失。

用法：
    python3 scripts/check-gates-wired.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-gates-wired.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS_DIR = os.path.join(REPO_ROOT, "scripts")
WORKFLOW_DIR = os.path.join(REPO_ROOT, ".github", "workflows")

GATE_RE = re.compile(r"check-[a-z0-9\-]+\.py")


def main() -> int:
    verbose = "--verbose" in sys.argv
    if not os.path.isdir(SCRIPTS_DIR):
        print("❌ 找不到 scripts/ —— 目录结构变了？本闸门会变成瞎的")
        return 1
    if not os.path.isdir(WORKFLOW_DIR):
        print("❌ 找不到 .github/workflows —— 本闸门会变成瞎的")
        return 1

    gates = sorted(
        fn for fn in os.listdir(SCRIPTS_DIR)
        if fn.startswith("check-") and fn.endswith(".py")
    )
    if not gates:
        print("❌ scripts/ 下没有 check-*.py —— 本闸门会变成瞎的")
        return 1

    # 读所有 workflow 的全文
    wf_text = {}
    for fn in sorted(os.listdir(WORKFLOW_DIR)):
        if fn.endswith((".yml", ".yaml")):
            path = os.path.join(WORKFLOW_DIR, fn)
            wf_text[fn] = open(path, encoding="utf-8", errors="replace").read()

    # workflow 里**真正被调用**的闸门名（不是「提到」）
    # 📌 **2026-11-12 修**：原来只找「文件名出现过」→ **注释里提一句也算「已接线」**
    #    → 实测踩过：新闸门只在自己的**说明注释**里出现过 → 闸门接线闸门报「65/65 全接线」
    #    → 而它**其实一行都没跑**（规则 29：闸门不跑 = 不存在；规则 100：判据只认一种写法 = 双向失明）。
    # ✅ 现在只认**真正的调用行**：`python3 scripts/check-xxx.py`
    # 📌 **先剥掉注释行** —— 否则「说明注释里写了一句 `python3 scripts/check-x.py`」也算「已接线」
    #    （实测踩过：新闸门只在自己的说明注释里出现过 → 接线闸门报「65/65 全接线」→ 其实一行没跑）
    #    保留两种真实调用形态：① `python3 scripts/x.py` ② `run: python3 scripts/x.py`
    INVOKE_RE = re.compile(r"python3\s+scripts/(check-[a-z0-9\-]+\.py)\b")
    mentioned = set()
    for text in wf_text.values():
        code_only = "\n".join(
            ln for ln in text.split("\n") if not ln.lstrip().startswith("#")
        )
        mentioned.update(m.group(1) for m in INVOKE_RE.finditer(code_only))

    findings = []
    for g in gates:
        if g in mentioned:
            continue
        findings.append(
            f"[A] `{g}` **没有在任何 workflow 里被真正调用** → **它从来不会运行**"
            f" → 规则 29：「闸门不跑 = 不存在」"
            f"\n     📌 真实发生过（11-10）：第 42 道闸门建好后忘了注册，"
            f"守护「SQL 栈泄露」的防线**根本没上线**。"
            f"\n     修法：把它加进 `.github/workflows/ci-cd.yml` 的契约巡检步骤"
            f"（和 code-quality.yml 的 Java Code Quality 步骤）。"
        )

    if findings:
        print("❌ 闸门接线闸门：发现 %d 个巡检脚本**从未被任何 workflow 调用**\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 规则 29：**闸门不跑 = 不存在**。写了但没人跑，等于没写。\n"
            "   （本闸门只查「有没有被提到」这个**必要条件**；\n"
            "    「真跑了」由 CI 日志对账兜底 —— 磁盘 N 道 ↔ CI 实跑 N 道。）"
        )
        return 1

    if verbose:
        for fn, text in sorted(wf_text.items()):
            n = len(set(GATE_RE.findall(text)))
            print(f"  {fn}: 提到 {n} 个闸门")
    print("✅ 闸门接线：%d 个巡检脚本**全部**被 workflow 引用（A）" % len(gates))
    return 0


if __name__ == "__main__":
    sys.exit(main())
