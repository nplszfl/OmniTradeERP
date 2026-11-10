#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""workflow 里引用的脚本必须**真实存在** —— 规则 93 的延伸。

📌 **为什么需要这道闸门**（实测踩过）：

规则 93 说「**文档里的命令是「代码」，也会撒谎**」
→ 这里是同一类问题的**另一个源头**：**.github/workflows/*.yml 里 `run:` 的命令**。

📌 **真实发作过**（2026-11-10 15:15）：

我给 CI 加了飞书通知脚本 `scripts/notify-deploy-status.py`，在 workflow 里写：

    run: python3 scripts/notify-deploy-status.py --status not-deployed

但 **`notify` job 没有 `actions/checkout` 步骤** → runner 工作目录里**没有仓库代码**
→ `python3: can't open file '.../scripts/notify-deploy-status.py': No such file or directory`
→ 退出码 2 → **整个 CI/CD workflow 变红**。

⚠️ **它特别有欺骗性的地方**：报错看起来像「**脚本坏了**」，
而真正的原因是「**这个 job 根本没 checkout**」→ 排查方向会被带偏很久。

📌 **判据**：

| 断言 | 内容 |
|---|---|
| **A** | workflow 里 `python3 <path>` / `python <path>` 引用的脚本，**文件必须真实存在** |
| **B** | 任何 `run:` 里**调用仓库内脚本**的 job，**必须先有 `actions/checkout` 步骤**（否则脚本不在工作目录里） |

📌 **为什么 B 是「job 级」而不是「step 级」**：
GitHub Actions 里每个 step 都在**同一个工作目录**执行；
`checkout` 把仓库放到 `$GITHUB_WORKSPACE` → **整个 job 后续的 step 都能用**
→ 所以判据是「**这个 job 里有 checkout**」，不是「checkout 在这一步之前」。

📌 **反向验证**（规则 40 / 75）：
① 删掉 `scripts/notify-deploy-status.py` → A 报红；
② 把 `notify` job 的 checkout 删掉 → B 报红。

用法：
    python3 scripts/check-workflow-script-refs.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-workflow-script-refs.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKFLOW_DIR = os.path.join(REPO_ROOT, ".github", "workflows")

# `run:` 里调用脚本的形态。
# 📌 **必须捕获完整路径**（含目录）：早先用 `(?:[^\s]*\/)?` 是**贪婪**的
#    → 把 `scripts/` 前缀一起吞掉 → 变成 `check-port-consistency.py`
#    → 去仓库根目录找 → **全部报「文件不存在」** → 85 条假阳性（规则 99）。
#    → 现在捕获完整 token，再判断它是不是「仓库内路径」。
PY_CALL_RE = re.compile(
    r"(?:python3?|py)\s+((?:[\w\-.]+\/)*[\w\-.]+\.py)\b"
)
# 仓库内脚本的路径前缀（本仓脚本都在这些目录下）
REPO_SCRIPT_DIRS = ("scripts/", "database/", "k8s/", "erp-web/", "docker/")


def workflow_files():
    if not os.path.isdir(WORKFLOW_DIR):
        return []
    return sorted(
        os.path.join(WORKFLOW_DIR, fn)
        for fn in os.listdir(WORKFLOW_DIR)
        if fn.endswith((".yml", ".yaml"))
    )


def iter_jobs(text):
    """粗粒度地切出 job：返回 [(job_name, job_body)]。

    📌 用缩进判断（job 级是 2 空格，step 是 6 空格）→ 不依赖 PyYAML，
    避免「YAML 里某个值含特殊字符」导致整个文件解析失败。
    """
    lines = text.splitlines()
    jobs = []
    cur_name = None
    cur_body = []
    in_jobs = False
    for line in lines:
        if re.match(r"^jobs:\s*$", line):
            in_jobs = True
            continue
        if in_jobs:
            m = re.match(r"^  ([A-Za-z0-9_\-]+):\s*$", line)
            if m:
                if cur_name:
                    jobs.append((cur_name, "\n".join(cur_body)))
                cur_name = m.group(1)
                cur_body = []
                continue
            if cur_name is not None:
                cur_body.append(line)
    if cur_name:
        jobs.append((cur_name, "\n".join(cur_body)))
    return jobs


def step_bodies(job_text):
    """切出每个 step 的 (name, body)。"""
    out = []
    lines = job_text.splitlines()
    cur_name = None
    cur_body = []
    started = False
    for line in lines:
        if re.match(r"^    - name:", line):
            if started:
                out.append((cur_name, "\n".join(cur_body)))
            cur_name = line.split(":", 1)[1].strip()
            cur_body = []
            started = True
            continue
        if re.match(r"^    - (uses|run):", line):
            if started:
                out.append((cur_name, "\n".join(cur_body)))
                cur_name = "<unnamed>"
                cur_body = []
            started = True
            continue
        if started:
            cur_body.append(line)
    if started:
        out.append((cur_name, "\n".join(cur_body)))
    return out


def main() -> int:
    verbose = "--verbose" in sys.argv
    files = workflow_files()
    if not files:
        print("❌ 找不到 .github/workflows —— 本闸门会变成瞎的")
        return 1

    findings = []
    checked_refs = 0
    # 📌 **按 (job, 脚本, 断言) 去重**（规则 96）：
    #    同一个 job 里 3 个 step 调同一个脚本 → 缺 checkout 是**同一个问题**
    #    → 报 3 次就是噪音 → 数字虚高会让人不信闸门。
    reported = set()

    for path in files:
        rel = os.path.relpath(path, REPO_ROOT)
        text = open(path, encoding="utf-8").read()

        for job_name, job_body in iter_jobs(text):
            steps = step_bodies(job_body)
            has_checkout = any(
                "actions/checkout" in (body or "") for _, body in steps
            )

            for step_name, step_body in steps:
                if not step_body:
                    continue
                for m in PY_CALL_RE.finditer(step_body):
                    script = m.group(1)
                    # 只管「仓库内路径」：必须带目录前缀，且是本仓的目录
                    if "/" not in script:
                        continue
                    if not script.startswith(REPO_SCRIPT_DIRS):
                        continue  # 系统路径 / 其它目录 → 不在本仓
                    checked_refs += 1
                    full = os.path.join(REPO_ROOT, script)
                    if not os.path.exists(full):
                        findings.append(
                            f"[A] `{rel}` 的 job `{job_name}` / step「{step_name}」"
                            f" 调用了 `{script}`，但**这个文件不存在**"
                            f" → runner 上 `python3 {script}` 直接报"
                            f" `No such file or directory`（退出码 2）→ **CI 变红**"
                        )
                    elif not has_checkout:
                        key = (job_name, script, "B")
                        if key in reported:
                            continue
                        reported.add(key)
                        findings.append(
                            f"[B] `{rel}` 的 job `{job_name}` 调用了仓库内脚本 `{script}`，"
                            f"但**这个 job 没有 `actions/checkout` 步骤**"
                            f" → runner 工作目录里**没有仓库代码** → 脚本不在 → "
                            f" `No such file or directory` → **CI 变红**"
                            f"\n     📌 实测（11-10 15:15）：就是这个坑。"
                            f"报错看起来像「脚本坏了」，真正原因却是「没 checkout」→ 排查方向会被带偏。"
                        )

    if findings:
        print("❌ workflow 脚本引用闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 workflow 里的 `run:` 命令**也是代码**，也会撒谎（规则 93 的延伸）。\n"
            "   两个致命形态：\n"
            "   ① 脚本文件不存在 → `No such file or directory` → 退出码 2 → CI 变红\n"
            "   ② job 没有 checkout → 仓库不在工作目录 → 脚本同样「不存在」"
        )
        return 1

    if verbose:
        print("检查了 %d 个 workflow、%d 处脚本引用" % (len(files), checked_refs))
    print("✅ workflow 脚本引用：%d 处引用全部存在，且所在 job 都有 checkout（A/B）" % checked_refs)
    return 0


if __name__ == "__main__":
    sys.exit(main())
