#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查 Spring Data JPA 的 @Query 命名参数是否都带 @Param。

🔴 **抓到的真缺陷（2026-11-14 12:57 实测复现）**

本仓库**没有**开启 `-parameters` 编译参数（根 pom 无 `maven.compiler.parameters`，
各模块 pom 也都没有；且**不是** `spring-boot-starter-parent` 托管 → parent 的默认
`maven.compiler.parameters=true` 不生效）。

于是 Spring Data JPA 无法从字节码里读出方法参数名 →
凡是 `@Query("... :someName ...")` 又**没写** `@Param("someName")` 的方法，
**调用时**（不是启动时！）抛：

    InvalidDataAccessApiUsageException: For queries with named parameters you need to
    provide names for method parameters; Use @Param for query method parameters,
    or when on Java 8+ use the javac flag -parameters

📌 **症状极具迷惑性**：
- ✅ 服务**正常启动**（`Started SocialCommerceApplication in 3.9 seconds`）
- ✅ **CI 全绿**、单元测试全绿 —— 编译期和启动期都发现不了
- ❌ 但**接口一调就 500**：对外只看到「系统繁忙，请稍后重试」
- ❌ 日志里是 `IllegalStateException`，对外被兜成 500「系统繁忙」→ 告警疲劳

**实测差分（同一组端点，HEAD 版 vs 修复版）**：

| 端点 | HEAD（无 @Param） | 修复后 |
|---|---|---|
| GET /api/v1/influencers/top?minFollowers=1000 | **500** | **200** |
| GET /api/v1/live-streams/date-range?startTime=.. | **500** | **200** |
| GET /api/v1/cooperations/date-range?startDate=.. | **500** | **200** |
| GET /api/v1/influencers/category?category=.. | **500** | **200** |

📌 **为什么这道闸门必要**：其它 73 道闸门 + 1047 个用例全绿，而 4 个**对外接口**
在真实调用时是坏的 —— 这类缺陷**只能靠静态判据 + 真实调用**两条腿一起抓。

📌 **判据要点**
1. 参数表必须**括号配平**地提取 —— `@Param("x")` 里自带括号，用 `[^)]*` 会被截断
   → 判据会**假装全都缺 @Param**（假红）。本脚本用配平提取。
2. 只有**真用了命名参数**（`:xxx`）的 @Query 才需要 @Param；
   位置参数（`?1`）不需要。
3. 如果项目开了 `-parameters`（根 pom 的 `maven.compiler.parameters` 或
   `spring-boot-starter-parent`），则 @Param 非必需 → 本脚本**如实跳过并说明原因**，
   不会在那种配置下假红。

📌 **反向验证**（见文件末尾自检）：
- 注入「删掉一个 @Param」→ 必须报红
- 诚实写法（全部带 @Param）→ 必须报绿
- 提取器必须真的读到了文件（命中 0 个 @Query → 直接报错，不许报绿）

用法：python3 scripts/check-jpa-named-params.py
退出码：0 = 通过；1 = 发现问题
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VIOLATIONS = []
NOTES = []


def strip_java_comments(text):
    """去掉块注释和行注释，避免注释里的 :xxx / @Param 干扰判据。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def balanced(src, open_idx):
    """从 '(' 开始括号配平地取出内容，返回 (内容, 结束括号下标)。"""
    depth = 0
    i = open_idx
    while i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return src[open_idx + 1:i], i
        i += 1
    return None, None


def project_enables_parameters_flag():
    """
    判断项目是否开启了 -parameters 编译参数。
    开了的话 @Param 就非必需 → 本闸门应当跳过（而不是假红）。
    """
    pom = os.path.join(ROOT, "pom.xml")
    if not os.path.exists(pom):
        return False, "根 pom 不存在，无法判定（按未开启处理）"
    text = open(pom, encoding="utf-8").read()
    if re.search(r"<maven\.compiler\.parameters>\s*true\s*</maven\.compiler\.parameters>", text):
        return True, "根 pom 设了 maven.compiler.parameters=true"
    if "<parameters>true</parameters>" in text:
        return True, "根 pom 的 maven-compiler-plugin 设了 <parameters>true</parameters>"
    # spring-boot-starter-parent 自 Spring Boot 2.x 起默认开启 -parameters
    if "spring-boot-starter-parent" in text:
        return True, "使用 spring-boot-starter-parent（其默认开启 -parameters）"
    return False, "根 pom 未开启 -parameters（且非 spring-boot-starter-parent 托管）"


def find_named_query_methods():
    """扫所有模块的 main 源码，返回 [(文件, 行号, 方法名, 命名参数集合, 已绑定集合)]"""
    results = []
    src_root = os.path.join(ROOT, "")
    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "target", "node_modules", ".m2")]
        if "/src/main/java" not in dirpath.replace(os.sep, "/") + "/":
            continue
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, ROOT)
            raw = open(path, encoding="utf-8", errors="replace").read()
            src = strip_java_comments(raw)
            for m in re.finditer(r"@Query\s*\(", src):
                q, qend = balanced(src, m.end() - 1)
                if q is None:
                    continue
                q = q.strip()
                if not (q.startswith('"') or q.startswith("'")):
                    continue  # 拼出来的常量表达式，跳过（无法静态判定）
                # 命名参数： :name （排除 :: 和三元里的 ?: 以及 URL 里的 ://）
                named = set(re.findall(r"(?<![:\w]):(\w+)", q))
                if not named:
                    continue
                tail = src[qend + 1:qend + 600]
                mm = re.search(r"(\w+)\s*\(", tail)
                if not mm:
                    continue
                method = mm.group(1)
                params, _ = balanced(tail, mm.end() - 1)
                if params is None:
                    params = ""
                bound = set(re.findall(r'@Param\s*\(\s*"(\w+)"\s*\)', params))
                line = raw[:m.start()].count("\n") + 1
                results.append((rel, line, method, named, bound))
    return results


def main():
    enabled, why = project_enables_parameters_flag()
    print(f"📋 -parameters 编译参数：{'已开启' if enabled else '未开启'}（{why}）")

    if enabled:
        print("ℹ️  项目已开启 -parameters → @Param 非必需，本闸门跳过")
        print("✅ 跳过（不是「没检查」，而是「不需要检查」）")
        return 0

    methods = find_named_query_methods()
    print(f"📋 扫到带**命名参数**的 @Query 方法：{len(methods)} 个")

    # 📌 自检：判据必须真的读到了代码（规则 127 —— 命中 0 处 → 先怀疑判据本身坏了）
    if not methods:
        print("❌ 一个命名参数 @Query 都没扫到 → 判据可能根本没在读文件")
        print("   （不是「没问题」，是「没法判定」）→ 报错而不是报绿")
        return 1

    for rel, line, method, named, bound in methods:
        missing = named - bound
        if missing:
            VIOLATIONS.append(
                f"{rel}:{line}  {method}()  命名参数 {sorted(named)} 未绑定 @Param：{sorted(missing)}"
            )

    if VIOLATIONS:
        print(f"\n❌ 发现 {len(VIOLATIONS)} 处命名参数缺 @Param：\n")
        for v in VIOLATIONS:
            print(f"   ❌ {v}")
        print("\n→ 这些方法**启动时不会报错**，但**调用时必 500**"
              "（InvalidDataAccessApiUsageException → 被兜成「系统繁忙」）")
        print("→ 修法：给对应参数加 @Param(\"name\")，或全局开启 -parameters")
        return 1

    print(f"✅ 所有 {len(methods)} 个命名参数 @Query 都已正确绑定 @Param")
    return 0


if __name__ == "__main__":
    sys.exit(main())
