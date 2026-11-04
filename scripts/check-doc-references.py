#!/usr/bin/env python3
"""文档引用文件存在性巡检（部署类清单）。

📌 **背景（2026-11-05 实测）**：
`docs/Deployment.md` 里写着「部署方式」，读者照着敲命令 →
- `kubectl apply -f deployment-production.yml` → **该文件根本不存在**
- `helm install omnitrade ./charts/omnitrade-erp -f values-production.yaml`
  → **没有 charts/ 目录、没有 values-production.yaml**（整段是虚构的）
- 「包含：全部15个微服务 + AI服务 + MySQL + Redis + ES + RocketMQ」
  → 实际 compose 里 **没有 ES、没有 RocketMQ**，服务数也变了

**假文档比没文档更坏** —— 它让人以为「照着做就行」，然后卡在
`no such file or directory`。

📌 **为什么不做成「通用文档链接巡检」**：
实测过：扫全仓 .md 里的反引号路径 + markdown 链接，
**81 处引用里 27 处「不存在」**，绝大多数是**假红**：
- 简称（`nacos.yaml` 其实是 `k8s/nacos.yaml`）
- 省略路径（`erp-common/.../Result.java`）
- 相对模块目录的路径（`src/main/resources/application.yml`）
- 历史调查报告里的当时快照（`docs/CUSTOMER-RESPONSE-SHAPE.md`）
→ **闸门必须窄**：只查「部署类清单」这一个语义域，判据才立得住。

---

## 检查项

1. **部署类文档里引用的清单文件必须真实存在**
   范围：`docs/Deployment.md`、`k8s/KUBERNETES_DEPLOYMENT_GUIDE.md`、
   `README.md`、`docs/Quick-Start.md`、`docs/Installation.md`
2. 只认这几类引用（避免把简称/省略路径当缺陷）：
   - `docker-compose*.yml`
   - `k8s/*.yaml`
   - 仓库根的 `*.sh`
3. 文档里**不得出现 Helm 安装指令**（本仓库没有 charts/）——
   有 Helm 章节就说明文档写于另一套部署形态，已漂移

---

## 为什么这么判

- **只查部署类文档**：其它 .md 有大量历史快照和简称，判据立不住 → 宁可漏报也不假红
- **只查这几类路径**：compose / k8s 清单 / 部署脚本 = 「照着敲就能跑」的东西
- **Helm 单独判**：不是路径存在性问题，是**形态漂移**问题（仓库里根本没有 charts/）

反向验证（必做）：
1. 在 `docs/Deployment.md` 里写回 `kubectl apply -f deployment-production.yml`
   → 闸门**必须报红**
2. 写入 `helm install omnitrade ./charts/omnitrade-erp`
   → 闸门**必须报红**
3. 两处还原 → 闸门**必须变绿**
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

# 📌 闸门只管「部署类文档」—— 其它文档有大量历史快照和简称，判据立不住
DOCS = [
    "docs/Deployment.md",
    "docs/Quick-Start.md",
    "docs/Installation.md",
    "k8s/KUBERNETES_DEPLOYMENT_GUIDE.md",
    "README.md",
]

# 📌 只认这几类「照着敲就能跑」的路径
PATH_PATTERNS = [
    re.compile(r"docker-compose[A-Za-z0-9_.\-]*\.yml"),
    re.compile(r"k8s/[A-Za-z0-9_.\-]+\.yaml"),
    re.compile(r"(?<![\w/.\-])[A-Za-z0-9_\-]+\.sh"),  # 仓库根的脚本
]

# 📌 **环境名家族**（`*-prod.yml` / `*-production.yaml` / `values-*.yaml` …）
#    —— 这类文件在本仓库里要么真实存在（docker-compose.prod.yml 等），
#    要么是**虚构的部署产物**（deployment-production.yml / values-production.yaml）。
#    判据：这样的名字**必须真实存在**。
#    📌 为什么不把所有 *.yml/*.yaml 都算进来：
#    `k8s/KUBERNETES_DEPLOYMENT_GUIDE.md` 里有「**创建** `configmap.yaml`:」
#    —— 那是**让人先创建**再 apply 的配方，不是引用现成文件 → 判据必须放过它。
ENV_FILE_PATTERN = re.compile(
    r"^[A-Za-z0-9_.\-]*-(?:prod|production|dev|development|test|staging)\.ya?ml$"
)

HELM_PATTERN = re.compile(r"helm\s+(install|template|upgrade)\b", re.I)

errors = []


def referenced_paths(text):
    """抽出文档里引用的部署类路径。

    📌 **两种写法都要扫**（反向验证抓出来的盲区）：
    - 行内 code：`` `docker-compose.full.yml` ``
    - **围栏代码块**：```bash ... ```  ← **命令就写在这里**
      最初只扫行内 code → 往代码块里写 `kubectl apply -f deployment-production.yml`
      → 闸门**纹丝不动** → 假绿。
      （规则 55：判据必须匹配「代码实际怎么写」）
    """
    found = set()
    chunks = re.findall(r"`([^`\n]+)`", text)          # 行内 code
    chunks += re.findall(r"```[a-zA-Z]*\n(.*?)```", text, re.S)  # 围栏代码块
    for chunk in chunks:
        chunk = chunk.strip()
        # 单个反引号里可能是一串命令（"kubectl apply -f a.yaml"）→ 逐词扫
        for token in re.split(r"[\s|]+", chunk):
            token = token.strip().strip(".,;:'\"")
            if not token or token.startswith(("http://", "https://", "/")):
                continue
            if "*" in token or "..." in token or "<" in token or ">" in token:
                continue
            for pat in PATH_PATTERNS:
                if pat.fullmatch(token):
                    found.add(token)
                    break
            else:
                if ENV_FILE_PATTERN.fullmatch(token):
                    found.add(token)
    return found


for doc in DOCS:
    if not os.path.exists(doc):
        errors.append(f"部署类文档不存在: {doc}（闸门清单要跟着仓库一起更新）")
        continue
    text = open(doc, encoding="utf-8", errors="replace").read()

    for path in sorted(referenced_paths(text)):
        # 📌 **两种解析都要试**：仓库根（`docker-compose.full.yml`）
        #    和**文档所在目录**（`k8s/build-images.sh` 在 `k8s/` 下的文档里就是简称）
        #    → 否则会把「简称」当缺陷 → 假红。
        base = os.path.dirname(doc)
        if os.path.exists(path) or os.path.exists(os.path.join(base, path)):
            continue
        errors.append(
            f"{doc}: 引用了**不存在的文件** `{path}`"
            f" → 读者照着敲会得到 'no such file or directory'"
        )

    for hit in HELM_PATTERN.finditer(text):
        line_no = text[: hit.start()].count("\n") + 1
        snippet = text.splitlines()[line_no - 1].strip()
        errors.append(
            f"{doc}:{line_no}: 出现 Helm 部署指令 —— "
            f"本仓库**没有 charts/ 目录也没有 values-production.yaml**，"
            f"这段是写于另一套部署形态的漂移内容（原文: {snippet[:80]}）"
        )

if errors:
    print("❌ 文档引用巡检失败：")
    for e in errors:
        print(f"  - {e}")
    sys.exit(1)

print(f"✅ 文档引用巡检通过（{len(DOCS)} 个部署类文档）")
