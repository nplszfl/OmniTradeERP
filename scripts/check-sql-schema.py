#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SQL 契约巡检：Java 代码里的 SQL ↔ database/*.sql 里的 DDL 交叉校验。

## 为什么需要这个脚本

本项目踩过两个**单测和 mvn test 全绿也发现不了**的 SQL 缺陷，
最后是靠起临时 MySQL 容器逐条跑 SQL 才查出来的 —— 但那种验证方式太重，
不可能每次提交都做一遍。

缺陷 1（列名对不上）：
    `t_order_item.internal_sku` 存的是**变体码**（SKU-BLU-001-BLK），
    而 `t_product.internal_sku` 存的是**SPU 码**（SKU-BLU-001）。
    写 `p.internal_sku = oi.internal_sku` → **永远匹配不上**，SQL 不报错、结果恒为空。

缺陷 2（表根本不存在）：
    datasource 指向 `omnitrade_erp` 库 → 容器一启动就连不上库。

这类问题的共同特征是：**代码能编译、单测能过、接口能返回，只有真跑才炸**。

本脚本把「代码里引用的表/列」和「DDL 里真实存在的表/列」拉平做静态校验：

1. **表不存在**：代码 SQL 里 `FROM erp_order.t_order`，但 DDL 里没这张表
2. **列不存在**：代码 SQL 里用了 `oi.some_col`，但该表没有这一列
3. **schema 不存在**：代码引用了 `erp_xxx.t_yyy`，但项目里没有 `erp_xxx` 这个库
4. **库名拼写**：datasource 里的库名，在 database/*.sql 里找不到对应 CREATE DATABASE

## 用法

    python3 scripts/check-sql-schema.py            # 打印报告
    python3 scripts/check-sql-schema.py --strict   # 有问题就退出码 1

## 设计取舍

- **只做静态校验，不连数据库** —— 快、无依赖、可挂 CI
- **宁可漏报也不误报**：无法确定的场景（动态表名、字符串拼接的列名）一律跳过
- 只扫 Java 源码里的字符串字面量（SQL 基本都写在字符串里），不扫 XML mapper
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# ⚠️ DDL 来源不止 database/：
#   - database/*.sql            —— 主 DDL 目录
#   - erp-*/src/main/resources/db/migration/*.sql  —— Flyway 迁移（product-recommendation）
#   - erp-*/src/main/resources/schema.sql          —— 随服务打包的建表脚本
#   - erp-*/src/test/resources/schema.sql          —— 测试用 H2 建表脚本
# 只扫 database/ 会把「用 Flyway 迁移建表」的服务误报成缺表。
DB_DIR = ROOT / "database"
JAVA_DIRS = [
    d for d in ROOT.glob("erp-*-service")
    if d.is_dir()
] + [ROOT / "erp-common"]


def collect_ddl_files():
    """收集所有 DDL 来源"""
    files = []
    if DB_DIR.exists():
        files += sorted(DB_DIR.glob("*.sql"))
    for svc in sorted(ROOT.glob("erp-*-service")):
        for pat in ("src/main/resources/db/migration/*.sql",
                    "src/main/resources/schema.sql",
                    "src/test/resources/schema.sql"):
            files += sorted(svc.glob(pat))
    return files

# SQL 函数/关键字里出现的 "table" 形态词，排除掉
SQL_KEYWORDS = {
    "select", "from", "where", "group", "order", "by", "having", "join",
    "left", "right", "inner", "outer", "cross", "on", "and", "or", "not",
    "insert", "into", "values", "update", "set", "delete", "union", "all",
    "distinct", "as", "case", "when", "then", "else", "end", "limit",
    "offset", "asc", "desc", "null", "is", "in", "like", "between",
    "exists", "count", "sum", "avg", "min", "max", "coalesce", "ifnull",
    "date", "time", "timestamp", "year", "month", "day", "hour", "minute",
    "second", "curdate", "curtime", "now", "date_sub", "date_add",
    "date_format", "weekday", "interval", "concat", "if", "round",
    "substring", "length", "trim", "upper", "lower", "replace", "cast",
    "true", "false", "default", "primary", "key", "unique", "index",
    "constraint", "foreign", "references", "auto_increment", "comment",
    "engine", "charset", "collate", "unsigned", "zerofill", "text",
    "int", "bigint", "smallint", "tinyint", "decimal", "numeric", "float",
    "double", "varchar", "char", "date", "datetime", "timestamp", "blob",
    "json", "enum", "using", "exists", "explain", "analyze", "straight_join",
    "duplicate", "row", "rows", "unnest", "lateral", "with", "recursive",
}

# SQL 里常见的函数名后跟 ( 形如 xxx( —— 不是表名
FUNC_CALL = re.compile(r"\b([a-z_][a-z0-9_]*)\s*\(", re.I)


# ---------------------------------------------------------------- DDL 解析


def parse_ddl():
    """返回 (schemas, tables)

    schemas: {schema_name}
    tables:  {(schema, table): {column_lower, ...}}
    """
    schemas = set()
    tables = {}

    for sql_file in collect_ddl_files():
        text = sql_file.read_text(encoding="utf-8", errors="replace")
        current_schema = None
        # 测试 schema / Flyway 迁移没有 USE 语句，表名本身就是唯一的
        is_standalone = "src/test/resources" in str(sql_file) or "migration" in str(sql_file)
        # 模块自带的 schema.sql 同理：PostgreSQL 的脚本通常不写 USE（靠 search_path），
        # 但它定义的表就是这个模块的表 —— 不能因为没有 CREATE DATABASE 就当成没定义，
        # 否则 inventory-alert 的 5 张表会被误报成「运行期必然报错」。
        if "src/main/resources/schema.sql" in str(sql_file):
            is_standalone = True

        # 按分号切成语句（SQL 里字符串里的分号很少见，够用）
        for stmt in text.split(";"):
            s = stmt.strip()
            if not s:
                continue

            m = re.search(r"CREATE\s+DATABASE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?",
                          s, re.I)
            if m:
                current_schema = m.group(1).lower()
                schemas.add(current_schema)
                continue

            m = re.search(r"USE\s+`?([a-zA-Z0-9_]+)`?\s*$", s, re.I)
            if m:
                current_schema = m.group(1).lower()
                schemas.add(current_schema)
                continue

            # CREATE TABLE
            m = re.search(
                r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?\s*"
                r"\.\s*`?([a-zA-Z0-9_]+)`?\s*\(",
                s, re.I)
            if m:
                schema, table = m.group(1).lower(), m.group(2).lower()
            else:
                m = re.search(
                    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?\s*\(",
                    s, re.I)
                if not m:
                    continue
                table = m.group(1).lower()
                schema = current_schema or "?"
            if schema == "?":
                if not is_standalone:
                    continue
                schema = "__standalone__" 

            # 解析列名：逐行看括号内的定义
            body = s[s.index("(", m.start() if m.lastindex else 0):]
            depth = 0
            cols = set()
            for line in body.splitlines():
                stripped = line.strip().rstrip(",")
                if not stripped:
                    continue
                cm = re.match(r"`?([a-zA-Z_][a-zA-Z0-9_]*)`?\s+[A-Za-z]", stripped)
                if cm:
                    name = cm.group(1).lower()
                    if name not in ("primary", "unique", "key", "index", "constraint",
                                     "foreign", "fulltext", "spatial", "check"):
                        cols.add(name)
                # 跟踪括号深度，找 CREATE TABLE 真正结束的位置
                depth += line.count("(") - line.count(")")
                if depth <= 0:
                    break
            tables[(schema, table)] = cols

    return schemas, tables


# ---------------------------------------------------------------- SQL 提取


def extract_sql_from_java():
    """从 Java 源码里抽出 SQL 字符串。

    Java 的字符串是 `"a" + "b" + "c"` 这种拼接形式，
    这里把连续的字面量拼接还原成一条完整 SQL。
    """
    results = []
    for jdir in JAVA_DIRS:
        if not jdir.exists():
            continue
        for jf in sorted(jdir.rglob("*.java")):
            if "/target/" in str(jf):
                continue
            text = jf.read_text(encoding="utf-8", errors="replace")
            # 匹配一个或多个字符串字面量（中间可有 + 拼接）
            for m in re.finditer(
                    r'((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)',
                    text):
                chunk = m.group(1)
                parts = re.findall(r'"((?:[^"\\]|\\.)*)"', chunk)
                if not parts:
                    continue
                joined = "".join(parts)
                if not joined.strip():
                    continue
                low = joined.lower()
                # 只保留像 SQL 的片段
                if not re.search(r"\b(select|insert\s+into|update|delete\s+from)\b", low):
                    continue
                # 行号
                line = text.count("\n", 0, m.start()) + 1
                results.append((jf, line, joined))
    return results


# ---------------------------------------------------------------- 校验



def column_exists(col, cols):
    """判断代码里引用的列在 DDL 里是否存在。

    要兼容两种写法：
    1. 原生 SQL：列名就是 DDL 里的 snake_case（planned_start_date）
    2. JPQL：属性名是 camelCase（plannedStartDate），由 Hibernate 的
       默认命名策略转成 snake_case 落到数据库

    另外 JPQL 里 `c.influencer.id` 这种「关联导航」不是列，
    它对应的是外键列 influencer_id —— 所以也放行。

    📌 设计取舍：宁可漏报也不误报。无法确定的写法一律放行。
    """
    if col.lower() in cols:
        return True
    # camelCase → snake_case（Spring Boot 3 默认策略）
    snake = re.sub(r"(?<!^)(?=[A-Z])", "_", col).lower()
    if snake in cols:
        return True
    # JPQL 关联导航 c.influencer.id → 外键列 influencer_id
    if snake + "_id" in cols:
        return True
    # DDL 是 camelCase、代码是 snake_case 的反向情况
    for c in cols:
        camel = re.sub(r"_([a-z])", lambda mm: mm.group(1).upper(), c)
        if camel.lower() == col.lower():
            return True
    return False


def check_sql(sql, tables, schemas):
    """返回问题列表 [(kind, detail)]"""
    problems = []
    low = sql.lower()

    # 去掉字符串字面量，避免把 'xxx' 当标识符
    stripped = re.sub(r"'[^']*'", " ", low)

    # 1) 找出所有 schema.table 引用
    #    分两种情况：
    #    a) FROM/JOIN 后面的 x.y —— x 一定是 schema，schema 不存在就是拼错
    #    b) 其他位置的 x.y —— 可能是「别名.列」，只有 x 是已知 schema 时才校验
    from_refs = set()
    for m in re.finditer(
            r"(?:from|join)\s+([a-z][a-z0-9_]*)\s*\.\s*([a-z_][a-z0-9_]*)\b", stripped):
        sch, tbl = m.group(1), m.group(2)
        if sch in SQL_KEYWORDS:
            continue
        from_refs.add((sch, tbl))
        if sch not in schemas:
            problems.append((
                "schema 不存在",
                f"`{sch}.{tbl}` —— 项目里没有 `{sch}` 这个库（database/*.sql 里没有对应的 CREATE DATABASE）",
            ))
        elif (sch, tbl) not in tables:
            problems.append((
                "表不存在",
                f"{sch}.{tbl} 在 database/*.sql 里没有对应的 CREATE TABLE",
            ))

    for m in re.finditer(r"\b([a-z][a-z0-9_]*)\s*\.\s*([a-z_][a-z0-9_]*)\b", stripped):
        schema, table = m.group(1), m.group(2)
        if schema in SQL_KEYWORDS or table in SQL_KEYWORDS:
            continue
        if (schema, table) in from_refs:
            continue
        # 排除 别名.列 的误判：如果 schema 不是已知 schema，很可能是别名
        if schema not in schemas:
            continue
        if (schema, table) not in tables:
            problems.append((
                "表不存在",
                f"{schema}.{table} 在 database/*.sql 里没有对应的 CREATE TABLE",
            ))

    # 2) 别名.列 校验：先建立 alias -> (schema, table) 映射
    alias_map = {}
    for m in re.finditer(
            r"(?:from|join)\s+([a-z][a-z0-9_]*)\s*\.\s*([a-z_][a-z0-9_]*)\s+"
            r"(?:as\s+)?([a-z_][a-z0-9_]*)?",
            stripped):
        schema, table, alias = m.group(1), m.group(2), m.group(3)
        if schema not in schemas:
            continue
        key = alias if alias and alias not in SQL_KEYWORDS else table
        alias_map[key] = (schema, table)

    # 无 schema 前缀的 FROM/JOIN（当前库）
    for m in re.finditer(
            r"(?:from|join)\s+([a-z_][a-z0-9_]*)\s+(?:as\s+)?([a-z_][a-z0-9_]*)?",
            stripped):
        table, alias = m.group(1), m.group(2)
        if table in SQL_KEYWORDS:
            continue
        key = alias if alias and alias not in SQL_KEYWORDS else table
        for (sc, tb) in tables:
            if tb == table:
                alias_map.setdefault(key, (sc, tb))

    # ⚠️ 别名.列 校验必须用**保留原始大小写**的文本：
    #    JPQL 里的属性名是 camelCase（c.plannedStartDate），
    #    而 DDL 里是 snake_case（planned_start_date）。
    #    之前统一 lower() 之后，camelCase 信息丢了，
    #    于是 plannedStartDate 被当成 plannedstartdate → 全部误报「列不存在」。
    stripped_orig = re.sub(r"'[^']*'", " ", sql)

    for m in re.finditer(r"\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\.\s*([a-zA-Z_][a-zA-Z0-9_]*)\b",
                         stripped_orig):
        alias, col = m.group(1), m.group(2)
        if alias.lower() not in alias_map:
            continue
        if col.lower() in SQL_KEYWORDS:
            continue
        schema, table = alias_map[alias.lower()]
        cols = tables.get((schema, table))
        if cols is None:
            continue
        if not column_exists(col, cols):
            problems.append((
                "列不存在",
                f"{alias}.{col} → {schema}.{table} 里没有这一列",
            ))

    # 去重
    seen = set()
    uniq = []
    for kind, detail in problems:
        key = (kind, detail)
        if key not in seen:
            seen.add(key)
            uniq.append((kind, detail))
    return uniq


def check_datasource():
    """检查各服务 datasource 的库名在 database/*.sql 里是否真能建出来"""
    problems = []
    for yml in sorted(ROOT.glob("erp-*-service/src/main/resources/application.yml")):
        text = yml.read_text(encoding="utf-8", errors="replace")
        # ⚠️ 必须先剥掉注释：yml 里常有「之前这里指向 jdbc:postgresql://.../xxx」这类
        #    说明性注释，不剥掉会把注释里的旧库名当成真实配置 → 误报。
        text = re.sub(r"^\s*#.*$", "", text, flags=re.M)
        # ⚠️ 必须同时匹配 mysql 和 postgresql：
        #    只匹配 jdbc:mysql:// 会让 PostgreSQL 服务**整个被跳过** ——
        #    巡检报告说「18 个 datasource 全部找到」，其实那几个 PG 服务根本没查。
        matched = False
        for m in re.finditer(
                r"jdbc:(?:mysql|postgresql)://[^/]+/([a-zA-Z0-9_]+)", text):
            matched = True
            db = m.group(1)
            svc = yml.parts[-5] if len(yml.parts) >= 5 else str(yml)
            problems.append((svc, db))
        # ⚠️ 兜底：yml 里写了 jdbc: URL，却一个库名都没解析出来 → 说明 URL 用了
        #    ${MYSQL_DATABASE:xxx} 这类变量占位，脚本**静默跳过**了这个服务。
        #    静默跳过 = 假装检查通过，必须显式报出来。
        if not matched and re.search(r"jdbc:(?:mysql|postgresql)://", text):
            svc = yml.parts[-5] if len(yml.parts) >= 5 else str(yml)
            problems.append((svc, "<解析不出库名：URL 里用了 ${VAR:默认值} 形式的库名>"))
    return problems


def extract_jpa_tables():
    """从 JPA 实体里抽出 @Table(name=...) / @TableName("...") 声明的表。

    这些表不在任何 SQL 字符串里，但同样要求 DDL 里有对应 CREATE TABLE。
    返回 [(file, table)]
    """
    results = []
    for jdir in JAVA_DIRS:
        if not jdir.exists():
            continue
        for jf in sorted(jdir.rglob("*.java")):
            if "/target/" in str(jf):
                continue
            raw = jf.read_text(encoding="utf-8", errors="replace")
            # ⚠️ 必须先剥掉注释再匹配 —— 否则 javadoc 里写的
            #    「实体原来写的是 {@code @TableName("t_competitor_product")}」
            #    会被当成真的声明，把已经改对的表名报成"缺 DDL"。
            text = strip_java_comments(raw)
            names = []
            for m in re.finditer(r"@Table\s*\(\s*name\s*=\s*\"([a-zA-Z0-9_]+)\"", text):
                names.append(m.group(1))
            for m in re.finditer(r"@TableName\s*\(\s*\"([a-zA-Z0-9_]+)\"", text):
                names.append(m.group(1))
            for name in names:
                line = raw.count("\n", 0, raw.find(name)) + 1
                results.append((jf, name))
    return results


def strip_java_comments(text):
    """去掉 Java 注释（块注释 + 行注释），只保留真正的代码。

    用途：javadoc 里经常会写「原来是 @TableName("xxx")」这种历史说明，
    正则直接扫全文会把它误当成真实声明。
    """
    # 块注释
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    # 行注释
    text = re.sub(r"//[^\n]*", "", text)
    return text


def detect_orm_mode():
    """判断每个服务是 MyBatis-Plus 还是 JPA，以及是否会自动建表。

    返回 {service_name: (orm, auto_ddl)}
      orm      ∈ {"MyBatis-Plus", "JPA"}
      auto_ddl ∈ {True, False}  —— 是否会在启动时自动建表

    📌 这个区分决定了缺表的严重程度：
    - MyBatis-Plus 且无 ddl-auto → **表不存在就必然运行时报错**（严重）
    - JPA 且 ddl-auto: update    → Hibernate 会自动建表（提示级）
    """
    modes = {}
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        if not svc_dir.is_dir():
            continue
        name = svc_dir.name
        yml = svc_dir / "src/main/resources/application.yml"
        auto_ddl = False
        is_jpa = False
        if yml.exists():
            text = yml.read_text(encoding="utf-8", errors="replace")
            if re.search(r"ddl-auto:\s*update", text):
                auto_ddl = True
            if re.search(r"^\s*jpa:", text, re.M):
                is_jpa = True
        has_mp = False
        for jf in svc_dir.rglob("*.java"):
            if "/target/" in str(jf):
                continue
            if "@TableName" in jf.read_text(encoding="utf-8", errors="replace"):
                has_mp = True
                break
        orm = "MyBatis-Plus" if has_mp else ("JPA" if is_jpa else "未知")
        modes[name] = (orm, auto_ddl)
    return modes


def service_of(jf):
    """文件属于哪个服务模块"""
    parts = jf.parts
    for p in parts:
        if p.startswith("erp-") and p.endswith("-service"):
            return p
    return "?"


def check_jpa_tables(tables, modes):
    """JPA/MyBatis 实体的表在 DDL 里找不到 → 报告（按严重程度分级）"""
    missing = {}
    for jf, table in extract_jpa_tables():
        tl = table.lower()
        if any(t == tl for (_, t) in tables):
            continue
        missing.setdefault((jf, table), None)

    results = []
    for (jf, table) in missing:
        svc = service_of(jf)
        orm, auto_ddl = modes.get(svc, ("未知", False))
        if orm == "JPA" and auto_ddl:
            severity = "提示"
            detail = "JPA ddl-auto:update 会自动建表，但 DDL 未纳入版本管理（重建库会丢）"
        elif orm == "MyBatis-Plus":
            severity = "严重"
            detail = "MyBatis-Plus 且无 ddl-auto → 表不存在，运行时 SQL 必然报错"
        else:
            severity = "提示"
            detail = "ORM 类型未知，需人工确认"
        results.append((jf, table, severity, detail))
    return results


# 模块 schema.sql ↔ database/*.sql 配对检查
# 同一套 DDL 有两份副本（服务内副本给 dev/test 的 H2 用，database/ 的给生产 MySQL 用），
# 两份不一致 → 生产的表结构和 dev 测的根本不是一回事，最难排查。
MODULE_DB_PAIR = {
    "erp-anomaly-detection-service": "database/anomaly-detection-service.sql",
    "erp-inventory-alert-service": "database/inventory-alert-service.sql",
    "erp-product-recommendation-service": "database/product-recommendation-service.sql",
    "erp-tenant-service": "database/tenant-service.sql",
}


def _tables_in(text):
    """从 DDL 文本里抽出表名集合（剥掉注释）"""
    text = re.sub(r"--[^\n]*", "", text)
    # ⚠️ 反引号必须闭合：`?` 可选会导致 billing_rules_XX 被截成 billing_rules，
    #    表名漂移就检测不出来了。
    found = set()
    for m in re.finditer(
            r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(?:`([a-zA-Z0-9_]+)`|([a-zA-Z0-9_]+))\s*\(",
            text, re.I):
        found.add((m.group(1) or m.group(2)).lower())
    return found


def _cols_in(text, table):
    """抽出某张表的列名集合"""
    text = re.sub(r"--[^\n]*", "", text)
    m = re.search(r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(?:`" + re.escape(table) +
                  r"`|" + re.escape(table) + r")\s*\((.*?)\n\s*\)\s*(?:ENGINE|;|$)",
                  text, re.I | re.S)
    if not m:
        return None
    cols = set()
    for line in m.group(1).splitlines():
        line = line.strip().rstrip(",")
        if not line:
            continue
        cm = re.match(r"`?([a-zA-Z_][a-zA-Z0-9_]*)`?\s+[A-Za-z]", line)
        if cm and cm.group(1).lower() not in ("primary", "unique", "key", "index",
                                              "constraint", "foreign", "fulltext",
                                              "spatial", "check"):
            cols.add(cm.group(1).lower())
    return cols


def check_module_db_drift():
    """模块 schema.sql 与 database/*.sql 的表/列是否一致"""
    problems = []
    for module, db_file in MODULE_DB_PAIR.items():
        mod_sql = ROOT / module / "src/main/resources/schema.sql"
        db_sql = ROOT / db_file
        if not mod_sql.exists():
            problems.append((module, db_file, "模块内没有 schema.sql，无法比对"))
            continue
        if not db_sql.exists():
            problems.append((module, db_file, "database/ 下没有对应的建库脚本"))
            continue
        t_mod = _tables_in(mod_sql.read_text(encoding="utf-8"))
        t_db = _tables_in(db_sql.read_text(encoding="utf-8"))
        only_mod = t_mod - t_db
        only_db = t_db - t_mod
        if only_mod:
            problems.append((module, db_file,
                             f"表 {', '.join(sorted(only_mod))} 只在模块 schema.sql 里有，database/ 里没有"))
        if only_db:
            problems.append((module, db_file,
                             f"表 {', '.join(sorted(only_db))} 只在 database/ 里���，模块 schema.sql 没有"))
        for tbl in sorted(t_mod & t_db):
            c_mod = _cols_in(mod_sql.read_text(encoding="utf-8"), tbl)
            c_db = _cols_in(db_sql.read_text(encoding="utf-8"), tbl)
            if c_mod is None or c_db is None:
                continue
            if c_mod != c_db:
                diff_m = c_mod - c_db
                diff_d = c_db - c_mod
                detail = []
                if diff_m:
                    detail.append(f"模块多 {', '.join(sorted(diff_m))}")
                if diff_d:
                    detail.append(f"database 多 {', '.join(sorted(diff_d))}")
                problems.append((module, db_file, f"表 {tbl} 的列不一致 → {'；'.join(detail)}"))
    return problems


def main():
    strict = "--strict" in sys.argv
    schemas, tables = parse_ddl()
    modes = detect_orm_mode()

    print("=" * 78)
    print("SQL 契约巡检：Java SQL ↔ database/*.sql DDL")
    print("=" * 78)
    print(f"解析到 {len(schemas)} 个 schema，{len(tables)} 张表\n")

    sqls = extract_sql_from_java()
    print(f"扫描 {len(sqls)} 段 SQL（来自 {len(JAVA_DIRS)} 个模块）\n")

    total = 0
    fatal = 0
    by_kind = {}
    for jf, line, sql in sqls:
        problems = check_sql(sql, tables, schemas)
        if not problems:
            continue
        rel = jf.relative_to(ROOT)
        for kind, detail in problems:
            total += 1
            fatal += 1
            by_kind[kind] = by_kind.get(kind, 0) + 1
            print(f"❌ [{kind}] {rel}:{line}")
            print(f"   {detail}")
            snippet = " ".join(sql.split())[:150]
            print(f"   SQL: {snippet}\n")

    # JPA / MyBatis 实体表
    jpa_problems = check_jpa_tables(tables, modes)
    if jpa_problems:
        print("-" * 78)
        print("实体表检查（@Table / @TableName）")
        print("-" * 78)
        by_file = {}
        for jf, table, sev, detail in jpa_problems:
            by_file.setdefault((jf.relative_to(ROOT), sev, detail), []).append(table)
        for (rel, sev, detail), ts in sorted(by_file.items()):
            icon = "🔴" if sev == "严重" else "⚠️ "
            print(f"{icon} [{sev}] {rel}")
            print(f"   表 {', '.join(sorted(ts))} 在 database/*.sql 里没有 CREATE TABLE")
            print(f"   {detail}")
            total += 1
            if sev == "严重":
                fatal += 1
            by_kind["实体表缺失"] = by_kind.get("实体表缺失", 0) + len(ts)
        print()

    # datasource 库名
    # 模块 schema.sql ↔ database/*.sql 一致性
    print("-" * 78)
    print("模块 schema.sql ↔ database/*.sql 一致性检查")
    print("-" * 78)
    drift = check_module_db_drift()
    for module, db_file, detail in drift:
        print(f"❌ {module} ↔ {db_file}")
        print(f"   {detail}")
    if not drift:
        print(f"✓ {len(MODULE_DB_PAIR)} 对模块 DDL 副本表/列完全一致")
    print()

    print("-" * 78)
    print("datasource 库名检查")
    print("-" * 78)
    ds = check_datasource()
    ds_bad = []
    for svc, db in ds:
        if db.lower() not in schemas:
            ds_bad.append((svc, db))
            print(f"❌ {svc} → 库 `{db}` 在 database/*.sql 里没有 CREATE DATABASE")
    if not ds_bad:
        print(f"✓ {len(ds)} 个 datasource 库名全部能在 DDL 里找到")
    print()

    print("=" * 78)
    print(f"合计问题 {total + len(ds_bad) + len(drift)} 项（其中致命 {fatal + len(ds_bad) + len(drift)} 项）")
    if by_kind:
        for k, v in sorted(by_kind.items()):
            print(f"  {k}: {v} 项")
    print("=" * 78)

    if strict and (fatal > 0 or ds_bad or drift):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
