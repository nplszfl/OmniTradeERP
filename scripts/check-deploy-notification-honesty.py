#!/usr/bin/env python3
"""CI 部署通知的**诚实性**闸门 —— 「没部署」不许说成「部署成功」。

📌 **为什么需要这道闸门**（2026-11-10 实测）：

`ci-cd.yml` 的 `deploy-k8s` job 在**没配 `KUBE_CONFIG` secret** 时，
下面**每一个真实部署 step**（apply 清单 / set image / `Verify deployment`）
都会被 `if: steps.kube.outputs.configured == 'true'` **跳过** →

    ⚠️ 未配置 KUBE_CONFIG secret → 跳过部署（本仓库当前无任何 secret）
    ✅ 部署成功！            ← 7 秒后，同一次 run 里

→ **job 的结论是 `success`，但集群里一个 Deployment 都没 apply。**
→ 下游的 `Notify Deployment Status` 把 job 的 `success()` 直接翻译成「部署成功」→
**这是一次货真价实的「假成功」**（规则 82 的家族：说了「已 xxx」，但一个请求都没发）。

📌 **为什么不能只靠「加个 if」**：这次修完，下次有人加新 workflow / 改措辞，
同样的谎还能再撒一遍 → 必须有闸门。

📌 **判据设计**（对着「代码实际怎么写」来，不做泛扫）：

| 断言 | 内容 |
|---|---|
| **A** | workflow 里任何**宣告部署成功**的 step，其 `if` **必须依赖「是否真部署」的事实**（`outputs.` / `deployed` / `configured`） |
| **B** | 必须存在一个**「没部署」的分支**（引用同一个 output 且带 `!=`）→ 降级要看得见（规则 19） |
| **C** | 被引用的 output **不能悬空**：`needs.X.outputs.Y` → `jobs[X].outputs` 必须真的声明了 `Y` |

📌 **判据只管 workflow 的 step，不扫脚本正文**（刻意收窄，避免假阳性）：
「谎报部署成功」在 CI 里是**结构问题**（`if` 条件缺失），位置就在 workflow。
脚本层的等价物由 `scripts/check-deploy-scripts.py` 覆盖（它验证 `k8s/deploy.sh`
**不会在 apply 失败时打印「部署完成」**）。
⚠️ 反面教材（本次实测）：一开始把脚本正文也扫进去 → `check-deploy-scripts.py`
**自己的 docstring 里写着「部署完成」**（那是在**描述**这个缺陷）→ 闸门直接假红。
→ **假阳性会把下一个人逼去「放松闸门」**（规则 99）→ 宁可窄，不可贪。

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
把 notify step 退回成 `if: success()` 的无条件宣告 → 本闸门必须 exit 1。

用法：
    python3 scripts/check-deploy-notification-honesty.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-deploy-notification-honesty.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(REPO_ROOT, ".github", "workflows")

# 📌 「宣告成功」的**多种等价写法**都要认（规则 100：这个 API 有几种写法？我认了几种？）
CLAIM_RE = re.compile(
    r"部署成功|部署完成|部署已成功|"
    r"deploy(?:ed|ment)?\s+(?:was\s+)?success(?:ful(?:ly)?)?|"
    r"success(?:ful(?:ly)?)?\s+deploy",
    re.IGNORECASE,
)

# 📌 否定标记：**出现在宣告之前**才算否定（「这不是『部署成功』」不算一次宣告）
NEG_RE = re.compile(r"不是|并非|未|没|跳过|谎报|未部署|without|not\b|skip", re.IGNORECASE)

# 📌 「是否真部署」这个事实的**代表符号**（判据 A / B 的守卫）
GUARD_RE = re.compile(r"outputs\.|deployed|configured|DEPLOYED|CONFIGURED", re.IGNORECASE)

# 📌 workflow 里引用脚本的形态（用于把 claim 检测延伸到脚本正文）
SCRIPT_REF_RE = re.compile(r"[\w./-]+\.(?:sh|py)\b")


def _load_yaml(path: str):
    try:
        import yaml  # type: ignore
    except ImportError:
        print("❌ 缺少 PyYAML —— 请先 `pip install pyyaml`")
        sys.exit(2)
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def _strip_full_line_comments(text: str) -> str:
    """去掉**整行**的 shell 注释（行首允许空白，然后是 `#`）。

    📌 **为什么必须去**：workflow 的 `run: |` 块里可以写 shell 注释，
    而注释里经常要**描述**这类宣告（「…若照着 success() 打印『部署成功』＝假成功」）→
    不去就会把**说明文字**当成**真实宣告**（实测：闸门先把自己写的注释报了红）。
    📌 只去**整行**注释，不去行尾注释 —— 行尾的 `#` 可能落在字符串里。
    """
    out = []
    for line in text.splitlines():
        if line.lstrip().startswith("#"):
            continue
        out.append(line)
    return "\n".join(out)


def _claim_lines(text: str):
    """返回 [(行号, 行)] —— 这些行**宣告**了部署成功（否定式的不算）。"""
    out = []
    for idx, line in enumerate(text.splitlines(), start=1):
        for m in CLAIM_RE.finditer(line):
            prefix = line[: m.start()]
            if NEG_RE.search(prefix):
                continue  # 否定在宣告之前 → 不是宣告
            out.append((idx, line.strip()))
            break
    return out


def _script_bodies(text: str):
    """step 的 run 里引用到的、**仓库内真实存在**的脚本。

    📌 **刻意返回空** —— 判据只管 workflow 的 step（理由见模块 docstring）。
    保留这个函数是为了以后想扩展时有个明确的落点，**并提醒一句**：
    真的扫脚本正文时，`scripts/check-deploy-scripts.py` 的 docstring 会造成假阳性。
    """
    return {}


def check(verbose: bool = False):
    errors: list[str] = []
    notes: list[str] = []

    if not os.path.isdir(WF_DIR):
        print(f"❌ 找不到 workflow 目录：{WF_DIR}")
        return 1

    wf_files = sorted(
        f for f in os.listdir(WF_DIR) if f.endswith((".yml", ".yaml"))
    )
    if not wf_files:
        print(f"❌ {WF_DIR} 下没有任何 workflow 文件")
        return 1

    for fname in wf_files:
        path = os.path.join(WF_DIR, fname)
        doc = _load_yaml(path)
        if not isinstance(doc, dict):
            continue
        jobs = doc.get("jobs") or {}
        if not isinstance(jobs, dict):
            continue

        # ---- 收集「引用了某个 job output」的声明，供判据 C 核验 ----
        # 形如 needs.deploy-k8s.outputs.deployed
        declared_outputs = {
            jname: set((job.get("outputs") or {}).keys())
            for jname, job in jobs.items()
            if isinstance(job, dict)
        }

        for jname, job in jobs.items():
            if not isinstance(job, dict):
                continue
            steps = job.get("steps") or []
            if not isinstance(steps, list):
                continue

            for step in steps:
                if not isinstance(step, dict):
                    continue
                run = step.get("run") or ""
                if not isinstance(run, str) or not run.strip():
                    continue
                cond = str(step.get("if") or "")
                step_name = str(step.get("name") or "<无名 step>")

                # claim 可能写在 step 正文，也可能写在它调用的脚本里
                sites = [("<step>", _strip_full_line_comments(run))]
                for srel, body in _script_bodies(run).items():
                    sites.append((srel, body))

                for site_label, site_text in sites:
                    claims = _claim_lines(site_text)
                    if not claims:
                        continue

                    # 守卫可以来自 step 的 if，也可以来自脚本正文本身
                    guard_in_cond = bool(GUARD_RE.search(cond))
                    guard_in_body = bool(GUARD_RE.search(site_text))
                    if guard_in_cond or guard_in_body:
                        if verbose:
                            notes.append(
                                f"{fname}:{jname} / {step_name} → 宣告处({site_label})"
                                f" 有守卫（if={cond.strip() or '<无>'}）"
                            )
                        continue

                    where = "，".join(f"L{ln}" for ln, _ in claims)
                    errors.append(
                        f"{fname} → job `{jname}` → step「{step_name}」"
                        f"{'（脚本 ' + site_label + '）' if site_label != '<step>' else ''}\n"
                        f"      宣告了「部署成功」（{where}），"
                        f"但**既没有依赖 `if` 条件、也没有依赖「是否真部署」的事实**。\n"
                        f"      → `deploy-k8s` 没配 KUBE_CONFIG 时会跳过全部真实部署 step，"
                        f"job 结论仍是 `success` → 这里就会谎报成功。"
                        f"\n      修法：让宣告以 `needs.<deploy-job>.outputs.<deployed>` "
                        f"（或等价的「是否真部署」事实）为前提。"
                    )

            # ---- 判据 C：output 引用不能悬空 ----
            for step in steps:
                if not isinstance(step, dict):
                    continue
                cond = str(step.get("if") or "")
                for m in re.finditer(
                    r"needs\.([A-Za-z0-9_-]+)\.outputs\.([A-Za-z0-9_-]+)", cond
                ):
                    dep_job, dep_out = m.group(1), m.group(2)
                    if dep_job not in declared_outputs:
                        errors.append(
                            f"{fname} → job `{jname}` → step「{step.get('name')}」"
                            f"\n      引用了 `needs.{dep_job}.outputs.{dep_out}`，"
                            f"但 **`{dep_job}` 这个 job 不存在**。"
                        )
                    elif dep_out not in declared_outputs[dep_job]:
                        errors.append(
                            f"{fname} → job `{jname}` → step「{step.get('name')}」"
                            f"\n      引用了 `needs.{dep_job}.outputs.{dep_out}`，"
                            f"但 **`{dep_job}` 没有声明这个 output**"
                            f"（已声明：{sorted(declared_outputs[dep_job]) or '无'}）→ 引用悬空。"
                        )

            # ---- 判据 B：必须有「没部署」的分支 ----
            needs_list = job.get("needs") or []
            if isinstance(needs_list, str):
                needs_list = [needs_list]
            for dep in needs_list:
                if not isinstance(dep, str):
                    continue
                has_skip_branch = False
                for step in steps:
                    if not isinstance(step, dict):
                        continue
                    cond = str(step.get("if") or "")
                    if (
                        f"needs.{dep}.outputs." in cond
                        and "!=" in cond
                        and re.search(r"deploy|configured|跳过|未", cond, re.IGNORECASE)
                    ):
                        has_skip_branch = True
                        break
                if has_skip_branch:
                    continue
                # 只有当这个依赖**真的声明了**部署类 output 时才要求（否则不是部署链路）
                dep_outs = declared_outputs.get(dep, set())
                if not any(re.search(r"deploy", o, re.IGNORECASE) for o in dep_outs):
                    continue
                errors.append(
                    f"{fname} → job `{jname}` 依赖 `{dep}` 的部署事实，"
                    f"但**没有「没部署」的分支**\n"
                    f"      → 降级时调用方看不到原因（规则 19：降级必须带原因且能被调用方看见）。"
                    f"\n      修法：加一个 `if: needs.{dep}.outputs.<deployed> != 'true'` 的 step，"
                    f"明确说明「本次未部署」。"
                )

    if verbose:
        for n in notes:
            print(f"  · {n}")

    if errors:
        print("❌ 部署通知诚实性闸门：发现 %d 处问题\n" % len(errors))
        for e in errors:
            print("  • " + e)
            print()
        return 1

    print(f"✅ 部署通知诚实性：所有 workflow 的「部署成功」宣告都以「是否真部署」为前提")
    print(f"   （扫了 {len(wf_files)} 个 workflow 文件）")
    return 0


def main() -> int:
    verbose = "--verbose" in sys.argv or "-v" in sys.argv
    return check(verbose=verbose)


if __name__ == "__main__":
    sys.exit(main())
