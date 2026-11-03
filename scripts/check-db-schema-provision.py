#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据库供给巡检：每个服务用的库，在 compose 挂载的 SQL 里能不能被建出来？

🔴 **这个坑踩过两次**：
   1. erp_social —— social-commerce-service.sql 里已经写了 CREATE DATABASE，
      但**没挂进 docker-compose.yml** → 库永远不存在。
   2. 一些服务用 JPA `ddl-auto: update` —— Hibernate **只建表，不建库**，
      所以「DDL 补齐了」不等于「库能建出来」，还必须真的被 compose 挂载执行。

💡 判定链（三条都要成立才算通过）：
   ① 服务 application.yml 里 JDBC URL 指向的库名
   ② 该库能被某个**被 compose 挂载**的 .sql 文件 CREATE DATABASE 出来
   ③ 该 .sql 文件本身含 SET NAMES utf8mb4（否则中文双重编码）

用法：
    python3 scripts/check-db-schema-provision.py            # 人看
    python3 scripts/check-db-schema-provision.py --strict   # CI 用（有问题退出码非 0）
"""

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
COMPOSE = ROOT / "docker-compose.yml"
DB_DIR = ROOT / "database"

# 从 JDBC URL 里抠库名： jdbc:mysql://host:3306/erp_social?...
JDBC_RE = re.compile(r"jdbc:mysql://[^/]+/([A-Za-z0-9_]+)\?")
# CREATE DATABASE，兼容带反引号和不带反引号两种写法
CREATE_DB_RE = re.compile(
    r"CREATE\s+DATABASE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?",
    re.IGNORECASE,
)


def collect_service_dbs():
    """{库名: [模块名, ...]} —— 从各服务的 application.yml 读"""
    result = {}
    for yml in sorted(ROOT.glob("erp-*/src/main/resources/application.yml")):
        module = yml.parts[len(ROOT.parts)]
        text = yml.read_text(encoding="utf-8")
        for m in JDBC_RE.finditer(text):
            result.setdefault(m.group(1), set()).add(module)
    return result


def collect_mounted_files():
    """compose 里挂到 mysql init 目录的 sql 文件名集合"""
    if not COMPOSE.exists():
        return set()
    text = COMPOSE.read_text(encoding="utf-8")
    return set(re.findall(r"\./database/([A-Za-z0-9_\-]+\.sql)", text))


def collect_provisioned_dbs(mounted):
    """{库名: [提供该库的已挂载文件名, ...]}"""
    result = {}
    for name in sorted(mounted):
        path = DB_DIR / name
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in CREATE_DB_RE.finditer(text):
            result.setdefault(m.group(1), []).append(name)
    return result


def collect_utf8_files(mounted):
    """已挂载且含 SET NAMES utf8mb4 的文件集合"""
    ok = set()
    for name in sorted(mounted):
        path = DB_DIR / name
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if re.search(r"SET\s+NAMES\s+utf8mb4", text, re.IGNORECASE):
            ok.add(name)
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="有问题时退出码非 0")
    args = ap.parse_args()

    service_dbs = collect_service_dbs()
    mounted = collect_mounted_files()
    provisioned = collect_provisioned_dbs(mounted)
    utf8_ok = collect_utf8_files(mounted)

    problems = []

    print("=" * 78)
    print("数据库供给巡检：服务 JDBC 库名 ↔ compose 挂载的 CREATE DATABASE")
    print("=" * 78)
    print(f"服务用到 {len(service_dbs)} 个库 | compose 挂载 {len(mounted)} 个 sql | "
          f"能创建 {len(provisioned)} 个库\n")

    for db, modules in sorted(service_dbs.items()):
        mods = ", ".join(sorted(modules))
        providers = provisioned.get(db)
        if not providers:
            problems.append((db, mods, "该库没有任何**已挂载**的 sql 会创建它"))
            print(f"  ❌ {db:32s} {mods}")
            print(f"       → 没有任何已挂载的 sql 会 CREATE DATABASE {db}")
            print(f"       → 服务在 compose 环境启动会报 Unknown database '{db}'")
            continue

        # charset 检查：提供这个库的每个文件都要有 SET NAMES utf8mb4
        missing_utf8 = [p for p in providers if p not in utf8_ok]
        if missing_utf8:
            problems.append((db, mods, f"建库文件缺 SET NAMES utf8mb4: {missing_utf8}"))
            print(f"  ⚠️  {db:32s} {mods}")
            print(f"       → {missing_utf8} 缺少 SET NAMES utf8mb4（中文可能双重编码）")
        else:
            print(f"  ✅ {db:32s} {mods}  <- {', '.join(providers)}")

    # 反向：挂了但没有服务用（只是提示，不算问题）
    orphan = sorted(set(provisioned) - set(service_dbs))
    if orphan:
        print(f"\nℹ️  这些库有 sql 建但当前没有服务直接使用: {', '.join(orphan)}")

    print()
    if problems:
        print(f"❌ 发现 {len(problems)} 个问题")
        for db, mods, why in problems:
            print(f"   - {db} ({mods}): {why}")
        if args.strict:
            return 1
    else:
        print("✅ 所有服务的库都能由 compose 挂载的 sql 创建出来")
    return 0


if __name__ == "__main__":
    sys.exit(main())
