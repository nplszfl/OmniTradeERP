#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL init 脚本的**执行顺序**闸门 —— 规则 61 的守门人。

📌 **为什么需要这道闸门**（2026-11-10 实测）：

`docker-entrypoint-initdb.d/` 里的脚本由 docker-entrypoint **按目标文件名字母序**执行
（规则 61：**不是** compose 的挂载顺序，更不是源文件名）。
每个文件是**一个独立的 mysql 会话**，且**任何一条语句报错 → 从出错行起该文件剩余语句全部不执行**，
而容器退出码可能仍是 0 → 后面几百行种子**静默丢失**。

🔴 **实测抓到的真实损失**（用全新 MySQL 容器真跑出来的，不是推演）：
`database/seed-data.sql` 挂成 `seed-data.sql`（**漏了 `zz-` 前缀**）→ 排第 **15** 位，
它种 `t_user`（**7** 个账号，含 `ops002` / `ops003`）→
而 `user-service.sql`（第 **19** 位）**也**种 `t_user`，且开头就 `DROP TABLE IF EXISTS t_user` 重建
→ 重建后只插 **5** 个 → **那两个账号被静默吃掉**。

📊 **前后对照（两个全新 MySQL 容器逐表对账，113 张表）**：
| 表 | 修复前 | 修复后 |
|---|---|---|
| `erp_user.t_user` | **5** | **7**（找回 ops002 / ops003） |
| `erp_user.t_user_role` | **5** | **7**（这两个账号的角色关联） |
| 其余 **111 张表** | — | **完全一致**（`t_role` 5 / `t_permission` 31 / `t_role_permission` 67 都没丢） |

⚠️ **别把损失说大**：角色与权限**没有丢**（被 `user-service.sql` + `zz-seed-live-data.sql` 补回），
真实损失只有 **2 个账号 + 2 条用户角色关联**。

⚠️ **为什么长期潜伏**：initdb.d **只在数据目录为空时执行一次**
→ 已有 volume 的部署永远不重跑 → 只在**全新机器首次部署**时发作。

📌 **判据（对着「实际怎么跑」来）**：

| 断言 | 内容 | 防的是 |
|---|---|---|
| **A** | **执行顺序必须按 compose 挂载的「目标文件名」算** | 否则查的全是假的 |
| **B** | 种子插入的每一张表，必须存在**至少一个排在它之前**的创建方 | 表压根没人建 → 必然「Table doesn't exist」 |
| **C** | 种子脚本必须挂成 `zz-` 前缀 | 防御性约定：靠后 = 靠后的那份数据活下来 |
| **D** | 同一张表若被「**种子文件**」和「**非种子文件**」同时播种 → **种子文件必须排在非种子文件之后** | **真丢数据**：非种子文件会 `DROP TABLE` 重建 → 排在它后面的种子**插完就被吃掉** |
| **B'** | 种子插入的表，之后被 `DROP TABLE` 且**没有更晚的种子文件补种同一张表** | 种子白插 |

⚠️ **B 的写法踩过一次坑（规则 99：假阳性会逼人放松闸门）**：
最初写成「**每一个**创建方都必须排在种子之前」→ 但**同一张表可能被多个文件创建**
（`init.sql` 和 `user-service.sql` 都建 `t_user`）→ 只看后者就会**误报**。
→ 正确写法：「**存在至少一个**更早的创建方即可」。

📌 **反向验证**（规则 40 / 75，注入真实的原始形态）：
把 `zz-seed-data.sql` 的挂载名改回 `seed-data.sql` → 本闸门必须 exit 1（D 报出那几张表）。

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
DROP_TABLE_RE = re.compile(
    r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?[`\"]?(?P<tab>\w+)[`\"]?", re.I
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


def read_src(src):
    return open(os.path.join(REPO_ROOT, src), encoding="utf-8", errors="replace").read()


def created_tables(mounts, order):
    """表名（小写） → 创建它的 init 目标文件名集合。"""
    out = {}
    for dst in order:
        for _, src in mounts[dst]:
            for m in CREATE_TABLE_RE.finditer(read_src(src)):
                out.setdefault(m.group("tab").lower(), set()).add(dst)
    return out


def dropped_tables(mounts, order):
    """表名（小写） → [(目标文件名, 位置), ...]。"""
    out = {}
    for dst in order:
        for _, src in mounts[dst]:
            for m in DROP_TABLE_RE.finditer(read_src(src)):
                out.setdefault(m.group("tab").lower(), []).append((dst, order.index(dst)))
    return out


def inserted_tables(mounts, dst):
    """某个 init 文件插入的表名集合（忽略注释行）。"""
    out = set()
    for _, src in mounts[dst]:
        for raw in read_src(src).splitlines():
            line = raw.strip()
            if line.startswith("--") or line.startswith("#"):
                continue
            m = INSERT_RE.search(line)
            if m:
                out.add(m.group("tab").lower().split(".")[-1])
    return out


def is_seed(dst):
    """种子脚本：目标名里带 seed。"""
    return "seed" in dst.lower()


def main() -> int:
    verbose = "--verbose" in sys.argv
    mounts = collect_mounts()
    if not mounts:
        print("❌ 找不到任何 initdb.d 挂载 —— compose 结构变了？本闸门会变成瞎的")
        return 1

    # 📌 **执行顺序 = 目标文件名字母序**（docker-entrypoint 的真实行为）
    order = sorted(mounts)
    pos = {name: i for i, name in enumerate(order)}
    owners = created_tables(mounts, order)
    drops = dropped_tables(mounts, order)

    findings = []

    # ── D：同一张表被「种子文件」和「非种子文件」同时播种 → 种子必须排在后面 ──
    seeders = {}   # 表 -> {目标名: 是否种子文件}
    for dst in order:
        for tab in inserted_tables(mounts, dst):
            seeders.setdefault(tab, {})[dst] = is_seed(dst)
    for tab, who in sorted(seeders.items()):
        seeds = [d for d, is_s in who.items() if is_s]
        others = [d for d, is_s in who.items() if not is_s]
        for sd in seeds:
            for o in others:
                if pos[o] > pos[sd]:
                    findings.append(
                        f"[D] 表 `{tab}`：种子 {sd}（第 {pos[sd]+1} 位）排在 "
                        f"{o}（第 {pos[o]+1} 位）**之前** → {o} 会 `DROP TABLE {tab}` 再重建"
                        f"（每个服务的 .sql 都这么干）→ **{sd} 插进去的行被静默吃掉**"
                    )

    for dst in order:
        if not is_seed(dst):
            continue
        for tab in sorted(inserted_tables(mounts, dst)):
            creators = owners.get(tab)
            if not creators:
                findings.append(
                    f"[B] {dst} 插入的表 `{tab}` **没有任何 init 脚本创建** → 必然「Table doesn't exist」"
                )
                continue
            # 📌 **存在至少一个更早的创建方即可** —— 同一张表可能被多个文件创建
            #    （`init.sql` 与 `user-service.sql` 都建 `t_user`），
            #    只看「每一个创建方都得更早」会**误报**（规则 99）。
            if not any(pos[c] <= pos[dst] for c in creators):
                findings.append(
                    f"[B] {dst}（第 {pos[dst]+1} 位）插入 `{tab}`，"
                    f"但它的创建方都在它之后（{', '.join(sorted(creators))}）→ "
                    f"全新数据目录上必然「Table doesn't exist」"
                )
            # 📌 **B'：种子插完后又被后面的脚本 DROP 掉 → 这段种子白插了**
            for d, dp in drops.get(tab, []):
                if dp <= pos[dst]:
                    continue
                if tab in inserted_tables(mounts, d):
                    continue  # 那个脚本自己又 INSERT 了同一张表 → 是替换，不是丢失
                # 更晚的**种子文件**补种了同一张表 → 也算补偿
                compensated = any(
                    is_seed(later) and pos[later] > pos[dst]
                    and tab in inserted_tables(mounts, later)
                    for later in order if pos[later] > dp
                )
                if compensated:
                    continue
                findings.append(
                    f"[B'] {dst}（第 {pos[dst]+1} 位）插入 `{tab}`，"
                    f"之后 {d}（第 {dp+1} 位）执行了 `DROP TABLE {tab}` → "
                    f"**这段种子被静默丢弃**（DROP 之后表是空的，而种子不报错）"
                )

    for dst in order:
        if not is_seed(dst):
            continue
        if not dst.lower().startswith("zz-"):
            srcs = sorted({s for _, s in mounts[dst]})
            findings.append(
                f"[C] 种子 {dst}（源：{', '.join(srcs)}）**没有 `zz-` 前缀** → "
                f"按字母序排在第 {pos[dst]+1} 位 → 排在它后面的播种文件一旦 DROP 重建就会吃掉它的数据"
            )

    if findings:
        print("❌ MySQL init 执行顺序闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 docker-entrypoint 按**目标文件名**字母序执行 initdb.d 里的脚本；\n"
            "   每个服务的 .sql 开头都会 `DROP TABLE` 再重建 → **种子必须排在所有播种者之后**\n"
            "   （用 `zz-` 前缀强制），否则种子插进去的行会被静默吃掉（规则 61）。"
        )
        return 1

    if verbose:
        print("推导出的 init 执行顺序（按挂载目标名）：")
        for i, name in enumerate(order):
            mark = " ← 种子" if is_seed(name) else ""
            print(f"  {i+1:>2}. {name}{mark}")
    print("✅ MySQL init 执行顺序：%d 个脚本，种子全部排在建表/播种之后且带 `zz-` 前缀" % len(order))
    return 0


if __name__ == "__main__":
    sys.exit(main())
