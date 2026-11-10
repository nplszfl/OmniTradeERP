#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""内部细节**不得泄露给前端**的闸门 —— 规则 14 的守门人。

📌 **为什么需要这道闸门**：

规则 14「**SQL 栈不得泄露给前端**」在本项目**真实发作过**：
漏传 `totalAmount` 的下单请求，前端收到了整段 SQL：
`Field 'total_amount' doesn't have a default value ... SQL: INSERT INTO t_order_item ...`
→ **数据库结构（表名/列名）直接暴露**。

修法是在 `GlobalExceptionHandler` 里把数据访问类异常列入**黑名单**：
`DataAccessException` / `DataIntegrityViolationException` / `SQLException` → 只回「系统繁忙」。

⚠️ **但这条防线有两个可以绕过的口子，且都没有闸门盯着**：

| 口子 | 说明 |
|---|---|
| **A** | 有人「顺手」把 `DataAccessException` 挪进白名单 → SQL 又开始泄露 |
| **B** | **包装绕过**：`try { repository.xxx() } catch (Exception e) { throw new RuntimeException("…: " + e.getMessage()); }` → 抛出去的是**普通 RuntimeException**（不在黑名单里）→ 黑名单**完全失效**，SQL 消息直达前端 |

📌 **判据（窄）**：

| 断言 | 内容 |
|---|---|
| **A** | `GlobalExceptionHandler` 的「可原样返回 message」判定里，必须**排除** `DataAccessException` / `DataIntegrityViolationException` / `SQLException` |
| **B** | 不得出现「**try 里含数据访问调用** + **catch 里把 `e.getMessage()` 塞进抛出的 RuntimeException**」的写法（= 包装绕过黑名单） |

📌 **为什么 B 只查「数据访问调用」，不查 HTTP 调用**：
规则 14 的对象是 **SQL / 数据库结构**；
而平台 API 失败时把上游消息透出是**合理的**（规则 13：「只有 NPE/ClassCast 才回『系统繁忙』」）→
把 HTTP 也扫进来 = 判据过宽 → **假阳性** → 逼人放松闸门（规则 99）。

📌 **反向验证**（规则 40 / 75，注入真实的原始形态）：
① 把 `DataAccessException` 从黑名单里删掉 → A 报红；
② 给一个 service 加上「try 里查库 + catch 里塞 `e.getMessage()`」→ B 报红。

用法：
    python3 scripts/check-internal-detail-leak.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-internal-detail-leak.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", ".idea"}

# 必须被排除在「可原样返回 message」之外的类型（规则 14）
MUST_EXCLUDE = ("DataAccessException", "DataIntegrityViolationException", "SQLException")

# 数据访问调用的识别（只看这三类，够用且不会误伤）
DB_CALL_RE = re.compile(r"\b(?:repository|mapper|dao)\.\w+\s*\(")
CATCH_RE = re.compile(
    r"catch\s*\(\s*(?:[\w.]+\s*\|\s*)?(?:Exception|Throwable)\s+(\w+)\s*\)\s*\{"
)
RETHROW_MSG_RE = re.compile(
    r"throw\s+new\s+(?:[\w.]*)?RuntimeException\s*\([^)]*\.getMessage\(\)"
)


def java_files():
    out = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/java" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def block_end(text, start_brace):
    """从 '{' 开始找配平的 '}'，返回结束下标（不含）。找不到返回 None。"""
    depth = 0
    for i in range(start_brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return None


def find_handler():
    for p in java_files():
        if os.path.basename(p) == "GlobalExceptionHandler.java":
            return p
    return None


def check_blacklist(handler, findings):
    """A：黑名单必须排除数据访问类异常。"""
    text = open(handler, encoding="utf-8").read()
    # 找「判定是否可原样返回 message」的方法体
    m = re.search(r"boolean\s+\w*Business\w*\s*\([^)]*\)\s*\{", text) or \
        re.search(r"boolean\s+\w+\s*\([^)]*Exception[^)]*\)\s*\{", text)
    if not m:
        findings.append(
            "[A] `GlobalExceptionHandler` 里找不到「是否可原样返回 message」的判定方法 → "
            "结构变了？本闸门会变成瞎的"
        )
        return
    start = text.find("{", m.end() - 1)
    end = block_end(text, start)
    if end is None:
        return
    body = text[start:end]
    for cls in MUST_EXCLUDE:
        if cls not in body:
            findings.append(
                f"[A] `GlobalExceptionHandler` 的黑名单里**没有** `{cls}` → "
                f"它的 message 含完整 SQL / 表名 / 列名 → **数据库结构泄露给前端**（规则 14）"
            )


def check_wrapper_bypass(files, findings):
    """B：try 里查库 + catch 把 e.getMessage() 塞进 RuntimeException = 绕过黑名单。"""
    for path in files:
        text = open(path, encoding="utf-8").read()
        for cm in CATCH_RE.finditer(text):
            var = cm.group(1)
            cstart = text.find("{", cm.end() - 1)
            if cstart < 0:
                continue
            cend = block_end(text, cstart)
            if cend is None:
                continue
            cbody = text[cstart:cend]
            rm = RETHROW_MSG_RE.search(cbody)
            if not rm or (var + ".getMessage()") not in rm.group(0):
                continue
            # 往回找与该 catch 配对的 try 块
            tstart = text.rfind("try", 0, cm.start())
            if tstart < 0:
                continue
            tbrace = text.find("{", tstart)
            if tbrace < 0 or tbrace > cm.start():
                continue
            tend = block_end(text, tbrace)
            if tend is None:
                continue
            tryseg = text[tbrace:tend]
            if not DB_CALL_RE.search(tryseg):
                continue
            line = text[:cm.start()].count("\n") + 1
            rel = os.path.relpath(path, REPO_ROOT)
            findings.append(
                f"[B] `{rel}:{line}` —— try 里有数据访问调用，catch 却把 `{var}.getMessage()` "
                f"塞进抛出的 RuntimeException → 抛出去的是**普通 RuntimeException**（不在黑名单里）"
                f"→ **黑名单完全失效** → SQL 消息直达前端（规则 14 的包装绕过形态）"
            )


def main() -> int:
    verbose = "--verbose" in sys.argv
    files = java_files()
    handler = find_handler()
    if handler is None:
        print("❌ 找不到 `GlobalExceptionHandler.java` —— 异常处理结构变了？本闸门会变成瞎的")
        return 1

    findings = []
    check_blacklist(handler, findings)
    check_wrapper_bypass(files, findings)

    if findings:
        print("❌ 内部细节泄露闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 规则 14：SQL 栈 / 数据库结构**不得泄露给前端**。\n"
            "   真实发作过：漏传字段的下单请求，前端收到了整段 `INSERT INTO t_order_item ...`。\n"
            "   黑名单必须排除 `DataAccessException` / `DataIntegrityViolationException` / `SQLException`，\n"
            "   且**不得用 `RuntimeException` 包装原始 message 来绕过它**。"
        )
        return 1

    if verbose:
        print("检查了 %d 个 main 源文件" % len(files))
    print("✅ 内部细节泄露：%d 个源文件均无「SQL/表名/列名直达前端」的路径（规则 14）" % len(files))
    return 0


if __name__ == "__main__":
    sys.exit(main())
