#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**业务错误的 HTTP 状态码**闸门 —— 规则 28 / 44 / 107。

📌 **为什么需要这道闸门**（2026-11-11 08:15 审计实证，**至今没有闸门守着**）：

| 抛法 | → HTTP | 语义 | 对不对 |
|---|---|---|---|
| `throw new BizException("订单不存在")` | **400** | 业务错误 = 客户端错误 | ✅ |
| `throw new RuntimeException("订单不存在")` | **500** | **同一个概念** | ❌ |

`BizException` 的 docstring 写得很清楚：

> ⚠️ 默认错误码是 **400** 而不是 500：业务异常表达的是「请求本身不合法」
> （库存不足、仓库不存在、密码错误……），属于客户端错误。
> 若默认给 500，监控里全是「服务端故障」告警，实际上服务本身没有任何问题。

而 `GlobalExceptionHandler.isBusinessRuntimeException()` 又**专门识别**了
「带业务提示的 RuntimeException」并把消息原样透出 —— **却仍返回 500**。

**实测规模（11-11 08:15）**：
- `new RuntimeException("...不存在...")` 共 **68 处**
  （social-commerce 50 + tenant-service 18）
- 消息**零歧义**：达人不存在 / 租户不存在 / 直播不存在 / 合作不存在 / 商品不存在 /
  订单不存在 / 店铺配置不存在 / 用户不存在 / 凭证不存在 / 账单不存在 / 功能开关不存在
- **同一模块两种口径**（规则 44 / 107）：`LiveStreamMonitorService` 里 3 处是 `BizException`，
  另外 47 处是 `RuntimeException` → **只修一处 = 白修**

📌 **危害**：
① 监控里全是假的「服务端故障」→ **告警疲劳**（真故障被淹没，规则 43）
② 客户端/网关可能对 5xx **重试** → 重试一个本该 4xx 的请求 = 纯浪费
③ API 语义错：**用户请求了不存在的资源** 是客户端错误

---

📌 **判据（窄，规则 99）**

对 **main 源码**（不含测试）里的每一处
`new RuntimeException("…不存在…")` → 报红。

**为什么用「不存在」这个语义词**：
- 本仓库实测**完美分离**：「不存在」68 处**全是**业务错误；
  上游/基础设施故障（`导出Excel失败` / `LLM API调用失败` / `HTTP请求失败` /
  `签名失败` / `获取XX失败`）**一个都不含**「不存在」→ **零假阳性**
- 「已存在」「不能为空」「不合法」当前 **0 处** → **不纳入**（避免为不存在的模式放宽判据）

📌 **必须先剥注释**（规则 114 的教训 —— 同一个坑）：
`GlobalExceptionHandler` 的 **Javadoc 里就写着** `throw new RuntimeException("账户不存在")`
（作为「业务异常白名单」的说明）→ **不剥注释就会把它当成违规** → 假红。

📌 **反向验证**（规则 40 / 75 / 106）：
① 把某处 `BizException` 改回 `RuntimeException` → 报红；
② 把上游故障的消息也带上「不存在」（如「LLM 服务不存在」）→ **仍报红**（说明只认语义词）；
③ 把「不存在」改成「失败」→ **不报**（判据确实窄）；
④ 注释/Javadoc 里写 `new RuntimeException("XX不存在")` → **不报**（已剥注释）。

用法：
    python3 scripts/check-business-error-status.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-business-error-status.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── 业务语义词：「查不到资源」→ 客户端错误 ──
BUSINESS_NOT_FOUND = re.compile(r"不存在")

# ── 只认 RuntimeException（BizException 是正确的写法）──
NEW_RUNTIME_EXC = re.compile(r'new\s+RuntimeException\s*\(\s*"([^"]*)"')


def strip_comments(src: str) -> str:
    """剥掉块注释（含 Javadoc）与行注释，**保留行数**（便于报行号）。

    📌 必须剥：`GlobalExceptionHandler` 的 Javadoc 里就写着
    `throw new RuntimeException("账户不存在")`（作为白名单说明）→ 不剥就会假红。
    """
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            # 字符串字面量：原样保留（含转义）
            q = c
            out.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append(src[i:i + 2])
                    i += 2
                    continue
                out.append(src[i])
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if src.startswith("//", i):
            while i < n and src[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if src.startswith("/*", i):
            while i < n and not src.startswith("*/", i):
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            out.append("  ")
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def find_java_files(root: str) -> list[str]:
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in {".git", "target", "node_modules", ".idea"}]
        for fn in filenames:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []
    scanned = 0

    for path in find_java_files(REPO_ROOT):
        rel = os.path.relpath(path, REPO_ROOT)
        # 📌 **只扫 main 源码**（测试里的 mock 仓库用 RuntimeException 是另一回事）
        if "/src/test/" in rel.replace(os.sep, "/"):
            continue
        src = open(path, encoding="utf-8", errors="ignore").read()
        cleaned = strip_comments(src)
        scanned += 1
        for m in NEW_RUNTIME_EXC.finditer(cleaned):
            msg = m.group(1)
            if not BUSINESS_NOT_FOUND.search(msg):
                continue
            line = cleaned[:m.start()].count("\n") + 1
            findings.append({
                "file": rel,
                "line": line,
                "msg": msg,
            })

    if not findings:
        print(f"✅ 业务错误状态码：{scanned} 个 main 源文件里，"
              f"没有「查不到资源」却抛 RuntimeException（→ HTTP 500）的写法")
        return 0

    print(f"❌ 业务错误状态码：{len(findings)} 处「XX不存在」抛 RuntimeException → HTTP 500")
    for it in findings:
        print(f"\n   {it['file']}:{it['line']}")
        print(f"     消息: {it['msg']}")
        print('     → 修法: new BizException("...")  （业务错误 → HTTP 400，不是「服务端故障」500）')
        print("     （规则 28 / 44 / 107：同一模块同一概念必须一个口径）")
    if verbose:
        print(f"\n扫描 main 源文件数: {scanned}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
