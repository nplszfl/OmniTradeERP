#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL init 脚本的**执行顺序**闸门 —— 规则 61 的守门人。

📌 **为什么需要这道闸门**（2026-11-10 实测）：

`docker-entrypoint-initdb.d/` 里的脚本由 docker-entrypoint **按文件名字母序**执行
（规则 61：**不是** compose 的挂载顺序）。每个文件是**一个独立的 mysql 会话**，
且**任何一条语句报错 → 从出错行起该文件剩余语句全部不执行**，而容器退出码可能仍是 0
→ 后面几百行种子**静默丢失**。

🔴 **实测抓到的真实缺陷**：
`database/seed-data.sql` 在两个 compose 里都挂成 `seed-data.sql`（**漏了 `zz-` 前缀**）
→ 它排第 **15** 位，而它 `INSERT INTO t_user / t_role / t_user_role / t_permission /
t_role_permission` 这些表由 `user-service.sql` 在第 **19** 位才创建
→ **在全新数据目录上**（`docker compose down -v` 后首次启动），
seed-data.sql 会「Table doesn't exist」→ **用户/角色/权限的种子静默丢失**
→ 登录没有账号、权限全空、多租户体系空转。

⚠️ **为什么一直没人发现**：initdb.d **只在数据目录为空时执行一次**
→ 已有 volume 的部署永远不重跑 → 这个缺陷**长期潜伏**，只在**新机器首次部署**时发作。

📌 **判据（对着「实际怎么跑」来，不看源码文件名）**：

| 断言 | 内容 |
|---|---|
| **A** | **执行顺序必须按 compose 挂载的「目标文件名」算**（不是源文件名！）—— 否则查的全是假的 |
| **B** | 种子脚本插入的每一张表，必须由一个**排在它之前**（或同一文件）的 init 脚本创建 |
| **C** | 种子脚本必须挂成 `zz-` 前缀（强制排到最后）—— 命名约定兜底，防止将来加新表时又漏 |

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
把 `zz-seed-data.sql` 的挂载名改回 `seed-data.sql` → 本闸门必须 exit 1。

用法：
    python3 scripts/check-init-sql-exec-order.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-init-sql-exec-order.py --verbose # 打印推导出的执行顺序
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INIT_DIR = "/docker-entrypoint-initdb.d"

MOUNT_RE = re.compile(
    r"\./(?P<src>[^\s:]+?\.sql):" + re.escape(INIT_DIR) + r"/(?P<dst>[^\s:]+?\.sql)"
)
CREATE_TABLE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?(?P<tab>\w+)[`\"]?", re.I
)
INSERT_RE = re.compile(
    r"INSERT\s+(?:IGNORE\s+)?INTO\s+[`\"]?(?P<tab>[\w.]+)[`\"]?", re.I
)


def find_compose_files():
    out = []
    for fn in sorted(os.listdir(REPO_ROOT)):
        if fn.startswith("docker-compose") and fn.endswith((".yml", ".yaml")):
            out.append(os.path.join(REPO_ROOT, fn))
    return out


def collect_mounts():
    """挂载目标名 → {(compose 文件, 源文件)}。

    📌 **按「目标文件名」归并** —— 同一个目标名在不同 compose 里可能来自不同源文件。
    """
    mounts = {}
    for cf in find_compose_files():
        text = open(cf, encoding="utf-8").read()
        for m in MOUNT_RE.finditer(text):
            src = m.group("src")
            dst = m.group("dst")
            if not os.path.exists(os.path.join(REPO_ROOT, src)):
                continue
            mounts.setdefault(dst, set()).add((os.path.basename(cf), src))
    return mounts


def created_tables(mounts):
    """表名（小写） → 创建它的 init 目标文件名集合。"""
    out = {}
    for dst, srcs in mounts.items():
        for _, src in srcs:
            text = open(os.path.join(REPO_ROOT, src), encoding="utf-8",
                        errors="replace").read()
            for m in CREATE_TABLE_RE.finditer(text):
                out.setdefault(m.group("tab").lower(), set()).add(dst)
    return out


def inserted_tables(mounts, dst):
    """某个 init 文件插入的表名集合（忽略注释行里的关键字）。"""
    out = set()
    for _, src in mounts[dst]:
        text = open(os.path.join(REPO_ROOT, src), encoding="utf-8",
                    errors="replace").read()
        for raw in text.splitlines():
            line = raw.strip()
            if line.startswith("--") or line.startswith("#"):
                continue
            m = INSERT_RE.search(line)
            if m:
                out.add(m.group("tab").lower().split(".")[-1])
    return out


def is_seed(dst):
    """种子脚本：源文件名以 seed- 开头，或目标名以 seed-/zz-seed- 开头。"""
    base = dst.lower()
    return "seed" in base


def main() -> int:
    verbose = "--verbose" in sys.argv
    mounts = collect_mounts()
    if not mounts:
        print("❌ 找不到任何 initdb.d 挂载 —— compose 结构变了？本闸门会变成瞎的")
        return 1

    # 📌 **执行顺序 = 目标文件名字母序**（docker-entrypoint 的真实行为）
    order = sorted(mounts)
    pos = {name: i for i, name in enumerate(order)}
    owners = created_tables(mounts)

    findings = []

    for dst in order:
        if not is_seed(dst):
            continue
        srcs = sorted({s for _, s in mounts[dst]})
        for tab in sorted(inserted_tables(mounts, dst)):
            creators = owners.get(tab)
            if not creators:
                findings.append(
                    f"[B] {dst} 插入的表 `{tab}` **没有任何 init 脚本创建** → 必然「Table doesn't exist」"
                )
                continue
            for c in sorted(creators):
                if pos[c] > pos[dst]:
                    findings.append(
                        f"[B] {dst}（第 {pos[dst]+1} 位）插入 `{tab}`，"
                        f"但建表在 {c}（第 {pos[c]+1} 位）→ **建表排在种子之后** → "
                        f"全新数据目录上这条种子会失败，其后语句静默丢失"
                    )

    for dst in order:
        if not is_seed(dst):
            continue
        if not dst.lower().startswith("zz-"):
            srcs = sorted({s for _, s in mounts[dst]})
            findings.append(
                f"[C] 种子 {dst}（源：{', '.join(srcs)}）**没有 `zz-` 前缀** → "
                f"按字母序排在第 {pos[dst]+1} 位 → 将来新增的建表文件一旦排在它后面就复发"
            )

    if findings:
        print("❌ MySQL init 执行顺序闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 docker-entrypoint 按**目标文件名**字母序执行 initdb.d 里的脚本；\n"
            "   种子脚本必须挂成 `zz-<name>.sql` 强制排到最后，\n"
            "   否则「建表在种子之后」→ 全新数据目录上种子静默丢失（规则 61）。"
        )
        return 1

    if verbose:
        print("推导出的 init 执行顺序（按挂载目标名）：")
        for i, name in enumerate(order):
            mark = " ← 种子" if is_seed(name) else ""
            print(f"  {i+1:>2}. {name}{mark}")
    print("✅ MySQL init 执行顺序：%d 个脚本，种子全部排在建表之后且带 `zz-` 前缀" % len(order))
    return 0


if __name__ == "__main__":
    sys.exit(main())
