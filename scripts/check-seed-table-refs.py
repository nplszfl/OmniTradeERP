#!/usr/bin/env python3
"""
闸门：种子文件引用的表，必须真的被某个 DDL 文件创建过。

为什么需要这个闸门
------------------
docker-entrypoint-initdb.d 的执行模型是「**每个 .sql 文件一个独立的 mysql 会话**」，
且**没有事务包裹**。一个文件里任何一条语句报错：

    ERROR 1146 (42S02) at line 187: Table 'erp_finance.fin_record' doesn't exist

会让**该文件从出错行起剩余的全部语句不执行**，而 mysql 客户端默认
「遇错继续」（不加 --force 时是遇错退出，但 entrypoint 的 init 循环会
继续跑下一个文件）—— 于是：

  * 出错行之后的种子**静默丢失**
  * 退出码可能仍是 0 → 容器看起来「初始化成功」
  * 最典型的受害者就是 seed-live-data.sql 这种大文件：
    前面某段引用的表不存在，后面所有段的种子全部没种上

本闸门做静态三方交叉校验：
    1. 种子文件（seed-*.sql / zz-*.sql）里 INSERT/UPDATE/DELETE 的目标表
    2. DDL 文件（database/*.sql，不含种子）里 CREATE TABLE 建的表
    3. 库归属：种子里的 `USE \`db\`;` 决定了这条语句属于哪个库，
       CREATE TABLE 必须在**同一个库**里建过

用法：
    python3 scripts/check-seed-table-refs.py
    python3 scripts/check-seed-table-refs.py --strict   # 非 0 退出码
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_DIR = ROOT / "database"

# 种子文件判定：文件名含 seed
def is_seed_file(p: Path) -> bool:
    return "seed" in p.name.lower()


# 解析一个 sql 文件：返回 [(库名 or None, 表名)] 形式的引用列表
TABLE_RE = re.compile(
    r"\b(?:INSERT\s+INTO|REPLACE\s+INTO|UPDATE|DELETE\s+FROM)\s+"
    r"(?:`(?P<db1>[^`]+)`\s*\.\s*)?`?(?P<t1>[A-Za-z_][\w]*)`?",
    re.IGNORECASE,
)
CREATE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?"
    r"(?:`(?P<db1>[^`]+)`\s*\.\s*)?`?(?P<t1>[A-Za-z_][\w]*)`?",
    re.IGNORECASE,
)
USE_RE = re.compile(r"^\s*USE\s+`?(?P<db>[A-Za-z_][\w]*)`?\s*;", re.IGNORECASE | re.MULTILINE)


def strip_comments(sql: str) -> str:
    """去掉 -- 行注释 和 /* */ 块注释，避免注释里的表名被误判。"""
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.DOTALL)
    sql = re.sub(r"--[^\n]*", " ", sql)
    return sql


def main() -> int:
    if not DB_DIR.exists():
        print(f"❌ 找不到 database 目录: {DB_DIR}")
        return 1

    sql_files = sorted(DB_DIR.glob("*.sql"))
    if not sql_files:
        print(f"❌ {DB_DIR} 下没有 .sql 文件")
        return 1

    # ---- 1. 收集「哪个库建了哪张表」 ----
    # created[(db, table)] = 源文件
    created: dict[tuple[str, str], str] = {}
    for p in sql_files:
        if is_seed_file(p):
            continue
        sql = strip_comments(p.read_text(encoding="utf-8", errors="replace"))
        cur_db = None
        for line in sql.splitlines():
            m = USE_RE.match(line)
            if m:
                cur_db = m.group("db")
                continue
            for cm in CREATE_RE.finditer(line):
                db = cm.group("db1") or cur_db
                tbl = cm.group("t1")
                if db:
                    created[(db.lower(), tbl.lower())] = p.name

    # 收集「哪些库被 CREATE DATABASE 声明过」
    declared_dbs = set()
    db_re = re.compile(r"CREATE\s+DATABASE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z_][\w]*)`?", re.IGNORECASE)
    for p in sql_files:
        sql = strip_comments(p.read_text(encoding="utf-8", errors="replace"))
        for m in db_re.finditer(sql):
            declared_dbs.add(m.group(1).lower())

    # ---- 2. 扫描种子文件里的表引用 ----
    problems: list[str] = []
    checked = 0
    for p in sql_files:
        if not is_seed_file(p):
            continue
        raw = p.read_text(encoding="utf-8", errors="replace")
        sql = strip_comments(raw)
        cur_db = None
        for lineno, line in enumerate(sql.splitlines(), 1):
            m = USE_RE.match(line)
            if m:
                cur_db = m.group("db")
                continue
            for tm in TABLE_RE.finditer(line):
                db = tm.group("db1") or cur_db
                tbl = tm.group("t1")
                if not db:
                    # 没写库名也没 USE —— 无法静态判定，跳过（不误报）
                    continue
                checked += 1
                key = (db.lower(), tbl.lower())
                if key in created:
                    continue
                # 库本身都没声明 → 更严重
                if db.lower() not in declared_dbs:
                    problems.append(
                        f"  {p.name}:{lineno}  引用了库 `{db}`，但没有任何 DDL 文件 CREATE DATABASE `{db}`"
                    )
                else:
                    problems.append(
                        f"  {p.name}:{lineno}  引用了表 `{db}`.`{tbl}`，但没有任何 DDL 文件在 `{db}` 库里建过这张表"
                    )

    print("🔍 种子表引用闸门")
    print(f"   DDL 建表清单: {len(created)} 张表 / {len(declared_dbs)} 个库")
    print(f"   种子表引用校验: {checked} 处")
    if not problems:
        print("✅ 所有种子引用的表都能在对应库里找到 CREATE TABLE")
        return 0

    # 去重后输出
    uniq = sorted(set(problems))
    print(f"\n❌ 发现 {len(uniq)} 处问题（种子会在这里静默中断，后续种子全部丢失）:")
    for line in uniq:
        print(line)
    print()
    print("💡 修法二选一：")
    print("   a) 在对应 DDL 文件里补上这张表的 CREATE TABLE（表结构以实体类为准）")
    print("   b) 把种子挪到「表确实存在」的那个 DDL 文件里，或删掉这段种子")
    return 1


if __name__ == "__main__":
    code = main()
    sys.exit(code if "--strict" in sys.argv else (0 if code == 0 else 1))
