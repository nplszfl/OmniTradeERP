#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「安全文档的声明 ↔ 仓库现实」巡检：**docs/SECURITY.md 说的风险必须真的还在**。

📌 **为什么需要这道闸门**（规则 51 / 93：手写结论会静默过期；文档里的命令是「代码」，也会撒谎）：

    `docs/SECURITY.md` 第六节「还没修完的残余风险」是一份**风险登记表**：
    它明确写了「还有两条通往同一结果的路径没堵 —— 如实记在这里，不藏」。
    📌 **但这份登记表本身是手写的** → 有人把 `admin123` 修掉了、文档还写着
    「DEFAULT_PASSWORD = "admin123"」→ **文档开始骗人**。
    而这份文档的**唯一价值就是它诚实** —— 一旦过期，它就从「风险清单」变成「历史读物」，
    还会误导下一个人「这活儿还没干完」→ 又做一遍，或者反过来「文档写了有风险」→ 忽略真实的新风险。

📌 **判据方向很关键**：不是「文档没写的风险要报」（那是**不可能判**的），
    而是 **「文档声称存在的风险，必须真的还存在」** → 一旦现实变了，**文档必须跟着改**。
    📌 这跟 `check-seed-row-claims.py`（种子条数声明 ↔ 实际行数）是同一个思路。

📌 **只判「存在 / 不存在」，不判「有多少个」**（规则 137：等式是过拟合的伪恒等式）：
    文档里「数据库口令硬编码 28 处」「24 个服务无认证」这类**计数**故意**不查** ——
    计数口径一改就假红。✅ 只查**布尔型声明**（某常量还在 / 某字段还不在 / 某文件还含某口令）。

📌 **双向失明要一起堵**（规则 100）：
    - 方向 A：文档说风险还在，** reality 里已经没了** → 文档过期 → 判红
    - 方向 B：文档点名的那处，**闸门的豁免名单里对不上** → 文档和闸门**各说各话** → 判红
      （这正是 2026-11-12 `6f35de6` 之前的状态：闸门把豁免项 `continue` 掉，
        输出最后一行写着「✅ 没有硬编码凭证」，而仓库里确实躺着 `admin123` → **闸门输出与现实不一致**）

用法：
    python3 scripts/check-security-doc-claims.py           # 巡检（文档过期 → exit 1）
    python3 scripts/check-security-doc-claims.py --verbose  # 打印每条判据的细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

DOC = os.path.join(REPO_ROOT, "docs", "SECURITY.md")

# 📌 文档「风险 2」点名的常量：**文件路径 + 常量名 + 字面量**（三者都要对上）
R2_FILE = "erp-user-service/src/main/java/com/crossborder/erp/user/service/impl/UserServiceImpl.java"
R2_CONST = "DEFAULT_PASSWORD"
R2_VALUE = "admin123"

# 📌 文档「风险 1」点名的种子 SQL（明文口令写在注释里）
R1_SQL_FILES = [
    "database/seed-data.sql",
    "database/seed-tenant.sql",
    "database/user-service.sql",
]

# 📌 文档「风险 1」声称「全仓无 must_change_password 字段」
R1_ABSENT_TOKEN = "must_change_password"

# 📌 第 60 道闸门的「已知待整改」豁免名单
GATE60 = os.path.join(REPO_ROOT, "scripts", "check-hardcoded-secret.py")
GATE60_LIST = "KNOWN_PENDING_JAVA_PWD"


def read(path: str) -> str:
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""


def iter_code_files():
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith((".java", ".sql", ".xml", ".yml", ".yaml")):
                yield os.path.join(dirpath, fn)


def main() -> int:
    verbose = "--verbose" in sys.argv
    problems = []
    checks = []

    doc = read(DOC)
    if not doc:
        print(f"❌ 找不到 {DOC} —— 无法核验安全文档的声明")
        return 1

    # ── C1：风险 2 的兜底常量还在吗？ ────────────────────────────────
    r2_path = os.path.join(REPO_ROOT, R2_FILE)
    r2_text = read(r2_path)
    r2_alive = bool(r2_text) and re.search(
        r'private\s+static\s+final\s+String\s+' + R2_CONST + r'\s*=\s*"' + R2_VALUE + r'"',
        r2_text,
    )
    checks.append(("C1 风险2 兜底常量仍在", r2_alive))
    if not r2_alive:
        problems.append(
            f"文档「风险 2」说 `{R2_CONST} = \"{R2_VALUE}\"` 还在 `{R2_FILE}`，"
            f"但**仓库里已经找不到了** → 文档已过期 → "
            f"请更新 docs/SECURITY.md 第六节（该风险要么已修、要么挪位置），"
            f"并从第 60 道闸门的 `{GATE60_LIST}` 里删掉对应项"
        )

    # ── C2：风险 1 的种子 SQL 还含明文口令吗？ ──────────────────────
    missing_files = []
    no_plaintext = []
    for rel in R1_SQL_FILES:
        p = os.path.join(REPO_ROOT, rel)
        t = read(p)
        if not t:
            missing_files.append(rel)
        elif R2_VALUE not in t:
            no_plaintext.append(rel)
    c2_alive = not missing_files and not no_plaintext
    checks.append(("C2 风险1 种子SQL仍含明文口令", c2_alive))
    if missing_files:
        problems.append(
            f"文档「风险 1」点名了这些种子 SQL，但**文件不存在了**：{missing_files} → 文档已过期"
        )
    if no_plaintext:
        problems.append(
            f"文档「风险 1」说这些种子 SQL 里**明文口令**（`-- 密码 {R2_VALUE}（BCrypt）`）还在，"
            f"但**这些文件里已经搜不到 `{R2_VALUE}` 了**：{no_plaintext} → 文档已过期"
        )

    # ── C3：文档声称「全仓无 must_change_password 字段」 ──────────────
    found_field = []
    for p in iter_code_files():
        t = read(p)
        if R1_ABSENT_TOKEN in t:
            found_field.append(os.path.relpath(p, REPO_ROOT))
    c3_alive = not found_field
    checks.append(("C3 风险1 无强制改密字段", c3_alive))
    if found_field:
        problems.append(
            f"文档「风险 1」声称「全仓无 `{R1_ABSENT_TOKEN}` 字段」，"
            f"但**这些文件里已经有了**：{found_field[:5]} → 文档已过期（强制改密机制可能已经加了）"
        )

    # ── C4：文档 ↔ 第 60 道闸门的豁免名单，说的要是同一处 ─────────────
    gate = read(GATE60)
    list_ok = GATE60_LIST in gate
    in_list = bool(gate) and R2_FILE in gate and R2_CONST in gate
    c4_alive = list_ok and in_list
    checks.append(("C4 文档与闸门豁免名单一致", c4_alive))
    if not list_ok:
        problems.append(
            f"第 60 道闸门里找不到 `{GATE60_LIST}` → 豁免名单被改名/删掉了 → "
            f"文档「风险 2」说的「已登记进第 60 道闸门的已知待整改名单」已经对不上"
        )
    elif not in_list:
        problems.append(
            f"文档「风险 2」说 `{R2_CONST}` **已登记**在第 60 道闸门的 `{GATE60_LIST}` 里，"
            f"但闸门脚本里**对不上**（既没有 `{R2_FILE}`，也没有 `{R2_CONST}`）→ "
            f"**文档和闸门各说各话** → 谁也不信谁"
        )

    # ── 报告 ────────────────────────────────────────────────────────
    print("📋 巡检「安全文档的声明 ↔ 仓库现实」")
    print(f"ℹ️  文档：{os.path.relpath(DOC, REPO_ROOT)}")
    for name, ok in checks:
        print(f"   {'✅' if ok else '❌'} {name}")
    if verbose:
        for name, ok in checks:
            print(f"      {name} → {'成立' if ok else '已不成立（文档过期）'}")

    if problems:
        print(f"\n❌ 发现 {len(problems)} 处「文档与现实不一致」：")
        for p in problems:
            print(f"   • {p}")
        print("\n✅ 修法：**更新文档**（docs/SECURITY.md 第六节）—— 让它如实描述现在的风险；")
        print("   风险真被修掉了 → 从「残余风险」里划掉，并在闸门的豁免名单里同步删掉对应项。")
        return 1

    print("\n✅ 文档第六节声称的残余风险**都还真实存在**（文档没在骗人）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
