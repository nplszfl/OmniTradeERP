#!/usr/bin/env python3
"""K8s 清单的「引用完整性」闸门：所有跨对象引用（volume/envFrom/valueFrom/claimName/SA）必须真的存在。

🔴 **为什么需要这个闸门**（2026-11-19 实测定位的真缺陷）：
   `k8s/mysql.yaml` 的 StatefulSet 挂了 ConfigMap `mysql-init-script`
   → 但**全仓从未定义它**（`k8s/*.yaml` 里没有，也没有任何脚本 `kubectl create configmap` 它）
   → Pod 卡在 `ContainerCreating`：`MountVolume.SetUp failed ... configmap "mysql-init-script" not found`
   → `k8s/deploy.sh` 的 `kubectl wait --for=condition=ready pod -l app=mysql --timeout=300s`
      **超时 300 秒** → 脚本 `set -euo pipefail` → **整条部署链中止**
   → Redis / Nacos / 所有业务服务**一个都部署不了**。
   📌 **CI 永远抓不到**：CI 的 deploy step 因 `KUBE_CONFIG` 未配置而**整段跳过**（规则 101/146）
   → 所以必须有一个**不依赖集群**的静态闸门。

📌 **判定口径**：
   「可用的 ConfigMap/Secret 集合」 = ① `k8s/*.yaml` 里 `kind: ConfigMap/Secret` 定义出来的
   ② `k8s/*.sh` / `scripts/*.sh` 里 `kubectl ... create configmap|secret <name>` **会创建**的
   （② 很重要：`mysql-init-script` 属于这一类 —— 由 `k8s/create-mysql-init-configmap.sh` 生成）

📌 **退出码**：0 = 全绿；1 = 有引用解析不了（**部署必挂**）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
K8S_DIR = ROOT / "k8s"
SCRIPT_DIRS = [ROOT / "k8s", ROOT / "scripts"]


def collect_defined() -> dict[tuple[str, str], list[str]]:
    """k8s/*.yaml 里定义的 ConfigMap / Secret → {(kind, name): [文件]}"""
    defined: dict[tuple[str, str], list[str]] = {}
    for f in sorted(K8S_DIR.glob("*.yaml")):
        try:
            docs = list(yaml.safe_load_all(f.read_text(encoding="utf-8")))
        except Exception as exc:  # 解析失败 → 判失败，不能静默跳过
            print(f"❌ 解析 {f.name} 失败: {exc}", file=sys.stderr)
            sys.exit(1)
        for d in docs:
            if not isinstance(d, dict):
                continue
            kind = d.get("kind")
            name = (d.get("metadata") or {}).get("name")
            # 📌 **覆盖「被引用的所有 kind」**（规则 602 的同类盲区）：
            #   ConfigMap/Secret（volume/envFrom/valueFrom）
            #   + PersistentVolumeClaim（claimName）
            #   + ServiceAccount（serviceAccountName）
            if kind in ("ConfigMap", "Secret", "PersistentVolumeClaim", "ServiceAccount") and name:
                defined.setdefault((kind, name), []).append(f.name)
    return defined


def collect_script_created() -> dict[tuple[str, str], list[str]]:
    """脚本里 `kubectl ... create configmap|secret <name>` 会创建的。

    📌 **必须认全「名字是变量」的形态**（规则 100/475：判据只认一种形态 = 静默失明）：
       本仓自己就是这么写的 → `kubectl ... create configmap "${CM_NAME}"`
       → 只按字面名匹配会**漏掉它** → 闸门误报「缺失」→ 逼人改代码。
       → 处置：先收集脚本里的 `VAR="value"` 赋值，再把 `${VAR}` / `$VAR` 解析成字面值。
    """
    created: dict[tuple[str, str], list[str]] = {}
    # ① create 语句：名字允许是字面名或变量引用
    pat = re.compile(
        r"kubectl[^\n|]*?\bcreate\s+(configmap|secret)\s+(?:generic\s+)?"
        r"[\"']?(\$\{?[A-Za-z_][A-Za-z0-9_]*\}?|[A-Za-z0-9_.-]+)[\"']?",
        re.I,
    )
    for d in SCRIPT_DIRS:
        if not d.is_dir():
            continue
        for f in sorted(d.glob("*.sh")):
            txt = f.read_text(encoding="utf-8", errors="replace")
            # ② 收集变量赋值：VAR="value" / VAR=value（只取简单字面量）
            assigns: dict[str, str] = {}
            for am in re.finditer(
                r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=[\"']?([^\"'\n]+)[\"']?\s*$",
                txt, re.M,
            ):
                assigns[am.group(1)] = am.group(2).strip()
            for m in pat.finditer(txt):
                kind = "ConfigMap" if m.group(1).lower() == "configmap" else "Secret"
                raw = m.group(2)
                # ③ 变量 → 字面值（找不到就留原样，那说明真的没定义）
                name = raw
                vm = re.match(r"^\$\{?([A-Za-z_][A-Za-z0-9_]*)\}?$", raw)
                if vm:
                    name = assigns.get(vm.group(1), raw)
                created.setdefault((kind, name), []).append(f.name)
    return created


def collect_refs() -> list[tuple[str, str, str, str]]:
    """所有跨对象引用 → [(引用类型, kind, name, 位置)]

    📌 **覆盖 4 类引用**（规则 602 的同类盲区，第 1 版只查 volume → 漏掉另外 3 类）：
       ① volume.configMap.name / volume.secret.secretName
       ② envFrom[].configMapRef.name / envFrom[].secretRef.name
       ③ env[].valueFrom.configMapKeyRef.name / secretKeyRef.name
       ④ volumes[].persistentVolumeClaim.claimName / spec.serviceAccountName
    """
    refs: list[tuple[str, str, str, str]] = []

    def walk(node, file_name, path):
        if isinstance(node, dict):
            # ① volume
            vols = node.get("volumes")
            if isinstance(vols, list):
                for v in vols:
                    if not isinstance(v, dict):
                        continue
                    vn = v.get("name")
                    cm = (v.get("configMap") or {}).get("name") if isinstance(v.get("configMap"), dict) else None
                    sc = (v.get("secret") or {}).get("secretName") if isinstance(v.get("secret"), dict) else None
                    pvc = (v.get("persistentVolumeClaim") or {}).get("claimName") if isinstance(v.get("persistentVolumeClaim"), dict) else None
                    if vn and cm:
                        refs.append(("volume.configMap", "ConfigMap", cm, f"{file_name}:volume[{vn}]"))
                    if vn and sc:
                        refs.append(("volume.secret", "Secret", sc, f"{file_name}:volume[{vn}]"))
                    if vn and pvc:
                        refs.append(("volume.pvc", "PersistentVolumeClaim", pvc, f"{file_name}:volume[{vn}]"))
            # ② envFrom
            envfrom = node.get("envFrom")
            if isinstance(envfrom, list):
                for e in envfrom:
                    if not isinstance(e, dict):
                        continue
                    if isinstance(e.get("configMapRef"), dict) and e["configMapRef"].get("name"):
                        refs.append(("envFrom.configMap", "ConfigMap", e["configMapRef"]["name"], f"{file_name}{path}.envFrom"))
                    if isinstance(e.get("secretRef"), dict) and e["secretRef"].get("name"):
                        refs.append(("envFrom.secret", "Secret", e["secretRef"]["name"], f"{file_name}{path}.envFrom"))
            # ③ env[].valueFrom
            envs = node.get("env")
            if isinstance(envs, list):
                for e in envs:
                    if not isinstance(e, dict):
                        continue
                    vf = e.get("valueFrom")
                    if not isinstance(vf, dict):
                        continue
                    if isinstance(vf.get("configMapKeyRef"), dict) and vf["configMapKeyRef"].get("name"):
                        refs.append(("valueFrom.configMap", "ConfigMap", vf["configMapKeyRef"]["name"], f"{file_name}{path}.env[{e.get('name','?')}]"))
                    if isinstance(vf.get("secretKeyRef"), dict) and vf["secretKeyRef"].get("name"):
                        refs.append(("valueFrom.secret", "Secret", vf["secretKeyRef"]["name"], f"{file_name}{path}.env[{e.get('name','?')}]"))
            # ④ serviceAccountName
            san = node.get("serviceAccountName")
            if isinstance(san, str) and san:
                refs.append(("serviceAccountName", "ServiceAccount", san, f"{file_name}{path}"))
            for k, v in node.items():
                walk(v, file_name, f"{path}.{k}")
        elif isinstance(node, list):
            for i, v in enumerate(node):
                walk(v, file_name, f"{path}[{i}]")

    for f in sorted(K8S_DIR.glob("*.yaml")):
        docs = list(yaml.safe_load_all(f.read_text(encoding="utf-8")))
        for d in docs:
            if isinstance(d, dict):
                walk(d, f.name, "")
    return refs


def main() -> int:
    print("🔎 巡检 K8s 清单的引用完整性（volume / envFrom / valueFrom / claimName / serviceAccountName）")
    defined = collect_defined()
    created = collect_script_created()
    available = set(defined) | set(created)
    refs = collect_refs()

    print(f"ℹ️  yaml 里定义的对象: ConfigMap {sum(1 for k, _ in available if k == 'ConfigMap')} 个 / "
          f"Secret {sum(1 for k, _ in available if k == 'Secret')} 个 / "
          f"PVC {sum(1 for k, _ in available if k == 'PersistentVolumeClaim')} 个 / "
          f"SA {sum(1 for k, _ in available if k == 'ServiceAccount')} 个")
    print(f"ℹ️  脚本会创建的:      ConfigMap {sum(1 for k, _ in created if k == 'ConfigMap')} 个 / "
          f"Secret {sum(1 for k, _ in created if k == 'Secret')} 个")
    print(f"ℹ️  待判引用数: {len(refs)}")

    if not refs:
        print("❌ 一个引用都没读到 → 提取器失效，不能判定", file=sys.stderr)
        return 1

    missing = []
    for rtype, kind, name, where in refs:
        if (kind, name) not in available:
            missing.append((rtype, kind, name, where))

    for rtype, kind, name, where in refs:
        tag = "yaml 定义" if (kind, name) in defined else ("脚本创建" if (kind, name) in created else "🔴 缺失")
        print(f"   {'✅' if tag != '🔴 缺失' else '🔴'} {rtype:22} → {kind}/{name:26} ← {tag:8} ({where})")

    if missing:
        print()
        print(f"❌ {len(missing)} 个引用解析不了 → **部署必挂**（Pod 卡在 ContainerCreating / "
              "CreateContainerConfigError）")
        for rtype, kind, name, where in missing:
            print(f"   ❌ {rtype:22} → {kind}/{name} ← {where}")
        print("✅ 修法：① 在 k8s/*.yaml 里补上定义；或 ② 在部署脚本里 `kubectl create "
              f"{kind.lower()} {name}`；或 ③ 该引用其实允许缺失 → 加上 `optional: true`")
        return 1

    print()
    print("✅ 所有跨对象引用（ConfigMap / Secret / PVC / ServiceAccount）都能解析")
    return 0


if __name__ == "__main__":
    sys.exit(main())
