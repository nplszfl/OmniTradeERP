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
        return 0

    print(json.dumps(plan, ensure_ascii=False, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
