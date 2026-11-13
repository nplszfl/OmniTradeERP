#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 70 道闸门：构建产物 / 机器专属文件**不许进仓库**。

📌 **为什么需要这道闸门**（2026-11-13 23:45 实测踩出来的真缺陷）：

并行 session 跑完新闸门后执行了 `git add -A` → **无差别暂存**把 Python 生成的
`scripts/__pycache__/check-frontend-v-html.cpython-314.pyc` 一起带进了索引 → **下一次提交就会进仓库**。

危害有三条（都不是「审美问题」）：

    ① **机器专属** —— `cpython-314` 是 Python 3.14 的字节码标签，
       换台机器（3.11 / 3.12）就是**纯垃圾**，且和源码不同步。
    ② **每次都变** —— 每次跑闸门字节码就变一次 → 仓库反复 churn →
       `git log` 被无意义的二进制 diff 淹没 → **真变更被埋掉**。
    ③ **git 历史洗不掉** —— 提交进去就长期留着（规则 140 那一类后果）。

📌 **只加 `.gitignore` 是不够的**（规则 156：**闸门的扫描范围就是它的保护范围**）：

`.gitignore` 只挡住**还没被跟踪**的文件。一旦某个产物**已经进了历史**，
或者有人用 `git add -f` 强行加进来 → `.gitignore` **完全失效**，而**没有任何闸门会响**。
→ 仓库里**当时没有**任何闸门管这一类 → 这就是覆盖缺口。

📌 **本闸门的判据**：

| 断言 | 内容 |
|---|---|
| **C1** | **被 git 跟踪的**文件里，不许出现构建产物 / 机器专属文件（判据 = `git ls-files`） |
| **C2** | `.gitignore` 必须**留着**对应的忽略规则 → 否则产物随时会再被 `git add -A` 带进来 |

📌 **为什么 C1 的真相来源是 `git ls-files` 而不是「扫磁盘」**（规则 128）：

「磁盘上有没有」≠「仓库里有没有」。工作区里**每个**开发机都有 `target/`、`node_modules/`、
`__pycache__/` —— 那是**正常的**，是 `.gitignore` 在起作用。
📌 闸门要抓的是「**已经进了版本库**」的那些 —— 判据必须落在**跟踪状态**上。
（而且这样判据**与平台无关**：本地和 CI 的磁盘布局不同，跟踪状态相同。）

📌 **为什么 C2 要单独判**：
`.gitignore` 里的规则是**唯一能挡住「还没进版本库」的产物**的东西。
它被谁删掉（重构 `.gitignore` 时手滑）→ 产物立刻会重新被 `git add -A` 捎进来 → **必须有人拦**。

📌 **判据的等价写法**（规则 100 / 117 —— 每类产物都要认全）：

| 类别 | 认的形态 |
|---|---|
| Python 字节码 | `__pycache__/` 目录、`*.pyc`、`*.pyo` |
| Java 编译产物 | `*.class`、`target/` 目录 |
| 前端依赖/构建 | `node_modules/`、`dist/` 目录 |
| 日志 | `*.log` |
| 本地环境变量（含密钥）| `.env`、`.env.local`、`.env.*`（**`.env.example` 除外** —— 那是模板，该进仓库）|
| IDE / 系统 | `.DS_Store`、`*.iml`、`.idea/`、`*.swp` |
| 打包产物 | `*.jar`、`*.war` |

📌 **「除外」清单很重要**（规则 100 的反向）：`.env.example` 是**故意**进仓库的模板
（规则 156：最省解法 = `.env.example`）→ 判据若把它当泄露 → **假阳性** → 逼人放松闸门。

用法：
    python3 scripts/check-build-artifacts.py
退出码：0 = 通过；1 = 发现问题。
"""

from __future__ import annotations

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── 产物类别 → (跟踪文件的判定正则, .gitignore 里必须存在的规则片段) ──────
# 📌 `.gitignore` 的判据用**片段**而不是整行 → 允许写成 `*.py[cod]` 这类等价形态
#    （规则 100：等价写法必须一起认）。
ARTIFACT_CLASSES = [
    ("Python 字节码", r"(^|/)__pycache__/|(^|/)[^/]+\.py[co]$", ["__pycache__/", "*.py[cod]"]),
    ("Java 编译产物", r"(^|/)target/|(^|/)[^/]+\.class$", ["target/"]),
    # 📌 **本地 Maven 仓库**（2026-11-14 05:20 实测发现：仓库里被跟踪了 1175 个 `.m2/` 文件 / 54 MB）
    #    → 机器专属 + 每次依赖变动都变 + 与 pom 声明的版本**对不上**（提交的是 3.3.5，pom 写 3.1.5）
    ("本地 Maven 仓库", r"(^|/)\.m2/", [".m2/"]),
    ("前端依赖/构建", r"(^|/)node_modules/|(^|/)dist/", ["node_modules/", "dist/"]),
    ("日志", r"(^|/)[^/]+\.log$", ["*.log"]),
    # 📌 `.env.example` / `.env.template` 是**故意**进仓库的模板 → 必须排除
    ("本地环境变量", r"(^|/)\.env($|\.(?!example|template|sample)[^/]*$)", [".env"]),
    ("IDE / 系统", r"(^|/)\.DS_Store$|(^|/)\.idea/|(^|/)[^/]+\.iml$|(^|/)[^/]+\.swp$",
     [".DS_Store", ".idea/", "*.iml"]),
    ("打包产物", r"(^|/)[^/]+\.(jar|war)$", []),
]

# 📌 **`.gitignore` 片段的等价形态** —— 片段匹配用的是「该规则存在」而不是逐字相等，
#    所以 `*.py[cod]` 与 `*.pyc` 要算同一个意思（规则 100）。
IGNORE_EQUIVALENTS = {
    "*.py[cod]": ["*.py[cod]", "*.pyc", "*.pyo", "*.py[co]"],
    "*.log": ["*.log", "*.log*"],
    ".env": [".env", ".env*"],
    ".DS_Store": [".DS_Store", ".DS_Store*"],
    ".idea/": [".idea/", ".idea"],
    "*.iml": ["*.iml"],
}


def git_tracked_files():
    """返回被 git 跟踪的文件列表。

    📌 **用 `git ls-files` 而不是扫磁盘** —— 判据必须落在「**已经进了版本库**」上（规则 128）。
    📌 git 不可用 → 返回 None → 调用方**必须** fail-closed（不能当成「没发现问题」放行，
       规则 43：WARN 级别的「没查到」= 虚假安全感）。
    """
    try:
        r = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=ROOT, capture_output=True, text=True, timeout=120,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if r.returncode != 0:
        return None
    return [p for p in r.stdout.split("\0") if p]


def gitignore_rules():
    """`.gitignore` 里所有非注释、非空规则。"""
    path = os.path.join(ROOT, ".gitignore")
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8", errors="ignore") as f:
            raw = f.read()
    except OSError:
        return None
    out = []
    for line in raw.split("\n"):
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        out.append(s)
    return out


def rule_present(rules, fragment):
    """`.gitignore` 里有没有这条规则（含等价形态）。"""
    for alt in IGNORE_EQUIVALENTS.get(fragment, [fragment]):
        for r in rules:
            if r == alt or r.rstrip("/") == alt.rstrip("/"):
                return True
            # 目录写法：`__pycache__` 与 `__pycache__/` 等价
            if alt.endswith("/") and r.rstrip("/") == alt.rstrip("/"):
                return True
    return False


def main() -> int:
    print("📋 巡检「构建产物 / 机器专属文件被提交进仓库」")

    errors = []

    # ── C1：跟踪文件里不许有产物 ────────────────────────────────────────
    tracked = git_tracked_files()
    if tracked is None:
        print("🔴 拿不到 git 跟踪文件列表（git 不可用或不在仓库里）")
        print("   → **不能当成「没发现问题」放行** —— 闸门要么查，要么失败（规则 43）")
        print("   → ✅ 修法：确保在仓库根目录、且 `git` 在 PATH 里")
        return 1

    print(f"ℹ️  被跟踪文件：{len(tracked)} 个")
    print(f"ℹ️  真相来源：`git ls-files`（**不是扫磁盘** —— 工作区里有产物是正常的）\n")

    hits = {}
    for rel in tracked:
        for name, pattern, _ in ARTIFACT_CLASSES:
            if re.search(pattern, rel):
                hits.setdefault(name, []).append(rel)
                break

    for name, _, _ in ARTIFACT_CLASSES:
        got = hits.get(name, [])
        if got:
            # 📌 **同类命中要合并计数，不能一个一个刷屏**（115 条一模一样的输出 = 没人看 = 闸门白做）
            #    → 只列前 5 条样本，剩下给总数 + 一条合并的修法（规则 99：闸门太吵就会被拆掉）
            for rel in got[:5]:
                errors.append(
                    f"[{rel}] **构建产物被提交进了仓库**（类别：{name}）\n"
                    "      → 危害：机器专属（换台机器就是垃圾）+ 每次都变（仓库反复 churn）\n"
                    "        + **git 历史洗不掉**（提交进去就长期留着）\n"
                    "      → ✅ 修法：`git rm --cached <路径>`（**只动索引**）+ 确认 `.gitignore` 有对应规则"
                )
            if len(got) > 5:
                errors.append(
                    f"[…] **同类还有 {len(got) - 5} 个**（类别：{name}，共 {len(got)} 个）\n"
                    f"      → 一并修法：`git rm -r --cached <该目录>`（**只动索引**，磁盘文件保留）"
                )

    # ── C2：`.gitignore` 必须留着对应规则 ──────────────────────────────
    rules = gitignore_rules()
    if rules is None:
        errors.append(
            "[.gitignore] 读不到 `.gitignore`（不存在或读失败）\n"
            "      → 它是**唯一**能挡住「还没进版本库的产物」的东西\n"
            "      → ✅ 修法：确认仓库根目录有 `.gitignore`"
        )
    else:
        missing = []
        for name, _, fragments in ARTIFACT_CLASSES:
            for frag in fragments:
                if not rule_present(rules, frag):
                    missing.append((name, frag))
        for name, frag in missing:
            errors.append(
                f"[.gitignore] 缺少忽略规则 `{frag}`（类别：{name}）\n"
                "      → 规则不在 → 产物会被 `git add -A` **顺手捎进下一次提交**\n"
                "        （2026-11-13 23:45 真的发生过：`.pyc` 被暂存）\n"
                f"      → ✅ 修法：在 `.gitignore` 加一行 `{frag}`"
            )

    if errors:
        print(f"🔴 发现 {len(errors)} 处「构建产物进了仓库 / 忽略规则缺失」：\n")
        for e in errors:
            print(f"   {e}\n")
        print(f"合计问题 {len(errors)} 项")
        print()
        print("📌 **只加 `.gitignore` 不够** —— 它只挡「还没被跟踪」的文件；")
        print("   已经进了历史、或被 `git add -f` 强加进来的，`gitignore` 完全失效 → 闸门兜底")
        return 1

    print("✅ 没有构建产物被提交进仓库；`.gitignore` 的忽略规则齐全")
    return 0


if __name__ == "__main__":
    sys.exit(main())
