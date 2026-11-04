#!/usr/bin/env python3
"""网关路由 ↔ 部署编排 覆盖度巡检（路由到哪，就得部署到哪）。

📌 **背景（2026-11-04 实测）**：
网关 `erp-gateway/src/main/resources/application.yml` 里配了 **22 个** `lb://xxx-service`，
但这些服务在两套编排里**大部分都不存在**：

| 服务 | 模块目录 | maven reactor | 主类 | compose | k8s |
|---|---|---|---|---|---|
| erp-analytics-service | ✅ | ✅ | ✅ | ❌ | ❌ |
| erp-anomaly-detection-service | ✅ | ✅ | ✅ | ❌ | ❌ |
| erp-customer-service | ✅ | ✅ | ✅ | ✅ | ❌ |
| erp-inventory-alert-service | ✅ | ✅ | ✅ | ❌ | ❌ |
| erp-product-recommendation-service | ✅ | ✅ | ✅ | ❌ | ❌ |
| erp-purchase-service | ✅ | ✅ | ✅ | ✅ | ❌ |
| erp-reporting-service | ✅ | ✅ | ✅ | ✅ | ❌ |
| erp-social-commerce-service | ✅ | ✅ | ✅ | ❌ | ❌ |
| erp-supplier-service | ✅ | ✅ | ✅ | ✅ | ❌ |
| erp-tenant-service | ✅ | ✅ | ✅ | ❌ | ❌ |

**这 10 个都是真模块**（目录在、进了 maven reactor、有 `@SpringBootApplication` 主类、
有独立的 `application.yml` 和数据库），**不是死引用**。

🔴 **后果**：
- **k8s 上这 10 个服务的 API 必然 503** ——
  网关 `lb://erp-tenant-service` 要在集群里解析一个 Service，
  而 k8s 清单里**连 Service 对象都没有** → `503 Service Unavailable`
- **compose 上有 5 个也不存在**（analytics / anomaly-detection / inventory-alert /
  product-recommendation / social-commerce / tenant）→ 本地开发环境同样是 503
- 前端 `erp-web/src/api/` 有 **customer / analytics / inventory-alert / purchase /
  social / supplier / tenant** 七个 api 客户端 → 对应页面在本地和 k8s 上都打不开

📌 **为什么一直没被发现**：
单测全绿、`mvn package` 通过、k8s 清单语法合法、`check-port-consistency.py` 也绿
—— 因为**没有任何一道闸门在问「网关路由到的服务是不是真的被部署了」**。

📌 **本脚本的职责**：把「路由 ↔ 部署」这条契约变成可自动检出。

---

## 检查项

1. **网关每个 `lb://xxx` 目标，k8s 里必须有对应的 Service 对象**
   （没有 Service → 负载均衡解析不到 → 503）
2. **网关每个 `lb://xxx` 目标，k8s 里必须有对应的 Deployment**
   （只有 Service 没有 Deployment → Service 没有 endpoints → 503）
3. **网关每个 `lb://xxx` 目标，compose 里必须有对应的 service**
   （本地开发环境同样会 503）
4. **compose / k8s 里不该有网关完全没路由的服务**
   （孤儿服务：部署了但没人调，或者路由名写错了）

用法：python3 scripts/check-gateway-deploy-coverage.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GATEWAY_YML = os.path.join(ROOT, "erp-gateway", "src", "main", "resources", "application.yml")
K8S_DIR = os.path.join(ROOT, "k8s")
COMPOSE = os.path.join(ROOT, "docker-compose.yml")

# 📌 **不要写死清单文件名** ——
# 📌 写死的清单必然漂移（新加一个 yaml 就忘了加进列表 → 漏检）。
# 📌 这里扫**整个 k8s/ 目录**，跟 `k8s-image-matrix.py` 的口径保持一致。
# 📌 （这正是本项目反复踩的坑：手写清单 ↔ 现实脱节）


def gateway_targets():
    """网关 `lb://xxx` 的目标服务名集合。"""
    if not os.path.exists(GATEWAY_YML):
        return set()
    s = open(GATEWAY_YML, encoding="utf-8").read()
    # 去掉注释行，避免把注释里提到的名字算进来
    s = "\n".join(l for l in s.splitlines() if not l.strip().startswith("#"))
    return set(re.findall(r"lb://([A-Za-z0-9_.-]+)", s))


def k8s_objects():
    """k8s 清单里的 (Deployment 名集合, Service 名集合, image→module 映射)。"""
    deps, svcs, images = set(), set(), {}
    for fn in sorted(os.listdir(K8S_DIR)) if os.path.isdir(K8S_DIR) else []:
        if not fn.endswith((".yaml", ".yml")):
            continue
        path = os.path.join(K8S_DIR, fn)
        for doc in yaml.safe_load_all(open(path, encoding="utf-8")):
            if not isinstance(doc, dict):
                continue
            name = (doc.get("metadata") or {}).get("name")
            kind = doc.get("kind")
            if kind == "Deployment" and name:
                deps.add(name)
                for c in (doc.get("spec", {}).get("template", {}).get("spec", {})
                          .get("containers", [])):
                    img = c.get("image")
                    if img:
                        images[name] = img
            elif kind == "Service" and name:
                svcs.add(name)
    return deps, svcs, images


def compose_modules():
    """compose 里通过 build.args.MODULE 构建的服务 → 模块名集合。

    同时兼容 `image:` 形式的 ERP 服务（少数 compose 变体这么写）。
    """
    mods = set()
    if not os.path.exists(COMPOSE):
        return mods
    try:
        doc = yaml.safe_load(open(COMPOSE, encoding="utf-8")) or {}
    except Exception:
        return mods
    for name, svc in (doc.get("services") or {}).items():
        if not isinstance(svc, dict):
            continue
        build = svc.get("build")
        if isinstance(build, dict):
            args = build.get("args") or {}
            mod = args.get("MODULE")
            if mod:
                mods.add(mod)
                continue
            # build 是字符串时，按「服务名 ≈ 模块名」兜底
            if name.endswith("-service"):
                mods.add(f"erp-{name}")
        img = svc.get("image") or ""
        if img.startswith("nplszfl/erp-"):
            mods.add(img.split(":")[0].split("/")[-1])
    return mods


def main():
    targets = gateway_targets()
    if not targets:
        print("🔴 网关里没读到任何 lb:// 目标 —— 网关配置路径变了？")
        return 1

    deps, svcs, images = k8s_objects()
    comp = compose_modules()

    problems = []
    notes = []

    print(f"═══ 网关路由到 {len(targets)} 个服务 ═══")

    for t in sorted(targets):
        in_svc = t in svcs
        in_dep = t in deps
        in_compose = t in comp
        # 模块目录是否存在（区分「死路由」和「真模块没部署」）
        module_dir = os.path.isdir(os.path.join(ROOT, t))

        flags = []
        if not in_svc:
            flags.append("k8s 无 Service")
        if not in_dep:
            flags.append("k8s 无 Deployment")
        if not in_compose:
            flags.append("compose 无 service")

        if flags:
            kind = "真模块（目录在）" if module_dir else "目录都不存在（疑似死路由）"
            problems.append(
                f"{t}\n"
                f"      模块目录：{'存在' if module_dir else '不存在'} → {kind}\n"
                f"      缺失：{', '.join(flags)}\n"
                f"      → 网关 lb://{t} 在这些环境里必然 503")
        else:
            print(f"   ✅ {t:36s} k8s(Deployment+Service) + compose 都有")

    # 孤儿：compose / k8s 里有、但网关完全没路由的 ERP 服务
    # 📌 erp-gateway 自己除外 —— 它是入口，网关不需要（也不应该）路由到自己
    orphan_k8s = {d for d in deps
                  if d.startswith("erp-") and d not in targets
                  and d not in ("erp-gateway", "erp-gateway-service")}
    orphan_compose = {m for m in comp if m.startswith("erp-") and m not in targets
                      and m not in ("erp-gateway", "erp-gateway-service")}
    for o in sorted(orphan_k8s):
        notes.append(f"k8s 部署了 {o}，但网关没有任何 lb:// 路由指向它")
    for o in sorted(orphan_compose):
        notes.append(f"compose 部署了 {o}，但网关没有任何 lb:// 路由指向它")

    if problems:
        print()
        print(f"🔴 发现 {len(problems)} 个「网关路由到、但没部署」的服务：")
        for p in problems:
            print(f"   • {p}")
        print()
        print("💡 修法二选一：")
        print("   A. 把这些服务**真的部署上**（k8s 加 Deployment+Service，compose 加 service）")
        print("   B. 如果确实是死路由 → 从网关配置里**删掉** lb:// 目标")
        print("      （留着 = 每次请求都 503，比直接 404 还难排查）")
    if notes:
        print()
        print("ℹ️  提示（不算错误）：")
        for n in notes:
            print(f"   • {n}")

    if problems:
        print()
        print(f"🔴 合计 {len(problems)} 处部署覆盖缺口")
        return 1

    print()
    print(f"✅ 网关路由的 {len(targets)} 个服务在 k8s 和 compose 里都存在")
    return 0


if __name__ == "__main__":
    sys.exit(main())
