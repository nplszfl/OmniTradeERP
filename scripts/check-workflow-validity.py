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
9. **`if:` 表达式里不许出现 `secrets`**（job 级 + step 级都查）
   （📌 GitHub 的上下文可用性表里 `if` **拿不到 secrets** → 写成 `if: ${{ secrets.X != '' }}`
   会被 GitHub **拒收整个 workflow 文件**：`Unrecognized named-value: 'secrets'`
   → 又是「0 个 job、整个 workflow 作废」的老坑。
   ⚠️ **本地 YAML 解析得通** → 本地校验**发现不了**，只有 GitHub 才会拒收。
   ✅ 正解：job 级 `env: FOO: ${{ secrets.X }}` 中转 → step 级写 `if: env.FOO != ''`）
10. **Dockerfile 与生成脚本一致**（`docker/Dockerfile.*` 里的「根 pom 声明了 N 个 module」
    必须等于根 pom 实际的 module 数 → 根 pom 加模块后忘了跑 `scripts/gen-dockerfiles.py` 会红）
11. **job 的**必需键存在**（`runs-on` / `steps`）—— 缺任何一个 → GitHub **拒收整个 workflow**
    📌 **我自己踩过**（11-10 21:25）：批量给 job 插 \`timeout-minutes\` 的脚本
    把每个 job 的 \`runs-on\` 删掉了 → **三个 workflow 全部 0 秒失败**（0 个 job、0 行日志）
    → 而**所有闸门都绿** → 因为本闸门当时把 \`runs-on\` 放在**允许键白名单**里，
      却**从不检查它是否存在** → **只校验「键的值合不合法」，不校验「必需的键在不在」**（规则 88）
    ⚠️ 这类错误发生在**解析期** → **任何 job 里的闸门都跑不到** → CI 侧的闸门**结构上无法发现**它
    → ✅ 唯一能提前拦住的地方是**提交前**（本脚本可独立跑 + \`.githooks/pre-commit\`）

📌 **本文件与实现保持一致**：上面 11 条 = 代码里实际做的 11 项检查。
改实现时**同步改这里** —— 否则下一个维护者会按这份清单误判覆盖面（规则 44）。

用法：python3 scripts/check-workflow-validity.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

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
                # 📌 **`if` 表达式里不能出现 `secrets`** ——
                #    GitHub 的上下文可用性表：`if` 可用 github/needs/vars/inputs/
                #    strategy/matrix/job/runner/env/steps —— **没有 secrets**。
                #    写成 `if: ${{ secrets.X != '' }}` 会被 GitHub **拒收整个文件**：
                #        Unrecognized named-value: 'secrets'.
                #    → 又是「0 个 job、整个 workflow 作废」的老坑。
                #    ✅ 正解：job 级 `env: FOO: ${{ secrets.X }}` 中转，
                #       然后 step 级写 `if: env.FOO != ''`。
                if re.search(r"\bsecrets\s*\.", cond):
                    errors.append(
                        f"[{fn}] job '{jn}' 的 if 表达式里用了 secrets 上下文\n"
                        f"      if: {cond}\n"
                        f"      → GitHub 的 `if` **拿不到 secrets 上下文** → "
                        f"**整个 workflow 文件被拒收**（0 个 job）\n"
                        f"      → 正解：job 级 `env: FOO: ${{{{ secrets.X }}}}` 中转，"
                        f"再在 step 级写 `if: env.FOO != ''`"
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
                # 📌 step 级 `if` 同样**拿不到 secrets 上下文**（见上面 job 级的说明）
                scond = step.get("if")
                if isinstance(scond, str) and re.search(r"\bsecrets\s*\.", scond):
                    errors.append(
                        f"[{fn}] job '{jn}' step「{sname}」的 if 表达式里用了 secrets 上下文\n"
                        f"      if: {scond}\n"
                        f"      → GitHub 的 `if` **拿不到 secrets 上下文** → "
                        f"**整个 workflow 文件被拒收**（0 个 job）\n"
                        f"      → 正解：job 级 `env: FOO: ${{{{ secrets.X }}}}` 中转，"
                        f"再写 `if: env.FOO != ''`"
                    )
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

    # ── 检查 9b：Dockerfile 里「根 pom 声明了 N 个 module」的 N 必须**独立**核实 ──
    # 📌 **为什么要独立核实**（2026-11-11 02:45 实测踩过，规则 100）：
    #   检查 9 跑的是**生成脚本自己的 --check** → 生成器和校验器**用同一个方法**
    #   → 就算**方法本身是错的**，两边也会**互相印证** → **假绿**。
    #   实测：根 pom 里有一段**被注释掉**的 `<module>erp-assembly</module>`
    #   （故意不启用）→ 朴素正则数成 **27**，而 **Maven 真实 reactor 是 26**
    #   → 16 个 Dockerfile 全都写着「27 个 module」= **事实错误**，而闸门**全绿**。
    # ✅ 独立真值源 = **ElementTree 解析根 pom**（XML 注释在 ET 里天然不生效）
    #   → 与生成器的正则**实现不同** → 不可能「同样错」。
    #   📌 **反向验证**：把某个 Dockerfile 的 26 改成 27 → 本检查必须报红。
    try:
        pom_path = os.path.join(ROOT, "pom.xml")
        _ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        _root = ET.parse(pom_path).getroot()
        _real_modules = [e.text for e in _root.findall(".//m:modules/m:module", _ns) if e.text]
        docker_dir = os.path.join(ROOT, "docker")
        if os.path.isdir(docker_dir):
            for fn in sorted(os.listdir(docker_dir)):
                if not fn.startswith("Dockerfile."):
                    continue
                txt = open(os.path.join(docker_dir, fn), encoding="utf-8").read()
                m = re.search(r"根 pom 声明了\s*(\d+)\s*个 module", txt)
                if not m:
                    continue
                claimed = int(m.group(1))
                if claimed != len(_real_modules):
                    errors.append(
                        f"[docker/{fn}] 写着「根 pom 声明了 {claimed} 个 module」，"
                        f"但根 pom 实际启用的是 **{len(_real_modules)}** 个\n"
                        f"      → **事实错误**（Maven 的真实 reactor 才是真值，"
                        f"`mvn validate` 的 Reactor Build Order 可复核）\n"
                        f"      → ⚠️ 检查 9 只保证「Dockerfile == 生成脚本」——"
                        f"**生成器和校验器同样错时就是假绿**（规则 100）\n"
                        f"      → 修法：改 scripts/gen-dockerfiles.py，然后跑它重新生成"
                    )
    except Exception as e:
        print(f"❌ 检查 9b 解析根 pom 失败: {e}", file=sys.stderr)
        errors.append("[pom.xml] 检查 9b 无法独立核实 module 数 —— 闸门自身失效，必须报红")

    # ── 检查 11：job 的**必需键存在** ────────────────────────────────
    # 📌 缺 `runs-on` / `steps` → GitHub **拒收整个 workflow 文件** → 0 个 job、0 行日志
    #    → 其它 job 里的闸门**一个都跑不到** → 结构上无法在 CI 里发现
    for fn in files:
        try:
            d2 = yaml.safe_load(open(os.path.join(WF_DIR, fn), encoding="utf-8").read())
        except Exception:
            continue          # YAML 解析失败已在上面积累报错
        if not isinstance(d2, dict):
            continue
        for jn, job in (d2.get("jobs") or {}).items():
            if not isinstance(job, dict):
                continue
            for required in ("runs-on", "steps"):
                if required not in job:
                    errors.append(
                        f"[{fn}] job '{jn}' **缺少必需的 '{required}'**\n"
                        f"      → GitHub 会**拒收整个 workflow 文件** → **所有 job 全部 0 秒失败**\n"
                        f"      ⚠️ 解析期错误 → 任何 job 里的闸门都跑不到 → **闸门全绿也发现不了**\n"
                        f"      修法：给这个 job 补上 `{required}: ...`"
                    )
            if not job.get("runs-on"):
                errors.append(
                    f"[{fn}] job '{jn}' 的 `runs-on` **为空** → GitHub 拒收该 workflow"
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
