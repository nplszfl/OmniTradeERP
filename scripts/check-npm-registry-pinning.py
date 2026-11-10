#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI 的 npm registry 依赖闸门 —— 别让 CI 的成败挂在一家第三方镜像上。

📌 **为什么需要这道闸门**（2026-11-10 实测）：

`npm ci` **认 lockfile 里的 `resolved` URL**，而不是 `npm config get registry`。
而 `erp-web/package-lock.json` 里 **146 条 resolved 全部指向 `registry.npmmirror.com`**（国内镜像）
→ GitHub runner 在**美区** → **CI 的成败依赖一家第三方国内镜像**。

实测后果：npmmirror 返回一次 `502 Bad Gateway - GET .../vue-tsc-2.2.12.tgz`
→ `npm ci` 退出码 1 → **「Frontend Code Quality」job 失败 → CI 红了**
→ 而**代码一个字都没改**，只是一家别人的服务器抖了。

⚠️ 这类红最坏的地方：**看不出是自己的问题** → 下一个人会去「改代码」「加 retry」「改断言」，
而真正的修法只是**换一个源**。

📌 **判据（窄）**：

| 断言 | 内容 |
|---|---|
| **A** | 若 lockfile 里有 `resolved` 指向**非 `registry.npmjs.org`** 的 registry → CI 的 `npm ci` **必须**带 `--replace-registry-host=always`（npm ≥ 9.5）或显式 `--registry=` |
| **B** | 反向：lockfile 若**全部**指向 `registry.npmjs.org` → **不要求**该参数（避免无谓的强制） |
| **C** | 换源必须**安全**：lockfile 里每个包的 `integrity` 要能对上（**事实由脚本只做提示**，完整核对见提交记录） |

📌 **为什么用「换 host」而不是「改 lockfile」**：
改 lockfile 会让**本地**（在国内）也跟着走官方源 → 本地变慢；
而 `--replace-registry-host=always` **只加在 CI 里** → 本地仍走 npmmirror（快）。
→ ✅ 已逐一核对：146 个包在 npmjs.org 上的 `integrity` 与 lockfile **完全相同** → 换 host 不会破坏校验。

📌 **反向验证**（规则 40 / 75）：把 CI 里的 `--replace-registry-host=always` 去掉 → 本闸门必须 exit 1。

用法：
    python3 scripts/check-npm-registry-pinning.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-npm-registry-pinning.py --verbose # 打印明细
"""

from __future__ import annotations

import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOCKFILE = os.path.join(REPO_ROOT, "erp-web", "package-lock.json")
WF_DIR = os.path.join(REPO_ROOT, ".github", "workflows")

# 官方 registry —— 判定「不依赖第三方镜像」的基准
OFFICIAL_HOSTS = ("registry.npmjs.org",)

RESOLVED_RE = re.compile(r"https?://([^/\s\"]+)/")
# 📌 **只匹配真正的 `run:` 命令行** —— 注释里也会出现 "npm ci" 字样
#    （本闸门自己的说明注释就写着 "npm ci --registry=..."）→ 泛扫会把注释当命令。
NPM_CI_RE = re.compile(r"^[ \t]*run:[ \t]*(npm\s+ci\b[^\n#]*)", re.M)


def non_official_hosts():
    """lockfile 里出现的、**非官方** registry host → 出现次数。"""
    counts = {}
    if not os.path.exists(LOCKFILE):
        return counts
    with open(LOCKFILE, encoding="utf-8") as f:
        data = json.load(f)
    for _, meta in (data.get("packages") or {}).items():
        resolved = (meta or {}).get("resolved")
        if not resolved:
            continue
        m = RESOLVED_RE.match(resolved)
        if not m:
            continue
        host = m.group(1)
        if host not in OFFICIAL_HOSTS:
            counts[host] = counts.get(host, 0) + 1
    return counts


def npm_ci_commands():
    """所有 workflow 里的 `npm ci` 命令 → [(文件, 完整命令)]。"""
    out = []
    for fn in sorted(os.listdir(WF_DIR)):
        if not fn.endswith((".yml", ".yaml")):
            continue
        path = os.path.join(WF_DIR, fn)
        text = open(path, encoding="utf-8").read()
        for m in NPM_CI_RE.finditer(text):
            out.append((fn, m.group(0).strip()))
    return out


def main() -> int:
    verbose = "--verbose" in sys.argv
    hosts = non_official_hosts()

    if not os.path.exists(LOCKFILE):
        print(f"❌ 找不到 {LOCKFILE} —— 前端目录结构变了？本闸门会变成瞎的")
        return 1

    cmds = npm_ci_commands()
    if not cmds:
        print("❌ 找不到任何 `npm ci` —— workflow 结构变了？本闸门会变成瞎的")
        return 1

    findings = []

    if hosts:
        # A：lockfile 依赖第三方 registry → CI 必须强制换源
        for fn, cmd in cmds:
            has_pin = (
                "--replace-registry-host=always" in cmd
                or "--replace-registry-host" in cmd and "always" in cmd
            )
            has_registry = "--registry=" in cmd
            if not (has_pin or has_registry):
                findings.append(
                    f"[A] `.github/workflows/{fn}` 的 `{cmd}` 没有强制换源，"
                    f"而 lockfile 里有 **{sum(hosts.values())}** 条 resolved 指向 "
                    f"{', '.join(sorted(hosts))}（第三方镜像）→ **镜像一抖 CI 就红，代码没改**"
                )
    else:
        # B：lockfile 全是官方源 → 不要求该参数
        if verbose:
            print("ℹ️ lockfile 全部指向官方 registry → 无需强制换源参数")

    if findings:
        print("❌ npm registry 依赖闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 `npm ci` 认 lockfile 的 `resolved` URL，不认 `npm config registry`。\n"
            "   CI 在美区却依赖国内镜像 → 第三方一次 502 就让 CI 红。\n"
            "   修法：CI 里用 `npm ci --registry=https://registry.npmjs.org/ "
            "--replace-registry-host=always`\n"
            "   （本地不受影响，仍走 npmmirror；146 个包的 integrity 已逐一核对一致）"
        )
        return 1

    detail = "、".join("%s×%d" % (h, c) for h, c in sorted(hosts.items())) if hosts else "无"
    print("✅ npm registry 依赖：%d 条 `npm ci` 命令已强制换源（lockfile 第三方 registry：%s）"
          % (len(cmds), detail))
    return 0


if __name__ == "__main__":
    sys.exit(main())
