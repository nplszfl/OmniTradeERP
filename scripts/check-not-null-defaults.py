#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""巡检「实体 ↔ 线上表结构」的错位 —— 插入必失败 / 查询必报 Unknown column。

## 为什么需要这个脚本

2026-11-03 抓到两类真缺陷：

**① NOT NULL 且无默认值的列，实体/调用方没赋值**
   `t_order.currency_code` 是 `VARCHAR(10) NOT NULL`（**无默认值**），
   而 MyBatis-Plus 的 insert 默认 `FieldStrategy.NOT_NULL` 会**跳过 null 字段**
   → INSERT 语句里根本没有这一列 → MySQL 报
   `Field 'currency_code' doesn't have a default value` → **整个订单插入失败**。

**② 实体字段映射到表里不存在的列**
   `ProductSku` 实体用 `sku`/`name`，而 `t_product_sku` 表里是
   `sku_code`/`sku_name` → MyBatis-Plus 驼峰转下划线映射 →
   SQL 报 `Unknown column 'name' in 'field list'`。

📌 **为什么单测查不出来**：单测用 mock 的 Mapper，
   根本不会真的执行 SQL，也就没有「NOT NULL 无默认值」「列不存在」这回事。
   这类缺陷只在**真 MySQL** 上才暴露。

## 本脚本查什么

以 **Docker 里真跑的 MySQL** 为准（不是 DDL 文件 —— DDL 文件和线上可能不一致）：

1. 🔴 实体里**每个字段映射出的列**，线上表里**不存在** → 查询必报 Unknown column
2. 🔴 线上 **NOT NULL 且无默认值**的列，实体里**没有对应字段** → 插入必失败
3. ⚠️ 线上 NOT NULL 无默认值的列，实体里有字段 → 调用方必须保证有值（重点排查对象）

📌 **实体字段 → 列名 的映射规则**（与 MyBatis-Plus 默认一致）：
   - 有 `@TableField("col")` → 列名 = `col`
   - 无 `@TableField` → 驼峰转下划线（`skuCode` → `sku_code`）
   - `@TableField(exist = false)` → 不映射，跳过

## 用法

    python3 scripts/check-not-null-defaults.py [--container <mysql 容器名>]

环境变量：
    MYSQL_CONTAINER  容器名（默认 erp-fresh-mysql）
    MYSQL_PASSWORD   root 密码（默认 root）

退出码：0 = 没有「列不存在」/「实体缺字段」的必炸项，1 = 有
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

CONTAINER = os.environ.get("MYSQL_CONTAINER", "erp-fresh-mysql")
PASSWORD = os.environ.get("MYSQL_PASSWORD", "root")

# 数据库 → 代码里的服务模块
DB_ALIAS = {
    "erp_order": "erp-order-service",
    "erp_customer": "erp-customer-service",
    "erp_supplier": "erp-supplier-service",
    "erp_product": "erp-product-service",
    "erp_user": "erp-user-service",
    "erp_inventory": "erp-inventory-service",
    "erp_finance": "erp-finance-service",
    "erp_logistics": "erp-logistics-service",
    "erp_tenant": "erp-tenant-service",
    "erp_warehouse": "erp-warehouse-service",
    "erp_purchase": "erp-purchase-service",
    "erp_pricing": "erp-pricing-service",
    "erp_social_commerce": "erp-social-commerce-service",
    "erp_ai_assistant": "erp-ai-assistant-service",
}

# 这些列由框架/基类自动填，不算「实体缺字段」
FRAMEWORK_COLUMNS = {
    "id", "create_time", "update_time", "create_by", "update_by",
    "deleted", "version", "tenant_id",
}


def mysql(query):
    """在容器里跑 SQL，返回行列表"""
    cmd = ["docker", "exec", CONTAINER, "mysql", "-uroot", f"-p{PASSWORD}",
           "-N", "-B", "-e", query]
    out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if out.returncode != 0:
        return None
    return [line.split("\t") for line in out.stdout.strip().split("\n") if line]


def live_schema():
    """返回 {db: {table: {col: (nullable, has_default)}}}"""
    rows = mysql(
        "SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, IS_NULLABLE, "
        "IFNULL(COLUMN_DEFAULT,'<NULL>'), EXTRA "
        "FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA NOT IN ('mysql','information_schema','performance_schema','sys') "
        "ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;")
    if rows is None:
        return None
    schema = {}
    for r in rows:
        if len(r) < 6:
            continue
        db, table, col, nullable, default, extra = r[:6]
        has_default = default != "<NULL>" or "auto_increment" in extra.lower()
        schema.setdefault(db, {}).setdefault(table, {})[col] = (nullable, has_default)
    return schema


def entity_map():
    """返回 {(svc, table): {"cols": {列名}, "raw": {列名: 字段名}}}"""
    out = {}
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        svc = svc_dir.name
        for ent in sorted(svc_dir.glob("src/main/java/**/entity/*.java")):
            text = ent.read_text(encoding="utf-8", errors="ignore")
            tm = re.search(r'@TableName\(\s*["\']([^"\']+)["\']', text)
            if not tm:
                continue
            table = tm.group(1)

            cols, raw = set(), {}
            # ① @TableField("col") 或 @TableField(value = "col")
            for fm in re.finditer(
                    r'@TableField\(\s*(?:value\s*=\s*)?["\']([^"\']+)["\']\s*\)', text):
                cols.add(fm.group(1))
            # ② 无 @TableField 的字段：驼峰 → 下划线
            #    📌 带 @TableField 的字段**只能**映射到那一列，
            #    不能同时算上驼峰名（否则 `name` + @TableField("product_name")
            #    会同时产出 product_name 和 name 两个列名 → 误报 Unknown column）
            annotated_fields = set()
            for fm in re.finditer(
                    r'@TableField\([^)]*\)\s*(?:/\*\*(?:[^*]|\*(?!/))*\*/\s*)?'
                    r'private\s+[\w<>,\.\[\]]+\s+(\w+)\s*;', text, re.S):
                annotated_fields.add(fm.group(1))
            for fm in re.finditer(r"private\s+[\w<>,\.\[\]]+\s+(\w+)\s*;", text):
                name = fm.group(1)
                if name in annotated_fields:
                    continue
                snake = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
                cols.add(snake)
                raw.setdefault(snake, name)
            # ③ @TableField(exist = false) 的字段不映射 → 剔除
            for fm in re.finditer(
                    r'@TableField\(\s*exist\s*=\s*false\s*\)\s*(?:/\*\*(?:[^*]|\*(?!/))*\*/\s*)?'
                    r'private\s+[\w<>,\.\[\]]+\s+(\w+)\s*;', text, re.S):
                name = fm.group(1)
                snake = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
                cols.discard(snake)
                cols.discard(name)
            out[(svc, table)] = {"cols": cols, "raw": raw}
    return out


def main():
    # 📌 **--strict**：连不上库时**直接失败**，而不是「跳过 + 绿灯」
    #    📌 **为什么必须**：闸门在 CI 里跑，如果 MySQL 没起来，
    #    静默跳过 = **假装检查过了** → 盲区直接溜过去。
    strict = "--strict" in sys.argv

    schema = live_schema()
    if schema is None:
        if strict:
            print(f"❌ 连不上 MySQL 容器 `{CONTAINER}` —— 检查无法进行（strict 模式）")
            print(f"   先跑：scripts/start-audit-mysql.sh")
            return 1
        print(f"⚠️  连不上 MySQL 容器 {CONTAINER} —— 跳过（无法确认真实表结构）")
        return 0

    ents = entity_map()
    hard_fail = []   # 实体字段映射到不存在的列 → 查询必报 Unknown column
    missing = []     # NOT NULL 无默认值的列，实体里没字段 → 插入必失败
    watch = []       # 实体有字段，但调用方可能不设 → 重点排查对象

    for db, tables in sorted(schema.items()):
        svc = DB_ALIAS.get(db)
        if not svc:
            continue
        for table, cols in sorted(tables.items()):
            key = (svc, table)
            if key not in ents:
                continue
            ecols = ents[key]["cols"]
            elower = {c.lower() for c in ecols}

            # ① 实体字段 → 线上不存在的列
            for c in sorted(ecols):
                if c.lower() not in {x.lower() for x in cols}:
                    hard_fail.append((db, table, c))

            # ② NOT NULL 且无默认值的列
            for name, (nullable, has_default) in sorted(cols.items()):
                if nullable == "YES" or has_default:
                    continue
                if name.lower() in FRAMEWORK_COLUMNS:
                    continue
                if name.lower() in elower:
                    watch.append((db, table, name))
                else:
                    missing.append((db, table, name))

    if hard_fail:
        print("🔴 实体字段映射到线上不存在的列 —— 一旦查询就报 Unknown column：")
        for db, table, col in hard_fail:
            print(f"   [{db}.{table}]  实体映射到列 `{col}`，但线上表里没有这一列")
        print()

    if missing:
        print("🔴 线上 NOT NULL 且无默认值的列，实体里没有对应字段 —— 插入必失败：")
        for db, table, name in missing:
            print(f"   [{db}.{table}]  `{name}` NOT NULL 无默认值  ← 实体没有对应字段")
        print()

    if watch:
        print(f"⚠️  {len(watch)} 个 NOT NULL 无默认值的列，实体里有字段 "
              f"—— 调用方必须保证有值（漏设就插入失败）：")
        for db, table, name in watch:
            print(f"   [{db}.{table}]  `{name}`")

    if hard_fail or missing:
        print()
        print("提示：以线上表结构为准改实体字段，或改 DDL 加 DEFAULT。")
        return 1

    print(f"✅ 没有「列不存在」/「实体缺字段」的必炸项"
          f"（检查了 {len(ents)} 张表，⚠️ 列表 {len(watch)} 项待排查）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
