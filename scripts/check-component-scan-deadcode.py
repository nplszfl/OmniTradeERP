#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「组件扫描不到 = 死代码」的闸门 —— 规则 21 的守门人。

📌 **为什么需要这道闸门**：

规则 21 是本项目**最大的隐性坑**：各服务的 `@SpringBootApplication` 都在**自己子包**下，
扫描范围**不包含** `com.crossborder.erp.common` →
→ `erp-common` 里凡是靠组件扫描生效的 bean，**全是死代码**。

⚠️ **它最坏的性质是「静默」**：
类在、编译过、IDE 里一切正常 → **但运行时就是不生效**。
本项目真实发作过：`GlobalExceptionHandler` 从来没有被任何服务注册过
→ 抛出的业务异常**根本没有被拦截** → 前端拿到的是**裸的 500**。

📌 **唯一的正确解法**（已经在用）：
给所有服务用的公共组件，**一律走 `AutoConfiguration.imports`**（不依赖组件扫描），
或在某个 `@AutoConfiguration` 里用 `@Import(X.class)` 显式引入。

📌 **本闸门查什么**：

| 断言 | 内容 |
|---|---|
| **A** | `erp-common` 里带 `@Component` / `@Service` 的类，必须**能被注册**：出现在某个 `AutoConfiguration.imports` 里，**或**被某个 `@Import(X.class)` 引用 |
| **B** | 所有 `AutoConfiguration.imports` 里列出的类，**必须真实存在**（否则自动装配直接报错 → **整个服务起不来**） |

📌 **为什么 A 不能只查 imports**：本项目两种注册方式都在用
——① 直接把类列进 `AutoConfiguration.imports`（它自己是个 `@Configuration`）
② 在某个 `@AutoConfiguration` 里 `@Import(X.class)`（`PerformanceMonitorAspect` / `GlobalExceptionHandler` 都是这种）
→ **只认一种 = 双向失明**（规则 100）。

📌 **为什么 B 也要查**：反向的坑 —— imports 里写了一个**不存在的类名**
→ Spring Boot 自动装配阶段直接 `ClassNotFoundException` → **所有服务都起不来**。
这类错误在**编译期发现不了**（imports 是纯文本资源）。

📌 **反向验证**（规则 40 / 75）：
① 给 `erp-common` 加一个没注册的 `@Component` → A 报红；
② 把 imports 里某个类名改错 → B 报红。

用法：
    python3 scripts/check-component-scan-deadcode.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-component-scan-deadcode.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", ".idea"}
COMMON_MODULE = "erp-common"
IMPORTS_REL = "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

COMPONENT_RE = re.compile(r"@Component\b|@Service\b")
CLASS_RE = re.compile(r"public\s+(?:abstract\s+)?class\s+(\w+)")
IMPORT_RE = re.compile(r"@Import\s*\(([^)]*)\)")
FQCN_RE = re.compile(r"([\w.]+)\.class")


def java_files(module):
    base = os.path.join(REPO_ROOT, module, "src", "main", "java")
    out = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


def fqcn_of(path, module):
    """从文件里的 `package` 声明 + 类名推出全限定类名。

    📌 **不能从路径猜**：包名 ≠ 模块名
    （`erp-common` 的包是 `com.crossborder.erp.common`，
      `erp-order-service` 的是 `com.crossborder.erp.order`）→ 猜出来的全是错的 → 假阳性。
    """
    text = open(path, encoding="utf-8", errors="replace").read()
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    pkg = m.group(1) if m else ""
    cm = re.search(r"public\s+(?:abstract\s+)?class\s+(\w+)", text)
    if not cm:
        return None
    return (pkg + "." + cm.group(1)) if pkg else cm.group(1)


def find_imports_files():
    out = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "META-INF" in dirpath and "spring" in dirpath:
            for fn in filenames:
                if fn.endswith(".imports"):
                    out.append(os.path.join(dirpath, fn))
    return sorted(out)


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []

    common_java = java_files(COMMON_MODULE)
    if not common_java:
        print("❌ 找不到 erp-common 的源文件 —— 模块结构变了？本闸门会变成瞎的")
        return 1

    # ── 收集「已注册」的类 ────────────────────────────────────────
    registered = set()          # 简单类名
    registered_fq = set()        # 全限定名
    imports_files = find_imports_files()

    for path in imports_files:
        rel = os.path.relpath(path, REPO_ROOT)
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            registered_fq.add(line)
            registered.add(line.rsplit(".", 1)[-1])

    # @Import(X.class) 引用的类（简单名 + 全限定名）
    for module in sorted(d for d in os.listdir(REPO_ROOT)
                         if os.path.isdir(os.path.join(REPO_ROOT, d)) and d.startswith("erp-")):
        for path in java_files(module):
            text = open(path, encoding="utf-8").read()
            for m in IMPORT_RE.finditer(text):
                for fq in FQCN_RE.finditer(m.group(1)):
                    full = fq.group(1)
                    if "." in full:
                        registered_fq.add(full)
                    registered.add(full.rsplit(".", 1)[-1])

    # ── A：erp-common 的 @Component 必须能被注册 ──────────────────
    components = []
    for path in common_java:
        text = open(path, encoding="utf-8").read()
        if not COMPONENT_RE.search(text):
            continue
        m = CLASS_RE.search(text)
        if not m:
            continue
        name = m.group(1)
        fq = fqcn_of(path, COMMON_MODULE)
        components.append((name, fq, os.path.relpath(path, REPO_ROOT)))

    for name, fq, rel in components:
        if name in registered or (fq and fq in registered_fq):
            continue
        findings.append(
            f"[A] `{rel}` 的 `{name}` 带了 `@Component`，但**没有任何服务会扫到它**"
            f"（各服务的 `@SpringBootApplication` 只扫自己子包，不含 `com.crossborder.erp.common`）"
            f"→ **运行时是死代码**"
            f"→ 要么列进 `AutoConfiguration.imports`，要么在某个 `@AutoConfiguration` 里 `@Import({name}.class)`（规则 21）"
        )

    # ── B：imports 里列出的类必须真实存在 ────────────────────────
    existing = set()
    for module in sorted(d for d in os.listdir(REPO_ROOT)
                         if os.path.isdir(os.path.join(REPO_ROOT, d)) and d.startswith("erp-")):
        for path in java_files(module):
            fq = fqcn_of(path, module)
            if fq:
                existing.add(fq)

    for path in imports_files:
        rel = os.path.relpath(path, REPO_ROOT)
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line not in existing:
                findings.append(
                    f"[B] `{rel}` 里列了 `{line}`，但**这个类不存在**"
                    f" → Spring Boot 自动装配阶段直接 `ClassNotFoundException`"
                    f" → **所有服务都起不来**（编译期发现不了，imports 是纯文本资源）"
                )

    if findings:
        print("❌ 组件扫描 / 自动装配闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 规则 21：各服务的 `@SpringBootApplication` 只扫自己子包，"
            "**不含 `com.crossborder.erp.common`**。\n"
            "   → erp-common 里的组件**必须**走 `AutoConfiguration.imports` 或 `@Import` 注册，"
            "否则**运行时是死代码**（编译过、看着正常、就是不生效）。"
        )
        return 1

    if verbose:
        print("检查了 %d 个 erp-common 源文件、%d 个 .imports 文件" % (len(common_java), len(imports_files)))
    print("✅ 组件扫描：erp-common 的 %d 个 @Component 全部已注册；%d 个 .imports 条目全部存在（A/B）"
          % (len(components), len(registered_fq)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
