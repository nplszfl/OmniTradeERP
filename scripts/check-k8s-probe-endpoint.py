#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""k8s 探针路径 ↔ 服务实际提供的端点 一致性巡检（2026-11-12）。

📌 **背景（真实缺陷，2026-11-12 实测）**：

k8s 清单里 **23 个 Deployment 的 readinessProbe / livenessProbe 都指向
`/actuator/health`**，但其中 **17 个服务的模块根本没有 `spring-boot-starter-actuator` 依赖**：

| 层 | 事实 |
|---|---|
| k8s 清单 | `path: /actuator/health`（探针打服务的**自身端口**） |
| 模块 pom | **17/23 没有 actuator 依赖**（用 `mvn dependency:tree` 实测确认，**也没有传递依赖**） |
| application.yml | 其中 **8 个还配了** `management.endpoints.web.exposure.include` → **配置是死的**（没依赖，配了也白配） |
| 运行时 | `/actuator/health` → **404** → 探针永远失败 → **Pod 永远 NotReady** |
| 后果 | `kubectl rollout status --timeout=5m` 超时 → **首次真实部署必然失败** |

📌 **为什么一直没被发现**：这批 k8s 清单**一次都没被执行过**（`KUBE_CONFIG` 没配 →
deploy job 的所有真 step 被 `skipped`，但 job 结论仍是 `success` —— 规则 101）。
静态看「Deployment 数 ↔ 构建矩阵 ↔ 端口 三方一致」全绿，**但没人问过那个路径存不存在**。

📌 **本闸门查什么**：
1. **C1**：k8s 里用 `/actuator/**` 做探针的容器 → 对应模块**必须有 actuator 依赖**
2. **C2**：模块配了 `management.endpoints.web.exposure` → **必须真的有 actuator 依赖**
   （配了却没依赖 = 「文档/配置说了要做 A，代码做的是 B」，规则 44）
3. **C3**：探针端口 = 该服务的 `server.port`（探针打错端口 = 探针永远失败）

📌 **为什么不能用「路径看起来对不对」判**：路径名是**约定**，不是**事实**。
真相来源 = **模块 pom 的真实依赖**（规则 128：基线要锚在独立事实源）。

📌 **反向验证必须注入真实的原始缺陷**（规则 40 / 75）：
把某个模块的 actuator 依赖删掉 → 本闸门必须 exit 1；恢复 → 必须 exit 0。

用法：
    python3 scripts/check-k8s-probe-endpoint.py            # 巡检（有问题 → exit 1）
    python3 scripts/check-k8s-probe-endpoint.py --verbose   # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S_DIR = os.path.join(REPO_ROOT, "k8s")

ACTUATOR_ARTIFACT = "spring-boot-starter-actuator"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


# ── pom 侧 ───────────────────────────────────────────────────────────────

def module_dir_for_image(image: str) -> str | None:
    """镜像名 → 模块目录。镜像一般叫 `erp-xxx-service`，模块目录同名。"""
    cand = image.split("/")[-1]
    if os.path.isdir(os.path.join(REPO_ROOT, cand)):
        return cand
    # 去掉镜像 tag 前缀的 registry 部分
    return None


def _strip_ns(elem):
    """去掉 XML 命名空间前缀（`{uri}tag` → `tag`），让 XPath 写法与命名空间无关。"""
    for e in elem.iter():
        if isinstance(e.tag, str) and e.tag.startswith("{"):
            e.tag = e.tag.split("}", 1)[1]
    return elem


def has_actuator(module: str) -> bool:
    """模块 pom 里**声明**了 actuator（用解析器看，不用正则 —— 规则 114）。"""
    pom = os.path.join(REPO_ROOT, module, "pom.xml")
    if not os.path.exists(pom):
        return False
    try:
        root = _strip_ns(ET.parse(pom).getroot())
    except ET.ParseError:
        return False
    for dep in root.iter("dependency"):
        art = dep.find("artifactId")
        if art is not None and (art.text or "").strip() == ACTUATOR_ARTIFACT:
            return True
    return False


def server_port(module: str) -> int | None:
    """模块 application.yml 的 server.port（探针端口要对得上）。"""
    yml = os.path.join(REPO_ROOT, module, "src", "main", "resources", "application.yml")
    if not os.path.exists(yml):
        return None
    text = open(yml, encoding="utf-8").read()
    m = re.search(r"^server:\s*\n\s+port:\s*(\d+)", text, re.M)
    return int(m.group(1)) if m else None


def configures_management(module: str) -> bool:
    """模块 application.yml 里配了 management.endpoints.web.exposure。"""
    yml = os.path.join(REPO_ROOT, module, "src", "main", "resources", "application.yml")
    if not os.path.exists(yml):
        return False
    text = open(yml, encoding="utf-8").read()
    return bool(re.search(r"^\s*management:\s*$", text, re.M)) and \
           bool(re.search(r"^\s*exposure:\s*$", text, re.M))


# ── k8s 侧 ───────────────────────────────────────────────────────────────

def iter_k8s_docs():
    """(文件名, 文档文本) —— 按 `---` 切开。"""
    if not os.path.isdir(K8S_DIR):
        return
    for fn in sorted(os.listdir(K8S_DIR)):
        if not fn.endswith((".yaml", ".yml")):
            continue
        path = os.path.join(K8S_DIR, fn)
        text = open(path, encoding="utf-8").read()
        for doc in re.split(r"\n---\n", text):
            if doc.strip():
                yield fn, doc


def probes_in(doc: str):
    """文档里的 (probe 类型, 路径, 端口) 三元组。"""
    out = []
    for m in re.finditer(
        r"(readinessProbe|livenessProbe):\s*\n\s+httpGet:\s*\n\s+path:\s*(\S+)\s*\n\s+port:\s*(\d+)",
        doc,
    ):
        out.append((m.group(1), m.group(2), int(m.group(3))))
    return out


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings: list[str] = []
    checked_containers: set[str] = set()
    checked_modules: set[str] = set()

    # ── C1 / C3：探针 ↔ actuator 依赖 / 探针端口 ──────────────────────
    for fn, doc in iter_k8s_docs():
        img_m = re.search(r'image:\s*"?([\w./-]+):', doc)
        if not img_m:
            continue
        image = img_m.group(1)
        module = module_dir_for_image(image)
        for kind, path, port in probes_in(doc):
            if not path.startswith("/actuator"):
                continue   # 只管 actuator 这类「框架提供的端点」
            if module is None:
                findings.append(
                    f"{fn}: 容器 {image} 的 {kind} 指向 {path}，"
                    f"但**找不到对应模块目录** → 探针打不到任何东西"
                )
                continue
            checked_containers.add(image)
            if not has_actuator(module):
                findings.append(
                    f"{fn}: 容器 {image}（模块 {module}）的 {kind} 指向 {path}，"
                    f"但 **{module}/pom.xml 没有 `{ACTUATOR_ARTIFACT}` 依赖** → "
                    f"该路径会 **404** → 探针永远失败 → **Pod 永远 NotReady** → "
                    f"`rollout status` 超时 → **首次真实部署必然失败**"
                )
                continue
            # C3：探针端口 vs server.port
            sp = server_port(module)
            if sp is not None and sp != port:
                findings.append(
                    f"{fn}: 容器 {image} 的 {kind} 打端口 {port}，"
                    f"但 {module} 的 `server.port` 是 {sp} → **探针打错端口** → 永远失败"
                )

    # ── C2：配了 management 暴露却没 actuator 依赖 ─────────────────────
    if os.path.isdir(REPO_ROOT):
        for name in sorted(os.listdir(REPO_ROOT)):
            if not os.path.isdir(os.path.join(REPO_ROOT, name)):
                continue
            if not os.path.exists(os.path.join(REPO_ROOT, name, "pom.xml")):
                continue
            if not configures_management(name):
                continue
            checked_modules.add(name)
            if not has_actuator(name):
                findings.append(
                    f"{name}: application.yml 配了 `management.endpoints.web.exposure`，"
                    f"但 **{name}/pom.xml 没有 `{ACTUATOR_ARTIFACT}` 依赖** → "
                    f"**这段配置是死的**（配了也白配）→ 「配置说要做 A，代码做的是 B」（规则 44）"
                )

    print("📋 巡检「k8s 探针路径 ↔ 服务实际提供的端点」")
    print(f"ℹ️  用了 actuator 探针的容器：{len(checked_containers)} 个")
    print(f"ℹ️  配了 management 暴露的模块：{len(checked_modules)} 个")
    if verbose:
        for f in findings:
            print(f"   🔴 {f}")

    if findings:
        print(f"\n🔴 发现 {len(findings)} 处「探针/配置指向的端点不存在」：\n")
        for f in findings:
            print(f"   • {f}\n")
        print("✅ 修法：给对应模块加 `spring-boot-starter-actuator` 依赖"
              "（版本由根 pom 的 `spring-boot-dependencies` BOM 管，**不用写 version**）")
        print("📌 **别改成「按值/路径过滤」来糊弄** —— 那是把真缺陷藏起来")
        return 1

    print("\n✅ 所有 actuator 探针都有对应依赖，探针端口与服务端口一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
