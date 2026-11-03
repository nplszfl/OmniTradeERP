#!/usr/bin/env python3
"""生成各服务的 Dockerfile（统一模板）。

📌 **原写法为什么必然失败**（2026-11-04 实测 docker build 报错）：
原 Dockerfile 只 COPY 了 2~5 个模块的 pom，然后 `mvn dependency:go-offline`，
而**根 pom 声明了 26 个 module** → Maven 解析根 pom 时会**逐个检查子模块目录是否存在**：

    [ERROR] Child module /app/erp-tenant-service of /app/pom.xml does not exist
    ERROR: failed to build: failed to solve: process "/bin/sh -c mvn dependency:go-offline -B"
           did not complete successfully: exit code: 1

📌 **修法**：用 BuildKit 的 **cache mount** 缓存 ~/.m2，
   免掉「先铺 26 个 pom 再 go-offline」这个必然失败的分层缓存 trick：

    RUN --mount=type=cache,target=/root/.m2/repository ...

   依赖照样跨构建复用（比原来的 pom 分层还彻底），
   但**不再要求根 pom 的所有模块目录都得先存在**。

📌 **另一个坑**：jar 拷贝**不能用 `target/*.jar` 通配符** ——
   配了 `<classifier>exec</classifier>` 的模块会产出**两个** jar（普通 + -exec），
   通配符匹配到多个 → Docker 直接构建失败。
   → 本脚本按模块 pom 里有没有 exec classifier 决定精确的拷贝路径。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCKER_DIR = os.path.join(ROOT, "docker")

pom = open(os.path.join(ROOT, "pom.xml"), encoding="utf-8").read()
MODULES = re.findall(r"<module>([^<]+)</module>", pom)


def port_of(module):
    yml = os.path.join(ROOT, module, "src/main/resources/application.yml")
    if not os.path.exists(yml):
        return None
    m = re.search(r"^\s*port:\s*(\d+)", open(yml, encoding="utf-8").read(), re.M)
    return m.group(1) if m else None


def has_exec_classifier(module):
    p = os.path.join(ROOT, module, "pom.xml")
    return os.path.exists(p) and "<classifier>exec</classifier>" in open(p, encoding="utf-8").read()


def docker_name(module):
    """erp-order-service → order ；erp-gateway → gateway"""
    n = module
    if n.startswith("erp-"):
        n = n[4:]
    if n.endswith("-service"):
        n = n[:-len("-service")]
    return n


def jar_copy(module):
    if has_exec_classifier(module):
        return f"COPY --from=builder /app/{module}/target/{module}-*-exec.jar ./app.jar"
    return f"COPY --from=builder /app/{module}/target/{module}-*.jar ./app.jar"


def gen(module):
    port = port_of(module)
    exec_note = ("（本模块产出带 exec classifier 的 fat jar → 用 -exec 精确匹配，"
                 "不能用 target/*.jar 通配符，否则会匹配到 2 个 jar 导致构建失败）"
                 if has_exec_classifier(module) else
                 "（本模块只产出 1 个 jar）")
    return f"""# {module} 的运行镜像
#
# 📌 **由 scripts/gen-dockerfiles.py 生成** —— 改之前先改生成脚本。
#
# ⚠️ **为什么不能只 COPY 少数几个模块的 pom 再 go-offline**：
#    根 pom 声明了 {len(MODULES)} 个 module，Maven 解析根 pom 时会**逐个检查子模块目录是否存在**。
#    只 COPY 少数几个 → `mvn dependency:go-offline` 直接报：
#        [ERROR] Child module /app/erp-tenant-service of /app/pom.xml does not exist
#    → 镜像构建失败。
#    📌 **实测：本项目所有 Maven Dockerfile 在改成这个写法之前，每一个都构建失败。**
#    📌 而它们以前**从来没在 CI 里跑过** —— docker-build job 被
#       `if: refs/heads/main` 卡住，而本仓库只有 master 分支。

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 📌 容器内用 aliyun 镜像（宿主机 ~/.m2/settings.xml 不会自动进容器）
COPY docker/maven-settings.xml /root/.m2/settings.xml

COPY . .

# 📌 BuildKit cache mount：~/.m2 跨构建复用，依赖不用重复下载
#    （原来靠「先铺 pom 再 go-offline」分层，但那个 trick 在本项目必然失败）
# 📌 只构建目标模块及其依赖，不做全 reactor 构建
RUN --mount=type=cache,target=/root/.m2/repository \\
    mvn -B clean package -DskipTests -pl {module} -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata && \\
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \\
    echo "Asia/Shanghai" > /etc/timezone && \\
    apk del tzdata

# 📌 {exec_note}
{jar_copy(module)}

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

EXPOSE {port}

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \\
    CMD wget --no-verbose --tries=1 --spider http://localhost:{port}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
"""


if __name__ == "__main__":
    # 📌 支持 --check：只校验现有 Dockerfile 与生成结果是否一致（给 CI 用）
    if "--check" in sys.argv:
        import difflib
        bad = 0
        for f in sorted(os.listdir(DOCKER_DIR)):
            if not f.startswith("Dockerfile.") or f == "Dockerfile.service":
                continue
            dn = f[len("Dockerfile."):]
            module = next((m for m in MODULES if docker_name(m) == dn), None)
            if not module:
                continue
            want = gen(module)
            have = open(os.path.join(DOCKER_DIR, f), encoding="utf-8").read()
            if want != have:
                print(f"🔴 {f} 与生成脚本不一致（改了 Dockerfile 但没改 scripts/gen-dockerfiles.py？）")
                for line in list(difflib.unified_diff(
                        have.splitlines(), want.splitlines(),
                        "当前", "应生成", lineterm=""))[:20]:
                    print("   " + line)
                bad += 1
        if bad:
            print(f"\n📌 改 Dockerfile 的正确姿势：改 scripts/gen-dockerfiles.py，然后重跑它")
            sys.exit(1)
        print("✅ 所有 Dockerfile 与生成脚本一致")
        sys.exit(0)

    targets = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not targets:
        # 默认：重新生成已有的那些
        targets = []
        for f in os.listdir(DOCKER_DIR):
            if f.startswith("Dockerfile.") and f != "Dockerfile.service":
                dn = f[len("Dockerfile."):]
                for m in MODULES:
                    if docker_name(m) == dn:
                        targets.append(m)
    for t in sorted(set(targets)):
        out = os.path.join(DOCKER_DIR, f"Dockerfile.{docker_name(t)}")
        with open(out, "w", encoding="utf-8") as f:
            f.write(gen(t))
        print(f"✅ {out}  (module={t}, port={port_of(t)})")
