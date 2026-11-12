#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「环境变量模板里混进了真密钥」的闸门 —— 规则 161。

📌 **为什么需要这道闸门**（2026-11-13 02:30 实测踩出来的真盲区）：

`check-hardcoded-secret.py`（第 45 道）管的是**硬编码密钥**，但它的扫描范围是：

    src/main/resources/application*.yml   ← 只有这些
    + Java 源码里的 `*PASSWORD*` 常量

📌 **`.env.example` 不在它的范围内** —— 而 `.env.example` 恰恰是**最容易被塞进真密钥**的地方：

    1. 它是**给开发者复制用的模板** → 开发者拿到 `.env` 后，
       最自然的动作就是「把生成好的值粘回模板，省得下次再生成」
    2. 它**被 git 跟踪**（`.env` 本身被 `.gitignore` 挡着 → 模板是唯一的出口）
    3. 模板文件长得就像「配置文件」→ 开发者不会意识到自己粘的是**真密钥**

📌 **实测盲区**（2026-11-13 02:30）：

    把一个 48 位随机串写进 `.env.example` 的 `JWT_SECRET=`：
    → **65 道闸门全部 exit 0，零失败**
    → 因为没有任何一道闸门看 `.env.example` 的值

📌 **后果**：`JWT_SECRET` 一旦进仓库 → 拿到仓库的人就能**伪造管理员 token**
（规则 140/146 的同一类后果），而且 **git 历史里洗不掉**。

📌 **本闸门的判据（窄口径，避免「一上来全红」）**：

| 断言 | 内容 |
|---|---|
| **C1** | `.env.example` 等**被跟踪的环境变量模板**里，凭证类键的值**不得是字面量**（占位符/空值合法）|
| **C2** | 被跟踪的模板**不得包含 `.env` 本身**（真密钥文件必须只在 `.gitignore` 里）|

📌 **为什么不复用 `check-hardcoded-secret.py` 的扫描范围，而是另起一道**：
    规则 156：**「闸门的扫描范围就是它的保护范围」** —— 那道闸门扫不到 `.env.example`，
    就**不代表** `.env.example` 安全。✅ 覆盖面要么补上，要么**显式说明**。
    📌 本文件在末尾**显式声明了它不覆盖什么**（见「已知边界」）。

📌 **已知边界（诚实记录，不藏）**：
    - 本闸门只看**被 git 跟踪**的 `.env*` 模板 → `.env` 本体（未跟踪）不管
    - 只看**键名指向凭证**的行 → 一个叫 `FOO` 的键装了真密钥，本闸门认不出
    - **不检查 git 历史**（已经提交过的真密钥，本闸门管不了 → 那是轮换密钥的事，不是本闸门的事）
    - 📌 **占位符判定是「包含标记词」** → 值里**恰好含有** `example` / `placeholder` /
      `todo` 等字样的**真密钥会被放过**（如 `placeholderAKIAIOSFODNN7EXAMPLE`）。
      这是**故意**的宽容（与第 45 道同源）—— 宁可漏报也不误报（规则 99），
      但**知道这个边界**比不知道强。

用法：
    python3 scripts/check-env-template-secrets.py
退出码：0 = 通过；1 = 发现问题。
"""

import os
import re
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# ── 凭证键的**精确**名单（与第 45 道保持同源，避免两套名单打架）──────
#    📌 精确匹配而不是子串：子串会把 `max-tokens` / `token-expiration` 之类误判成密钥。
CREDENTIAL_KEYS = {
    "secret", "jwt-secret", "jwt_secret", "auth-secret", "signing-key",
    "private-key", "encryption-key", "credential", "credentials",
    "api-key", "api_key", "apikey",
    "app-secret", "app_secret",
    "client-secret", "client_secret",
    "access-key", "access_key",
    "access-token", "access_token",
    "refresh-token", "refresh_token",
    "token", "password", "passwd", "pwd",
}

# ── **带前缀**的凭证键（真实环境变量几乎都带前缀）────────────────────
#    📌 **实测踩过的坑（2026-11-13 02:40）**：只认精确名 →
#       `DB_PASSWORD=...` / `AUTH_TOKEN=...` **全部漏掉** → 真密钥照样进仓库。
#       （规则 100：「闸门只认一种写法」= 双向失明 —— 等价写法必须一起认。）
#    📌 为什么用「**后缀**匹配」而不是「任意子串」：
#       子串会把 `max-tokens` / `token-expiration` / `password-policy` 之类
#       **不是密钥**的键误判成密钥 → 假阳性 → 逼下一个人放松闸门（规则 99）。
#       ✅ 后缀匹配只命中「**以凭证词结尾**」的键 → 语义准确。
#    📌 **白名单例外**：`password-policy` / `token-expiration` / `secret-ttl`
#       这类**不是密钥**，显式排除（规则 100：等价写法要一起认，但反例也要一起认）。
CREDENTIAL_SUFFIXES = ("secret", "password", "passwd", "pwd", "token", "apikey", "api-key", "api_key")

NOT_A_CREDENTIAL_SUFFIX = (
    "password-policy", "password-policy-enabled", "password-min-length",
    "token-expiration", "token-expire", "token-ttl", "secret-ttl",
    "secret-enabled", "token-enabled", "password-reset-enabled",
)


def is_credential_key(key: str) -> bool:
    """键名是否指向凭证。

    📌 两条路：① 精确命中名单 ② **后缀**命中（CREDENTIAL_SUFFIXES）。
    📌 但**排除**已知不是密钥的后缀（`password-policy` 之类）→ 防假阳性。
    """
    k = key.lower().strip()
    if k in CREDENTIAL_KEYS:
        return True
    for bad in NOT_A_CREDENTIAL_SUFFIX:
        if k == bad or k.endswith("_" + bad) or k.endswith("-" + bad):
            return False
    for suf in CREDENTIAL_SUFFIXES:
        if k == suf or k.endswith("_" + suf) or k.endswith("-" + suf):
            return True
    return False

# ── 明显不是密钥的占位符 ────────────────────────────────────────────
PLACEHOLDER_MARKERS = (
    "PLACEHOLDER", "CHANGE_ME", "SET_BY", "TODO", "REPLACE",
    "example", "your-", "your_", "xxxx", "<",
)

# ── 「整串就是占位符」的短值 ─────────────────────────────────────────
#    📌 **实测踩过的坑（2026-11-13 02:45）**：`REDIS_PASSWORD=xxx` 被判成真密钥
#       → 假阳性。理由：标记表里只有 `"xxxx"`（4 个 x），而 `xxx`（3 个）不匹配。
#    📌 修法：值**整体**就是这类「显然不是密钥」的短串时也算占位符
#       （而不是把 `"xxx"` 加进标记表 → 那样 `xxxxxxxsecret` 这种也会被放过）。
#    📌 用**整串相等**而不是子串 → `xxx` 放行，但 `xxxabc123` 仍会被判红。
PLACEHOLDER_EXACT = {
    "xxx", "xxxx", "xxxxx", "xxxxxx", "tbd", "todo", "fixme", "none", "null",
    "changeme", "change_me", "change-me", "placeholder", "example", "sample",
    "default", "dummy", "fake", "test", "your-secret", "your_secret",
}

# ── 环境变量模板文件名 ──────────────────────────────────────────────
#    📌 只认**根目录**的 `.env*` → 模块目录下的同名文件不算（避免误报）
TEMPLATE_NAMES = {".env.example", ".env.sample", ".env.template", ".env.dist"}

# `KEY=value` / `export KEY=value`
ENV_LINE = re.compile(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$")


def is_placeholder(value: str) -> bool:
    v = value.strip()
    if len(v) >= 2 and v[0] == v[-1] and v[0] in "\"'":
        v = v[1:-1]
    if not v.strip():
        return True
    # 📌 整串就是占位符短串 → 放行（见 PLACEHOLDER_EXACT 的说明）
    if v.lower() in PLACEHOLDER_EXACT:
        return True
    up = v.upper()
    return any(m.upper() in up for m in PLACEHOLDER_MARKERS)


def tracked_files():
    """用 `git ls-files` 取被跟踪的文件（规则 33：真相来源 = 仓库本身，不是手抄清单）。"""
    try:
        out = subprocess.run(
            ["git", "-C", REPO_ROOT, "ls-files"],
            capture_output=True, text=True, timeout=60,
        ).stdout
    except Exception:
        return []
    return [x for x in out.split("\n") if x.strip()]


def main() -> int:
    findings = []
    tracked = tracked_files()
    if not tracked:
        print("ℹ️  取不到 git 跟踪文件列表 → 本闸门无法工作（不是「通过」）")
        return 1

    # ── C2：真密钥文件 `.env` 不能被跟踪 ───────────────────────────
    for rel in tracked:
        base = os.path.basename(rel)
        if base == ".env" or base.startswith(".env."):
            if base in TEMPLATE_NAMES:
                continue  # 模板是允许的（但值仍要过 C1）
            findings.append((rel, 1, base,
                             "真密钥文件被 git 跟踪了 → 必须进 .gitignore（规则 146）"))

    # ── C1：模板里凭证键的值不得是字面量 ───────────────────────────
    for rel in tracked:
        base = os.path.basename(rel)
        if base not in TEMPLATE_NAMES:
            continue
        path = os.path.join(REPO_ROOT, rel)
        if not os.path.isfile(path):
            continue
        try:
            with open(path, encoding="utf-8") as f:
                lines = f.read().split("\n")
        except Exception:
            continue
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith("#"):
                continue  # 注释里的示例不算
            m = ENV_LINE.match(line)
            if not m:
                continue
            key, val = m.group(1), m.group(2)
            if not is_credential_key(key):
                continue
            if is_placeholder(val):
                continue
            findings.append((rel, i, key,
                             "模板里的凭证键带了**非占位符的字面量** → "
                             "开发者把真密钥粘进模板就会直接进仓库（git 历史洗不掉）"))

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处环境变量模板的密钥风险：")
        for rel, lineno, key, msg in findings:
            print(f"   {rel}:{lineno} [{key}]")
            print(f"        {msg}")
        print("\n✅ 修法：")
        print("   ① 模板里的值**留空**（`JWT_SECRET=` —— 复制后自己填）")
        print("   ② 真密钥只放 `.env`（已被 .gitignore 挡住）→ **别粘回模板**")
        print("   ③ 已经提交过真密钥 → **必须轮换该密钥**（改历史不等于没泄露）")
        return 1

    print("\n✅ 环境变量模板里没有真密钥：凭证键的值全是空值或占位符")
    print("ℹ️  **已知边界**：只看**被跟踪**的根目录 `.env*` 模板 + **键名在凭证名单里**的行；")
    print("    不检查 git 历史、不看未跟踪的 `.env`、也不认「叫别的名字的键装了密钥」。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
