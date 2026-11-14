#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「硬编码常量冒充业务数据」巡检 —— 第 76 道闸门。

📌 **为什么需要这道闸门**（2026-11-15 02:55 实测踩出来的坑）：

commit `646dc06`「去假数据」重构把 pricing 的「内存存储冒充数据库」和
AI 知识库的硬编码都清了，**75 道闸门全绿**。
但同一个重构**漏了 product-description 模块**：

```java
private List<String> getProductFeatures(Long productId) {
    // 模拟：返回产品特性
    return Arrays.asList("高性能", "稳定可靠", "易于使用", "节能环保", "设计精巧");
}
```

→ **完全忽略 productId**。实测：
```
GET /api/description/highlights/1   → ['高性能','稳定可靠','易于使用','节能环保','设计精巧']
GET /api/description/highlights/999 → ['高性能','稳定可靠','易于使用','节能环保','设计精巧']
```
连**库里根本不存在的 id** 都返回同一批文案 → **假数据**（规则 72 / 80 / 82）。

📌 **为什么第 75 道闸门没抓到**：
`check-in-memory-storage.py` 只查两类 ——
① 集合字段**当持久化存储**（`private final List<X> list = new CopyOnWriteArrayList<>()`）
② 用 `Random` 造业务数据
**「方法体直接 return 一个硬编码常量列表」两类都不是** →
这是典型的**「闸门覆盖的盲区不按规则编号来，按当初有没有顺手加一个来」**（规则 102）。

📌 **本闸门的判据**（刻意收窄，避免误伤）：
1. 只扫 `src/main/java` 下的 **`*ServiceImpl.java`**（业务实现类）
2. 只抓 `return Arrays.asList(...)` / `return List.of(...)` / `return new String[]{...}`
   且**元素是字符串字面量**（业务文案）——数字/ID 列表不在此列（那是配置，不是数据）
3. **豁免**：空集合 `List.of()`；元素是变量/常量引用（不是字面量）；注释里已标注「模拟」
4. **豁免**：方法名含 `template`/`mock`/`sample`/`demo`/`example`（演示/模板用途）
5. **豁免**：方法体里有 `mapper`/`repository`/`dao` 字段调用（说明是真查库，只是返回了兜底常量）

⚠️ **豁免规则本身也是盲区**（规则 202）→ 每条豁免都有对应的反向探针用例。

📌 **反向验证**：注入 → 必须变红；还原 → 必须变绿。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── 只扫业务实现类 ────────────────────────────────────────────────
SERVICE_IMPL_RE = re.compile(r"ServiceImpl\.java$")

# ── 硬编码常量返回 ────────────────────────────────────────────────
# return Arrays.asList("a", "b")  /  return List.of("a")  /  return new String[]{"a"}
HARDCODE_RE = re.compile(
    r"return\s+(?:java\.util\.)?(?:Arrays\.asList|List\.of|Set\.of)\s*\(([^)]*)\)",
    re.DOTALL,
)
ARRAY_LITERAL_RE = re.compile(
    r"return\s+new\s+(?:String|Object)\s*\[\s*\]\s*\{([^}]*)\}",
    re.DOTALL,
)

# 只认**字符串字面量**元素 —— 数字/ID 列表是配置，不是「假业务文案」
# ⚠️ 量词必须**在括号内**：写成 "([^"\\]|\\.)*" 的话，* 会量化整个分组，
#    而 Python 的 findall 对被量化的分组**只返回最后一次重复** → 「默认特性」会变成「性」。
STRING_LITERAL_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')

# 「模拟」自认 → 说明作者知道这是假的（但仍然要报，除非在豁免名单里）
MOCK_COMMENT_RE = re.compile(r"模拟|mock|Mock|假数据|TODO")

# 方法名豁免（演示/模板/示例用途）
EXEMPT_METHOD_NAME_RE = re.compile(
    r"(?i)(template|mock|sample|demo|example|placeholder)"
)

# 方法体里有持久层调用 → 说明是真查库，常量只是兜底
# ⚠️ 不能用 \b 打头：字段名通常是 productMapper / orderRepository，
#    「t」→「M」之间没有词边界 → \b(?:...|Mapper) 永远匹配不上。
PERSISTENCE_CALL_RE = re.compile(
    r"(?:mapper|repository|dao)\w*\s*\.", re.IGNORECASE
)


def find_method_context(lines, start_idx):
    """从 `return ...` 那一行往上找方法签名，往下找方法体结束。

    返回 (method_name, full_method_text) 或 (None, None)。
    ⚠️ 持久层调用可能出现在 return **上方**（先查库、为空才兜底），
    所以必须返回**完整方法**（签名 + 方法体），不能只取下方。
    """
    # 往上找方法声明（可能跨行）
    # ⚠️ 停止条件必须是「**带可见性修饰符**的方法声明」——
    #    只看 `) {` 会把上面的 `if (...) {` 当成方法头 → 方法名找不到、
    #    方法体边界也错（真实查询语句在 return 上方 → 持久层豁免失效）。
    METHOD_DECL_RE = re.compile(r"\b(public|private|protected)\b[^{;]*\{\s*$")
    header = []
    i = start_idx
    while i >= 0 and len(header) < 20:
        header.insert(0, lines[i])
        if METHOD_DECL_RE.search(lines[i]):
            break
        i -= 1
    header_text = "\n".join(header)
    m = re.search(r"\b(?:public|private|protected)\s+(?:static\s+|final\s+|synchronized\s+)*"
                  r"[\w<>,\[\]\s\.]+?\s+(\w+)\s*\(", header_text)
    method_name = m.group(1) if m else None

    # 往下找方法体结束（按大括号配平）
    depth = 0
    body = []
    started = False
    for j in range(start_idx, min(start_idx + 120, len(lines))):
        line = lines[j]
        body.append(line)
        depth += line.count("{") - line.count("}")
        if "{" in line:
            started = True
        if started and depth <= 0:
            break
    return method_name, "\n".join(header) + "\n" + "\n".join(body)


def check_file(path):
    """返回该文件的问题列表。"""
    problems = []
    try:
        with open(path, encoding="utf-8") as fh:
            content = fh.read()
    except (OSError, UnicodeDecodeError):
        return problems

    lines = content.split("\n")

    # ⚠️ 必须对**全文**匹配（re.DOTALL），不能逐行扫 ——
    #    真实写法是跨行的：
    #        return Arrays.asList(
    #            "高性能",
    #            "稳定可靠"
    #        );
    #    逐行扫完全看不见它 → 闸门对**真缺陷**是绿的（规则 55 / 100）。
    for pattern in (HARDCODE_RE, ARRAY_LITERAL_RE):
        for m in pattern.finditer(content):
            args = m.group(1)
            elements = [e.strip() for e in args.split(",") if e.strip()]
            if not elements:
                continue
            literals = []
            pure_literals = True
            for el in elements:
                lit = STRING_LITERAL_RE.fullmatch(el)
                if lit:
                    literals.append(lit.group(1))
                else:
                    pure_literals = False
            if not literals:
                # 全是数字/变量 → 是配置或 ID 清单，不是「硬编码业务文案」
                continue
            if not pure_literals:
                # 元素里混了变量 → 不是纯常量
                continue

            line_idx = content.count("\n", 0, m.start())
            method_name, body = find_method_context(lines, line_idx)
            if method_name and EXEMPT_METHOD_NAME_RE.search(method_name):
                continue
            if body and PERSISTENCE_CALL_RE.search(body):
                continue

            problems.append({
                "file": os.path.relpath(path, ROOT),
                "line": line_idx + 1,
                "method": method_name or "?",
                "detail": "方法 `%s` 直接返回硬编码常量当业务数据：%s"
                          % (method_name or "?", ", ".join(repr(x) for x in literals[:4])),
            })
    return problems


def collect(root=ROOT):
    problems = []
    for dirpath, dirnames, filenames in os.walk(root):
        # 跳过 target / .git / node_modules
        dirnames[:] = [d for d in dirnames
                       if d not in ("target", ".git", "node_modules", "dist", "build")]
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            if not SERVICE_IMPL_RE.search(fn):
                continue
            p = os.path.join(dirpath, fn)
            problems.extend(check_file(p))
    return problems


# ── 自检（反向验证）──────────────────────────────────────────────
SELF_TEST_BAD = """package com.crossborder.erp.demo.service.impl;

import java.util.Arrays;
import java.util.List;

public class BadHardcodeServiceImpl {

    /** 模拟：返回产品特性 */
    public List<String> getProductFeatures(Long productId) {
        return Arrays.asList("高性能", "稳定可靠", "易于使用");
    }
}
"""

SELF_TEST_OK_MIXED = """package com.crossborder.erp.demo.service.impl;

import java.util.List;

public class OkMixedServiceImpl {

    /** 元素是变量 → 不是硬编码 */
    public List<String> build(List<String> source) {
        return List.of(source);
    }
}
"""

SELF_TEST_OK_EMPTY = """package com.crossborder.erp.demo.service.impl;

import java.util.List;

public class OkEmptyServiceImpl {

    public List<String> nothing() {
        return List.of();
    }
}
"""

SELF_TEST_OK_NUMERIC = """package com.crossborder.erp.demo.service.impl;

import java.util.List;

public class OkNumericServiceImpl {

    /** 监控的商品ID清单 = 配置，不是文案 */
    public List<Long> monitoredIds() {
        return List.of(1L, 2L, 3L);
    }
}
"""

SELF_TEST_OK_TEMPLATE = """package com.crossborder.erp.demo.service.impl;

import java.util.Arrays;
import java.util.List;

public class OkTemplateServiceImpl {

    /** 方法名含 template → 演示用途，豁免 */
    public List<String> buildTemplate(Long id) {
        return Arrays.asList("占位一", "占位二");
    }
}
"""

SELF_TEST_OK_PERSISTENCE = """package com.crossborder.erp.demo.service.impl;

import java.util.Arrays;
import java.util.List;

public class OkPersistenceServiceImpl {

    private ProductMapper productMapper;

    /** 兜底常量，但方法体里有真实查询 */
    public List<String> list(Long id) {
        List<String> real = productMapper.findFeatures(id);
        if (real == null || real.isEmpty()) {
            return Arrays.asList("默认特性");
        }
        return real;
    }
}
"""


def run_self_test():
    import tempfile
    import shutil

    tmp = tempfile.mkdtemp(prefix="check-hardcoded-fake-data-")
    try:
        base = os.path.join(tmp, "erp-demo", "src", "main", "java",
                            "com", "crossborder", "erp", "demo", "service", "impl")
        os.makedirs(base, exist_ok=True)
        cases = [
            ("BadHardcodeServiceImpl.java", "坏例-硬编码特性清单", SELF_TEST_BAD, True),
            ("OkMixedServiceImpl.java", "好例-元素是变量", SELF_TEST_OK_MIXED, False),
            ("OkEmptyServiceImpl.java", "好例-空集合", SELF_TEST_OK_EMPTY, False),
            ("OkNumericServiceImpl.java", "好例-数字ID清单", SELF_TEST_OK_NUMERIC, False),
            ("OkTemplateServiceImpl.java", "好例-模板方法名", SELF_TEST_OK_TEMPLATE, False),
            ("OkPersistenceServiceImpl.java", "好例-有真实持久层调用", SELF_TEST_OK_PERSISTENCE, False),
        ]
        failures = []
        for fname, name, content, should_fail in cases:
            f = os.path.join(base, fname)
            with open(f, "w", encoding="utf-8") as fh:
                fh.write(content)
            problems = collect(tmp)
            got = len(problems) > 0
            ok = got == should_fail
            print("  %s %s: 期望%s，实际%s"
                  % ("✅" if ok else "❌", name,
                     "报红" if should_fail else "不报红",
                     "报红" if got else "不报红"))
            if not ok:
                failures.append(name)
                for p in problems:
                    print("       └─ %s:%d %s" % (p["file"], p["line"], p["detail"]))
            os.remove(f)
        if failures:
            print("❌ 自检失败：%s" % ", ".join(failures))
            return 1
        print("✅ 自检全部通过")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    if "--self-test" in sys.argv:
        print("🧪 「硬编码常量冒充业务数据」闸门自检")
        return run_self_test()

    verbose = "--verbose" in sys.argv
    problems = collect(ROOT)

    if not problems:
        print("✅ 没发现「硬编码常量冒充业务数据」的缺陷")
        return 0

    print("❌ 「硬编码常量冒充业务数据」巡检：发现 %d 处\n" % len(problems))
    for p in problems:
        print("  %s:%d  [%s]" % (p["file"], p["line"], p["method"]))
        print("     %s" % p["detail"])
        if verbose:
            print("     > %s" % p["snippet"][:160] if "snippet" in p else "")
        print()
    print("📌 src/main 不得用硬编码常量冒充业务数据 —— "
          "要么查真实持久层，要么如实返回空（不编）。")
    print("   （规则 72 / 80 / 82 / 203：同 key 连查两次若一模一样 = 假数据）")
    return 1


if __name__ == "__main__":
    sys.exit(main())
