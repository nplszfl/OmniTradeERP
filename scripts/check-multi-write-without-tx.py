#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「写多张表却没有事务」的闸门 —— 规则 111 / 129 的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过，2026-11-11）：
    `PricingSuggestionServiceImpl.applySuggestion()` 写 3 张表
    （suggestion / product / priceHistory）**没有事务** → 中间一步失败 →
    前两张已落库、第三张没写 → **数据不一致 + 界面却说「已采纳」= 假成功**。
    修了之后要有闸门守住，别让下一个多表写的方法裸奔。

📌 **判据的四个坑**（写这类闸门前必须先记住，都是本实现实测踩出来的）：

1. **注解被正则吃掉** → 判据看不见 `@Transactional`
   → 方法正则若只匹配 `public ...`，而注解在**上一行** → 注解不在匹配范围内
   → 表现：明明有事务的方法被判成「没事务」（**假红**）
   → ✅ 修法：正则把**注解一起纳入匹配**，判据读 **match 文本**。

2. **调用图方向搞反** → 私有 helper 被误判
   → 「有没有事务保护」应该查**谁调用了它**（callers），不是「它调用了谁」（callees）
   → 表现：`checkAndCreateAlert()`（私有，被 `@Transactional` 的方法调用）被判红
   → ✅ 修法：建 **callers** 反向索引 + 传递闭包。

3. **只数「mapper.方法」而不是「表」** → 同一张表的 insert + updateById 被当成两张表
   → ✅ 修法：按 **mapper 名**去重（一个 mapper = 一张表）。

4. **分支互斥** → switch/case 里每个分支各写一张表，运行时只会走一个分支
   → `delete()` 写 3 张表，但 switch 每 case 只写 1 张 → **不是多表写**
   → ✅ 修法：给每个写操作记录**分支路径**（switch-case 标签 / if-else 分支）；
     两条写在某个条件上下文里取值不同 → 互斥。

用法：
    python3 scripts/check-multi-write-without-tx.py            # 巡检（有问题 → exit 1）
    python3 scripts/check-multi-write-without-tx.py --verbose  # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# 写操作：mapper / service / repository 的写方法
WRITE_RE = re.compile(
    r"\b(\w+(?:Mapper|Service|Repository))\s*\.\s*"
    r"(insert|updateById|update|deleteById|delete|save|saveBatch|remove|removeById)\s*\("
)

# 方法：注解 + 签名（**注解必须在匹配范围内**，见坑 1）
METHOD_RE = re.compile(
    r"(?m)^[ ]{4}((?:@\w+(?:\([^)]*\))?[ \t]*\n[ \t]{4})*)"
    r"(?:public|private|protected)\s+[\w<>,\[\]\.\? ]+\s+(\w+)\s*\([^)]*\)\s*"
    r"(?:throws [\w, ]+)?\{"
)


def iter_java_files():
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/java" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                yield os.path.join(dirpath, fn)


def strip_literals(src: str) -> str:
    """把字符串/字符/注释替换成等长空格 → 注释里的关键字不干扰扫描。"""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c in "\"'":
            q = c
            i += 1
            while i < n:
                if src[i] == "\\":
                    out[i] = " "
                    i += 1
                    if i < n:
                        out[i] = " "
                        i += 1
                    continue
                if src[i] == q:
                    break
                out[i] = " "
                i += 1
            i += 1
            continue
        if src.startswith("//", i):
            while i < n and src[i] != "\n":
                out[i] = " "
                i += 1
            continue
        if src.startswith("/*", i):
            while i < n and not src.startswith("*/", i):
                if src[i] != "\n":
                    out[i] = " "
                i += 1
            for _ in range(2):
                if i < n:
                    out[i] = " "
                    i += 1
            continue
        i += 1
    return "".join(out)


def scan_writes(clean: str):
    """扫描方法体里的写操作，给每个写标注**分支路径**。

    分支路径 = 逐层「能决定互斥」的上下文标签：
      - switch 的 case 标签（`case sales` / `default`）
      - if / else 分支
    返回 [(pos, table, path)]
    """
    n = len(clean)
    # 栈：[(kind, id, label)]，kind ∈ switch / if / other
    stack = []
    case_label = {}          # switch block id -> 当前 case 标签
    next_id = 0
    pend = None              # 等待进入的块类型
    writes = []

    def current_path():
        path = []
        for kind, bid, label in stack:
            if kind == "switch":
                path.append(("switch", bid, case_label.get(bid, "case:?default")))
            elif kind == "if":
                path.append(("if", bid, label))
        return tuple(path)

    i = 0
    while i < n:
        ch = clean[i]
        m = re.match(r"\b(case|default)\b", clean[i:])
        if m and stack and stack[-1][0] == "switch":
            rest = clean[i + m.end(): i + m.end() + 80]
            # 📌 case 标签要取到 '->' 或 ':' 为止 —— **Java 21 的 switch 箭头语法
            #    `case "sales" ->` 后面没有冒号** → 只按 ':' 切会把**所有 case 切成同一个标签**
            #    → 三张表都「不互斥」→ 假红（实测踩过）。
            m2 = re.match(r"\s*([^:]{0,60}?)\s*(?:->|:)", rest)
            label = (m2.group(1) if m2 else "?").strip()
            case_label[stack[-1][1]] = m.group(1) + ":" + label
            i += m.end()
            continue
        m = re.match(r"\b(if|else|switch|for|while|do|try|catch)\b", clean[i:])
        if m:
            kw = m.group(1)
            if kw == "if":
                pend = "if"
            elif kw == "else":
                # else 分支：把当前 if 块的标签改成 else
                for k in range(len(stack) - 1, -1, -1):
                    if stack[k][0] == "if":
                        stack[k] = ("if", stack[k][1], "else")
                        break
            elif kw == "switch":
                pend = "switch"
            elif kw in ("for", "while", "do"):
                pend = "loop"
            elif kw in ("try", "catch"):
                pend = "try"
            i += m.end()
            continue
        if ch == "{":
            next_id += 1
            kind = pend or "other"
            if kind == "switch":
                case_label[next_id] = "case:?default"
            stack.append((kind, next_id, "if" if kind == "if" else kind))
            pend = None
            i += 1
            continue
        if ch == "}":
            if stack:
                stack.pop()
            pend = None
            i += 1
            continue
        # 写操作？
        m = WRITE_RE.match(clean, i)
        if m:
            writes.append((m.start(), m.group(1), current_path()))
            i = m.end()
            continue
        i += 1
    return writes


def paths_exclusive(pa, pb) -> bool:
    """两个写操作是否互斥（运行时不会同时执行）。"""
    # 路径长度不同 → 公共前缀里若已分叉 → 互斥
    for a, b in zip(pa, pb):
        if a[0] == b[0] and a[1] == b[1] and a[2] != b[2]:
            return True
    return False


def can_write_together(writes) -> bool:
    """是否存在**两张不同的表**可能被同时写入。"""
    by_table = {}
    for pos, table, path in writes:
        by_table.setdefault(table, []).append(path)
    tables = list(by_table)
    for a in range(len(tables)):
        for b in range(a + 1, len(tables)):
            for pa in by_table[tables[a]]:
                for pb in by_table[tables[b]]:
                    if not paths_exclusive(pa, pb):
                        return True
    return False


def _self_test() -> bool:
    """🧪 自检：把**真实样本**注入一份**隔离副本**，验证本闸门会变红。

    📌 **自检必须在副本上做**（规则 43 / 51）：直接改真数据 → 结论与数字不同源。
    📌 **必须注入「真实的原始缺陷」**，不是「人为加一行」（规则 40 / 75）：
       注入的样本长这样（无事务的多表写）：

           public void applySuggestion() {
               suggestionMapper.updateById(suggestion);
               productMapper.updateById(product);   // ← 没事务
               priceHistoryMapper.insert(history);
           }
    """
    import tempfile
    import shutil
    with tempfile.TemporaryDirectory() as td:
        pkg = os.path.join(td, "scripts")
        os.makedirs(pkg, exist_ok=True)
        sample = os.path.join(td, "src", "main", "java", "com", "x")
        os.makedirs(sample, exist_ok=True)
        src = os.path.join(sample, "BadSvc.java")
        with open(src, "w", encoding="utf-8") as f:
            f.write(
                "package com.x;\n"
                "import org.springframework.stereotype.Service;\n"
                "@Service\n"
                "public class BadSvc {\n"
                "    public void applySuggestion() {\n"
                "        suggestionMapper.updateById(suggestion);\n"
                "        productMapper.updateById(product);\n"
                "        priceHistoryMapper.insert(history);\n"
                "    }\n"
                "}\n"
            )
        shutil.copy(os.path.abspath(__file__), os.path.join(pkg, "check-multi-write-without-tx.py"))
        # 📌 **子进程必须跳过自检** → 否则「自检 → 复制自己 → 子进程再自检 → …」**无限递归**
        env = dict(os.environ, GATE_SELF_TEST_CHILD="1")
        r = subprocess.run([sys.executable,
                            os.path.join(pkg, "check-multi-write-without-tx.py")],
                           capture_output=True, text=True, timeout=120, env=env)
        ok = r.returncode == 1 and "applySuggestion" in (r.stdout + r.stderr)
        print(f"   🧪 [自检] 副本上注入「无事务的多表写」→ 期望 exit 1 且指名 applySuggestion，"
              f"实际 exit {r.returncode} → {'✅ 判据是活的' if ok else '❌ 判据失明！'}")
        return ok


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []
    checked = 0

    for path in iter_java_files():
        try:
            text = open(path, encoding="utf-8").read()
        except Exception:
            continue
        rel = os.path.relpath(path, REPO_ROOT)
        cls_head = text[: text.find("class ")] if "class " in text else ""
        cls_tx = "@Transactional" in cls_head

        methods = {}
        for m in METHOD_RE.finditer(text):
            ann, name = m.group(1) or "", m.group(2)
            i = m.end()
            depth, j = 1, i
            while j < len(text) and depth > 0:
                if text[j] == "{":
                    depth += 1
                elif text[j] == "}":
                    depth -= 1
                j += 1
            methods[name] = (ann, text[i:j], m.start())

        # 调用者索引（坑 2）
        callers = {n: set() for n in methods}
        for name, (ann, body, pos) in methods.items():
            for other in methods:
                if other == name:
                    continue
                if re.search(r"(?<![\w.])\b" + re.escape(other) + r"\s*\(", body):
                    callers[other].add(name)

        def covered(start, seen=None):
            seen = set() if seen is None else seen
            for c in callers.get(start, ()):
                if c in seen:
                    continue
                seen.add(c)
                ann, _, _ = methods[c]
                if "@Transactional" in ann:
                    return True
                if covered(c, seen):
                    return True
            return False

        for name, (ann, body, pos) in methods.items():
            clean = strip_literals(body)
            writes = scan_writes(clean)
            tables = sorted({t for _, t, _ in writes})
            if len(tables) < 2:
                continue
            checked += 1
            if "@Transactional" in ann or cls_tx or covered(name):
                continue
            if not can_write_together(writes):
                continue      # 分支互斥 → 运行时只会写一张表
            line = text.count("\n", 0, pos) + 1
            findings.append((rel, line, name, tables))

    print("📋 巡检「写多张表却没有事务」")
    print(f"ℹ️  写 >=2 张表的方法：{checked} 处")
    if verbose:
        for rel, line, name, tables in findings:
            print(f"   {rel}:{line} {name}() -> {tables}")

    self_ok = True
    if os.environ.get("GATE_SELF_TEST_CHILD") != "1":
        self_ok = _self_test()
    if not self_ok:
        print("\n❌ 自检失败：本闸门对「无事务的多表写」已经失明 → 先修判据，别信下面的绿")
        return 2

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「多表写但没有事务」：")
        for rel, line, name, tables in findings:
            print(f"   {rel}:{line}")
            print(f"        方法 {name}() 写 {len(tables)} 张表 {tables}，"
                  f"却没有 @Transactional → 中途失败会**部分落库**"
                  f"（数据不一致 + 假成功，规则 111 / 129）")
        print("\n✅ 修法：给方法加 `@Transactional(rollbackFor = Exception.class)`"
              "（catch 住异常再抛业务异常时注意回滚语义）")
        return 1

    print("\n✅ 所有「写多张表」的方法都有事务保护")
    return 0


if __name__ == "__main__":
    sys.exit(main())
