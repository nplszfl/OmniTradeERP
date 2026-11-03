#!/usr/bin/env python3
"""k8s / compose 引用的镜像 ↔ 仓库里能产出的镜像 一致性巡检。

📌 **为什么需要这道闸门**（2026-11-04 实测）：
CI 的 `docker-build` job 只构建 **3 个镜像**（gateway / order / frontend），
但 `k8s/*.yaml` 里引用了 **15 个 ERP 镜像**：

    nplszfl/erp-finance-service:latest
    nplszfl/erp-inventory-service:latest
    nplszfl/erp-logistics-service:latest
    nplszfl/erp-platform-service:latest
    nplszfl/erp-product-service:latest
    nplszfl/erp-warehouse-service:latest
    ...

📌 **后果**：一旦真的配上 KUBE_CONFIG 开始部署，
这 12 个镜像**从来没被构建过、也从来没被推送过** → `ImagePullBackOff`
→ Pod 起不来 → 部署「成功」了但服务全挂。

📌 **这类 bug 的特征**：
- 单测全绿、mvn package 全绿、CI 全绿
- **只有真的 pull 镜像时才炸**
- 📌 和「端口漂移」「DDL 没挂载」是同一类：**编排层和代码层各写各的**。

---

## 检查项

1. **k8s/compose 引用的每个 ERP 镜像** → 必须能对应到一个**真实的 Maven 模块**
   （根 pom 的 `<module>` 列表里，且该模块有 application.yml）
2. **每个镜像必须有构建配方** ——
   要么有专属 `docker/Dockerfile.<name>`，要么能用通用的 `docker/Dockerfile.service`
   （它用 `MODULE`/`PORT` build-arg 覆盖任意模块）
3. **反向检查**：仓库里有专属 Dockerfile、但 k8s/compose 里从没引用 → 只是提示（不算错）
4. **命名规范**：镜像名必须严格等于 `nplszfl/<模块名>` ——
   写错成 `erp-order` / `erp-orders-service` → pull 时才发现不存在

用法：python3 scripts/check-k8s-images.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCKER_DIR = os.path.join(ROOT, "docker")
K8S_DIR = os.path.join(ROOT, "k8s")

# 📌 本项目镜像的命名空间
IMAGE_PREFIX = "nplszfl/"

# 📌 第三方镜像（不是本项目产的），不校验
THIRD_PARTY = (
    "mysql:", "redis:", "nacos/", "postgres:", "nginx:", "busybox:",
    "mongo:", "kafka", "zookeeper", "consul", "prometheus", "grafana",
)


def load_modules():
    """根 pom 声明的模块列表"""
    pom = open(os.path.join(ROOT, "pom.xml"), encoding="utf-8").read()
    return set(re.findall(r"<module>([^<]+)</module>", pom))


def is_service(module):
    """模块是不是一个真正的微服务（有 application.yml → 有 server.port）"""
    yml = os.path.join(ROOT, module, "src/main/resources/application.yml")
    if not os.path.exists(yml):
        return False
    return bool(re.search(r"^\s*port:\s*\d+", open(yml, encoding="utf-8").read(), re.M))


def docker_short_name(module):
    """erp-order-service → order ；erp-gateway → gateway"""
    n = module
    if n.startswith("erp-"):
        n = n[4:]
    if n.endswith("-service"):
        n = n[:-len("-service")]
    return n


def dedicated_dockerfiles(modules):
    """模块 → 专属 Dockerfile 文件名

    📌 键必须是**模块名**（erp-order-service），不是短名（order）——
       否则拿模块名去查会全部 miss → 专属 Dockerfile 会被误判成「不存在」。
    """
    out = {}
    if not os.path.isdir(DOCKER_DIR):
        return out
    existing = {f for f in os.listdir(DOCKER_DIR) if f.startswith("Dockerfile.")}
    for m in modules:
        fname = f"Dockerfile.{docker_short_name(m)}"
        if fname in existing:
            out[m] = fname
    return out


def generic_dockerfile_exists():
    p = os.path.join(DOCKER_DIR, "Dockerfile.service")
    return os.path.exists(p)


def collect_images():
    """从 k8s/*.yaml 和 docker-compose*.yml 里收集引用的镜像"""
    found = []   # (来源文件, 镜像名)

    if os.path.isdir(K8S_DIR):
        for f in sorted(os.listdir(K8S_DIR)):
            if not f.endswith((".yaml", ".yml")):
                continue
            p = os.path.join(K8S_DIR, f)
            for m in re.finditer(r"^\s*image:\s*[\"']?([^\s\"'#]+)", open(p, encoding="utf-8").read(), re.M):
                found.append((f"k8s/{f}", m.group(1)))

    for f in sorted(os.listdir(ROOT)):
        if not (f.startswith("docker-compose") and f.endswith((".yml", ".yaml"))):
            continue
        p = os.path.join(ROOT, f)
        for m in re.finditer(r"^\s*image:\s*[\"']?([^\s\"'#]+)", open(p, encoding="utf-8").read(), re.M):
            found.append((f, m.group(1)))

    return found


def main():
    modules = load_modules()
    dedicated = dedicated_dockerfiles(modules)
    generic_ok = generic_dockerfile_exists()

    if not os.path.isdir(K8S_DIR):
        print(f"❌ 找不到 {K8S_DIR}")
        return 1

    images = collect_images()
    errors = []
    infos = []
    checked = set()

    for src, img in images:
        if not img.startswith(IMAGE_PREFIX):
            continue
        if img.startswith(THIRD_PARTY):
            continue
        # 去掉 tag / digest
        name = img.split("@")[0].split(":")[0]
        module = name[len(IMAGE_PREFIX):]
        checked.add(module)

        # 1) 模块必须真实存在
        if module not in modules:
            errors.append(
                f"[{src}] 镜像 `{name}` → 模块 `{module}` **在根 pom 里不存在**\n"
                f"      → 这个镜像**根本构建不出来** → 部署时 ImagePullBackOff"
            )
            continue

        # 2) 模块必须是个真服务（有 application.yml → 有端口）
        if not is_service(module):
            errors.append(
                f"[{src}] 镜像 `{name}` → 模块 `{module}` **没有 application.yml / 没有端口**\n"
                f"      → 它不是一个可运行的服务，k8s 里不该引用它"
            )
            continue

        # 3) 必须有构建配方
        has_dedicated = module in dedicated
        if not has_dedicated and not generic_ok:
            errors.append(
                f"[{src}] 镜像 `{name}` → 既没有专属 Dockerfile，"
                f"也没有通用的 docker/Dockerfile.service\n"
                f"      → **没有任何东西能构建出这个镜像**"
            )
        elif not has_dedicated:
            infos.append(
                f"[{src}] `{name}` 没有专属 Dockerfile，"
                f"依赖通用 docker/Dockerfile.service（MODULE={module}）"
            )

    # 反向：专属 Dockerfile 但从没被引用
    for module, fname in sorted(dedicated.items()):
        if module not in checked:
            infos.append(f"docker/{fname} 产出的镜像在 k8s/compose 里没有引用")

    print(f"📋 校验 k8s/compose 引用的 ERP 镜像 ↔ 仓库可产出的镜像\n")
    print(f"   根 pom 模块 {len(modules)} 个 | 专属 Dockerfile {len(dedicated)} 个 | "
          f"通用 Dockerfile.service {'有' if generic_ok else '无'}")

    if infos:
        print()
        for i in infos:
            print(f"ℹ️  {i}")

    if errors:
        print()
        print(f"🔴 发现 {len(errors)} 处「部署时会 ImagePullBackOff」的问题：\n")
        for e in errors:
            print(f"   {e}")
        print()
        print("📌 **这类问题单测查不出来** —— 镜像要真的被 pull 才会炸")
        print("   → CI 的 docker-build job 只构建了少数几个镜像，")
        print("     其余的**从来没被构建过，也从来没被推送过**")
        return 1

    print()
    print(f"✅ k8s/compose 引用的 {len(checked)} 个 ERP 镜像全部可构建")
    return 0


if __name__ == "__main__":
    sys.exit(main())
