#!/usr/bin/env python3
"""GitHub Actions workflow 合法性巡检。

背景（2026-11-04 实测踩坑）：
  `ci-cd.yml` 第 204 行写成了 `working: ./erp-web`（**少了个 `-directory`**），
  这不是合法 key → **GitHub 直接拒绝整个 workflow 文件**：

      Invalid workflow file: .github/workflows/ci-cd.yml
      (Line: 204, Col: 7): Unexpected value 'working'

  📌 **后果比想象的大**：
  - GitHub 连 job 都不创建 → 该 workflow 的 run **0 个 job**、**没有日志**
  - 整个 workflow **静默失效**，但 GitHub 上仍显示 "failure" 红色
  - 📌 **本项目实测：ci-cd.yml 从 2026-03-14 第一次运行起，
    连续 100 次 run 全部 failure，0 个 job，从未真正跑过一步**
  - 而 `code-quality.yml` 的 run 是**有 jobs 的** → 让人误以为「CI 有在跑」
  - → **「CI 形同虚设」但没有任何人发现**，因为它在 GitHub 上显示的是 failure，
    而不是「被跳过」。

📌 **本脚本的意义**：GitHub 的 workflow 校验发生在**服务端**，
本地 `yaml.safe_load` **能解析**（PyYAML 不校验 key 合法性）→ 本地全绿、
线上整个文件被拒。**必须有一个本地闸门补上这个缺口。**

---

## 检查项

1. **顶层 key** 必须是 name/on/env/defaults/concurrency/jobs/run-name/permissions
2. **job 级 key** 必须是合法集合
3. **step 级 key** 必须是合法集合（这里能抓出 `working:` 这种拼写错误）
4. step 不能同时有 `run` 和 `uses`
5. `uses:` 格式必须是 `owner/repo@ref`
6. `needs:` 必须指向**真实存在**的 job
7. `if:` 表达式里的 `refs/heads/xxx` 分支名必须**真实存在**
   （📌 本项目只有 master，写 `main` → job 永远被跳过 → 部署从不发生）
8. 关键步骤**不能有** `continue-on-error: true`
   （📌 闸门挂了但整体绿 = 白跑）

用法：python3 scripts/check-workflow-validity.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import subprocess
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(ROOT, ".github", "workflows")

TOP_KEYS = {"name", "on", "env", "defaults", "concurrency", "jobs", "run-name", "permissions"}

JOB_KEYS = {
    "name", "needs", "if", "runs-on", "permissions", "environment", "concurrency",
    "outputs", "env", "defaults", "steps", "container", "services", "strategy",
    "timeout-minutes", "continue-on-error", "uses", "with", "secrets",
}

STEP_KEYS = {
    "name", "id", "if", "uses", "run", "with", "env", "working-directory", "shell",
    "continue-on-error", "timeout-minutes",
}

USES_RE = re.compile(r"^[\w.\-]+/[\w.\-]+(/[\w.\-]+)*@[\w.\-]+$")

# 📌 这些 key 的名字**很接近**，手写时最容易打错
#    （本项目就踩过 `working` 应为 `working-directory`）
NEAR_MISS = {
    "working": "working-directory",
    "working_dir": "working-directory",
    "run-on": "runs-on",
    "uses-action": "uses",
    "needs-job": "needs",
    "continue_on_error": "continue-on-error",
    "timeout_minutes": "timeout-minutes",
}


def local_branches():
    """仓库真实存在的分支（远端 + 本地）。"""
    out = set()
    try:
        r = subprocess.run(
            ["git", "-C", ROOT, "branch", "-a", "--format=%(refname:short)"],
            capture_output=True, text=True, timeout=20)
        for line in r.stdout.splitlines():
            line = line.strip()
            if line and not line.startswith("HEAD ->"):
                out.add(line.replace("origin/", ""))
    except Exception:
        pass
    return out


def main():
    if not os.path.isdir(WF_DIR):
        print(f"❌ 找不到 {WF_DIR}")
        return 1

    files = sorted(f for f in os.listdir(WF_DIR) if f.endswith((".yml", ".yaml")))
    if not files:
        print(f"❌ {WF_DIR} 里没有 workflow 文件")
        return 1

    branches = local_branches()
    errors = []
    infos = []

    for fn in files:
        path = os.path.join(WF_DIR, fn)
        raw = open(path, encoding="utf-8").read()

        try:
            data = yaml.safe_load(raw)
        except Exception as e:
            errors.append(f"[{fn}] YAML 解析失败：{e}")
            continue
        if not isinstance(data, dict):
            errors.append(f"[{fn}] 顶层不是映射")
            continue

        # 顶层 key（YAML 1.1 会把 `on` 解析成布尔 True）
        for k in data:
            key = "on" if k is True else k
            if key not in TOP_KEYS:
                hint = f"  → 是不是想写 {NEAR_MISS[k]!r}？" if k in NEAR_MISS else ""
                errors.append(f"[{fn}] 顶层非法 key: {k!r}{hint}")

        jobs = data.get("jobs") or {}
        if not jobs:
            errors.append(f"[{fn}] 没有 jobs")
            continue

        for jn, job in jobs.items():
            if not isinstance(job, dict):
                errors.append(f"[{fn}] job '{jn}' 不是映射")
                continue

            for k in job:
                if k not in JOB_KEYS:
                    hint = f"  → 是不是想写 {NEAR_MISS[k]!r}？" if k in NEAR_MISS else ""
                    errors.append(f"[{fn}] job '{jn}' 非法 key: {k!r}{hint}")

            # needs 必须指向真实存在的 job
            needs = job.get("needs")
            if isinstance(needs, str):
                needs = [needs]
            for n in (needs or []):
                if n not in jobs:
                    errors.append(f"[{fn}] job '{jn}' 的 needs 指向不存在的 job: '{n}'")

            # if 里的分支名必须真实存在
            cond = job.get("if")
            if isinstance(cond, str):
                for br in re.findall(r"refs/heads/([\w.\-/]+)", cond):
                    if branches and br not in branches:
                        errors.append(
                            f"[{fn}] job '{jn}' 的 if 引用了**不存在的分支** "
                            f"'refs/heads/{br}'\n"
                            f"      → 该 job 在本仓库**永远不会被执行**\n"
                            f"      → 本仓库真实分支：{sorted(branches) or '（取不到）'}"
                        )

            for sn, step in enumerate(job.get("steps") or [], 1):
                if not isinstance(step, dict):
                    errors.append(f"[{fn}] job '{jn}' step#{sn} 不是映射")
                    continue
                sname = step.get("name", f"step#{sn}")
                for k in step:
                    if k not in STEP_KEYS:
                        hint = f"  → 是不是想写 {NEAR_MISS[k]!r}？" if k in NEAR_MISS else ""
                        errors.append(f"[{fn}] job '{jn}' step「{sname}」非法 key: {k!r}{hint}")
                if "run" in step and "uses" in step:
                    errors.append(f"[{fn}] job '{jn}' step「{sname}」同时有 run 和 uses")
                if "uses" in step and not USES_RE.match(str(step["uses"])):
                    errors.append(f"[{fn}] job '{jn}' step「{sname}」uses 格式可疑: {step['uses']!r}")
                # 📌 闸门步骤不许 continue-on-error → 挂了也得红
                if step.get("continue-on-error") is True:
                    body = str(step.get("run", "")) + str(step.get("uses", ""))
                    if "check-" in body or "巡检" in body:
                        errors.append(
                            f"[{fn}] job '{jn}' step「{sname}」是巡检闸门，"
                            f"却设了 continue-on-error: true\n"
                            f"      → 巡检挂了但整体仍绿 → **等于没跑**"
                        )

    # ── 检查 9：Dockerfile 与生成脚本一致 ───────────────────────────────
    gen = os.path.join(ROOT, "scripts", "gen-dockerfiles.py")
    if os.path.exists(gen):
        r = subprocess.run([sys.executable, gen, "--check"],
                           capture_output=True, text=True, cwd=ROOT, timeout=120)
        if r.returncode != 0:
            errors.append(
                "[docker/] Dockerfile 与 scripts/gen-dockerfiles.py **不一致**\n"
                "      → 说明有人直接改了 Dockerfile，但没改生成脚本\n"
                "      → 下次有人跑生成脚本，**改动会被无声覆盖**\n"
                "      → 正确姿势：改 scripts/gen-dockerfiles.py，然后跑它重新生成\n"
                + "      " + r.stdout.strip().replace("\n", "\n      ")
            )

    # ── 报告 ──────────────────────────────────────────────────────────
    print(f"📋 校验 {len(files)} 个 workflow 文件\n")

    if infos:
        for i in infos:
            print(f"ℹ️  {i}")
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处 workflow 合法性问题：\n")
        for e in errors:
            print(f"   {e}")
        print()
        print("📌 **GitHub 会直接拒绝整个 workflow 文件**（不是跳过，是不跑）：")
        print("   Invalid workflow file: .github/workflows/<file>")
        print("   → 0 个 job、0 行日志 → 这个 workflow **形同虚设**")
        print("   → 本地 `yaml.safe_load` **解析得通**（PyYAML 不校验 key）")
        print("   → 所以本地全绿 ≠ 线上能跑，**必须靠这个闸门兜底**")
        return 1

    print(f"✅ 所有 workflow 文件合法（{len(files)} 个）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
