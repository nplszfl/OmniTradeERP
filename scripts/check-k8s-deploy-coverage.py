#!/usr/bin/env python3
"""k8s 部署链路覆盖度巡检（构建 ↔ 部署 ↔ 清单 三方一致）。

📌 **背景（2026-11-04 实测）**：
整个 k8s 部署链路上散落着**三份手写清单**，全都和现实脱节：

| 位置 | 手写内容 |  reality |
|---|---|---|
| CI `docker-build` job | 3 个镜像 | k8s 引用 **13** 个 |
| CI `deploy-k8s` job | 3 条 `kubectl set image` | k8s 有 **13** 个 Deployment |
| `k8s/deploy.sh` | apply 5 个 yaml | k8s 有 **8** 个 yaml |
| `k8s/build-images.sh` | 2 个镜像 + 3 个 TODO | k8s 引用 **13** 个 |

而且**部署侧那 3 条命令全是错的**：
- `erp-frontend` 这个 Deployment 在 k8s 里**根本不存在**
  → `kubectl set image` 报 `deployments.apps "erp-frontend" not found` → 部署直接失败
- **container 名写错了**：Deployment `erp-gateway` 的 container 叫 `gateway`，
  不是 `erp-gateway` → `error: container erp-gateway not found`
- 另外 10 个服务**永远跑旧镜像**

📌 **为什么一直没被发现**：
`deploy-k8s` 这个 job 一直被「`KUBE_CONFIG` secret 没配置」挡着**从没真跑过**，
而 GitHub 上它显示的是 **success**（所有步骤都 skipped，job 照样绿）→
**看起来部署链路是通的，实际上一次都没跑过**。

📌 **本脚本的职责**：把「部署链路」从「看起来配好了」变成「可自动检出」。
它不查运行时行为，只查**结构一致性** —— 但结构一致性是运行时失败的前提。

---

## 检查项

1. **k8s 里每个 ERP Deployment 都能生成部署条目**
   （Deployment 名 / container 名 / 镜像 三者齐全，且模块目录存在）
2. **CI 的 deploy-k8s job 必须用部署计划脚本**，不许手写 `kubectl set image` 清单
   （手写 = 会漂移；即使现在恰好对，下次加服务就又错了）
3. **CI 的 deploy-k8s job 不许出现 `deployment/xxx` 的硬编码目标**
4. **`k8s/deploy.sh` 必须 apply 全部服务清单**
   （core-services.yaml / ai-services.yaml 之前从没被 apply 过）
5. **`k8s/build-images.sh` 必须从 k8s 清单反推镜像**（不许手写模块清单）
6. **构建矩阵与部署计划的镜像集合必须一致**
   （构建了不部署 / 部署了没构建，都是漂移）

用法：python3 scripts/check-k8s-deploy-coverage.py
退出码 0 = 全绿，1 = 有问题
"""
import json
import os
import re
import subprocess
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S_DIR = os.path.join(ROOT, "k8s")
WF = os.path.join(ROOT, ".github", "workflows", "ci-cd.yml")

# 基础设施镜像 —— 不由本项目构建，也不该被 set image 更新
INFRA_IMAGES = ("nacos/", "mysql", "redis", "bitnami/")


def sh(args):
    r = subprocess.run(args, capture_output=True, text=True,
                       cwd=ROOT, timeout=120)
    return r.returncode, r.stdout.strip(), r.stderr.strip()


def err(msg):
    print(f"   🔴 {msg}")


def main():
    errors = []
    notes = []

    # ── 1) 部署计划本身可生成 ──────────────────────────────────────
    rc, out, serr = sh([sys.executable, "scripts/k8s-deploy-plan.py", "--check"])
    print(f"📋 部署计划检查：{'✅' if rc == 0 else '🔴'}")
    print(out if rc == 0 else (out + "\n" + serr))
    if rc != 0:
        errors.append("scripts/k8s-deploy-plan.py 无法生成部署计划")

    # 解析出部署条目
    rc2, plan_json, _ = sh([sys.executable, "scripts/k8s-deploy-plan.py"])
    plan = json.loads(plan_json) if rc2 == 0 else {"deploy": []}
    deployments = plan.get("deploy", [])
    plan_images = {d["image"] for d in deployments}

    # ── 2) 构建矩阵 ↔ 部署计划 ─────────────────────────────────────
    rc3, matrix_json, _ = sh([sys.executable, "scripts/k8s-image-matrix.py"])
    if rc3 == 0:
        matrix = json.loads(matrix_json).get("include", [])
        build_images = {r["image"] for r in matrix}
        only_build = build_images - plan_images
        only_deploy = plan_images - build_images
        if only_build:
            err(f"构建了但**没有对应 Deployment**（k8s 清单里没有）: {sorted(only_build)}")
            errors.append(f"{len(only_build)} 个镜像只构建不部署")
        if only_deploy:
            err(f"要部署但**构建矩阵里没有**（镜像从没构建过）: {sorted(only_deploy)}")
            errors.append(f"{len(only_deploy)} 个镜像要部署但没构建")
        if not only_build and not only_deploy:
            print(f"✅ 构建矩阵 ↔ 部署计划：{len(plan_images)} 个镜像完全一致")
    else:
        errors.append("scripts/k8s-image-matrix.py 执行失败")

    # ── 3) CI deploy-k8s job：必须用计划脚本，不许硬编码 ───────────
    wf = yaml.safe_load(open(WF, encoding="utf-8"))
    deploy_job = (wf.get("jobs") or {}).get("deploy-k8s")
    if not deploy_job:
        errors.append("ci-cd.yml 里没有 deploy-k8s job")
    else:
        # 📌 **必须用 job 里真实的 run 文本，不能用 yaml.dump()** ——
        #    yaml.dump 会把换行转义成字面量 `\n` → 多行的 kubectl 命令变成一行
        #    → 正则跨行匹配不上 → **漏检**（第一版就栽在这）。
        # 📌 同时**去掉注释行** —— 否则我自己在注释里写的
        #    「之前这里手写了 3 条 kubectl set image」会被当成真的命令 → 误报。
        steps_text = "\n".join(
            ln
            for s in (deploy_job.get("steps") or [])
            for ln in (s.get("run") or "").splitlines()
            if ln.strip() and not ln.strip().startswith("#")
        )
        if "k8s-deploy-plan.py" not in steps_text:
            err("deploy-k8s job **没有调用 k8s-deploy-plan.py**")
            errors.append("deploy-k8s job 未使用部署计划脚本（手写清单会漂移）")
        else:
            print("✅ deploy-k8s job 使用了 k8s-deploy-plan.py")

        # 硬编码的 deployment 目标
        hardcoded = set(re.findall(r"deployment/([a-z0-9\-]+)", steps_text))
        hardcoded -= {d["deployment"] for d in deployments}
        # 只在**命令**里出现的才算（注释里提到不算）→ 这里粗筛，最终以命令行为准
        real_cmds = re.findall(r"kubectl set image\s+deployment/([a-z0-9\-]+)", steps_text)
        if real_cmds:
            err(f"deploy-k8s job 里仍有**手写**的 set image 目标: {sorted(set(real_cmds))}")
            errors.append(f"deploy-k8s job 手写了 {len(set(real_cmds))} 个部署目标")

        # 硬编码的 container 名（container 名 ≠ deployment 名，最容易写错）
        # 📌 正则要能跨行：**kubectl 命令在 YAML 里常写成 `deployment/xxx \` + 换行 + `ctr=xxx`**
        #    只用 `\s+` 匹配不上中间的续行符 `\` → 会漏检（第一版就漏了）
        SETIMG_RE = re.compile(
            r"kubectl set image\s+deployment/([a-z0-9\-]+)\s*[\\\s]*([a-z0-9\-]+)=")
        for m in SETIMG_RE.finditer(steps_text):
            dep, ctr = m.group(1), m.group(2)
            hit = next((d for d in deployments if d["deployment"] == dep), None)
            if hit is None:
                err(f"deployment/{dep} 在 k8s 清单里**根本不存在**"
                    f"（k8s/*.yaml 里没有这个 Deployment）→ kubectl 会报 not found")
                errors.append(f"deployment/{dep} 在 k8s 清单里不存在")
            elif hit["container"] != ctr:
                err(f"deployment/{dep} 的 container 名是 `{hit['container']}`，"
                    f"不是 `{ctr}` → kubectl 会报 container not found")
                errors.append(f"deployment/{dep} container 名写错")

        # ── 4) 必须 apply 服务清单（否则新加的 Deployment 不存在）──
        if "kubectl apply" not in steps_text:
            err("deploy-k8s job 没有 `kubectl apply` → "
                "清单里新加的 Deployment 在集群里不存在 → set image 报 not found")
            errors.append("deploy-k8s job 未 apply 清单")

    # ── 5) k8s/deploy.sh：必须 apply 全部服务清单 ───────────────────
    deploy_sh = os.path.join(K8S_DIR, "deploy.sh")
    if os.path.exists(deploy_sh):
        sh_text = open(deploy_sh, encoding="utf-8").read()
        # 服务清单 = 含 Deployment 的 yaml
        service_yamls = set()
        for fn in sorted(os.listdir(K8S_DIR)):
            if not fn.endswith((".yaml", ".yml")):
                continue
            txt = open(os.path.join(K8S_DIR, fn), encoding="utf-8").read()
            if re.search(r"^\s*kind:\s*Deployment\s*$", txt, re.M):
                service_yamls.add(fn)

        # 📌 **两种合法的 apply 方式**，两种都算过：
        #    a) 显式列文件名：`for f in k8s/a.yaml k8s/b.yaml; do`
        #    b) **自动发现**：`for f in k8s/*.yaml; do` + case 跳过基础设施
        #    📌 (b) 才是正解 —— 手写文件名列表必然会漂移
        #    （2026-11-04 实测：手写列表漏了 extended-services.yaml → 10 个服务的
        #     Deployment 根本没进集群，而脚本照样打印「✅ 部署完成！」）
        auto_discover = re.search(r"for\s+f\s+in\s+k8s/\*\.ya?ml", sh_text) is not None
        applied = set(re.findall(r"k8s/([a-z0-9\-]+\.yaml)", sh_text))

        if auto_discover:
            # 自动发现模式：`for f in k8s/*.yaml` 覆盖了**除 case 排除项以外**的全部清单。
            # → 只有「被 case 排除、且没在别处 apply、且确实带 Deployment」的清单
            #    才是真的漏了。
            excluded = set()
            for m in re.finditer(r"case\s+\"?\$\{?base\}?\"?\s+in\s+(.*?)\besac", sh_text, re.S):
                for w in re.findall(r"([a-z0-9\-]+\.ya?ml)", m.group(1)):
                    excluded.add(w)
            # 📌 被排除、但在脚本**其它地方**单独 apply 的（mysql/redis/nacos/namespace
            #    上面已经 apply 过了）→ 不算漏
            excluded_but_applied = excluded & applied
            # 真正漏的 = 带 Deployment + 被排除 + 其它地方也没 apply
            missing = set()
            for f in service_yamls & excluded:
                if f in excluded_but_applied:
                    continue
                txt = open(os.path.join(K8S_DIR, f), encoding="utf-8").read()
                if re.search(r"^\s*kind:\s*Deployment\s*$", txt, re.M):
                    missing.add(f)
            if missing:
                err(f"k8s/deploy.sh **没有 apply** 这些服务清单: {sorted(missing)}")
                errors.append(f"deploy.sh 漏了 {len(missing)} 个服务清单文件")
            else:
                print(f"✅ k8s/deploy.sh 自动发现并 apply 了全部 {len(service_yamls)} 个服务清单"
                      f"（基础设施 {len(excluded_but_applied)} 个单独 apply）")
        else:
            missing = service_yamls - applied
            if missing:
                err(f"k8s/deploy.sh **没有 apply** 这些服务清单: {sorted(missing)}")
                errors.append(f"deploy.sh 漏了 {len(missing)} 个服务清单文件")
            else:
                print(f"✅ k8s/deploy.sh apply 了全部 {len(service_yamls)} 个服务清单")
    else:
        errors.append("k8s/deploy.sh 不存在")

    # ── 6) k8s/build-images.sh：必须从清单反推 ──────────────────────
    build_sh = os.path.join(K8S_DIR, "build-images.sh")
    if os.path.exists(build_sh):
        b_text = open(build_sh, encoding="utf-8").read()
        # 📌 同上：只看**真实代码**，不看注释
        code = "\n".join(
            ln for ln in b_text.splitlines()
            if ln.strip() and not ln.strip().startswith("#")
        )
        if "k8s-image-matrix.py" not in code:
            err("k8s/build-images.sh **没有从 k8s 清单反推**镜像（手写清单会漂移）")
            errors.append("build-images.sh 未使用 k8s-image-matrix.py")
        else:
            print("✅ k8s/build-images.sh 从 k8s 清单反推镜像")

        # 📌 **真正的约束是「一套配方」而不是「有没有写 TODO」** ——
        #    之前用「文本里出现 # TODO ... Dockerfile」来判，结果**误报**：
        #    我在脚本的说明注释里引用了这句 TODO（描述它已经被干掉了）→ 自己也命中。
        #    ⚠️ 用「关键词在不在」判闸门 → 注释里提到就误报 → 闸门学会误报就废了。
        #    ✅ 改成**结构约束**：只能有**一个** docker build 调用，
        #       且必须用通用的 Dockerfile.service（而不是每个服务一个 Dockerfile.X）。
        n_build = len(re.findall(r"docker build", code))
        if n_build != 1:
            err(f"k8s/build-images.sh 里有 **{n_build} 处** `docker build`"
                f"（应为 1 处通用构建 + 循环）→ 手写多份必然漂移")
            errors.append(f"build-images.sh 有 {n_build} 处 docker build")
        if "Dockerfile.service" not in code:
            err("k8s/build-images.sh 的构建没走通用的 docker/Dockerfile.service")
            errors.append("build-images.sh 未使用 Dockerfile.service")

        # 硬编码的模块名（应该是 0 个 —— 全靠矩阵）
        hard = sorted(set(re.findall(r"\berp-[a-z\-]+-service\b", code)))
        if hard:
            err(f"k8s/build-images.sh 里出现了硬编码模块名: {hard}")
            errors.append("build-images.sh 硬编码了模块名")

    # ── 报告 ──────────────────────────────────────────────────────
    print()
    if errors:
        print(f"🔴 发现 {len(errors)} 处部署链路的问题：\n")
        for e in errors:
            print(f"   • {e}")
        print()
        print("📌 **这类问题的特征**：CI 显示 success，但部署链路其实一次都没跑通")
        print("   （所有步骤被 secret 判断挡成 skipped，job 照样绿）")
        print("   → 「看起来配好了，纯属假象」的又一形态")
        return 1

    print(f"✅ 部署链路结构一致（{len(deployments)} 个 ERP Deployment）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
