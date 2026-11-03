#!/usr/bin/env python3
"""从 k8s 清单生成「部署时要更新哪些 Deployment / 哪个 container」的部署计划。

📌 **为什么需要它**（2026-11-04 实测）：
CI 的 `deploy-k8s` job 里手写着三条 `kubectl set image`：

    kubectl set image deployment/erp-gateway    erp-gateway=nplszfl/erp-gateway:$SHA
    kubectl set image deployment/erp-order-service  erp-order-service=...
    kubectl set image deployment/erp-frontend   erp-frontend=...

**三条全是错的**：

1. **`erp-frontend` 这个 Deployment 在 k8s 里根本不存在**
   → `kubectl set image` 立刻报 `deployments.apps "erp-frontend" not found`
   → 整个部署步骤失败 → **部署从来没做成过**
   （这个 job 一直被 `KUBE_CONFIG` secret 挡着从没真跑过，所以没人发现）

2. **container 名写错了** —— k8s 里 Deployment 名和 container 名**不一样**：
       Deployment `erp-gateway`      → container `gateway`
       Deployment `erp-user-service` → container `user-service`
   → `kubectl set image deployment/erp-gateway erp-gateway=...`
   → 报 `error: container erp-gateway not found in deployment/erp-gateway`

3. **只覆盖了 3 个，k8s 里其实有 13 个 ERP Deployment**
   → 另外 10 个（finance / inventory / warehouse / product / user / platform /
     pricing / ai-assistant / product-description / inventory-prediction / logistics）
     **永远跑旧镜像**

📌 **和构建侧同一个道理**：
`scripts/k8s-image-matrix.py` 已经把「构建哪些镜像」改成**从 k8s 清单反推**了，
部署侧却还是手写清单 → 两边会漂移（构建了不部署 / 部署了没构建）。
✅ 本脚本用**同一个事实来源**（k8s/*.yaml）生成部署计划，
   构建矩阵和部署计划**由同一份清单推导**，结构上不可能漂移。

用法：
    python3 scripts/k8s-deploy-plan.py            # 打印 JSON
    python3 scripts/k8s-deploy-plan.py --check    # 只校验，不输出
    python3 scripts/k8s-deploy-plan.py --tsv      # 打印 TSV（deployment\tcontainer\timage）
    python3 scripts/k8s-deploy-plan.py --shell    # 打印 kubectl set image 命令
"""
import json
import os
import re
import sys

try:
    import yaml
except ImportError:  # pragma: no cover
    print("❌ 需要 PyYAML：pip install pyyaml")
    sys.exit(2)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S_DIR = os.path.join(ROOT, "k8s")

IMAGE_PREFIX = "nplszfl/"


def _iter_docs(path):
    """逐个 YAML 文档（支持多文档文件）。"""
    with open(path, encoding="utf-8") as f:
        for doc in yaml.safe_load_all(f):
            if isinstance(doc, dict):
                yield doc


def collect():
    """返回 [(deployment, container, image, source_file)] —— 只含本项目自己的镜像。

    📌 过滤掉 nacos / mysql / redis 这些**基础设施镜像**：
    它们不是本项目构建的，也不该被 CI 用 set image 更新版本。
    """
    out = []
    for fn in sorted(os.listdir(K8S_DIR)):
        if not fn.endswith((".yaml", ".yml")):
            continue
        for doc in _iter_docs(os.path.join(K8S_DIR, fn)):
            if doc.get("kind") != "Deployment":
                continue
            meta = doc.get("metadata") or {}
            name = meta.get("name")
            if not name:
                continue
            spec = doc.get("spec") or {}
            tmpl = (spec.get("template") or {}).get("spec") or {}
            for c in tmpl.get("containers") or []:
                image = (c or {}).get("image")
                if not image:
                    continue
                # 去掉 tag / digest，只要仓库名
                repo = image.split("@")[0].split(":")[0]
                if not repo.startswith(IMAGE_PREFIX):
                    continue      # nacos / mysql / redis 等基础设施镜像
                out.append((name, (c or {}).get("name") or name, repo, fn))
    # 同一 Deployment 可能多容器，按 (deploy, container) 去重
    seen = set()
    uniq = []
    for item in out:
        key = (item[0], item[1])
        if key in seen:
            continue
        seen.add(key)
        uniq.append(item)
    return uniq


def module_of(image_repo):
    return image_repo[len(IMAGE_PREFIX):]


def matrix_images():
    """构建侧（k8s-image-matrix.py）会构建哪些镜像。

    📌 **为什么要专门比一次**：
    构建侧 `k8s-image-matrix.py` 用**正则**扫 `^\s*image:`，
    部署侧本脚本用 **YAML 解析**只看 Deployment 的 containers ——
    **两套完全不同的解析方法**，所以**结构上就可能漂移**：
    - 正则会扫到 Deployment 之外的 image（initContainer、别的 kind、注释里的）
    - YAML 解析会漏掉正则抓到的行（比如多行 YAML 的折叠写法）
    → 表现就是「构建了但不部署」/「部署了但没构建」
    📌 这个漂移**光靠两个脚本各自自洽是发现不了的** —— 必须**互相比一次**。
    """
    import importlib.util
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "k8s-image-matrix.py")
    spec = importlib.util.spec_from_file_location("k8s_image_matrix", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return set(mod.collect())


def main():
    check_only = "--check" in sys.argv
    shell = "--shell" in sys.argv
    tsv = "--tsv" in sys.argv

    rows = collect()
    problems = []

    for deploy, container, image, src in rows:
        if not container:
            problems.append(f"{src}: Deployment `{deploy}` 的 container 没有 name")
        # Deployment 名和镜像仓库名对不上是很常见的（不是错），但要能对上模块
        mod = module_of(image)
        if not os.path.isdir(os.path.join(ROOT, mod)):
            problems.append(
                f"{src}: 镜像 `{image}` → 模块 `{mod}` 目录不存在"
                f"（k8s 引用了一个本地没有的模块）")

    # 📌 **构建侧 ↔ 部署侧必须一致**（详见 matrix_images() 的注释）
    plan_images = {i for _, _, i, _ in rows}
    try:
        build_images = matrix_images()
    except Exception as e:
        problems.append(f"无法读取构建矩阵（k8s-image-matrix.py）：{e}")
        build_images = set()

    only_plan = plan_images - build_images
    only_build = build_images - plan_images
    for i in sorted(only_plan):
        problems.append(
            f"镜像 `{i}` **在部署计划里但不在构建矩阵里**\n"
            f"      → CI 不会构建它 → kubectl set image 会把它设成一个不存在的镜像\n"
            f"      → Pod 直接 ImagePullBackOff")
    for i in sorted(only_build):
        problems.append(
            f"镜像 `{i}` **在构建矩阵里但不在部署计划里**\n"
            f"      → CI 构建了它，但 kubectl set image 不会更新它\n"
            f"      → 集群里会一直跑旧镜像")

    if problems:
        print("🔴 无法生成部署计划：")
        for p in problems:
            print(f"   {p}")
        return 1

    plan = {
        "deploy": [
            {"deployment": d, "container": c, "image": i, "module": module_of(i)}
            for d, c, i, _ in rows
        ]
    }

    if shell:
        # 供 CI 直接 eval 的 kubectl 命令
        for e in plan["deploy"]:
            print(f"kubectl set image deployment/{e['deployment']} "
                  f"{e['container']}=${{IMAGE_PREFIX}}{e['image']}:${{IMAGE_TAG}} "
                  f"-n erp --context=default")
        return 0

    if tsv:
        # 📌 TSV 比 eval 整行命令安全：只传三个纯字段，不拼接命令字符串
        for e in plan["deploy"]:
            print(f"{e['deployment']}\t{e['container']}\t{e['image']}")
        return 0

    if check_only:
        print(f"✅ k8s 里的 {len(rows)} 个 ERP Deployment 都能生成部署条目")
        for e in plan["deploy"]:
            print(f"   deployment/{e['deployment']:34s} container={e['container']:30s} {e['image']}")
        print()
        print(f"✅ 构建侧 ↔ 部署侧镜像集一致（{len(plan_images)} 个）")
        print("   （构建矩阵用正则扫 image:，部署计划用 YAML 解析 —— 两套解析方法，必须互相比一次）")
        return 0

    print(json.dumps(plan, ensure_ascii=False, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
