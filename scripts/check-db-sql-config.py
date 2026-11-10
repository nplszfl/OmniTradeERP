#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL 配置层的**硬性必填项**闸门 —— 规则 63 / 64 / 65。

📌 **为什么需要这道闸门**（三条都是本项目真踩过的坑，且**至今没有闸门守着**）：

| 规则 | 内容 | 踩过的坑 | 现在的后果 |
|---|---|---|---|
| **63** | 每个 init `.sql` **内部**要写 `SET NAMES utf8mb4;` | 容器级 locale 兜底不可靠（每个文件由**独立的 mysql 会话**执行，官方 mysql:8.0 镜像 LANG 为空 → 按 POSIX locale 退化成 latin1）→ 中文**双重编码**（实测 `C3A6E284A2`） | 新加一个 init 文件忘了写 → **该文件里的中文全是乱码** |
| **64** | MySQL 注释必须 `-- ` **带空格** | `--（中文）` 不带空格 → **1064 语法错误** | 一个字符 → 整份 init 脚本报错 → **init 整体中断** |
| **65** | MySQL 8 非 SSL 首握手要公钥 → JDBC URL 必须带 `allowPublicKeyRetrieval=true` | `Public Key Retrieval is not allowed` | 新加一个服务的 `application.yml` 忘了带 → **连接直接失败**，而且报错信息很不直观 |

⚠️ **共同点**：这三条**都不是「代码写错」，而是「忘了写一个字符」** →
单测查不出来（不碰 MySQL）、其它闸门也不管（都在扫别的东西）→
**只有专门一条闸门盯着它们**。

📌 **判据（窄）**：

| 断言 | 内容 |
|---|---|
| **A** | 每个**被 compose 挂进 initdb.d 的** `.sql` 必须含 `SET NAMES utf8mb4` |
| **B** | 同一批 `.sql` 里的注释行必须以 `-- `（带空格）开头（`--` 单独一行不算违规） |
| **C** | 仓库里每个 `jdbc:mysql://` URL 必须带 `allowPublicKeyRetrieval=true` |

📌 **为什么 A 只查「被 init 的那些」**：规则 63 的语境就是 docker-entrypoint 执行的脚本
→ 别的 `.sql`（比如 `schema.sql`、测试资源）由应用自己跑，不受那个 locale 问题影响
→ **判据必须窄**，否则会把无关文件判死（规则 99）。

📌 **反向验证**（规则 40 / 75，注入真实的原始形态）：
① 删掉某个 init 文件的 `SET NAMES utf8mb4;` → A 报红；
② 把 `-- ` 改成 `--` → B 报红；
③ 删掉某个 JDBC URL 的 `allowPublicKeyRetrieval=true` → C 报红。

用法：
    python3 scripts/check-db-sql-config.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-db-sql-config.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", ".idea"}

INIT_DIR = "/docker-entrypoint-initdb.d"
MOUNT_RE = re.compile(r"\./([^:\s]+\.sql):" + re.escape(INIT_DIR) + r"/([^:\s]+\.sql)")
SET_NAMES_RE = re.compile(r"SET\s+NAMES\s+utf8mb4", re.I)
JDBC_RE = re.compile(r"jdbc:mysql://[^\s\"']+")


def init_scripts():
    """被 compose 挂进 initdb.d 的 .sql 源文件（去重）。"""
    out = set()
    for fn in sorted(os.listdir(REPO_ROOT)):
        if not (fn.startswith("docker-compose") and fn.endswith((".yml", ".yaml"))):
            continue
        text = open(os.path.join(REPO_ROOT, fn), encoding="utf-8").read()
        for m in MOUNT_RE.finditer(text):
            src = m.group(1)
            if os.path.exists(os.path.join(REPO_ROOT, src)):
                out.add(src)
    return sorted(out)


def config_files():
    """所有可能含 JDBC URL 的配置文件。"""
    exts = (".yml", ".yaml", ".properties", ".env")
    out = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(exts):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []

    scripts = init_scripts()
    if not scripts:
        print("❌ 找不到任何 initdb.d 挂载 —— compose 结构变了？本闸门会变成瞎的")
        return 1

    # ── A：每个 init .sql 必须声明字符集 ────────────────────────────
    for rel in scripts:
        text = open(os.path.join(REPO_ROOT, rel), encoding="utf-8", errors="replace").read()
        if not SET_NAMES_RE.search(text):
            findings.append(
                f"[A] `{rel}` 没有 `SET NAMES utf8mb4;` → 每个 init 文件由 docker-entrypoint 用"
                f"**独立的 mysql 会话**执行，容器级 locale 兜底不可靠 → 该文件里的中文会**双重编码**（规则 63）"
            )

    # ── B：注释必须 `-- ` 带空格 ─────────────────────────────────
    for rel in scripts:
        path = os.path.join(REPO_ROOT, rel)
        for i, line in enumerate(open(path, encoding="utf-8", errors="replace"), 1):
            s = line.strip()
            if s.startswith("--") and len(s) > 2 and not s.startswith("-- ") and s != "--":
                findings.append(
                    f"[B] `{rel}:{i}` 注释是 `{s[:40]}` —— MySQL 注释必须 `-- ` **带空格**"
                    f"（`--（中文）` 会报 **1064 语法错误** → 整份 init 脚本中断，规则 64）"
                )

    # ── C：JDBC URL 必须带 allowPublicKeyRetrieval=true ───────────
    checked = 0
    for path in config_files():
        rel = os.path.relpath(path, REPO_ROOT)
        text = open(path, encoding="utf-8", errors="replace").read()
        for m in JDBC_RE.finditer(text):
            url = m.group(0)
            checked += 1
            if "allowPublicKeyRetrieval=true" not in url:
                findings.append(
                    f"[C] `{rel}` 的 JDBC URL 缺 `allowPublicKeyRetrieval=true` → "
                    f"MySQL 8 非 SSL 首握手要公钥 → **连接直接失败**"
                    f"（报错是 'Public Key Retrieval is not allowed'，很不直观，规则 65）"
                )

    if findings:
        print("❌ MySQL 配置层硬性必填项闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 这三条都是「**忘了写一个字符**」，不是代码写错：\n"
            "   ① 每个 init .sql 内部要写 `SET NAMES utf8mb4;`（每个文件独立 mysql 会话，locale 兜底不可靠）\n"
            "   ② MySQL 注释必须 `-- ` 带空格（否则 1064 → 整份 init 中断）\n"
            "   ③ JDBC URL 必须带 `allowPublicKeyRetrieval=true`（否则连接直接失败）"
        )
        return 1

    if verbose:
        print("A 检查了 %d 个 init .sql；C 检查了 %d 条 JDBC URL" % (len(scripts), checked))
    print("✅ MySQL 配置层必填项：%d 个 init 脚本 + %d 条 JDBC URL 全部合规（A/B/C）"
          % (len(scripts), checked))
    return 0


if __name__ == "__main__":
    sys.exit(main())
