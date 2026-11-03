#!/usr/bin/env python3
"""种子脚本幂等性巡检：init 会不会因为主键冲突而**被腰斩**。

背景（2026-11-03 实测踩坑）：
  `database/seed-data.sql` 往 `t_role`/`t_user`/`t_user_role` 插数据，
  但 `user-service.sql`（erp_user 的 DDL 属主）**已经插过一遍**。
  种子按 `zz-` 前缀排在它**之后**执行 → 直接撞主键：

      ERROR 1062 (23000) at line 26: Duplicate entry '1' for key 't_role.PRIMARY'

  📌 **致命之处**：docker-entrypoint 遇到 .sql 里的报错会**中止整个 init**，
     但**容器照样起来** → 看起来「初始化成功」，
     实际排在后面的 `zz-seed-live-data`/`zz-seed-tenant`/`zz-seed-warehouse`
     **一个都没执行** → 静默失败。

📌 本脚本把这类 bug 变成**可自动检出**的闸门，而不是靠人肉记得。

---

## 检查两类「init 会被腰斩 / 数据会被静默清零」的缺陷

### 1️⃣ 主键冲突（ERROR 1062 → init 中断）

  同���张表被**多个文件** INSERT，且**主键 id 有交集** →
  后执行的那个文件如果**没有** `ON DUPLICATE KEY UPDATE` / `INSERT IGNORE`
  → 必然报 1062 → **init 中断** → 后续所有文件不执行。

  📌 判定用**实际执行顺序**（跟 docker-entrypoint 一致）：
     - `docker-entrypoint-initdb.d` 按**文件名字母序**执行，不是挂载顺序
     - 种子文件统一 `zz-` 前缀 → 强制排到最后（跟 start-audit-mysql.sh 一致）

### 2️⃣ DDL 属主在种子之后重建表（静默清零）

  文件 A 往表 T 插了数据，文件 B（更晚执行）**自己就是 T 的 DDL 属主**
  → B 里带 `DROP TABLE` + `CREATE TABLE` → **A 的数据被静默清空**。

  📌 这类不报错 —— init 全绿，但数据没了（RBAC 权限树被清零就是踩过这个）。

---

## 为什么不能只查「有没有 ON DUPLICATE KEY」

  `seed-live-data.sql` 往 `t_permission` 插了 31 行、`seed-data.sql` 也插了 31 行，
  **id 完全相同** —— 但 live-data 那份**有** ON DUPLICATE KEY → 无害。
  所以必须**按实际执行顺序 + 主键交集 + 保护标记**三者一起判。

用法：python3 scripts/check-seed-idempotency.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL_DIR = os.path.join(ROOT, "database")

# 📌 与 scripts/start-audit-mysql.sh 保持一致：
#    种子文件统一改成 zz- 前缀 → 字母序强制排到最后。
def execution_name(fname):
    return f"zz-{fname}" if fname.startswith("seed-") else fname


def strip_comments(text):
    """去掉 -- 行注释 和 /* */ 块注释（否则注释里的 INSERT 会被误判）。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"--[^\n]*", "", text)
    return text


def split_statements(text):
    """按分号切语句，但**不切字符串里的分号**。

    📌 为什么要自己写：`re.split(r";", text)` 会被
       `'{"currency":"CNY","timezone":"Asia/Shanghai"}'` 这种
       JSON 字符串里的分号切碎 → 语句残缺 → INSERT 解析失败 → 漏报。
    """
    stmts = []
    buf = []
    quote = None       # 当前所处引号：' " `
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if quote:
            buf.append(ch)
            if ch == "\\" and i + 1 < n:
                buf.append(text[i + 1])
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch in "'\"`":
            quote = ch
            buf.append(ch)
            i += 1
            continue
        if ch == ";":
            stmts.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    if buf:
        stmts.append("".join(buf))
    return stmts


def split_value_tuples(body):
    """从 INSERT 的 VALUES 部分里切出各个值元组。

    📌 **不能用 `re.findall(r"\(([^()]*)\\)", ...)`** ——
       值里带函数调用就会切错：
           NOW() + INTERVAL 300 DAY
           JSON_EXTRACT(settings, '$.x')
       → `[^()]*` 匹配不到完整元组 → 整批数据被漏掉。

    这里手工扫：跟踪引号 + 括号深度。
    """
    tuples = []
    depth = 0
    quote = None
    cur = []
    for ch in body:
        if quote:
            cur.append(ch)
            if ch == quote:
                quote = None
            continue
        if ch in "'\"`":
            quote = ch
            cur.append(ch)
            continue
        if ch == "(":
            depth += 1
            if depth == 1:
                cur = []
                continue
        elif ch == ")":
            depth -= 1
            if depth == 0:
                tuples.append("".join(cur))
                cur = []
                continue
        if depth >= 1:
            cur.append(ch)
    return tuples


def split_top_level_fields(tup):
    """把一个值元组按**顶层逗号**切开（不看引号/括号里的逗号）。"""
    fields = []
    buf = []
    quote = None
    depth = 0
    for ch in tup:
        if quote:
            buf.append(ch)
            if ch == quote:
                quote = None
            continue
        if ch in "'\"`":
            quote = ch
            buf.append(ch)
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            fields.append("".join(buf).strip())
            buf = []
            continue
        buf.append(ch)
    fields.append("".join(buf).strip())
    return fields


def parse_file(path):
    """解析一个 .sql。

    返回 (插入的表 -> [(主键值, 是否有ON DUPLICATE保护)], 拥有的表set, 事件序列)
    事件序列 = [(事件类型, 表名)]，按语句出现顺序，用于检查**同文件内**的
    「先插数据、后 DROP+CREATE 表」→ 数据被自己清空。
    """
    raw = open(path, encoding="utf-8", errors="ignore").read()
    text = strip_comments(raw)

    # 当前 USE 的库
    cur_db = None
    inserts = defaultdict(list)   # table -> [(id, protected)]
    owns = set()                  # 这个文件 CREATE 了哪些表
    events = []                   # [(kind, table)] kind ∈ insert/ddl/drop
    drops = []                    # [(table, 是否有 IF EXISTS)]
    fks = []                      # [(constraint, 父表, 子表)]

    for stmt in split_statements(text):
        s = stmt.strip()
        if not s:
            continue

        m = re.match(r"USE\s+`?([A-Za-z0-9_]+)`?", s, re.I)
        if m:
            cur_db = m.group(1)
            continue

        # DROP TABLE → 会被外键阻塞（ERROR 3730）
        for dm in re.finditer(r"DROP\s+TABLE\s+(IF\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?", s, re.I):
            tbl = f"{cur_db}.{dm.group(2)}" if cur_db else dm.group(2)
            drops.append((tbl, bool(dm.group(1))))
            events.append(("drop", tbl))

        # FOREIGN KEY → 记录父子关系（用于检查 DROP 会不会被 FK 阻塞）
        for fm in re.finditer(
                r"CONSTRAINT\s+`?([A-Za-z0-9_]+)`?\s+FOREIGN\s+KEY\s*\(`?([A-Za-z0-9_]+)`?\)"
                r"\s*REFERENCES\s+`?([A-Za-z0-9_]+)`?", s, re.I):
            parent = f"{cur_db}.{fm.group(3)}" if cur_db else fm.group(3)
            child = f"{cur_db}.{fm.group(2)}" if cur_db else fm.group(2)
            fks.append((fm.group(1), parent, child))

        # CREATE TABLE → 这个文件是该表的 DDL 属主
        for cm in re.finditer(r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?([A-Za-z0-9_]+)`?", s, re.I):
            owns.add(f"{cur_db}.{cm.group(1)}" if cur_db else cm.group(1))
            events.append(("ddl", f"{cur_db}.{cm.group(1)}" if cur_db else cm.group(1)))

        # INSERT INTO ...
        im = re.match(r"INSERT\s+(IGNORE\s+)?INTO\s+`?([A-Za-z0-9_]+)`?\s*\(([^)]*)\)", s, re.I | re.S)
        if not im:
            continue
        ignore_flag = bool(im.group(1))
        table = im.group(2)
        cols = [c.strip().strip("`") for c in im.group(3).split(",")]
        full = f"{cur_db}.{table}" if cur_db else table
        events.append(("insert", full))
        if "id" not in cols:
            continue
        id_pos = cols.index("id")

        protected = "ON DUPLICATE KEY" in s.upper() or ignore_flag

        # 提取所有值元组
        body = s.split("VALUES", 1)[1] if "VALUES" in s.upper() else ""
        for tup in split_value_tuples(body):
            vals = split_top_level_fields(tup)
            if id_pos >= len(vals):
                continue
            raw_id = vals[id_pos].strip().strip("'")
            # 📌 **主键可能是数字，也可能是 UUID 字符串**：
            #    erp_tenant 的 tenants.id 是 VARCHAR(36)（Hibernate GenerationType.UUID）
            #    → 只认数字会把整个文件判成「没插任何表」→ **漏报**
            if not re.fullmatch(r"\d+", raw_id) and not re.fullmatch(
                    r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", raw_id):
                continue   # 表达式/自增/函数调用 → 不参与主键冲突判定
            key = int(raw_id) if raw_id.isdigit() else raw_id.lower()
            inserts[full].append((key, protected))

    return inserts, owns, events, drops, fks


def _ff_of(fname, all_fks, constraint):
    """这个外键约束是在哪个文件里定义的。"""
    for f, c, _p, _ch in all_fks:
        if c == constraint:
            return f
    return None


def main():
    if not os.path.isdir(SQL_DIR):
        print(f"❌ 找不到 {SQL_DIR}")
        return 1

    files = sorted(f for f in os.listdir(SQL_DIR) if f.endswith(".sql"))
    if not files:
        print(f"❌ {SQL_DIR} 里没有 .sql 文件")
        return 1

    # 按**实际执行顺序**排序（zz- 前缀）
    files.sort(key=execution_name)

    parsed = {}
    for f in files:
        parsed[f] = parse_file(os.path.join(SQL_DIR, f))

    errors = []
    infos = []

    # ── 检查 1：主键冲突 ───────────────────────────────────────────────
    # 同一张表被多个文件插入 → 主键交集 → 后执行的那份必须带保护
    by_table = defaultdict(list)   # table -> [(file, {id: protected})]
    for f in files:
        inserts, _, _, _, _ = parsed[f]
        for tbl, rows in inserts.items():
            ids = defaultdict(bool)
            for i, prot in rows:
                ids[i] = ids[i] or prot
            by_table[tbl].append((f, ids))

    for tbl, entries in sorted(by_table.items()):
        if len(entries) < 2:
            continue
        for i in range(len(entries)):
            for j in range(i + 1, len(entries)):
                f_late, ids_late = entries[j]
                f_early, ids_early = entries[i]
                overlap = set(ids_early) & set(ids_late)
                if not overlap:
                    continue
                # 后执行的那份必须带 ON DUPLICATE / IGNORE
                unprotected = [x for x in overlap if not ids_late.get(x, False)]
                if unprotected:
                    # 📌 主键可能是 int 也可能是 UUID 字符串 → 排序前先统一成 str
                    shown = sorted(str(x) for x in overlap)
                    errors.append(
                        f"🔴 主键冲突 → init 会被腰斩\n"
                        f"      表 `{tbl}`\n"
                        f"      {execution_name(f_early)} 先插 id {shown[:6]}"
                        f"{'...' if len(shown) > 6 else ''}\n"
                        f"      {execution_name(f_late)} 后插同样这些 id，"
                        f"但**没有** ON DUPLICATE KEY UPDATE / INSERT IGNORE\n"
                        f"      → 必然报 ERROR 1062 Duplicate entry\n"
                        f"      → docker-entrypoint **中止整个 init**（容器却照样起来）\n"
                        f"      → 排在后面的所有 .sql **一个都不执行**"
                    )

    # ── 检查 2：DDL 属主在种子之后重建表 → 静默清零 ──────────────────
    for i in range(len(files)):
        for j in range(i + 1, len(files)):
            f_early, f_late = files[i], files[j]
            inserts_early, _, _, _, _ = parsed[f_early]
            _, owns_late, _, _, _ = parsed[f_late]
            for tbl in inserts_early:
                if tbl in owns_late:
                    errors.append(
                        f"🔴 DDL 属主在种子之后重建表 → 数据被静默清零\n"
                        f"      表 `{tbl}`\n"
                        f"      {execution_name(f_early)} 插了数据\n"
                        f"      {execution_name(f_late)} 更晚执行，且**自己就是该表的 DDL 属主**"
                        f"（含 DROP TABLE + CREATE TABLE）\n"
                        f"      → 前面插的数据被**清空**，init 却**全绿不报错**"
                    )

    # ── 检查 3：同文件内「先 INSERT，后 DROP+CREATE」→ 自己把自己清空 ──
    #    📌 跨文件没风险不代表同文件没风险：
    #    seed-tenant.sql 开头自带 DROP TABLE tenants + CREATE TABLE tenants，
    #    如果这个 DROP+CREATE 出现在 INSERT **之后** → 刚插的数据被清空。
    for f in files:
        _, _, events, _, _ = parsed[f]
        seen_insert = set()
        for kind, tbl in events:
            if kind == "insert":
                seen_insert.add(tbl)
            elif kind == "ddl" and tbl in seen_insert:
                errors.append(
                    f"🔴 同文件内先插数据、后重建表 → 数据被自己清空\n"
                    f"      表 `{tbl}`\n"
                    f"      {execution_name(f)}：先 INSERT INTO \`{tbl}\`，"
                    f"后面又出现 CREATE TABLE \`{tbl}\`\n"
                    f"      → 刚插进去的数据被**清空**，init 却**全绿不报错**\n"
                    f"      → 应把 DROP+CREATE 放到所有 INSERT **之前**"
                )
                break

    # ── 检查 4：DROP TABLE 被外键阻塞（ERROR 3730）→ init 中断 ─────────
    #    📌 实测踩到过：`DROP TABLE tenants` 被 `tenant_users` 的
    #       `fk_tenant_users_tenant` 挡住 →
    #       ERROR 3730 (HY000): Cannot drop table 'tenants' referenced by
    #       a foreign key constraint ... on table 'tenant_users'
    #    → 跟 1062 一样，**中止整个 init**，容器却照样起来。
    #    📌 判定：表 T 被 DROP，而**别的表**有 FK 指向 T → 必然 3730。
    #       （同文件里先 DROP 父表、再 DROP 子表是可以的，
    #         但子表也在种子文件里 DROP 才成立 —— 这里只报「父表被引用」
    #         的粗判，宁可多报也不漏报。）
    all_fks = []
    for f in files:
        _, _, _, _, fks = parsed[f]
        for c, parent, child in fks:
            all_fks.append((f, c, parent, child))

    for f in files:
        _, _, _, drops, _ = parsed[f]
        for tbl, has_if_exists in drops:
            refs = [(c, child) for (_ff, c, parent, child) in all_fks if parent == tbl]
            if not refs:
                continue
            # 子表在同一文件里也 DROP 了 → 安全
            same_file_drops = {t for t, _ in drops}
            blocking = [(c, ch) for c, ch in refs
                        if not (ch in same_file_drops and _ff_of(f, all_fks, c) == f)]
            if not blocking:
                continue
            errors.append(
                f"🔴 DROP TABLE 被外键阻塞 → init 会被腰斩\n"
                f"      表 `{tbl}`（{execution_name(f)}）\n"
                f"      被这些外键引用：{', '.join(sorted(set(c for c, _ in blocking)))}\n"
                f"      → 必然报 ERROR 3730 (HY000) Cannot drop table "
                f"'{tbl.split('.')[-1]}' referenced by a foreign key constraint\n"
                f"      → docker-entrypoint **中止整个 init**（容器却照样起来）\n"
                f"      → 应先 DROP 子表，或给子表也加 DROP，或改用 TRUNCATE/DELETE"
            )

    # ── 报告 ──────────────────────────────────────────────────────────
    total_ins = sum(len(rows) for ins, _, _, _, _ in parsed.values() for rows in ins.values())
    print(f"📋 按实际执行顺序检查 {len(files)} 个 .sql 文件，{total_ins} 条 INSERT\n")

    if infos:
        for i in infos:
            print(f"ℹ️  {i}")
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处「init 会被腰斩 / 数据会被静默清零」的问题：\n")
        for e in errors:
            print(f"   {e}")
            print()
        print("提示：")
        print("  · 同表重复插入 → 加 ON DUPLICATE KEY UPDATE（或 INSERT IGNORE）")
        print("  · DDL 属主在种子之后 → 给种子文件加 zz- 前缀（强制排到最后）")
        print("  · 同文件内先插后建表 → 把 DROP+CREATE 提到所有 INSERT 之前")
        print("  · 改完必须用 scripts/start-audit-mysql.sh 做一次**全新初始化**验证")
        return 1

    print("✅ 种子脚本幂等性 OK（无主键冲突、无 DDL 属主清零风险）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
