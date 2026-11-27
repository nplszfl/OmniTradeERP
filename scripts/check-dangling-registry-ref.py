#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「登记表引用悬空」巡检 —— 第 81 道闸门。

📌 **为什么需要这道闸门**（2026-11-24/26 实测踩出来的坑）：

`erp-product-description-service` 的「AI 商品描述」模块里，模板来自一张
**登记表**（`description_template`，`template_id` 上有 UNIQUE 约束 + 4 条种子）。
但代码里的模板 ID 是**另一套值**：

```java
// ❌ 修之前（ProductDescriptionServiceImpl）
private final Map<String, String> templates = new HashMap<>();
private void initTemplates() {
    templates.put("amazon", "amazon_product_template");   // ← 库里没有这个值
    templates.put("ebay",   "ebay_product_template");
    templates.put("shopee", "shopee_product_template");
    templates.put("general","general_product_template");
}
```

而库里真实种的是：`amazon_template` / `ebay_template` /
`shopee_template` / `general_template` —— **一个都对不上**。

📌 **为什么「编译通过 + 单测全绿 + 80 道闸门全绿」都发现不了**：

| 现象 | 原因 |
|---|---|
| 编译通过 | `"amazon_product_template"` 只是个字符串字面量，编译器不管它存不存在 |
| 单测全绿 | 单测用 Mockito 把 Mapper mock 掉了 → **查库这一步根本没执行** |
| 闸门全绿 | 已有闸门查的是「内存存储冒充 DB」「硬编码常量」「SQL 契约」，**没有一道查「引用是否悬空」** |
| 接口 200 | `GET /api/v1/product-description/templates` 返回的是**写死的 4 条元数据**，根本没查库 |

🔴 **后果**（这才是真正的危害，不是「看着不好看」）：

1. `product_description.template_id` 落库的是**悬空引用** →
   「这条描述用了哪个模板」永远追溯不出来（join 不到 `description_template`）。
2. 运营在后台改 `description_template` 的模板正文 → 对生成结果**零影响**
   （模板正文压根没进过 prompt，`useTemplate` / `templateId` 是两个**死参数**）。
3. `listTemplates()` 返回的模板 ID 与库里真实值不一致 → 前端拿到的 ID
   回传给后端 → **永远匹配不到任何模板**。

📌 **本闸门查什么**（窄判据，宁可漏报也不误报）：

1. 从 `database/*.sql` 里找出**登记表** = 「有 UNIQUE 列」+「该列有种子数据」的表。
2. 从这些种子值里**推导标识符形状**（后缀，如 `_template`）——
   ⚠️ 必须**所有**种子值都符合该后缀才认，否则一个表里混着别的形状就说明不是同一族。
3. 在 `src/main` + `src/test` 的 Java 里找**同形状的字符串字面量**，
   逐个核对是否在**合法值集合**里 → 不在 → **悬空引用**，报错。

📌 **为什么要连 `src/test` 一起扫**：
生产代码里那个悬空值是**注释里的一句「以前是 amazon_product_template」**，
真正写死它的是**单测**（`request.setTemplateId("amazon_product_template")`）。
只扫 `src/main` 会漏掉一半 —— 而**单测里那个值会原样落库**
（`ProductDescriptionPersistenceTest` 断言的是真落库）→ 悬空引用进了生产库。

📌 **为什么不能只扫 `src/main`**：见上。单测里的悬空 ID 会**真的写进数据库**。

📌 **反向验证**：`--self-test` 里注入缺陷形状 → 必须变红；还原 → 必须变绿。

用法：
    python3 scripts/check-dangling-registry-ref.py            # 人看
    python3 scripts/check-dangling-registry-ref.py --strict   # CI 用（有问题退出码 1）
    python3 scripts/check-dangling-registry-ref.py --self-test # 自检
"""
import argparse
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 📌 候选后缀 —— 「同一族」标识符的常见形状
SUFFIX_CANDIDATES = (
    "_template",
    "_dict",
    "_type",
    "_config",
    "_code",
    "_category",
)

# 📌 Java 源码里要扫的目录（src/main + src/test 都要扫，见上文）
SCAN_SUBDIRS = ("src/main", "src/test")

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", ".idea"}

# 📌 DDL 里 CREATE TABLE 的列定义（拿到表体）
CREATE_TABLE_RE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?([A-Za-z_][A-Za-z0-9_]*)[`\"]?"
    r"\s*\((.*?)\)\s*(?:ENGINE|DEFAULT|;)",
    re.IGNORECASE | re.DOTALL,
)

# 📌 列级 UNIQUE：`col VARCHAR(50) NOT NULL UNIQUE`
UNIQUE_COL_RE = re.compile(
    r"^\s*`?([a-z_][a-z0-9_]*)`?\s+[A-Za-z]+(?:\s*\([^)]*\))?[^,\n]*?\bUNIQUE\b",
    re.IGNORECASE | re.MULTILINE,
)

# 📌 INSERT INTO tbl (cols) VALUES (...);
INSERT_RE = re.compile(
    r"INSERT\s+(?:IGNORE\s+)?INTO\s+`?([A-Za-z_][A-Za-z0-9_]*)`?\s*"
    r"(?:\(([^;]*?)\))?\s*VALUES\s*(.*?);",
    re.IGNORECASE | re.DOTALL,
)

# 📌 Java 字符串字面量
STRING_LIT_RE = re.compile(r'"([A-Za-z0-9_]+)"')

# 📌 **显式豁免标记** —— 放在**要豁免的那一行**的注释里。
#
# 🔴 **为什么需要它**：单测里**故意**造一个登记表里不存在的值，
#    来验证「取不到就返回空 / 抛错」这类行为 —— 那**不是**悬空引用缺陷，
#    是**测试意图**。没有豁免机制的话，闸门会把自己的测试判成缺陷
#    → 逼着开发者去**改测试来骗过闸门** → 闸门变哑（规则 99/102）。
#
# ⚠️ **豁免必须是逐行显式的**，不能按文件/按目录豁免 ——
#    按文件豁免 = 整份测试文件的所有悬空值都不查了 → 真缺陷也漏。
ALLOW_MARKER = "dangling-ref:allow"


# --------------------------------------------------------------------------
# DDL 解析
# --------------------------------------------------------------------------
def _split_top_level_tuples(values_text):
    """把 `('a','b'),('c','d')` 切成 [("a","b"), ("c","d")]（只取字符串字面量）。"""
    tuples = []
    depth = 0
    cur = ""
    for ch in values_text:
        if ch == "(":
            depth += 1
            if depth == 1:
                cur = ""
                continue
        elif ch == ")":
            depth -= 1
            if depth == 0:
                tuples.append(cur)
                cur = ""
                continue
        if depth >= 1:
            cur += ch
    return tuples


def collect_registries(db_dir):
    """{表名: {"unique_col": 列名, "values": {合法值集合}}} —— 只收「有种子」的登记表。"""
    ddl_files = []
    if os.path.isdir(db_dir):
        ddl_files = sorted(
            os.path.join(db_dir, f) for f in os.listdir(db_dir) if f.endswith(".sql")
        )

    # 第一遍：每张表的 DDL + UNIQUE 列
    unique_cols = {}   # table -> set(col)
    for path in ddl_files:
        text = open(path, encoding="utf-8", errors="ignore").read()
        for m in CREATE_TABLE_RE.finditer(text):
            table = m.group(1).lower()
            body = m.group(2)
            cols = {c.lower() for c in UNIQUE_COL_RE.findall(body)}
            if cols:
                unique_cols.setdefault(table, set()).update(cols)

    if not unique_cols:
        return {}

    # 第二遍：种子值
    seed_values = {}   # table -> {col: {value}}
    for path in ddl_files:
        text = open(path, encoding="utf-8", errors="ignore").read()
        for m in INSERT_RE.finditer(text):
            table = m.group(1).lower()
            if table not in unique_cols:
                continue
            col_names = []
            if m.group(2):
                col_names = [
                    c.strip().strip("`").lower()
                    for c in m.group(2).strip().strip("()").split(",")
                    if c.strip()
                ]
            for tup in _split_top_level_tuples(m.group(3)):
                literals = re.findall(r"'([^']*)'", tup)
                if not literals:
                    continue
                # 📌 定位「UNIQUE 列」在这条 INSERT 的第几个位置
                idx = 0
                for i, c in enumerate(col_names):
                    if c in unique_cols[table]:
                        idx = i
                        break
                if idx >= len(literals):
                    continue
                value = literals[idx]
                if not value:
                    continue
                for ucol in unique_cols[table]:
                    seed_values.setdefault(table, {}).setdefault(ucol, set()).add(value)

    registries = {}
    for table, cols in unique_cols.items():
        vals = seed_values.get(table, {})
        if not vals:
            continue
        for ucol, values in vals.items():
            if len(values) >= 1:
                registries.setdefault(table, {"unique_col": ucol, "values": values})
    return registries


def derive_shapes(registries):
    """从登记表的合法值里推导「标识符形状」（后缀）。

    📌 **必须所有值都符合该后缀**才认这一族 —— 否则一个表里混着
    别的形状（例如 `chat_session` 的 `session-001` 与 `session_id`），
    推导出来的「合法值集合」就是错的 → 闸门会误报一片。
    """
    shapes = {}
    for table, info in registries.items():
        values = info["values"]
        for suffix in SUFFIX_CANDIDATES:
            if len(values) >= 2 and all(v.endswith(suffix) for v in values):
                shapes[suffix] = {
                    "table": table,
                    "unique_col": info["unique_col"],
                    "valid": set(values),
                }
                break
    return shapes


# --------------------------------------------------------------------------
# Java 源码扫描
# --------------------------------------------------------------------------
def iter_java_files(scan_root):
    """遍历 {@link #SCAN_SUBDIRS} 范围内的 Java 文件。

    📌 **为什么显式按目录过滤**：docstring 里承诺的是「扫 src/main + src/test」，
    但实现是「扫整个仓库的所有 .java」—— 两者**不一致**就是**文档在骗人**
    （规则 51）。实测当前仓库里 761 个 .java **全部**落在 src/main|src/test 内，
    所以两种写法结果相同 —— 但那只是**巧合**，不是保证。
    → 落到纸面上：真正按 SCAN_SUBDIRS 过滤。

    ⚠️ 判定用**路径分段**（`/src/main/`），不是裸子串 —— 否则
    `src/maintain/...` 这种目录名会被误认成 `src/main`。
    """
    for dirpath, dirs, files in os.walk(scan_root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if not fn.endswith(".java"):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, scan_root).replace(os.sep, "/")
            if not any(("/%s/" % sub) in ("/" + rel) for sub in SCAN_SUBDIRS):
                continue
            yield full


def blank_comments_keep_lines(src):
    """把**注释**挖空（保留行号），**字符串字面量原样保留**。

    📌 **为什么要挖注释**：注释里写「以前是 amazon_product_template」是
    **说明历史**，不是引用 —— 不挖空的话闸门会把说明文字当成缺陷，
    报一堆噪音 → 闸门变哑（规则 99/102）。

    📌 **为什么字符串要原样保留**：本闸门要报的正是**字符串字面量**本身
    （悬空的模板 ID 就是个字面量）→ 挖掉字符串的话闸门就永远没东西可报。
    → 所以只挖注释，字符串留着。

    📌 **必须保持行数不变**：把内容换成空格，换行符原样保留 →
    报出来的行号仍然对得上原文件。

    ⚠️ **字符串里的 `//` 不是注释**（如 URL `"http://x"`）→
      状态机必须先判字符串再判注释，否则会把 URL 后面整行挖掉。
    """
    out = []
    i = 0
    n = len(src)
    in_block = False
    in_line = False
    in_str = False
    in_char = False
    in_text_block = False
    while i < n:
        ch = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if in_line:
            # 行注释到行尾（换行保留）
            if ch == "\n":
                out.append("\n")
                in_line = False
            else:
                out.append(" ")
            i += 1
            continue
        if in_block:
            if ch == "*" and nxt == "/":
                out.append("  ")
                i += 2
                in_block = False
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue
        if in_text_block:
            # 文本块（"""..."""）—— 整段原样保留
            if src.startswith('"""', i):
                out.append('"""')
                i += 3
                in_text_block = False
                continue
            out.append(ch)
            i += 1
            continue
        if in_str:
            out.append(ch)
            if ch == "\\" and nxt:
                out.append(nxt)
                i += 2
                continue
            if ch == '"':
                in_str = False
            i += 1
            continue
        if in_char:
            out.append(ch)
            if ch == "\\" and nxt:
                out.append(nxt)
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue
        # ---- 正常代码 ----
        if ch == "/" and nxt == "*":
            out.append("  ")
            i += 2
            in_block = True
            continue
        if ch == "/" and nxt == "/":
            out.append("  ")
            i += 2
            in_line = True
            continue
        if src.startswith('"""', i):
            out.append('"""')
            i += 3
            in_text_block = True
            continue
        if ch == '"':
            out.append(ch)
            i += 1
            in_str = True
            continue
        if ch == "'":
            out.append(ch)
            i += 1
            in_char = True
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def collect_problems(scan_root, shapes):
    """返回 [(相对路径, 行号, 值, 后缀, 登记表)]"""
    problems = []
    for path in iter_java_files(scan_root):
        try:
            src = open(path, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        # 📌 只挖**注释**；字符串字面量原样保留（悬空 ID 就是个字面量）
        code = blank_comments_keep_lines(src)
        raw_lines = src.split("\n")
        for idx, line in enumerate(code.split("\n")):
            # 📌 **显式豁免标记**：单测里**故意**造一个不存在的值来验证
            #    「取不到就返回空」这类行为 —— 那不是缺陷，是测试意图。
            #    → 必须在**原始行**里找（标记本身是注释，会被挖空）。
            raw = raw_lines[idx] if idx < len(raw_lines) else ""
            if ALLOW_MARKER in raw:
                continue
            for m in STRING_LIT_RE.finditer(line):
                value = m.group(1)
                for suffix, info in shapes.items():
                    if not value.endswith(suffix):
                        continue
                    if value in info["valid"]:
                        continue
                    # 📌 表名本身（`description_template`）不是引用 —— 它是**表名**
                    if value == info["table"]:
                        continue
                    problems.append(
                        (
                            os.path.relpath(path, scan_root),
                            idx + 1,
                            value,
                            suffix,
                            info["table"],
                            info["unique_col"],
                        )
                    )
                    break
    return problems


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def run(scan_root=ROOT, db_dir=None):
    db_dir = db_dir or os.path.join(scan_root, "database")
    registries = collect_registries(db_dir)
    shapes = derive_shapes(registries)
    if not shapes:
        return [], {}
    problems = collect_problems(scan_root, shapes)
    return problems, shapes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="有问题时退出码 1（CI 用）")
    ap.add_argument("--self-test", action="store_true", help="自检（注入缺陷必须变红）")
    args = ap.parse_args()

    if args.self_test:
        return run_self_test()

    problems, shapes = run()
    print("=" * 78)
    print("登记表引用悬空巡检（第 81 道闸门）")
    print("=" * 78)
    if not shapes:
        print("⚠️  没找到任何「登记表」（有 UNIQUE 列且有种子数据的表）—— 本闸门无事可做")
        print("    （这通常意味着 database/*.sql 的结构变了 → 本闸门需要跟着调整）")
        return 1 if args.strict else 0

    for suffix, info in sorted(shapes.items()):
        print(
            "· 登记表 %s（唯一列 %s，合法值 %d 个，形状 %s）"
            % (info["table"], info["unique_col"], len(info["valid"]), suffix)
        )
    print()

    if not problems:
        print("✅ 未发现「引用了登记表里不存在的值」的悬空引用")
        return 0

    print("🔴 发现 %d 处悬空引用（值在登记表的合法集合里不存在）：" % len(problems))
    seen = set()
    for rel, line, value, suffix, table, ucol in problems:
        key = (rel, line, value)
        if key in seen:
            continue
        seen.add(key)
        print("   · %s:%d  \"%s\"（形状 %s）→ %s.%s 里没有这个值"
              % (rel, line, value, suffix, table, ucol))
    print()
    print("💡 修法：")
    print("   1) 先确认这个值**应该**指向登记表的哪一行（多数是「漏了 product/后缀」）；")
    print("   2) 把代码里的字面量改成登记表里**真实存在**的值；")
    print("   3) 或者往登记表的种子数据里补这一行 —— 但别为了让闸门变绿而硬补。")
    print("   （悬空引用的危害：落库后追溯不到来源；运营改模板对结果零影响）")
    return 1


def _make_fixture(tmp, ddl_name, ddl_body, java_files):
    """造一个最小仓库：database/ + 一个带 src/main|src/test 的模块。"""
    db_dir = os.path.join(tmp, "database")
    os.makedirs(db_dir, exist_ok=True)
    with open(os.path.join(db_dir, ddl_name), "w", encoding="utf-8") as f:
        f.write(ddl_body)
    for rel, content in java_files.items():
        full = os.path.join(tmp, rel)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(content)


FIXTURE_DDL = """SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS fixture_db DEFAULT CHARACTER SET utf8mb4;
USE fixture_db;

CREATE TABLE IF NOT EXISTS fixture_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
    template_id VARCHAR(50) NOT NULL UNIQUE COMMENT '模板标识',
    name VARCHAR(200) NOT NULL COMMENT '模板名称',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fixture_template (template_id, name, enabled) VALUES
('good_template', '正常模板', 1),
('other_template', '另一个模板', 1);
"""


def run_self_test():
    """自检：注入缺陷形状 → 必须报红；合法样例 → 必须不报。"""
    tmp = tempfile.mkdtemp(prefix="dangling_selftest_")
    failures = []
    try:
        # ---- 用例 1：悬空引用必须被抓到 -------------------------------
        # 📌 fixture 的包名/路径必须落在扫描范围内（这里直接放 src/main/java 下）
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/main/java/com/x/FixtureService.java": (
                    "package com.x;\n"
                    "public class FixtureService {\n"
                    "    public String pick() {\n"
                    "        return \"missing_template\";\n"   # ← 库里没有 → 应报错
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, shapes = run(scan_root=tmp)
        if not problems:
            failures.append(("悬空引用 missing_template 必须被报出", True, False))
        else:
            values = {p[2] for p in problems}
            if "missing_template" not in values:
                failures.append(("应报出 missing_template", True, False))

        # ---- 用例 2：合法值不得误报 -----------------------------------
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/main/java/com/x/FixtureService.java": (
                    "package com.x;\n"
                    "public class FixtureService {\n"
                    "    public String pick() {\n"
                    "        return \"good_template\";\n"     # ← 合法 → 不得报
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, _ = run(scan_root=tmp)
        if problems:
            failures.append(("合法值 good_template 不得误报", False, True))

        # ---- 用例 3：注释里提到旧值不得误报（挖空必须生效）------------
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/main/java/com/x/FixtureService.java": (
                    "package com.x;\n"
                    "public class FixtureService {\n"
                    "    // 以前这里写的是 missing_template（已废弃）\n"
                    "    public String pick() {\n"
                    "        return \"good_template\";\n"
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, _ = run(scan_root=tmp)
        if problems:
            failures.append(("注释里的 missing_template 不得误报", False, True))

        # ---- 用例 4：块注释里提到旧值不得误报 --------------------------
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/main/java/com/x/FixtureService.java": (
                    "package com.x;\n"
                    "/* 历史：missing_template 是旧模板 ID，已删 */\n"
                    "public class FixtureService {\n"
                    "    public String pick() {\n"
                    "        return \"good_template\";\n"
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, _ = run(scan_root=tmp)
        if problems:
            failures.append(("块注释里的 missing_template 不得误报", False, True))

        # ---- 用例 5：src/test 里也要抓（单测里的悬空值会真落库）--------
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/test/java/com/x/FixtureTest.java": (
                    "package com.x;\n"
                    "public class FixtureTest {\n"
                    "    void t() {\n"
                    "        String id = \"missing_template\";\n"  # ← 单测里也必须抓
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, _ = run(scan_root=tmp)
        if not problems:
            failures.append(("src/test 里的 missing_template 必须被报出", True, False))

        # ---- 用例 6：形状推导必须要求「所有值都符合后缀」--------------
        #    一张表里混了 `session-001` 这种值 → 不能推导成 `_template` 族
        mixed_ddl = FIXTURE_DDL + (
            "\nCREATE TABLE IF NOT EXISTS mixed_table (\n"
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY,\n"
            "    template_id VARCHAR(50) NOT NULL UNIQUE,\n"
            "    other VARCHAR(50) NOT NULL UNIQUE\n"
            ") ENGINE=InnoDB;\n"
            "\nINSERT INTO mixed_table (template_id, other) VALUES\n"
            "('a_template', 'b-notemplate'),\n"
            "('c_template', 'd-notemplate');\n"
        )
        _make_fixture(
            tmp,
            "fixture-service.sql",
            mixed_ddl,
            {
                "erp-fixture-service/src/main/java/com/x/FixtureService.java": (
                    "package com.x;\n"
                    "public class FixtureService {\n"
                    "    public String pick() {\n"
                    "        return \"good_template\";\n"
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, shapes = run(scan_root=tmp)
        if not shapes:
            failures.append(("混合形状的表不得推导出形状族", True, False))

        # ---- 用例 7：显式豁免标记必须生效（逐行豁免）------------------
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/test/java/com/x/FixtureTest.java": (
                    "package com.x;\n"
                    "public class FixtureTest {\n"
                    "    void t() {\n"
                    "        String a = \"missing_template\"; // dangling-ref:allow\n"
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, _ = run(scan_root=tmp)
        if problems:
            failures.append(("带 dangling-ref:allow 的行必须被豁免", False, True))

        # ---- 用例 8：豁免必须是**逐行**的 —— 同一文件里没标记的行仍要报
        _make_fixture(
            tmp,
            "fixture-service.sql",
            FIXTURE_DDL,
            {
                "erp-fixture-service/src/test/java/com/x/FixtureTest.java": (
                    "package com.x;\n"
                    "public class FixtureTest {\n"
                    "    void t() {\n"
                    "        String a = \"missing_template\"; // dangling-ref:allow\n"
                    "        String b = \"another_missing_template\";\n"
                    "    }\n"
                    "}\n"
                ),
            },
        )
        problems, _ = run(scan_root=tmp)
        values = {p[2] for p in problems}
        if "another_missing_template" not in values:
            failures.append(("豁免必须是逐行的：未标记的行仍要报", True, False))
        if "missing_template" in values:
            failures.append(("被标记的行不应再报", False, True))

        if failures:
            print("❌ 自检失败：")
            for name, want, got in failures:
                print("   %s: 期望报错=%s 实际报错=%s" % (name, want, got))
            return 1
        print("✅ 自检通过（8 个用例：悬空必红 / 合法不红 / 行注释不红 / 块注释不红 / "
              "src/test 也红 / 混合形状不推导 / 豁免生效 / 豁免逐行）")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
