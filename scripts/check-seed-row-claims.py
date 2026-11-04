#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「种子条数声明 ↔ 实际行数」巡检：**代码和文档里写的条数，必须是真的**。

## 为什么需要这个脚本

2026-11-04 抓到一个「界面自相矛盾」的真缺陷时，顺手发现了**同一类问题的另一个变种**：

    docs/DEAD-ENDPOINTS.md
      「原来写死「330+」而真实只有 **6** 条 → 界面在承诺不存在的东西。」

    而 `database/ai-assistant-service.sql` 里 `knowledge_base` 的种子是 **31** 条。

→ **手写的数字结论会静默过期**（规则 51）。
   规则 51 已经解决过「**接口**是不是死了」这一类，
   但「**这张表有几条数据**」这一类**没人管**：
   文档说 6、代码说 31、数据库里 31 —— 三个数字，只有一个是真的，
   而且**没有任何闸门会发现**。

📌 更阴的一种：种子脚本**重跑会翻倍**（没有唯一键时 31 → 62 → 124…），
   而**前端开场白显示的就是这个条数** → 界面开始说假话。

## 本脚本查什么

1. **算出每张表的真实种子行数**
   —— 从 `database/*.sql` 的 `INSERT INTO ... VALUES` 逐条数。
   ⚠️ **必须先切掉 `ON DUPLICATE KEY UPDATE` 子句** ——
   它的 `VALUES(question)` 里也有括号 → 会被当成数据行 → **多算**。
   （实测：knowledge_base 被算成 35，真值 31；knowledge_document 算成 10，真值 5。）

2. **核验「自己声明了断言」的结论（致命）**
   —— 全仓扫 `rows: <表名>=<条数>`（.md / .java / .ts / .vue 的注释都行）
   —— 与真实行数不符 → **exit 1**
   —— 这就是规则 51 的做法：**让结论自己声明断言，脚本当场核验**。

3. **自动发现「同句里点了表名 + 给了条数」的声明（致命）**
   —— 形如「`knowledge_base` 真实种子只有 31 条」
   —— 同一句里出现已知表名 + 一个「N 条」→ 当场核验 → 不符 → **exit 1**

4. **报告每张有种子的表的真实行数**（供人对照）

## 为什么「同句里有表名」才致命

自由文本里的数字太杂（「共 9 条声明」「8 个服务」……），
没有表名就没法判断它说的是哪张表 → **只能跳过，不能瞎报**（规则 42）。
→ 所以闸门要求：**要断言，就带上表名**。

## 用法

    python3 scripts/check-seed-row-claims.py

退出码：
    0 = 全部一致
    1 = 有声明的条数**和真实行数对不上**（fatal —— 文档在骗人）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_DIR = ROOT / "database"

SCAN_GLOBS = ["*.md", "*.java", "*.ts", "*.vue", "*.yml", "*.yaml"]
SCAN_DIRS = [
    ROOT / "docs",
    ROOT / "erp-web/src",
    ROOT / "scripts",
    ROOT / ".github",
]
SCAN_FILES = [ROOT / "README.md"]

# 「N 条」类数字
N_ROWS = re.compile(r"(\d+)\s*条")
# 显式断言：rows: table=N
ASSERT_ROWS = re.compile(r"rows\s*:\s*([A-Za-z_]\w*)\s*=\s*(\d+)")

# `ON DUPLICATE KEY UPDATE` 子句（不区分大小写）
ON_DUP = re.compile(r"ON\s+DUPLICATE\s+KEY\s+UPDATE", re.I)


# ══════════════════════════════════════════════════════════════════════
# 1. 算真实种子行数
# ══════════════════════════════════════════════════════════════════════

def split_top_level(vals):
    """把 VALUES 里的内容切成顶层元组。

    ⚠️ 必须处理字符串里的括号和转义 —— 否则
    `'怎么用 (新) 功能？'` 这种问题文本会把计数带偏。
    """
    rows = []
    depth = 0
    cur = []
    instr = False
    quote = ""
    i = 0
    while i < len(vals):
        ch = vals[i]
        if instr:
            cur.append(ch)
            if ch == "\\" and i + 1 < len(vals):
                cur.append(vals[i + 1])
                i += 2
                continue
            if ch == quote:
                instr = False
        else:
            if ch in "'\"`":
                instr = True
                quote = ch
                cur.append(ch)
            elif ch == "(":
                depth += 1
                if depth == 1:
                    cur = []
                else:
                    cur.append(ch)
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    rows.append("".join(cur).strip())
                else:
                    cur.append(ch)
            elif depth > 0:
                cur.append(ch)
        i += 1
    return rows


def strip_on_duplicate(values_part):
    """切掉 `ON DUPLICATE KEY UPDATE ...` 子句。

    📌 **这是本脚本最容易踩的坑**：
    ```sql
    INSERT INTO knowledge_base (...) VALUES
    ('a', ...),
    ('b', ...),
    ON DUPLICATE KEY UPDATE question = VALUES(question), answer = VALUES(answer);
    ```
    `VALUES(question)` 里也有**一对括号** → 不切掉就会被当成**两条数据行** → 多算。
    （实测：knowledge_base 35 vs 真值 31；knowledge_document 10 vs 真值 5。）
    """
    out = []
    instr = False
    quote = ""
    i = 0
    n = len(values_part)
    while i < n:
        ch = values_part[i]
        if instr:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                out.append(values_part[i + 1])
                i += 2
                continue
            if ch == quote:
                instr = False
            i += 1
            continue
        if ch in "'\"`":
            instr = True
            quote = ch
            out.append(ch)
            i += 1
            continue
        # 顶层（不在字符串里）遇到 ON DUPLICATE → 截断
        # ⚠️ 关键词是 23 个字符（"ON DUPLICATE KEY UPDATE"），
        #    切片短了就会匹配不上 → 子句没被切掉 → **多算行数**。
        if ON_DUP.match(values_part, i):
            break
        out.append(ch)
        i += 1
    return "".join(out)


def split_fields(tuple_text):
    """把一个顶层元组切成字段列表（尊重引号和转义）。"""
    fields = []
    cur = []
    instr = False
    quote = ""
    i = 0
    while i < len(tuple_text):
        ch = tuple_text[i]
        if instr:
            cur.append(ch)
            if ch == "\\" and i + 1 < len(tuple_text):
                cur.append(tuple_text[i + 1])
                i += 2
                continue
            if ch == quote:
                instr = False
        else:
            if ch in "'\"`":
                instr = True
                quote = ch
                cur.append(ch)
            elif ch == ",":
                fields.append("".join(cur).strip())
                cur = []
            else:
                cur.append(ch)
        i += 1
    fields.append("".join(cur).strip())
    return fields


def parse_unique_keys():
    """{表名: [[唯一键列, ...], ...]} —— 每个元素是一条完整的唯一键定义。

    📌 **为什么要自然键**：
    **同一张表可能被多个 seed 文件插入**（实测 `product` 被
    `inventory-prediction-service.sql` 和 `pricing-service.sql` 各插 5 行，
    且都是 `ON DUPLICATE KEY UPDATE` 同一个 `product_code`）
    → 真实行数是 **5**，不是 5+5=**10**。
    → 直接相加 → **高估** → 断言核验必然假红。

    📌 **两种写法都要认**：
    - 表级：`UNIQUE KEY uk_x (col, ...)`
    - 列级：`col VARCHAR(50) NOT NULL UNIQUE`
    只认表级 → `product` 的自然键找不到 → 仍然高估。
    """
    out = {}
    if not DB_DIR.exists():
        return out
    for p in sorted(DB_DIR.glob("*.sql")):
        try:
            src = p.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        src = re.sub(r"--[^\n]*", " ", src)
        for m in re.finditer(
                r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z_]\w*)\s*\((.*?)\n\s*\)\s*ENGINE",
                src, re.S | re.I):
            table, body = m.group(1), m.group(2)
            # 表级约束：UNIQUE KEY uk_x (col, ...)
            for um in re.finditer(
                    r"UNIQUE\s+KEY\s+\w+\s*\(([^)]*)\)", body, re.I):
                cols = []
                for c in um.group(1).split(","):
                    # 去掉前缀长度：`question(191)` → `question`
                    c = c.strip().split("(")[0].strip().strip("`")
                    if c:
                        cols.append(c)
                if cols:
                    out.setdefault(table, []).append(cols)
            # 列级约束：`product_code VARCHAR(50) NOT NULL UNIQUE`
            for line in body.split("\n"):
                ln = line.strip().rstrip(",")
                if not ln or ln.upper().startswith(("UNIQUE KEY", "PRIMARY KEY",
                                                    "INDEX", "KEY", "CONSTRAINT",
                                                    "--", "FULLTEXT")):
                    continue
                if re.search(r"\bUNIQUE\b", ln, re.I):
                    col = ln.split()[0].strip("`")
                    if re.fullmatch(r"[A-Za-z_]\w*", col):
                        out.setdefault(table, []).append([col])
    return out


def count_seed_rows():
    """{表名: 种子行数}（按**自然键**去重）

    📌 **去重是必须的**：一张表被 N 个 seed 文件插入同一批自然键时
    （`ON DUPLICATE KEY UPDATE`），真实行数是 **一份**，不是 N 份。
    复合唯一键要用**整组列**（`uk_kb_category_question (category, question)`）
    —— 只用第一列会把 31 条压成 7 个分类 → **低估**。
    """
    counts = {}
    unique_keys = parse_unique_keys()
    if not DB_DIR.exists():
        return counts
    for p in sorted(DB_DIR.glob("*.sql")):
        try:
            src = p.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        src = re.sub(r"--[^\n]*", " ", src)
        for m in re.finditer(
                r"INSERT\s+INTO\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*VALUES\s*(.*?);",
                src, re.S | re.I):
            table = m.group(1)
            cols = [c.strip().strip("`") for c in m.group(2).split(",")]
            body = strip_on_duplicate(m.group(3))
            rows = split_top_level(body)
            if not rows:
                continue
            # 挑一条**所有列都在 INSERT 列表里**的唯一键
            key_cols = None
            for cand in unique_keys.get(table, []):
                if all(c in cols for c in cand):
                    key_cols = cand
                    break
            bucket = counts.setdefault(table, set())
            if key_cols:
                idxs = [cols.index(c) for c in key_cols]
                for r in rows:
                    f = split_fields(r)
                    bucket.add(tuple(f[i] if i < len(f) else "" for i in idxs))
            else:
                # 没有可用自然键 → 退化成「按文件累加」，并用文件名+序号避免跨文件误合并
                for i in range(len(rows)):
                    bucket.add((p.name, i))
    return {t: len(v) for t, v in counts.items()}


# ══════════════════════════════════════════════════════════════════════
# 2. 扫断言
# ══════════════════════════════════════════════════════════════════════

def iter_scannable_files():
    seen = set()
    for f in SCAN_FILES:
        if f.exists():
            seen.add(f)
    for d in SCAN_DIRS:
        if not d.exists():
            continue
        for pat in SCAN_GLOBS:
            for f in d.rglob(pat):
                if f.is_file() and ".git" not in f.parts:
                    seen.add(f)
    return sorted(seen)


def find_claims():
    """[(文件, 行号, 表名, 条数, 原文)]"""
    out = []
    for f in iter_scannable_files():
        try:
            lines = f.read_text(encoding="utf-8", errors="ignore").split("\n")
        except Exception:
            continue
        for lineno, line in enumerate(lines, 1):
            # (a) 显式断言
            for m in ASSERT_ROWS.finditer(line):
                out.append((f, lineno, m.group(1), int(m.group(2)), line.strip(),
                            "显式断言"))
            # (b) 同句里点了表名 + 给了条数
            for table in KNOWN_TABLES:
                if not re.search(r"\b" + re.escape(table) + r"\b", line):
                    continue
                for m in N_ROWS.finditer(line):
                    out.append((f, lineno, table, int(m.group(1)), line.strip(),
                                "句中带表名"))
    return out


def known_tables(counts):
    """有种子数据的表名集合。"""
    return set(counts.keys())


# ══════════════════════════════════════════════════════════════════════
# 3. 主流程
# ══════════════════════════════════════════════════════════════════════

def main():
    counts = count_seed_rows()
    global KNOWN_TABLES
    KNOWN_TABLES = known_tables(counts)

    print("=" * 72)
    print("「种子条数声明 ↔ 实际行数」巡检")
    print("=" * 72)
    print(f"有种子数据的表：{len(counts)} 张")
    for t, c in sorted(counts.items()):
        print(f"  · {t:32s} {c:4d} 条")
    print()

    claims = find_claims()
    problems = []
    unverifiable = []

    for f, lineno, table, claimed, text, kind in claims:
        if table not in counts:
            unverifiable.append((f, lineno, table, claimed, text, kind))
            continue
        real = counts[table]
        if claimed != real:
            problems.append((f, lineno, table, claimed, real, text, kind))

    if problems:
        print(f"🔴 有 {len(problems)} 处「条数声明」和真实行数对不上：\n")
        for f, lineno, table, claimed, real, text, kind in problems:
            rel = f.relative_to(ROOT) if f.is_relative_to(ROOT) else f
            print(f"  ❌ {rel}:{lineno}  [{kind}]")
            print(f"       表        : {table}")
            print(f"       声明条数  : {claimed}")
            print(f"       真实条数  : {real}")
            print(f"       原文      : {text[:160]}")
            print(f"       → 文档/注释在骗人（规则 51）")
            print()
    else:
        print("✅ 所有「条数声明」都和真实行数一致")
        print()

    if unverifiable:
        print(f"⚠️ 以下 {len(unverifiable)} 处提到了表名，但那张表**没有种子数据**"
              "（无法核验，不是错）：")
        for f, lineno, table, claimed, text, kind in unverifiable:
            rel = f.relative_to(ROOT) if f.is_relative_to(ROOT) else f
            print(f"  · {rel}:{lineno}  {table} = {claimed}")
        print()

    print("=" * 72)
    print(f"结论：核验 {len(claims)} 处声明，"
          f"不一致 {len(problems)} 处，无法核验 {len(unverifiable)} 处")
    print("=" * 72)

    if problems:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
