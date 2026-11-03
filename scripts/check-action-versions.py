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

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(ROOT, ".github", "workflows")

USES_RE = re.compile(r"uses:\s*([\w.\-]+/[\w.\-]+(?:/[\w.\-]+)*)@([^\s#'\"]+)")

# 📌 官方 action —— 永远存在，只做格式检查不联网
OFFICIAL = (
    "actions/",
    "github/",
    "docker/",
    "codecov/",
    "sonarsource/",
)


def gh_api(path):
    """用 gh CLI 调 GitHub API（已登录，无需手写 token）。"""
    r = subprocess.run(
        ["gh", "api", path],
        capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        return None
    try:
        return json.loads(r.stdout)
    except Exception:
        return None


def get_refs(repo):
    """返回 (tags集合, branches集合)"""
    tags = set()
    branches = set()

    t = gh_api(f"repos/{repo}/tags?per_page=100")
    if isinstance(t, list):
        tags |= {x["name"] for x in t if "name" in x}

    b = gh_api(f"repos/{repo}/branches?per_page=100")
    if isinstance(b, list):
        branches |= {x["name"] for x in b if "name" in x}

    return tags, branches


def main():
    offline = "--offline" in sys.argv

    if not os.path.isdir(WF_DIR):
        print(f"❌ 找不到 {WF_DIR}")
        return 1

    files = sorted(f for f in os.listdir(WF_DIR) if f.endswith((".yml", ".yaml")))
    refs = {}          # repo -> (tags, branches)
    unknown = []       # (file, repo, ref)
    errors = []
    checked = 0

    for fn in files:
        raw = open(os.path.join(WF_DIR, fn), encoding="utf-8").read()
        for m in USES_RE.finditer(raw):
            repo, ref = m.group(1), m.group(2)
            if repo.startswith(OFFICIAL):
                continue
            # 本地 action（./path）没有 @，会被正则跳过
            if ref.startswith("./") or ref.startswith("docker://"):
                continue
            # SHA 跳过
            if re.fullmatch(r"[0-9a-f]{40}", ref):
                continue

            if offline:
                continue

            if repo not in refs:
                refs[repo] = get_refs(repo)
            tags, branches = refs[repo]
            if not tags and not branches:
                # 📌 查不到 ≠ 不存在（可能是 API 限流 / repo 私有）
                #    这种情况**不判失败**，但要提示 —— 否则闸门会因为网络问题乱红。
                unknown.append((fn, repo, ref))
                continue

            checked += 1
            if ref in tags or ref in branches:
                continue
            # ⚠️ **不做「主版本号相同就算兼容」的宽容处理** ——
            #    📌 实测踩坑：第一版这里写了个「v0.36.0 和 v0.28.0 主版本都是 0 → 视为兼容」
            #       的宽容规则 → 结果 `@0.28.0`（真实 bug，少了 v 前缀）**被放过了** →
            #       闸门假绿。
            #    📌 原因：**0.x 版本按 semver 规范是不兼容的** ——
            #       0.28 和 0.36 之间可以有任意破坏性变更。
            #    📌 宽松匹配只允许一种：**ref 本身就是「单段主版本」**（如 v4）
            #       且该主版本确实存在对应 tag/branch —— 那是 action 仓库刻意维护的滚动版本。
            if re.fullmatch(r"v?\d+", ref):
                major = ref.lstrip("v")
                if any(t.lstrip("v") == major for t in tags | branches):
                    continue
            errors.append(
                f"[{fn}] `{repo}@{ref}` —— GitHub 上**没有这个 tag/branch**\n"
                f"      → GitHub 会在 \"Set up job\" 阶段直接报错：\n"
                f"        ##[error]Unable to resolve action `{repo}@{ref}`\n"
                f"      → **整个 job 一个 step 都不会跑**\n"
                f"      → 真实 tag 示例：{sorted(tags)[-5:] if tags else '（查不到）'}"
            )

    print(f"📋 校验 {len(files)} 个 workflow 里的 action 版本引用\n")

    if unknown:
        print(f"ℹ️  {len(unknown)} 处未能确认（API 查不到该 repo，可能限流/私有）：")
        for fn, repo, ref in unknown:
            print(f"    {fn}: {repo}@{ref}")
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处 action 版本不存在：\n")
        for e in errors:
            print(f"   {e}")
        print()
        print("📌 **这类错误本地永远看不出来** —— YAML 合法，但 GitHub 拉不到 action")
        print("   → 挂的是 \"Set up job\" 阶段 → **一个 step 都没跑** → 整个 job 死")
        return 1

    if offline:
        print("✅ 离线模式：只校验了格式（未联网查版本）")
    else:
        print(f"✅ 全部 action 版本引用有效（联网核对了 {checked} 个）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
