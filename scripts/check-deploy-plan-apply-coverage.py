#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「部署计划覆盖的 Deployment」必须 ⊆ 「CI 会 apply 的清单」—— 守门人。

📌 **为什么需要这道闸门**（本项目真实踩到的坑，2026-11-12）：

    CI 的 deploy job 里：
      ① `kubectl apply -f k8s/{gateway,core-services,services,ai-services}.yaml`
      ② `python3 scripts/k8s-deploy-plan.py --tsv` → 从 **k8s/*.yaml 全部**反推 → **23** 个 Deployment
      ③ `while read ...; do kubectl set image "deployment/$deploy" ...; done < deploy-plan.tsv`
         → 这个 step 有 `set -euo pipefail`

    🔴 `k8s/extended-services.yaml` 里有 **10** 个 Deployment
       （analytics / anomaly-detection / customer / inventory-alert /
         product-recommendation / purchase / reporting / social-commerce /
         supplier / tenant）→ **CI 从来没 apply 它**
       → 那 10 个 Deployment 在集群里**根本不存在**
       → 循环走到第一个（`erp-analytics-service`）就 `kubectl set image` → **not found**
       → `set -euo pipefail` → **整个 step 立刻中止** → 部署 job **必然失败**

    📌 也就是说：**第一次真的配上 `KUBE_CONFIG` 去部署，必挂。**
    而这个仓库**从未真正部署过**（规则 146）→ 这条路径**一次都没被执行过** → 缺陷一直藏着。

📌 **判据**：

| 断言 | 内容 |
|---|---|
| **C1** | 解析 `ci-cd.yml` 的 deploy job → 得到 CI 会 `kubectl apply` 的**清单集合**（要认 `for f in ...` 循环形式） |
| **C2** | 跑 `scripts/k8s-deploy-plan.py --tsv` → 得到**部署计划覆盖的 Deployment 集合** |
| **C3** | 用 **YAML 解析器**（不是正则，规则 114）把每个 Deployment 映射回它所属的清单 |
| **C4** | **计划里的每个 Deployment，其所属清单都必须在 CI 的 apply 集合里** —— 否则报红 |
| **C5** | 反向：**CI apply 的应用清单里不许混进有状态基础设施**（mysql / redis / nacos）—— CI 不该碰它们 |

📌 **为什么必须用 YAML 解析器数 Deployment**：
    正则数 `kind: Deployment` 会漏掉缩进变体；而且还要处理「一个清单里多个 Deployment」
    和「Deployment 与 StatefulSet 混在一起」（mysql.yaml 里是 StatefulSet，不是 Deployment）。

📌 **反向验证**（规则 40 / 75 / 123）：
    把 `k8s/extended-services.yaml` 从 CI 的 apply 列表里去掉 → 本闸门必须 exit 1 并指名；
    加回去 → 必须 exit 0。

用法：
    python3 scripts/check-deploy-plan-apply-coverage.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-deploy-plan-apply-coverage.py --verbose # 打印集合详情
"""

from __future__ import annotations

import glob
import os
import re
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

WORKFLOW = os.path.join(REPO_ROOT, ".github/workflows/ci-cd.yml")
PLAN_SCRIPT = os.path.join(REPO_ROOT, "scripts/k8s-deploy-plan.py")

# 有状态基础设施 —— CI 不该 apply（设计意图：基础设施由 deploy.sh / 运维单独管）
STATEFUL_MANIFESTS = ("k8s/mysql.yaml", "k8s/redis.yaml", "k8s/nacos.yaml")


def render_source_map(text: str) -> dict[str, str]:
    """**渲染产物文件名 → 源清单路径** 的映射。

    📌 **为什么需要它**（2026-11-14 04:00 实测）：
    `k8s/gateway.yaml` 的 Ingress host 改成了占位符 `__INGRESS_HOST__` →
    CI **不能**再直接 apply 原文件（占位符进集群 = 全站 404）→
    改成「先渲染成 `gateway.rendered.yaml`，再 apply 那个产物」。
    📌 于是 CI 实际 apply 的清单集合里**看不到** `k8s/gateway.yaml` 了
    → 本闸门会误报「CI 没 apply k8s/gateway.yaml」= **假阳性**（规则 87）。
    ✅ 解法：把渲染产物**映射回源清单**（渲染产物里的 Deployment 与源清单**完全相同**）
    → 覆盖判据**没有放松**（该 apply 的还是必须 apply）。
    """
    out = {}
    for m in re.finditer(r"render-[a-z-]*\.py\s+\S+\s+(k8s/[\w\-.]+\.ya?ml)\s+(\S+)", text):
        out[os.path.basename(m.group(2))] = m.group(1)
    return out


def extract_ci_applied_manifests() -> set[str]:
    """从 ci-cd.yml 里解析 CI 会 `kubectl apply` 的清单集合。

    📌 **要认三种写法**（漏一种就会误判）：
      ① `kubectl apply -f k8s/xxx.yaml`        —— 直接形式
      ② `for f in k8s/a.yaml k8s/b.yaml; do kubectl apply -f "$f"` —— 循环形式
      ③ `kubectl apply -f gateway.rendered.yaml` —— **渲染产物**形式
         → 必须**映射回源清单**（否则 ① 的正则匹配不到 `k8s/` 前缀 → 假红）
    """
    text = open(WORKFLOW, encoding="utf-8").read()
    rmap = render_source_map(text)
    found = set(re.findall(r"kubectl apply -f (k8s/[\w\-.]+\.yaml)", text))
    for m in re.finditer(r"for f in ([^\n;]+); do", text):
        found |= set(re.findall(r"k8s/[\w\-.]+\.yaml", m.group(1)))
    # ③ 渲染产物 → 源清单
    for m in re.finditer(r"kubectl apply -f ([\w\-.]+\.ya?ml)", text):
        base = os.path.basename(m.group(1))
        if base in rmap:
            found.add(rmap[base])
    return found


def plan_deployments() -> list[str]:
    """跑部署计划脚本 → 计划覆盖的 Deployment 名列表。"""
    r = subprocess.run(
        [sys.executable, PLAN_SCRIPT, "--tsv"],
        capture_output=True, text=True, cwd=REPO_ROOT, timeout=180,
    )
    if r.returncode != 0:
        return []
    out = []
    for line in r.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if parts:
            out.append(parts[0].strip())
    return out


def deployment_owner_map() -> dict[str, str]:
    """Deployment 名 → 它所属的清单文件（用 YAML 解析器，规则 114）。"""
    import yaml

    owner: dict[str, str] = {}
    for path in sorted(glob.glob(os.path.join(REPO_ROOT, "k8s/*.yaml"))):
        rel = os.path.relpath(path, REPO_ROOT)
        try:
            docs = list(yaml.safe_load_all(open(path, encoding="utf-8")))
        except Exception:
            continue
        for d in docs:
            if not isinstance(d, dict) or d.get("kind") != "Deployment":
                continue
            name = (d.get("metadata") or {}).get("name")
            if name:
                owner[name] = rel
    return owner


def main() -> int:
    verbose = "--verbose" in sys.argv
    applied = extract_ci_applied_manifests()
    plan = plan_deployments()
    owner = deployment_owner_map()

    errors: list[str] = []

    if not plan:
        errors.append(
            f"部署计划脚本 `{os.path.relpath(PLAN_SCRIPT, REPO_ROOT)}` 跑不出结果 → 无法对账"
        )
    if not owner:
        errors.append("用 YAML 解析器一个 Deployment 都没解析到 —— k8s 清单结构变了？")

    # ── C4：计划覆盖的 Deployment，其所属清单必须都在 CI 的 apply 集合里 ──
    missing_by_manifest: dict[str, list[str]] = {}
    for dep in plan:
        f = owner.get(dep)
        if f is None:
            errors.append(f"部署计划里的 `{dep}` 在 k8s/*.yaml 里找不到对应的 Deployment")
            continue
        if f not in applied:
            missing_by_manifest.setdefault(f, []).append(dep)

    for manifest, deps in sorted(missing_by_manifest.items()):
        errors.append(
            f"CI **没有 apply** `{manifest}`，但部署计划里有它的 {len(deps)} 个 Deployment：\n"
            + "".join(f"           · {d}\n" for d in deps)
            + f"      → 它们在集群里**根本不存在** → 循环里 `kubectl set image deployment/<名>` "
            f"→ **not found**\n"
            f"      → deploy step 有 `set -euo pipefail` → **整个部署 job 必然失败**\n"
            f"      ✅ 修法：把 `{manifest}` 加进 ci-cd.yml 的 apply 清单列表"
        )

    # ── C5：CI 不该 apply 有状态基础设施 ──
    for s in STATEFUL_MANIFESTS:
        if s in applied:
            errors.append(
                f"CI 会 apply `{s}` —— 有状态基础设施应由 deploy.sh / 运维单独管，"
                f"CI 碰它会**重置数据库**（mysql.yaml 里是 StatefulSet）"
            )

    print("📋 巡检「部署计划覆盖的 Deployment ⊆ CI apply 的清单」")
    print(f"ℹ️  CI 会 apply 的清单：{sorted(applied)}")
    print(f"ℹ️  部署计划覆盖的 Deployment：{len(plan)} 个")
    if verbose:
        for dep in plan:
            f = owner.get(dep, "?")
            mark = "✅" if f in applied else "❌"
            print(f"   {mark} {dep}  ← {f}")

    if errors:
        print(f"\n❌ 发现 {len(errors)} 处部署链路断裂：")
        for e in errors:
            print(f"   {e}")
        return 1

    print("\n✅ 部署计划覆盖的每个 Deployment，其清单都会被 CI apply → 首次真实部署不会因 not found 而失败")
    return 0


if __name__ == "__main__":
    sys.exit(main())
