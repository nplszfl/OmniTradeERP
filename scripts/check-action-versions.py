#!/usr/bin/env python3
"""GitHub Actions 引用的 action 版本是否存在（联网巡检）。

📌 **为什么需要这道闸门**：
2026-11-04 实测踩坑 —— 我把 Trivy action 固定成
    `aquasecurity/trivy-action@0.28.0`
（以为 tag 是 `0.28.0`），而**真实 tag 是 `v0.28.0`（带 v 前缀）** →

    ##[error]Unable to resolve action `aquasecurity/trivy-action@0.28.0`,
              unable to find version `0.28.0`

📌 **这个错误的杀伤力**：
- 挂在 **"Set up job"** 阶段 → **一个 step 都没跑** → 整个 job 直接死
- 挂在 **整份 workflow 文件** 的加载阶段 → 那个 job 根本不会被创建（0 job）
- 📌 本项目实测：`code-quality.yml` 的 Security Scan job **连续几个月全是这么挂的**，
  而它在 GitHub 上只显示一个笼统的 failure —— 没人知道是版本号写错了。

📌 **为什么不能用「本地能解析就行的思路」**：
`uses: owner/repo@v1.2.3` 在本地永远是**合法 YAML**，
只有 GitHub 在**运行时**去 GitHub 上拉才会发现版本不存在。
→ 必须**联网**去查真实 tag 列表。

📌 **不查 SHA 存在性**：只查 tag/branch 名是否存在，够用了。
SHA 拼错极少（而且拼错的 SHA 会报同样的 "Unable to resolve action"）。

---

## 检查项

对每个 workflow 里的 `uses: owner/repo@ref`：
- `@<tag>` → GitHub 上该 repo 是否有这个 tag
- `@<branch>` / `@<main|master|...>` → 是否有这个 branch
- `@<40位SHA>` → 跳过（SHA 只能靠 API 逐个查，成本高）

📌 **只查第三方 action，不查官方 marketplace 的**：
`actions/checkout`、`actions/setup-python` 等官方 action 一律存在，
查它们只是浪费 API 配额。但**仍然会验证格式**。

用法：
    python3 scripts/check-action-versions.py           # 联网检查
    python3 scripts/check-action-versions.py --offline # 只做格式检查（不联网）
退出码 0 = 全绿，1 = 有问题
"""
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(ROOT, ".github", "workflows")

USES_RE = re.compile(r"uses:\s*([\w.\-]+/[\w.\-]+(?:/[\w.\-]+)*)@([^\s#'\"]+)")

# 📌 **官方 action** —— 永远存在，只做格式检查不联网
OFFICIAL = (
    "actions/",
    "github/",
    "docker/",
    "codecov/",
    "sonarsource/",
)


def collect_uses(data):
    """从**已解析的 YAML** 里递归收集所有 `uses:` 的值。

    📌 **必须用 YAML 解析，不能用正则扫原文** ——
    第一版用正则扫全文，结果把**注释里**的 `uses: owner/repo@v0.28.0`
    也当成真引用 → 去查一个根本不存在的 repo → 查不到 →
    又因为「查不到就当未知、不判失败」→ **整条闸门假绿**。

    📌 教训（两个 bug 叠在一起）：
      ① 注释里的内容被当成真的（跟 check-feign-contract 的老毛病一模一样）
      ② 查不到就放过 → 两个 bug 互相掩护，闸门形同虚设。
    """
    out = []

    def walk(node):
        if isinstance(node, dict):
            for k, v in node.items():
                if k == "uses" and isinstance(v, str):
                    out.append(v)
                else:
                    walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(data)
    return out


def gh_api(path):
    """调 GitHub API。

    📌 **认证方式按优先级回退**：
    1. `GH_TOKEN` / `GITHUB_TOKEN` 环境变量（**CI 里就是 `GITHUB_TOKEN`**）
    2. 已登录的 `gh` CLI（本地开发）
    3. 匿名请求（只能查公开仓库，且**限流很严** —— 60 次/小时）

    📌 **实测踩坑（2026-11-04 CI）**：
    脚本原本只用 `gh` CLI → CI runner 上 gh **没登录** → 请求全部失败 →
    → 每个 action 都被判成「查不到这个 repo」→ `--strict` 直接失败。
    📌 而这**不是**仓库不存在，是**认证缺失** → 两件事不能混为一谈。
    """
    env = dict(os.environ)
    token = env.get("GH_TOKEN") or env.get("GITHUB_TOKEN")

    # 1) 带 token 的原生 HTTPS 请求（最可靠，CI 里一定可用）
    if token:
        try:
            req = urllib.request.Request(
                f"https://api.github.com{path}",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "User-Agent": "omni-erp-ci-gate",
                })
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception:
            pass

    # 2) 已登录的 gh CLI（本地开发）
    #    📌 注意：CI runner 上**可能压根没装 gh**，且没登录 ——
    #    `subprocess.run` 找不到可执行文件会抛 FileNotFoundError，
    #    **必须捕获**，否则整个脚本崩掉（而不是降级到匿名请求）。
    try:
        r = subprocess.run(
            ["gh", "api", path],
            capture_output=True, text=True, timeout=60, env=env)
        if r.returncode == 0:
            try:
                return json.loads(r.stdout)
            except Exception:
                return None
    except (FileNotFoundError, OSError):
        pass

    # 3) 匿名请求（公开仓库仍可查）
    try:
        req = urllib.request.Request(
            f"https://api.github.com{path}",
            headers={
                "Accept": "application/vnd.github+json",
                "User-Agent": "omni-erp-ci-gate",
            })
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None


def get_refs(repo):
    """返回 (tags集合, branches集合, repo是否存在)"""
    tags = set()
    branches = set()
    exists = None

    t = gh_api(f"repos/{repo}/tags?per_page=100")
    if isinstance(t, list):
        tags |= {x["name"] for x in t if "name" in x}
        exists = True

    b = gh_api(f"repos/{repo}/branches?per_page=100")
    if isinstance(b, list):
        branches |= {x["name"] for x in b if "name" in x}
        exists = True

    # 📌 直接查 repo 本身：**存在但没 tag/branch** 也是合法情况，
    #    与「查不到这个 repo」是两回事（后者 = 拼错或没权限）。
    meta = gh_api(f"repos/{repo}")
    if isinstance(meta, dict) and meta.get("full_name"):
        exists = True

    return tags, branches, exists


def get_action_yaml(repo, ref):
    """取回 action.yml / action.yaml 的内容（拿不到返回 None）。"""
    for name in ("action.yml", "action.yaml"):
        r = gh_api(f"repos/{repo}/contents/{name}?ref={ref}")
        if isinstance(r, dict) and r.get("content"):
            try:
                import base64
                return base64.b64decode(r["content"]).decode("utf-8", "ignore")
            except Exception:
                return None
    return None


def check_ref(repo, ref, tags, branches, label, errors, unknown):
    """核对某个 owner/repo@ref 是否存在。"""
    if ref in tags or ref in branches:
        return True
    # ⚠️ **不做「主版本号相同就算兼容」的宽容处理** ——
    #    📌 实测踩坑：第一版写了个「v0.36.0 和 v0.28.0 主版本都是 0 → 视为兼容」
    #       的宽容规则 → 结果 `@0.28.0`（真实 bug，少了 v 前缀）**被放过了** → 闸门假绿。
    #    📌 原因：**0.x 版本按 semver 规范就是不兼容的** ——
    #       0.28 到 0.36 之间可以有任意破坏性变更。
    #    📌 宽松匹配只允许一种：**ref 本身就是「单段主版本」**（如 v4），
    #       且该主版本确实存在对应 tag/branch —— 那是 action 仓库刻意维护的滚动版本。
    if re.fullmatch(r"v?\d+", ref):
        major = ref.lstrip("v")
        if any(t.lstrip("v") == major for t in tags | branches):
            return True
    errors.append(
        f"[{label}] `{repo}@{ref}` —— GitHub 上**没有这个 tag/branch**\n"
        f"      → GitHub 会在 \"Set up job\" 阶段直接报错：\n"
        f"        ##[error]Unable to resolve action `{repo}@{ref}`\n"
        f"      → **整个 job 一个 step 都不会跑**\n"
        f"      → 真实 tag 示例：{sorted(tags)[-5:] if tags else '（查不到）'}"
    )
    return False


def main():
    strict = "--strict" in sys.argv
    offline = "--offline" in sys.argv
    deep = "--deep" in sys.argv

    if not os.path.isdir(WF_DIR):
        print(f"❌ 找不到 {WF_DIR}")
        return 1

    files = sorted(f for f in os.listdir(WF_DIR) if f.endswith((".yml", ".yaml")))
    refs = {}          # repo -> (tags, branches)
    unknown = []       # (label, repo, ref)
    errors = []
    checked = 0
    nested_checked = 0

    for fn in files:
        raw = open(os.path.join(WF_DIR, fn), encoding="utf-8").read()
        try:
            data = yaml.safe_load(raw)
        except Exception as e:
            errors.append(f"[{fn}] YAML 解析失败：{e}")
            continue

        for uses in collect_uses(data):
            if "@" not in uses:
                continue
            repo, ref = uses.rsplit("@", 1)
            if repo.startswith(OFFICIAL):
                continue
            if ref.startswith("./") or ref.startswith("docker://"):
                continue
            if offline:
                continue
            if re.fullmatch(r"[0-9a-f]{40}", ref):
                # 📌 **SHA 也要验证** —— 固定 SHA 的好处是「不可变」，
                #    但上游把仓库删了/重建了，SHA 一样会失效。
                checked += 1
                r = gh_api(f"repos/{repo}/commits/{ref}")
                if r is None:
                    errors.append(
                        f"[{fn}] `{repo}@{ref}` —— **这个 SHA 解析不出来**\n"
                        f"      → 上游仓库被删/被重建 → GitHub 会报 Unable to resolve action\n"
                        f"      → **整个 job 一个 step 都不会跑**"
                    )
                continue

            if repo not in refs:
                refs[repo] = get_refs(repo)
            tags, branches, exists = refs[repo]
            if exists is None:
                # 📌 **API 查不动**（限流 / 没认证 / 网络不通）
                #    ⚠️ 这和「repo 不存在」是**两回事** —— 混为一谈就会误报。
                #    📌 实测踩坑（2026-11-04 CI）：CI 上没有认证 → 每个 action 都被
                #       误判成「不存在」→ --strict 直接失败 → 而它们全都在。
                #    ✅ 修法：优先用 GITHUB_TOKEN（CI 里一定有）。
                #    ✅ 仍然查不到时：**不判失败**，但要显眼地报出来
                #       （「静默跳过 = 假装检查过了」—— 本项目反复吃亏这个病）。
                unknown.append((fn, repo, ref))
                continue
            if not exists:
                errors.append(
                    f"[{fn}] `{repo}@{ref}` —— **GitHub 上没有这个 repo**\n"
                    f"      → repo 名拼错了\n"
                    f"      → GitHub 会报 Unable to resolve action，**整个 job 一个 step 都不跑**"
                )
                continue

            checked += 1
            if not check_ref(repo, ref, tags, branches, fn, errors, unknown):
                continue

            # 📌 **递归查一层**：这个 action 自己也引别的 action，
            #    那些引用同样必须存在。
            #    📌 实测踩坑（2026-11-04）：`aquasecurity/trivy-action@v0.28.0`
            #       自己引的是 `aquasecurity/setup-trivy@v0.2.1` ——
            #       而上游把那个 tag **删了**（只剩 v0.2.6 / v0.3.0 / v0.3.1）→
            #       → 顶层 action 存在，**但它一跑就炸**，
            #       错误信息还报的是**内层** action 的名字，
            #       排查时极容易看错地方。
            if not deep:
                continue
            yml = get_action_yaml(repo, ref)
            if not yml:
                continue
            for m in USES_RE.finditer(yml):
                nrepo, nref = m.group(1), m.group(2)
                if nrepo.startswith(OFFICIAL):
                    continue
                if re.fullmatch(r"[0-9a-f]{40}", nref):
                    # 📌 **SHA 也必须验证** —— 固定 SHA 的好处就是「不可变」，
                    #    但**上游可以删分支/tag**（SHA 指向的 commit 被 force-push 掉也会失效）→
                    #    → 实测踩坑：setup-trivy 的旧 tag 被上游删了，
                    #      同样的问题也会发生在 SHA 上（仓库被删/仓库被重建）。
                    nested_checked += 1
                    r = gh_api(f"repos/{nrepo}/commits/{nref}")
                    if r is None:
                        errors.append(
                            f"[{fn} → {repo}@{ref} 的内部依赖] `{nrepo}@{nref}` —— "
                            f"**这个 SHA 解析不出来**\n"
                            f"      → 上游仓库被删/被重建 → GitHub 会报 Unable to resolve action\n"
                            f"      → **整个 job 一个 step 都不会跑**"
                        )
                    continue
                if nrepo not in refs:
                    refs[nrepo] = get_refs(nrepo)
                ntags, nbranches, nexists = refs[nrepo]
                if nexists is None:
                    unknown.append((f"{fn} → {repo}@{ref}", nrepo, nref))
                    continue
                nested_checked += 1
                check_ref(nrepo, nref, ntags, nbranches,
                          f"{fn} → {repo}@{ref} 的内部依赖", errors, unknown)

    print(f"📋 校验 {len(files)} 个 workflow 里的 action 版本引用")
    print(f"   顶层核对 {checked} 个" + (f"，嵌套依赖核对 {nested_checked} 个" if deep else "（未查嵌套）"))
    print()

    if unknown:
        print(f"⚠️  {len(unknown)} 处**未能确认**（API 查不到该 repo —— 可能限流/私有）：")
        for fn, repo, ref in unknown:
            print(f"    {fn}: {repo}@{ref}")
        if not strict:
            print("    → 非 --strict 模式下**不算失败**，但请确认这些 repo 真实存在")
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处 action 引用问题：\n")
        for e in errors:
            print(f"   {e}")
        print()
        print("📌 **这类错误本地永远看不出来** —— YAML 合法，但 GitHub 拉不到 action")
        print("   → 挂的是 \"Set up job\" 阶段 → **一个 step 都没跑** → 整个 job 死")
        return 1

    if strict and unknown:
        # 📌 **strict 模式下「查不到」必须算失败** ——
        #    「没能核对」和「核对过没问题」是**两回事**。
        #    📌 本项目反复吃亏同一个病：**静默跳过 = 假装检查过了** ——
        #       闸门在 CI 里跑，API 限流了 → 一片 action 没查 → 闸门报绿
        #       → 真正的坏引用从旁边溜过去（本轮就差点这样：
        #          上一版脚本把「API 限流」和「repo 不存在」混为一谈 → 误报）。
        #    ✅ 正确做法：**传 GITHUB_TOKEN**（5000 次/小时，不容易被限流）
        #       + strict 下查不到就红 → 既不误报，也不放过盲区。
        print("🔴 strict 模式下有未能核对的引用 —— **本轮巡检存在盲区**，判失败\n")
        print("   → 多数情况是 API 限流或没配 GITHUB_TOKEN")
        print("   → 修法：给这个 step 传 `env: GITHUB_TOKEN: \${{ secrets.GITHUB_TOKEN }}`")
        return 1

    if offline:
        print("✅ 离线模式：只校验了格式（未联网查版本）")
    else:
        print(f"✅ 全部 action 版本引用有效（联网核对了 {checked} 个）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
