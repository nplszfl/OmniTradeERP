#!/usr/bin/env python3
"""可启动性巡检：每个会打包成可执行 jar 的模块，主类必须真的存在。

背景（2026-11-03 实测踩出来的坑）：

  erp-assembly 的 pom 写着
      <mainClass>com.crossborder.erp.ErpApplication</mainClass>
  但这个类**根本不存在**（模块里 0 个 java 文件）。

  📌 **mvn package 照样 BUILD SUCCESS！**
     → 产出一个 99MB 的 fat jar，manifest 里有
        Start-Class: com.crossborder.erp.ErpApplication
     → **真跑起来才炸**：java.lang.ClassNotFoundException:
        com.crossborder.erp.ErpApplication

  📌 所以「package 成功」**不等于**「模块可用」。
     以前 MEMORY 里写的是「缺主类会卡在 mvn package」——
     那是 **自动探测**主类时的行为；**显式写了 <mainClass>** 时插件**不校验**，
     照样打包成功，直到运行期才炸。

本脚本检查（**只针对 reactor 里的模块**，即真正会部署的那些）：
1. 显式配置了 <mainClass> → 该类文件必须存在
2. 没配置 <mainClass>（靠插件自动探测）→ 必须**恰好有一个** @SpringBootApplication
   - 0 个 → 插件找不到主类，package 直接失败
   - 多个 → 插件随便挑一个，可能挑错
3. 被注释掉的模块（不在 reactor 里）→ 只做提示，不算失败
   （它们不会被部署，没有运行期风险）

用法：python3 scripts/check-boot-main-class.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def reactor_modules():
    """父 pom 里真正启用的模块（注释掉的不算）。"""
    pom = os.path.join(ROOT, "pom.xml")
    if not os.path.exists(pom):
        return []
    text = open(pom, encoding="utf-8").read()
    # 先把 XML 注释整段去掉，再找 <module>
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    return re.findall(r"<module>\s*([^<\s]+)\s*</module>", text)


def has_boot_plugin(module_dir):
    """这个模块的 pom 里有没有配 spring-boot-maven-plugin。"""
    pom = os.path.join(module_dir, "pom.xml")
    if not os.path.exists(pom):
        return False
    text = open(pom, encoding="utf-8").read()
    return "spring-boot-maven-plugin" in text


def declared_main_class(module_dir):
    """pom 里显式配置的 <mainClass>（没有则返回 None）。"""
    pom = os.path.join(module_dir, "pom.xml")
    text = open(pom, encoding="utf-8").read()
    # 只看 build/plugins 段里的（父 pom 里可能给所有模块配了默认主类）
    m = re.search(r"<mainClass>\s*([^<]+?)\s*</mainClass>", text)
    return m.group(1).strip() if m else None


def source_root(module_dir):
    for cand in ("src/main/java",):
        p = os.path.join(module_dir, cand)
        if os.path.isdir(p):
            return p
    return None


def class_file_exists(module_dir, fqn):
    """这个全限定类的 .java 文件是否存在（按包路径找）。"""
    src = source_root(module_dir)
    if not src:
        return False
    rel = fqn.replace(".", os.sep) + ".java"
    return os.path.exists(os.path.join(src, rel))


def strip_comments(text):
    """去掉 Java 注释（行注释 + 块注释）。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def spring_boot_apps(module_dir):
    """模块里所有 @SpringBootApplication 标注的类（全限定名）。"""
    src = source_root(module_dir)
    if not src:
        return []
    out = []
    for dirpath, _dirs, files in os.walk(src):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            text = open(path, encoding="utf-8", errors="ignore").read()
            # 📌 **必须先剥掉注释** —— 否则「把注解注释掉」这种写法
            #    （// @SpringBootApplication）仍然会被当成有主类
            #    → 漏报。我做反向验证时就是这么被自己的脚本骗过去的。
            text = strip_comments(text)
            pm = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
            if not pm:
                continue
            # 📌 **要匹配全限定写法** `@org.springframework.boot.autoconfigure.
            #    SpringBootApplication` —— 只认简写 `@SpringBootApplication`
            #    会漏（我做反向验证时又踩了一次）。
            # 📌 **每个注解都要单独数** —— 一个文件里两个不同的 App 类，
            #    只取「第一个 class」会漏掉第二个。
            for am in re.finditer(
                    r"@(?:\w+\.)*SpringBootApplication\b", text):
                tail = text[am.end():]
                cm = re.search(r"\bclass\s+(\w+)", tail)
                if cm:
                    out.append(f"{pm.group(1)}.{cm.group(1)}")
    return out


def main():
    modules = reactor_modules()
    errors = []
    skipped = []

    for mod in modules:
        mod_dir = os.path.join(ROOT, mod)
        if not os.path.isdir(mod_dir):
            continue
        if not has_boot_plugin(mod_dir):
            continue

        explicit = declared_main_class(mod_dir)
        apps = spring_boot_apps(mod_dir)

        if explicit:
            if not class_file_exists(mod_dir, explicit):
                errors.append(
                    f"[{mod}] pom 配了 <mainClass>{explicit}</mainClass>，"
                    f"但**这个类不存在**\n"
                    f"      → mvn package 照样成功（插件不校验显式主类），"
                    f"但真跑起来会 ClassNotFoundException"
                )
            elif apps and explicit not in apps:
                errors.append(
                    f"[{mod}] pom 配的 <mainClass>{explicit}</mainClass> "
                    f"不是 @SpringBootApplication 类（实际有：{', '.join(apps)}）"
                )
        else:
            if len(apps) == 0:
                errors.append(
                    f"[{mod}] 没有 @SpringBootApplication 类，且 pom 没配 <mainClass>\n"
                    f"      → 插件自动探测不到主类，mvn package 会直接失败"
                )
            elif len(apps) > 1:
                errors.append(
                    f"[{mod}] 有 {len(apps)} 个 @SpringBootApplication："
                    f"{', '.join(apps)}\n"
                    f"      → 插件只能挑一个，可能挑错"
                )

    # 未启用的模块（注释掉的）—— 只是提示
    all_mods = set()
    for d in os.listdir(ROOT):
        p = os.path.join(ROOT, d, "pom.xml")
        if os.path.exists(p) and d not in modules:
            text = open(p, encoding="utf-8").read()
            if "spring-boot-maven-plugin" in text:
                all_mods.add(d)
    for mod in sorted(all_mods):
        mod_dir = os.path.join(ROOT, mod)
        explicit = declared_main_class(mod_dir)
        apps = spring_boot_apps(mod_dir)
        state = []
        if explicit:
            state.append(f"配了 mainClass={explicit}"
                         + ("（类不存在）" if not class_file_exists(mod_dir, explicit)
                            else ""))
        state.append(f"{len(apps)} 个 @SpringBootApplication")
        skipped.append(f"[{mod}] {'，'.join(state)}")

    if skipped:
        print(f"ℹ️  {len(skipped)} 个模块**未启用**（不在 reactor 里，不会被部署，"
              f"故不计入失败）：")
        for s in skipped:
            print(f"   {s}")
        print()

    if errors:
        print(f"🔴 {len(errors)} 个模块**打包出来跑不起来**（主类问题）：")
        for e in errors:
            print(f"   {e}")
        print()
        print("提示：显式 <mainClass> 时 spring-boot-maven-plugin **不会校验**类是否存在，")
        print("      package 会成功 —— 必须真起一次 jar 才能发现。")
        return 1

    print(f"✅ 所有会打 jar 的模块主类都存在"
          f"（检查了 {len(modules)} 个 reactor 模块）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
