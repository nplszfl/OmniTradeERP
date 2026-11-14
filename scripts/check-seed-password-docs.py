#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
闸门：种子口令的「三方一致性」 + 「覆盖不漂移」。

📌 **为什么要有这道闸门（分工，不是重复）**

| 层 | 负责 | 怎么验 |
|---|---|---|
| `SeedPasswordHashConsistencyTest`（JUnit） | **真·crypto**：哈希验不通文档口令 → 红 | `BCryptPasswordEncoder.matches()`（与生产同一个实现） |
| 本闸门（Python） | **结构/一致性**：文档 ↔ 测试 ↔ 种子文件 三方口径是否一致 | 纯文本分析，不重复实现 bcrypt |

📌 **不做 crypto 是刻意的**：自己实现 bcrypt 会引入「实现有 bug 却报绿」的新风险
（纯 Python / 自研实现都踩过）。**crypto 只由生产同款实现做一次**（JUnit 测试），
本闸门只做它做不到的那几件事。

判据：

- **C1** 回归测试存在，且声明了 `CLAIMED_PASSWORD`（把「我们验过口令」这个**声明**
  与**依赖**绑在一起 —— 声明没依赖 = 谎报事实）。
- **C2** `docs/Installation.md` 与 `docs/SECURITY.md` 文档化的口令 == 测试断言的口令
  （三处口径漂移 = 文档说的能登、代码验的不是那个）。
- **C3** 所有被 git 跟踪的 `database/*.sql` **数据行**里的 bcrypt 哈希：
  结构合法（60 字符 = 22 salt + 31 hash）且**全仓只有一个**（混用旧 hash = 有人只改了一半）。
- **C4** 覆盖不漂移：任何带哈希的种子 SQL 都必须被回归测试覆盖
  （要么在测试的文件名单里，要么测试本身是**动态扫目录**的）。
  写死名单 = 新增文件根本不被扫 → 闸门全绿而账号照样登不进去。

📌 **只用 `git ls-files` 扫文件** —— 不用 `os.walk`：
工作区里的本地垃圾（例如误建的 `database/database/*` 空目录）不该影响判据，
而 `git ls-files` 恰好就是「**仓库里真实存在什么**」的唯一事实源。
"""

import os
import re
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO_ROOT, "scripts"))

TEST_FILE = "erp-user-service/src/test/java/com/crossborder/erp/user/service/impl/SeedPasswordHashConsistencyTest.java"
DOC_INSTALL = "docs/Installation.md"
DOC_SECURITY = "docs/SECURITY.md"

# bcrypt：版本段(2a/2b/2y) + 成本段(2位) + 22 salt + 31 hash = 60
BCRYPT_RE = re.compile(r"\$2[aby]\$(\d{2})\$([./A-Za-z0-9]{53})")
# 数据行里出现 bcrypt 哈希
HASH_IN_LINE_RE = re.compile(r"\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}")
# 注释行（行首）
COMMENT_LINE_RE = re.compile(r"^\s*(--|#|/\*|\*|//)")

VIOLATIONS = []
NOTES = []


def read(rel):
    p = os.path.join(REPO_ROOT, rel)
    if not os.path.exists(p):
        return None
    return open(p, encoding="utf-8", errors="replace").read()


def git_ls_files(pattern):
    try:
        out = subprocess.run(
            ["git", "-C", REPO_ROOT, "ls-files", pattern],
            capture_output=True, text=True, timeout=60,
        )
        return [l for l in out.stdout.splitlines() if l.strip()]
    except Exception as e:  # noqa: BLE001
        NOTES.append(f"git ls-files 失败（{e}）→ 退回全量扫 .sql")
        return []


def seed_sql_files():
    """被 git 跟踪的 database/*.sql（唯一事实源 = git，不看工作区）"""
    files = git_ls_files("database/*.sql")
    if files:
        return files
    out = []
    d = os.path.join(REPO_ROOT, "database")
    if os.path.isdir(d):
        out = ["database/" + f for f in sorted(os.listdir(d)) if f.endswith(".sql")]
    return out


def data_row_hashes(rel):
    """返回 [(行号, 哈希)] —— 只认**数据行**（跳过注释行）"""
    text = read(rel)
    if text is None:
        return []
    hits = []
    for i, line in enumerate(text.split("\n"), start=1):
        if COMMENT_LINE_RE.match(line):
            continue
        for m in HASH_IN_LINE_RE.finditer(line):
            hits.append((i, m.group(0)))
    return hits


def extract_test_password():
    """从回归测试里取出 CLAIMED_PASSWORD = "xxx" """
    text = read(TEST_FILE)
    if text is None:
        return None
    m = re.search(r'CLAIMED_PASSWORD\s*=\s*"([^"]+)"', text)
    return m.group(1) if m else None


def _looks_like_password(tok):
    """像不像一个口令？

    📌 必须排掉这些**已实测会误伤**的 token（否则闸门假报红 → 下一个人会去「放松闸门」）：
      - 路径/文件名：`init.sql`、`application.yml`、`health`、`libexec`
      - 代码标识符：`@RequestParam`
    判据：字母开头 + 只含口令常见字符 + **至少含一个数字**
    （本仓的种子口令是 `admin123`；纯字母 token 一律排除）。
    """
    if not tok or len(tok) < 4 or len(tok) > 32:
        return False
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_!#$%^&*-]{3,31}", tok):
        return False
    if "." in tok or "@" in tok:
        return False
    return any(c.isdigit() for c in tok)


def extract_doc_password_claims():
    """从文档里抽取**明确的种子口令声明**（宁可漏，不可误报）。

    只认这三种写法：
      1. 表格行里的 `账号/口令` 配对（如 `| 前端 | ... | admin/admin123 |`）
      2. 含「密码/口令」的行里的 **反引号** token（如 「密码都是 `admin123` 的 BCrypt 哈希」）
      3. 代码常量声明（`DEFAULT_PASSWORD = "admin123"`）

    📌 之所以要这么收窄：早期版本用宽松正则 → 把 `database/init.sql`、
    `application.yml`、`@RequestParam` 全当成「口令」→ 闸门假报红。
    """
    claims = {}
    for rel in (DOC_INSTALL, DOC_SECURITY):
        text = read(rel)
        if text is None:
            NOTES.append(f"{rel} 不存在 → 跳过")
            claims[rel] = set()
            continue
        found = set()
        for line in text.split("\n"):
            # 1) 表格行里的 账号/口令
            if line.lstrip().startswith("|"):
                for m in re.finditer(
                    r"([A-Za-z_][A-Za-z0-9_-]{1,20})\s*/\s*([A-Za-z0-9_!#$%^&*-]{4,32})", line
                ):
                    tok = m.group(2)
                    if _looks_like_password(tok):
                        found.add(tok)
            # 2) 含「密码/口令」的行里的反引号 token
            if re.search(r"密码|口令|password", line, re.IGNORECASE):
                for m in re.finditer(r"`([A-Za-z0-9_!#$%^&*-]{4,32})`", line):
                    tok = m.group(1)
                    if _looks_like_password(tok):
                        found.add(tok)
            # 3) 代码常量声明
            for m in re.finditer(
                r"(?:DEFAULT_PASSWORD|CLAIMED_PASSWORD|SEED_PASSWORD)\s*=\s*\"([^\"]+)\"", line
            ):
                tok = m.group(1)
                if _looks_like_password(tok):
                    found.add(tok)
        claims[rel] = found
    return claims


def main():
    print("=" * 70)
    print("🚪 闸门：种子口令三方一致性 + 覆盖不漂移")
    print("=" * 70)

    # ── C1：回归测试存在 + 声明了口令 ─────────────────────────────
    test_text = read(TEST_FILE)
    test_pw = extract_test_password()
    print("\n📋 C1 回归测试（声明必须绑定依赖）")
    if test_text is None:
        VIOLATIONS.append(
            f"回归测试不存在：{TEST_FILE}\n"
            f"   → 「种子口令验过了」这个声明**没有任何依赖支撑** = 谎报事实"
        )
        print("   ❌ 测试文件不存在")
    else:
        print(f"   ✅ 测试存在：{TEST_FILE}")
        if test_pw:
            print(f"   ✅ CLAIMED_PASSWORD = {test_pw!r}")
        else:
            VIOLATIONS.append(f"{TEST_FILE} 里找不到 CLAIMED_PASSWORD 声明 → 判据可能已失效")

    # ── C2：文档 ↔ 测试口令一致 ─────────────────────────────────
    print("\n📋 C2 文档 ↔ 测试 口令一致")
    doc_claims = extract_doc_password_claims()
    if test_pw:
        for rel, cands in doc_claims.items():
            if not cands:
                NOTES.append(f"{rel} 里没解析出明确的口令声明 → 跳过（宁漏不误报）")
                print(f"   ℹ️  {rel} 无明确口令声明 → 跳过")
                continue
            if cands == {test_pw}:
                print(f"   ✅ {rel} 只声明了 {test_pw!r}")
            else:
                extra = sorted(cands - {test_pw})
                missing = test_pw not in cands
                VIOLATIONS.append(
                    f"{rel} 的种子口令声明与测试断言不一致："
                    f"测试 = {test_pw!r}，文档声明 = {sorted(cands)}"
                    + (f"（文档**没有** {test_pw!r}）" if missing else f"（多出 {extra}）")
                    + "\n   → 文档说的和代码验的不是同一个口令（= 有人登不进去）"
                )
                print(f"   ❌ {rel} 声明 {sorted(cands)}，测试断言 {test_pw!r}")

    # ── C3：所有数据行哈希结构合法 + 全仓只有一种 ─────────────────
    print("\n📋 C3 种子哈希：结构合法 + 全仓只有一种")
    files = seed_sql_files()
    all_hashes = {}
    hash_files = {}
    for rel in files:
        hits = data_row_hashes(rel)
        if not hits:
            continue
        hash_files[rel] = hits
        for ln, h in hits:
            all_hashes.setdefault(h, []).append(f"{rel}:{ln}")
    if not hash_files:
        VIOLATIONS.append("扫不到任何**数据行**里的 bcrypt 哈希 → 判据可能根本没在读文件")
        print("   ❌ 一个哈希都没扫到")
    else:
        for rel, hits in sorted(hash_files.items()):
            print(f"   • {rel:38} {len(hits):>2} 行")
        distinct = list(all_hashes)
        if len(distinct) == 1:
            print(f"   ✅ 全仓只有 1 种哈希（{len(all_hashes[distinct[0]])} 处）")
        else:
            for h, where in sorted(all_hashes.items()):
                print(f"   ❌ 哈希 {h[:22]}... 出现在 {len(where)} 处：{where[:4]}")
            VIOLATIONS.append(
                f"种子数据行里有 **{len(distinct)} 种**不同的哈希 —— 有人只改了一半\n"
                f"   → 用其中一种的账号登不进去（接口正常返回「密码错误」，无异常、无日志）"
            )
        # 结构合法性（盐 22 + 摘要 31，base64 字符集）
        for h, where in all_hashes.items():
            m = BCRYPT_RE.fullmatch(h)
            if not m:
                VIOLATIONS.append(f"哈希结构非法（长度/字符集不对）：{h} @ {where[:3]}")
            elif not (4 <= int(m.group(1)) <= 16):
                VIOLATIONS.append(f"bcrypt 成本段异常（cost={m.group(1)}）：{h} @ {where[:3]}")

    # ── C4：覆盖不漂移 ───────────────────────────────────────────
    print("\n📋 C4 覆盖不漂移（每个带哈希的种子文件都要被回归测试扫到）")
    if test_text is None:
        NOTES.append("测试不存在 → 跳过 C4")
    else:
        # 📌 **只认 SEED_FILES 数组里的文件**，不认散落在别处的字符串字面量
        #    （否则测试里任何一个举例用的文件名都会把一个真·未覆盖的文件「伪装成已覆盖」）
        m = re.search(r"SEED_FILES\s*=\s*\{(.*?)\}", test_text, re.S)
        listed = set(re.findall(r'"(database/[^"]+\.sql)"', m.group(1))) if m else set()
        print(f"   • 测试 SEED_FILES 声明：{sorted(listed) if listed else '（无）'}")
        uncovered = [f for f in sorted(hash_files) if f not in listed]
        if uncovered:
            VIOLATIONS.append(
                f"这些带哈希的种子文件**不在**回归测试的 SEED_FILES 里：{uncovered}\n"
                f"   → 它们不会被当成本仓的种子口径来验 → 闸门全绿，而那些账号照样登不进去\n"
                f"   → 修法：把它加进 SeedPasswordHashConsistencyTest.SEED_FILES"
            )
            print(f"   ❌ 未覆盖：{uncovered}")
        else:
            print("   ✅ 全部带哈希的种子文件都在 SEED_FILES 里")
        # 📌 **诚实声明**：本闸门只能验「名单」，**验不了**测试运行时的实际扫描行为
        #    （那需要真跑 JUnit）。测试里的动态扫目录是**运行时**的第二道保险。
        NOTES.append(
            "C4 只验「SEED_FILES 名单」；测试运行时的实际扫描行为由 JUnit 测试自己保证"
            "（它会 assert「扫到的文件数 == database/ 下的 .sql 数」）"
        )

    # ── 汇总 ────────────────────────────────────────────────────
    print("\n" + "=" * 70)
    for n in NOTES:
        print(f"ℹ️  {n}")
    if VIOLATIONS:
        print(f"\n❌ 发现 {len(VIOLATIONS)} 处口令一致性问题：\n")
        for v in VIOLATIONS:
            print(f"   ❌ {v}")
        print()
        return 1
    print("✅ 种子口令三方一致 + 覆盖不漂移")
    print("=" * 70)
    return 0


if __name__ == "__main__":
    sys.exit(main())
