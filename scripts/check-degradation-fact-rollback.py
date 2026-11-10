#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**降级事实的落地性**闸门 —— 规则 111。

📌 **为什么需要这道闸门**（11-10 互审实证，**至今没有闸门守着**）：

| 缺陷 | 形态 | 后果 |
|---|---|---|
| **标记了不等于落地了** | catch 里 `repo.save(entity)` 标记降级状态（ERROR/INACTIVE），**紧接着抛 RuntimeException/BizException** | `BizException extends RuntimeException` → Spring 默认 `rollbackFor=RuntimeException` → **整个事务回滚** → **标记被撤销** → 实体状态永远停在旧值 |

**实测后果链**（`LiveStreamMonitorService`，2026-11-10 22:45）：
店铺 token 刷新失败 → 标记 `ERROR` + 抛异常 → **回滚冲掉标记** → 店铺永远 `ACTIVE`
→ 下一轮 `findByStatus(ACTIVE)` 又取到它 → 又失败 → **死循环** → 每 30 秒一条 error
→ **真正的故障被噪音淹没**（规则 43）。

📌 **修法**：`@Transactional(noRollbackFor = BizException.class)`。

⚠️ **为什么这类缺陷必须有闸门**：它是**编译能过、单测测不出**的
—— 「标记能否落地」取决于**事务边界**，而 `@DataJpaTest` 的**外层测试事务**
会被 BizException 标记 `rollback-only`，**外层优先级高于内层方法的 `noRollbackFor`**
→ **无论业务方法怎么标，测试里都测不出来** → 只能靠**静态闸门**守住（规则 97 同款思路）。

---

📌 **判据（跨方法 + 同 try 块 —— 规则 100：只认单方法体 = 双向失明）**

> 📌 **实测踩过的坑（闸门自己的第一版就这么失明了）**：
> 降级标记（`setStatus(ERROR)`）和 `throw new BizException(...)` 都在
> **私有方法 `refreshToken(shop)`** 里，而 `@Transactional` 在**调用方**
> `syncLiveStreamData` 上。
> → 只扫「单个方法体内同时出现标记和 throw」→ **完全失明**（第一版实测 exit 0）。
> → ✅ 正解：**跨方法 + 同 try 块**两级判定：
>   ① **降级标记和抛异常必须在「同一个方法」的「同一个 try 块」里**（同一条失败路径）
>   ② 但**那个方法不一定就是 @Transactional 方法** —— 私有 `refreshToken` 自己不开事务，
>      它继承**调用方**的事务 → **调用方回滚 = 私有方法的标记一起被撤销**。

对每个 `@Transactional` 的 **public** 方法：
1. 存在**某个方法 M**（M ≠ 必须等于 @Transactional 方法）：
   - M 内有「降级标记」—— 形如 `setStatus(...)` / `setEnabled(false)` / `setState(...)`，
     且实参是**降级语义**（ERROR / DISABLED / INACTIVE / FAILED / SUSPENDED 等）
   - M 内有 `throw new BizException(...)` 或 `throw new RuntimeException(...)`
   - **且两者在 M 的同一个 try 块里**
2. **且**该 `@Transactional` 方法的注解**没有** `noRollbackFor` 包含 `BizException.class`
   （`rollbackFor` 包含 `RuntimeException.class` **不算豁免** —— 那反而更糟）

→ 报红：**降级标记 + 抛异常 会在同一事务里被回滚冲掉**。

📌 **为什么必须限定「同一个 try 块」（第二版的过度修正）**：
第二版按「类」一刀切 → `TikTokOrderService` 里「catch 标 ERROR（行 336）」和
「throw（行 120）」**在两个不同方法、不同失败路径** → **误报**
（规则 99：假阳性比假阴性更危险 → 会逼下一个人放松闸门）。
→ ✅ 必须**同方法 + 同 try 块**。

📌 **豁免情形（避免假阳性，规则 99）**：
- 已有 `noRollbackFor` 包含 `BizException.class` → 不报
- 该类里**既没有**降级标记**也没有**抛这两种异常 → 不报
- 类里没有 `public @Transactional` 方法 → 不报

📌 **反向验证**（规则 40 / 75 / 106，注入**真实的原始形态**）：
① 去掉 `noRollbackFor` → 报红；
② 把 `BizException` 改成 `RuntimeException` → **仍报红**（更糟）；
③ 把降级标记移出该类 → 不报；
④ 正常类（无降级标记）→ 不报。

用法：
    python3 scripts/check-degradation-fact-rollback.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-degradation-fact-rollback.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── 降级语义常量：表示「这次操作失败了 / 停用了」 ──
DEGRADED_STATUS = re.compile(
    r"\b(ERROR|FAILED|INVALID|EXPIRED|DISABLED|INACTIVE|SUSPENDED|BROKEN|DEGRADED|EXCEPTION)\b"
)

# ── 降级标记的 setter ──
DEGRADED_SETTER = re.compile(
    r"\.\s*(setStatus|setState|setEnabled|setActive|setAvailable|setHealthy)\s*\(\s*"
)

# ── 抛异常 ──
THROW = re.compile(r"throw\s+new\s+([A-Za-z_][\w.]*)\s*\(")

BIZ_EXCEPTION = "BizException"
RUNTIME_EXCEPTION = "RuntimeException"


def find_java_files(root: str) -> list[str]:
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in {".git", "target", "node_modules", ".idea"}]
        for fn in filenames:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def split_classes(src: str) -> list[tuple[int, int]]:
    """按顶层 class 声明切分源码，返回每个类的 (起, 止)。"""
    spans = []
    for m in re.finditer(r"^(?:public\s+|final\s+|abstract\s+)*class\s+\w+", src, re.M):
        start = m.start()
        # 找到 class 体的大括号
        brace = src.find("{", m.end())
        if brace < 0:
            continue
        depth = 0
        j = brace
        while j < len(src):
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
                if depth == 0:
                    spans.append((start, j + 1))
                    break
            j += 1
    return spans


def find_tx_methods(src: str, cls_start: int, cls_end: int) -> list[dict]:
    """找出类里所有 public @Transactional 方法。"""
    out = []
    for ann in re.finditer(r"@Transactional\b", src[cls_start:cls_end]):
        abs_ann = cls_start + ann.start()
        after = abs_ann + len("@Transactional")

        # 📌 **先取「紧跟注解的括号」**（注解参数）——
        #    ⚠️ 不能往后找任意 '('：那会找到**方法签名**的括号
        #    （第一版就这么写 → 打印成 `@Transactional(TikTokShopConfig shop)` → 闸门在撒谎，规则 88）
        params = ""
        k = after
        while k < len(src) and src[k] in " \t\r\n":
            k += 1
        if k < len(src) and src[k] == "(":
            depth = 0
            j = k
            while j < len(src):
                if src[j] == "(":
                    depth += 1
                elif src[j] == ")":
                    depth -= 1
                    if depth == 0:
                        params = src[k:j + 1]
                        break
                j += 1
            after = j + 1

        # 跳过其它注解与空白，找方法签名
        rest = src[after:cls_end]
        m_sig = re.search(r"\bpublic\b[^{;]{0,500}?\(", rest)
        if not m_sig:
            continue
        sig_abs = after + m_sig.start()
        line_no = src[:sig_abs].count("\n") + 1
        # 方法名
        sig_txt = rest[m_sig.start():m_sig.start() + 500]
        m_name = re.search(r"(\w+)\s*\(", sig_txt)
        name = m_name.group(1) if m_name else ""
        out.append({"line": line_no, "params": params.strip() or "", "name": name})
    return out


def _brace_span(body: str, open_idx: int) -> tuple[int, int] | None:
    """从 '{' 处按大括号配对，返回 (起, 止)。"""
    depth = 0
    j = open_idx
    while j < len(body):
        if body[j] == "{":
            depth += 1
        elif body[j] == "}":
            depth -= 1
            if depth == 0:
                return (open_idx, j)
        j += 1
    return None


def try_spans(body: str) -> list[tuple[int, int]]:
    """返回所有 **try 块和 catch 块** 的 (起, 止) 区间。

    📌 **必须包含 catch 块**（实测踩过）：
    真实形态是「catch 里标 ERROR + 抛异常」——
    标记和 throw **都在 catch 块内**，只在 \`try { }\` 的括号范围里找 → **完全失明**。
    """
    spans = []
    for m in re.finditer(r"\b(try|catch|finally)\b", body):
        brace = body.find("{", m.end())
        if brace < 0:
            continue
        sp = _brace_span(body, brace)
        if sp:
            spans.append((m.start(), sp[1]))
    return spans



_METHOD_SIG = re.compile(
    r"(?:public|protected|private|static|final|synchronized|\s)+"
    r"[\w<>\[\],\.\s]+?\s+(\w+)\s*\([^)]*\)\s*\{"
)


def _enclosing_method_name(body: str, pos: int) -> str:
    """找出 pos 所在的方法名（取离 pos 最近的、在它之前的方法签名）。"""
    best = ""
    best_pos = -1
    for m in _METHOD_SIG.finditer(body):
        if m.start() > pos:
            break
        # 排除 if/for/while/switch 等控制块（它们后跟 '(' 但不是方法签名）
        name = m.group(1)
        if name in {"if", "for", "while", "switch", "catch", "synchronized", "return", "new"}:
            continue
        if m.start() > best_pos:
            best_pos = m.start()
            best = name
    return best


def _method_bodies(src: str, cls_start: int, cls_end: int) -> dict[str, str]:
    """返回 {方法名: 方法体}。"""
    body = src[cls_start:cls_end]
    out = {}
    for m in _METHOD_SIG.finditer(body):
        name = m.group(1)
        if name in {"if", "for", "while", "switch", "catch", "synchronized", "return", "new"}:
            continue
        brace = body.find("{", m.end() - 1)
        if brace < 0:
            continue
        sp = _brace_span(body, brace)
        if sp:
            out[name] = body[sp[0]:sp[1]]
    return out

def find_risky_methods(src: str, cls_start: int, cls_end: int) -> list[dict]:
    """找出类里所有「同 try 块内既有降级标记又抛异常」的方法。

    📌 **必须限定「同一个 try 块」** —— 同一个类里不同方法、不同失败路径的
    标记和 throw **不构成**回滚风险（否则误报，规则 99）。
    """
    body = src[cls_start:cls_end]
    spans = try_spans(body)
    if not spans:
        return []

    markers = []
    for m in DEGRADED_SETTER.finditer(body):
        seg = body[m.end():m.end() + 80]
        if DEGRADED_STATUS.search(seg):
            markers.append(m)

    throws = []
    for m in THROW.finditer(body):
        exc = m.group(1).split(".")[-1]
        if exc in (BIZ_EXCEPTION, RUNTIME_EXCEPTION):
            throws.append(m)

    if not markers or not throws:
        return []

    for s0, e0 in spans:
        hit_m = [m for m in markers if s0 <= m.start() <= e0]
        hit_t = [m for m in throws if s0 <= m.start() <= e0]
        if hit_m and hit_t:
            # 📌 找出**风险所在的方法名** —— 后面用它做**可达性**判断
            owner = _enclosing_method_name(body, hit_m[0].start())
            return [{
                "line": src[:cls_start + hit_m[0].start()].count("\n") + 1,
                "marker_text": body[hit_m[0].start():hit_m[0].start() + 70].strip(),
                "throw_text": body[hit_t[0].start():hit_t[0].start() + 70].strip(),
                "exc": hit_t[0].group(1).split(".")[-1],
                "owner_method": owner,
            }]
    return []



def _reachable(tx_name: str, owner: str, bodies: dict[str, str]) -> bool:
    """@Transactional 方法能否**到达**风险方法（自身 或 一层直接调用）。"""
    if not owner:
        return True  # 定位不到方法名 → 保守报红（避免漏）
    if tx_name == owner:
        return True
    body = bodies.get(tx_name)
    if body is None:
        return False
    # 一层：tx 方法体内直接调用了 owner 方法
    return re.search(r"\b" + re.escape(owner) + r"\s*\(", body) is not None

def analyze_file(path: str) -> list[dict]:
    src = open(path, encoding="utf-8", errors="ignore").read()
    findings = []

    for cls_start, cls_end in split_classes(src):
        tx_methods = find_tx_methods(src, cls_start, cls_end)
        if not tx_methods:
            continue

        risky = find_risky_methods(src, cls_start, cls_end)
        if not risky:
            continue

        r = risky[0]
        owner = r.get("owner_method") or ""
        bodies = _method_bodies(src, cls_start, cls_end)

        for tm in tx_methods:
            if "noRollbackFor" in tm["params"] and BIZ_EXCEPTION in tm["params"]:
                continue  # 已豁免

            # 📌 **可达性判断**（实测踩过：闸门第二版误报 9 处）
            #    `associateWithLiveStream` / `associateWithInfluencer` 与 token 刷新**完全无关**
            #    → 类里有降级标记 + 抛异常 ≠ 这个 @Transactional 方法会遇到它。
            #    → 正解：@Transactional 方法**本身**是风险方法，
            #       **或它直接调用**了风险方法（**一层**调用图，够用且不会漏太多）。
            if not _reachable(tm["name"], owner, bodies):
                continue

            findings.append({
                "file": os.path.relpath(path, REPO_ROOT),
                "tx_line": tm["line"],
                "params": tm["params"],
                "marker": r,
                "throw": r,
                "owner_method": owner,
            })

    return findings


def main() -> int:
    verbose = "--verbose" in sys.argv
    java_files = find_java_files(REPO_ROOT)

    all_findings = []
    for f in java_files:
        try:
            all_findings.extend(analyze_file(f))
        except Exception as e:  # 闸门自身不能静默崩（规则 88）
            print(f"❌ 闸门解析失败: {os.path.relpath(f, REPO_ROOT)}: {e}", file=sys.stderr)
            return 1

    if not all_findings:
        print(f"✅ 降级事实落地性：{len(java_files)} 个 Java 文件里没有"
              f"「标记降级 + 抛异常」会被回滚冲掉的写法")
        return 0

    print(f"❌ 降级事实落地性：{len(all_findings)} 处「降级标记会被事务回滚冲掉」")
    for it in all_findings:
        print(f"\n   {it['file']}:{it['tx_line']}")
        print(f"     事务:     @Transactional{' ' + it['params'] if it['params'] else ''}")
        print(f"     风险方法: {it.get('owner_method') or '(未定位)'}")
        print(f"     降级标记: {it['marker']['marker_text']}   (行 {it['marker']['line']})")
        print(f"     抛出:     {it['throw']['throw_text']}   (行 {it['throw']['line']})")
        print("     → 修法: @Transactional(noRollbackFor = BizException.class)")
        print("     （降级标记必须真的落地 —— 规则 18 / 19 / 111）")
    if verbose:
        print(f"\n扫描文件数: {len(java_files)}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
