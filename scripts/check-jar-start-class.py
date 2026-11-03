#!/usr/bin/env python3
"""产物级可启动性巡检：打出来的 jar 里，Start-Class 必须真的躺在包内。

背景（2026-11-03 实测）：
  `erp-assembly` 的 pom 配了 `<mainClass>com.crossborder.erp.ErpApplication</mainClass>`，
  但这个类**不存在** → `mvn package` **照样 BUILD SUCCESS**，
  产出 99MB fat jar → 真跑起来才 `ClassNotFoundException`。

  📌 `scripts/check-boot-main-class.py` 查的是**源码层**（类文件在不在 src/main/java）。
  本脚本查的是**产物层**（类在不在 jar 里）——
  **两者都过，才说明「打包出来的东西能跑」**。

为什么源码层过了产物层还可能不过：
1. **jar 是旧的** —— target/ 里躺着一个上次构建的产物，
   源码后来改了（主类被删/改名），但没重新 package → 巡检源码时是绿的，部署时炸
2. **repackage 没把主类放进包** —— 插件配置异常时可能出现
3. **多模块依赖传递** —— 主类来自依赖 jar，而不是本模块源码

检查项（对 target/ 下**已构建**的 jar）：
1. manifest 里的 `Start-Class`（或 `Main-Class`）必须能在 jar 内找到对应 .class 条目
2. 找不到 jar 的模块跳过（还没构建，不算失败）

用法：python3 scripts/check-jar-start-class.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 需要跳过的目录
SKIP_DIRS = {"target", "node_modules", ".git", "erp-web", "docs", "scripts"}


def service_modules():
    """可能产出可执行 jar 的模块目录。"""
    out = []
    for d in sorted(os.listdir(ROOT)):
        p = os.path.join(ROOT, d)
        if not os.path.isdir(p) or d in SKIP_DIRS:
            continue
        if not os.path.exists(os.path.join(p, "pom.xml")):
            continue
        pom = open(os.path.join(p, "pom.xml"), encoding="utf-8").read()
        if "spring-boot-maven-plugin" in pom:
            out.append(d)
    return out


def find_jars(module_dir):
    """模块 target/ 下可执行的 jar（优先带 .original 备份的那种 fat jar）。"""
    target = os.path.join(module_dir, "target")
    if not os.path.isdir(target):
        return []
    jars = []
    for fn in sorted(os.listdir(target)):
        if not fn.endswith(".jar"):
            continue
        # .jar.original 是 repackage 前的备份，不是可执行产物
        if fn.endswith(".jar.original"):
            continue
        jars.append(os.path.join(target, fn))
    return jars


def manifest_of(jar_path):
    """读 manifest，返回 (Start-Class, Main-Class)。"""
    try:
        with zipfile.ZipFile(jar_path) as z:
            with z.open("META-INF/MANIFEST.MF") as f:
                text = f.read().decode("utf-8", errors="ignore")
    except (KeyError, zipfile.BadZipFile, OSError):
        return None, None
    # manifest 的长行可能有折行（行首一个空格 = 续行）
    text = re.sub(r"\r?\n ", "", text)
    start = re.search(r"^Start-Class:\s*(\S+)", text, re.M)
    main = re.search(r"^Main-Class:\s*(\S+)", text, re.M)
    return (start.group(1) if start else None,
            main.group(1) if main else None)


def class_in_jar(jar_path, fqn):
    """这个 FQN 的 .class 是否在 jar 内（BOOT-INF/classes 或依赖 jar 里都算）。"""
    entry = fqn.replace(".", "/") + ".class"
    try:
        with zipfile.ZipFile(jar_path) as z:
            names = set(z.namelist())
            if entry in names:
                return True
            if "BOOT-INF/classes/" + entry in names:
                return True
            # 主类可能来自依赖 jar（BOOT-INF/lib/xxx.jar）
            for n in names:
                if n.startswith("BOOT-INF/lib/") and n.endswith(".jar"):
                    try:
                        with z.open(n) as nested:
                            data = nested.read()
                        import io
                        with zipfile.ZipFile(io.BytesIO(data)) as nz:
                            if entry in nz.namelist():
                                return True
                    except (zipfile.BadZipFile, OSError, KeyError):
                        continue
    except (OSError, zipfile.BadZipFile):
        return False
    return False


def uses_exec_classifier(module):
    """这个模块的 pom 是否配了 <classifier>exec</classifier>。

    📌 **为什么要关心**：配了 classifier=exec 的模块会产出**两个** jar：
         xxx-<version>.jar          ← 普通 jar（供别的模块当依赖编译）
         xxx-<version>-exec.jar     ← fat jar（供部署）
       → 任何 `COPY .../target/*.jar dest.jar` 的 Dockerfile 会**匹配到 2 个文件**
       → Docker 直接报错：
         "When using COPY with more than one source file,
          the destination must be a directory and end with a /"
       → **镜像构建失败**。
    📌 这是**静态可判定**的（看 pom），不依赖 target/ 里有没有残留旧 jar。
    """
    pom = os.path.join(ROOT, module, "pom.xml")
    if not os.path.exists(pom):
        return False
    text = open(pom, encoding="utf-8").read()
    return "<classifier>exec</classifier>" in text


def check_dockerfile_globs():
    """检查 Dockerfile 的 jar 通配符：会不会匹配到多个文件。

    📌 **判据**：`COPY .../target/*.jar` 这种裸通配 →
       模块一旦配了 classifier=exec（产出 2 个 jar）→ Docker 构建直接失败。
    📌 正确写法：显式指定那个唯一的 fat jar。
    """
    errors = []
    docker_dir = os.path.join(ROOT, "docker")
    if not os.path.isdir(docker_dir):
        return errors
    for fn in sorted(os.listdir(docker_dir)):
        if not fn.startswith("Dockerfile"):
            continue
        path = os.path.join(docker_dir, fn)
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r"COPY\s+(?:--from=\S+\s+)?(\S*/target/\*\.jar)\s+", text):
            pattern = m.group(1)
            # 从路径里抽出模块名：/app/<module>/target/*.jar
            mm = re.search(r"/app/([^/]+)/target/", pattern)
            if not mm:
                continue
            module = mm.group(1)
            if uses_exec_classifier(module):
                errors.append(
                    f"[docker/{fn}] COPY {pattern} —— 模块 {module} 配了 "
                    f"<classifier>exec</classifier>，会产出 **2 个** jar\n"
                    f"      → 通配符匹配到多个文件 → Docker 构建直接失败\n"
                    f"      → 应改成显式指定 fat jar（参考 Dockerfile.order 用 *-exec.jar）"
                )
    return errors


def reactor_modules():
    """父 pom 里真正启用的模块（注释掉的不算）。"""
    pom = os.path.join(ROOT, "pom.xml")
    if not os.path.exists(pom):
        return []
    text = open(pom, encoding="utf-8").read()
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    return re.findall(r"<module>\s*([^<\s]+)\s*</module>", text)


def main():
    modules = service_modules()
    reactor = set(reactor_modules())
    errors = []
    checked = 0
    skipped = []
    not_deployed = []

    for mod in modules:
        jars = find_jars(os.path.join(ROOT, mod))
        if not jars:
            skipped.append(mod)
            continue
        for jar in jars:
            start_class, main_class = manifest_of(jar)
            if start_class is None:
                # 不是 boot fat jar（普通库 jar）→ 跳过
                continue
            checked += 1
            if not class_in_jar(jar, start_class):
                msg = (f"[{mod}] jar `{os.path.basename(jar)}` 的 "
                       f"Start-Class={start_class} **不在包内**\n"
                       f"      → 这个 jar 跑起来会 ClassNotFoundException"
                       f"（package 却成功了）")
                # 📌 **跟源码层巡检保持一致**：
                #    不在 reactor 里的模块**不会被部署** → 没有运行期风险
                #    → 只提示，不计失败。
                #    📌 例：erp-assembly 配了个不存在的 mainClass，
                #       单独 build 它确实会产出坏 jar —— 但没人部署它。
                if mod in reactor:
                    errors.append(msg)
                else:
                    not_deployed.append(msg)

    if skipped:
        print(f"ℹ️  {len(skipped)} 个模块**还没构建**（target/ 下无 jar，跳过）："
              f"{', '.join(skipped)}")
        print()

    if not_deployed:
        print(f"ℹ️  {len(not_deployed)} 个**未启用模块**的产物有问题"
              f"（不在 reactor 里，不会被部署，故不计入失败）：")
        for e in not_deployed:
            print(f"   {e}")
        print()

    # 📌 Dockerfile 通配符问题**单独一段**打印，
    #    不要再塞进 errors 里重复打一遍（我第一版重复了，反向验证时才发现）。
    glob_errors = check_dockerfile_globs()
    if glob_errors:
        print(f"🔴 {len(glob_errors)} 处 Dockerfile 的 jar 通配符会匹配到多个文件：")
        for e in glob_errors:
            print(f"   {e}")
        print()

    if errors or glob_errors:
        if errors:
            print(f"🔴 {len(errors)} 个**产物**跑不起来（Start-Class 不在 jar 内）：")
            for e in errors:
                print(f"   {e}")
            print()
        print("提示：显式 <mainClass> 时 spring-boot-maven-plugin 不校验类是否存在，")
        print("      package 会成功 —— 源码层绿灯不代表产物能跑。")
        return 1

    print(f"✅ 所有已构建 jar 的 Start-Class 都在包内"
          f"（检查了 {checked} 个 jar，覆盖 {len(modules)} 个模块）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
