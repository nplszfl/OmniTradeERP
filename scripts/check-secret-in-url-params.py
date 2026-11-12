#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「敏感参数不许走 URL 查询串」的闸门 —— 规则 138 同族 / 风险 6 的回归保护。

📌 **为什么需要这道闸门**（本项目真实踩过的坑，2026-11-12）：

    `POST /user/{id}/reset-password` 的口令参数原来用 `@RequestParam`
    → 调用方可以写 `POST /api/user/1/reset-password?defaultPassword=Secret123`
    → nginx / ingress / 网关的访问日志格式里有 `$request` = **完整请求行（含 query string）**
    → **明文口令进访问日志**（也可能进浏览器历史、代理日志、审计日志）。

    `e3782aa` 把它改成了 `@RequestBody(required = false) ResetPasswordRequest`
    （body 不进访问日志），`docs/SECURITY.md` 的「残余风险 6」也划掉了 ✅。

    **但当时没有任何闸门管这件事** —— 实测把修复回退成 `@RequestParam`，
    **61 个闸门全部照样绿** 😱
    → **文档声称「已修」= 一句没人守的话**（规则 51 / 126 同族：
       自称 AI 却 0 次 LLM 调用 = 谎报事实；自称「已修」却能一键回退 = 同款谎报）。

📌 **本闸门的判据**：

| 断言 | 内容 |
|---|---|
| **C1（后端）** | Controller 方法里**名字像敏感值**的参数（password / pwd / secret / token / apiKey / privateKey / credential…）**不许用 `@RequestParam`** —— 因为 `@RequestParam` 允许从 **query string** 绑定 → 会进访问日志。必须用 `@RequestBody`（DTO）。 |
| **C2（前端）** | 前端**不许把敏感值拼进 URL 查询串**（`?password=...`、`?token=...`）—— 同一类泄露，只是发生在浏览器/代理侧。 |

📌 **为什么不按「所有 @RequestParam」判**（规则 42 / 55 / 96）：
  `page` / `size` / `keyword` / `status` 这类 `@RequestParam` **完全正常** →
  全量判 = **大量误报** → 闸门会被「放松」→ 假绿。
  ✅ 只判**名字命中敏感词**的参数。

📌 **反向验证**（规则 40 / 75 / 123）：
  ① 把 `resetPassword` 改回 `@RequestParam` → 本闸门必须 exit 1 并指名文件与方法；
  ② 加一个**普通** `@RequestParam String keyword` → 必须**不报**（对照组，证明不是见参数就红）。

用法：
    python3 scripts/check-secret-in-url-params.py
    python3 scripts/check-secret-in-url-params.py --verbose
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", "coverage"}

# ── 敏感参数名：命中即视为「不该走 query string」 ────────────────────
# 📌 **用「包含」而不是「词边界」** ——
#    词边界（`\bpassword\b`）会漏掉本项目里真实存在的名字：
#    `defaultPassword` / `oldPassword` / `newPassword` → **全是拼接在后面的**
#    → 漏掉它们 = 闸门对自己的目标形态失明（规则 100 / 127）。
# 📌 **用「包含」就要配一个「非敏感限定词」排除表** ——
#    否则 `passwordPolicy`（策略字段，不是密钥）这类会被误报（规则 42 / 55）。
SENSITIVE_NAME_RE = re.compile(
    r"(?:^|[^a-z0-9])(?:"
    r"password|passwd|pwd|"
    r"secret|"
    r"token|"
    r"apikey|api_key|accesskey|access_key|secretkey|secret_key|privatekey|private_key|"
    r"credential|credentials|"
    r"authcode|auth_code|"
    r"signature|sign"
    r")(?:[^a-z0-9]|$)",
    re.IGNORECASE,
)

# ── 敏感参数名：命中即视为「不该走 query string」 ────────────────────
# 📌 **用「包含」而不是「词边界」** ——
#    词边界（`\bpassword\b`）会漏掉本项目里真实存在的名字：
#    `defaultPassword` / `oldPassword` / `newPassword` → **全是拼接在后面的**
#    → 漏掉它们 = 闸门对自己的目标形态失明（规则 100 / 127）。
# 📌 **用「包含」就要配一个「非敏感限定词」排除表** ——
#    否则 `passwordPolicy`（策略字段，不是密钥）这类会被误报（规则 42 / 55）。
SENSITIVE_WORDS = (
    "password", "passwd", "pwd",
    "secret", "token",
    "apikey", "api_key", "accesskey", "access_key",
    "secretkey", "secret_key", "privatekey", "private_key",
    "credential", "credentials",
    "authcode", "auth_code",
    "signature",
)
# 这些词出现在名字里 → 说明它是**策略/元信息**而不是**密钥本身**
SAFE_QUALIFIERS = (
    "policy", "rule", "expire", "ttl", "length", "strength", "complexity",
    "enabled", "required", "format", "regex", "validator", "error",
    "message", "label", "placeholder", "hint", "type", "list", "count",
)

# ── C1：后端 ────────────────────────────────────────────────────────
# 📌 **方法签名与参数表必须按花括号/圆括号配平来切** ——
#    朴素的 `[^)]*` 会在 `@RequestParam(required = false)` 的那个 `)` 处截断
#    → 参数表被切掉一半 → **判据对自己的目标形态失明**（规则 100 / 127）
METHOD_SIG_RE = re.compile(
    r"(?:public|protected|private)\s+[\w<>,\[\]\.\s]+?\s+(?P<name>[A-Za-z_]\w*)\s*\("
)


def _balanced(text: str, open_idx: int, left: str = "(", right: str = ")") -> tuple[str, int]:
    """从 open_idx（指向 left）开始，返回 (括号内文本, right 之后的位置)。"""
    depth = 0
    i = open_idx
    while i < len(text):
        if text[i] == left:
            depth += 1
        elif text[i] == right:
            depth -= 1
            if depth == 0:
                return text[open_idx + 1:i], i + 1
        i += 1
    return text[open_idx + 1:], len(text)


# 参数表里，形如：@RequestParam(required = false) String defaultPassword
# 📌 参数名 = 注解之后、下一个逗号/右括号之前的**最后一个标识符**
PARAM_TAIL_RE = re.compile(r"^(?P<decl>[^,)]*?)(?P<name>[A-Za-z_]\w*)\s*$", re.DOTALL)


def is_sensitive(name: str) -> bool:
    """参数名是否含敏感值。**包含语义** + 非敏感限定词排除。"""
    low = name.lower()
    if any(q in low for q in SAFE_QUALIFIERS):
        return False
    return any(w in low for w in SENSITIVE_WORDS)


# ── C2：前端 ────────────────────────────────────────────────────────
# 形如 request.get('/api/x?token=abc') / url: `...?password=${p}`
FRONTEND_QUERY_RE = re.compile(
    r"(?P<url>['\"`][^'\"`\n]{0,200}\?[^\n]{0,200}['\"`])"
)
QUERY_KEY_RE = re.compile(r"[?&]([A-Za-z_]\w*)\s*=")


def strip_comments(text: str) -> str:
    """**去掉注释**再扫。

    📌 **实测踩过**：闸门把源码里的**说明注释**
    （`//    ?defaultPassword=xxx 会进 nginx 访问日志`）当成真代码 → **假红**。
    → 闸门必须扫**代码**，不能扫注释（规则 45 / 127）。

    📌 `//` 的启发式：**前面是 `:` 不算注释** ——
    否则 `https://example.com` 会被从中间截断（URL 里全是 `//`）。
    """
    # 块注释
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.DOTALL)
    # 行注释（`://` 里的 `//` 不算）
    out = []
    for line in text.split("\n"):
        idx = None
        i = 0
        while i < len(line) - 1:
            if line[i] == "/" and line[i + 1] == "/":
                if i > 0 and line[i - 1] == ":":
                    i += 2
                    continue
                idx = i
                break
            i += 1
        out.append(line if idx is None else line[:idx])
    return "\n".join(out)


def iter_files(subdir: str, suffix: str):
    for dirpath, dirnames, filenames in os.walk(os.path.join(REPO_ROOT, subdir)):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(suffix):
                yield os.path.join(dirpath, fn)


def check_backend(verbose: bool) -> list[tuple[str, int, str]]:
    findings = []
    for path in iter_files(".", ".java"):
        rel = os.path.relpath(path, REPO_ROOT)
        if "/src/main/java" not in rel.replace(os.sep, "/"):
            continue
        try:
            text = strip_comments(open(path, encoding="utf-8").read())
        except Exception:
            continue
        for m in METHOD_SIG_RE.finditer(text):
            params, _ = _balanced(text, m.end() - 1)
            if "@RequestParam" not in params:
                continue
            # 在参数表里逐个找 @RequestParam（注解自身可能带圆括号 → 也要配平）
            for rp in re.finditer(r"@RequestParam\b", params):
                _, after_idx = _balanced(params, rp.end() - 1)
                # 参数声明 = 从注解之后到下一个顶层逗号/右括号
                tail = params[after_idx:]
                stop = len(tail)
                depth = 0
                for k, ch in enumerate(tail):
                    if ch in "(<[":
                        depth += 1
                    elif ch in ")>]":
                        depth -= 1
                    elif ch == "," and depth <= 0:
                        stop = k
                        break
                decl = tail[:stop].strip()
                pm = PARAM_TAIL_RE.match(decl)
                if not pm:
                    continue
                pname = pm.group("name")
                if not is_sensitive(pname):
                    continue
                line = text.count("\n", 0, m.start()) + 1
                findings.append((
                    rel, line,
                    f"方法 `{m.group('name')}(...)` 的敏感参数 `{pname}` 用了 `@RequestParam`"
                    f" → **可以走 URL 查询串**（`?{pname}=明文`）→ "
                    f"**明文口令进 nginx / ingress / 网关访问日志**（$request = 完整请求行）"
                ))
    return findings


def check_frontend(verbose: bool) -> list[tuple[str, int, str]]:
    findings = []
    for path in iter_files("erp-web/src", ".ts"):
        rel = os.path.relpath(path, REPO_ROOT)
        try:
            text = strip_comments(open(path, encoding="utf-8").read())
        except Exception:
            continue
        for m in FRONTEND_QUERY_RE.finditer(text):
            url = m.group("url")
            for km in QUERY_KEY_RE.finditer(url):
                key = km.group(1)
                if not is_sensitive(key):
                    continue
                line = text.count("\n", 0, m.start()) + 1
                findings.append((
                    rel, line,
                    f"前端把敏感值 `{key}` 拼进了 URL 查询串 → **明文进浏览器历史 / 代理日志 / Referer**"
                    f"  → {url[:120]}"
                ))
    return findings


def main() -> int:
    verbose = "--verbose" in sys.argv
    print("📋 巡检「敏感参数不许走 URL 查询串」")
    print("ℹ️  理由：query string 会进 nginx / ingress / 网关访问日志（$request = 完整请求行）")

    backend = check_backend(verbose)
    frontend = check_frontend(verbose)

    print(f"\nℹ️  C1 后端：敏感参数用 @RequestParam → {len(backend)} 处")
    print(f"ℹ️  C2 前端：敏感值拼进 URL 查询串 → {len(frontend)} 处")

    findings = backend + frontend
    if verbose:
        for rel, line, msg in findings:
            print(f"   {rel}:{line}\n        {msg}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「敏感值可能走 URL 查询串」：")
        for rel, line, msg in findings:
            print(f"   {rel}:{line}")
            print(f"        {msg}")
        print("\n✅ 修法：")
        print("   ① 后端 → 改用 `@RequestBody` + DTO（POST body **不进访问日志**）")
        print("      例：`@RequestBody(required = false) ResetPasswordRequest request`")
        print("   ② 前端 → 把敏感值放进请求体，不要拼进 URL")
        print("   📌 **普通**参数（`page` / `size` / `keyword`）用 `@RequestParam` 是正常的 → 本闸门不判")
        return 1

    print("\n✅ 没有敏感参数走 URL 查询串（口令/密钥不会进访问日志）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
