#!/usr/bin/env python3
"""k8s 部署脚本的**可执行**冒烟测试 —— 用假 kubectl / 假 docker 真正跑一遍。

📌 **为什么必须是「执行」而不是「静态检查」**（2026-11-04 实测）：
`k8s/deploy.sh` 原来**没有** `set -e`，而且用相对路径 `k8s/*.yaml` 引用清单。
文档 `k8s/KUBERNETES_DEPLOYMENT_GUIDE.md` 教人这么跑：

    cd .../OmniTradeERP/k8s && ./deploy.sh

→ 相对路径解析成 `k8s/k8s/mysql.yaml` → **不存在** → kubectl 报错
→ 但因为**没有 `set -e`**，7 条 apply **全部失败却全被吞掉**
→ 脚本照样打印 **「✅ 部署完成！」**，退出码 **0**
→ **运维以为部署好了，实际集群里一个资源都没有。**

📌 **静态检查（grep 有没有 `set -e`）发现不了这一类问题** ——
「相对路径 + 没有 set -e」是**组合**才会出事的。
所以这道闸门**真的把脚本跑起来**，用假的 kubectl / docker 观测行为：

| 场景 | 期望 |
|---|---|
| 全部成功 | exit 0，且 apply 的清单集合 == 期望集合 |
| 某个 apply 失败 | exit != 0，且**不得**打印「部署完成」 |
| 从 k8s/ 目录跑（指南的写法） | 与从仓库根跑**结果完全一致** |
| build-images.sh | 构建的镜像集合 == 构建矩阵的镜像集合 |

📌 **和「写完必须反向验证」是同一条纪律**：
本闸门自己也要被反向验证 —— 故意退回缺陷版本，它必须 exit 1。

用法：
    python3 scripts/check-deploy-scripts.py          # 巡检（有问题 → exit 1）
"""

import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S = os.path.join(ROOT, "k8s")

# 📌 **不要手写清单列表** ——
# 📌 手写的清单必然漂移（2026-11-04 实测：这里写死了 7 份，
#    新加 extended-services.yaml 之后 deploy.sh 确实 apply 了，
#    但这个闸门因为“期望值没跟上”而误报“漏了清单”→ **假阳性**）。
# 📌 真相来源 = **k8s/ 目录里实际有哪些 yaml**。
#    （新增清单 → 期望值自动跟上 → 闸门永远只报真问题）
# 📌 namespace.yaml 例外：它不是用 `apply -f` 应用的，
#    而是 `kubectl create namespace ... | kubectl apply -f -`。
def _manifests_in(dirpath, exclude=()):
    if not os.path.isdir(dirpath):
        return []
    out = []
    for fn in sorted(os.listdir(dirpath)):
        if not fn.endswith((".yaml", ".yml")):
            continue
        if fn in exclude:
            continue
        out.append(f"k8s/{fn}")
    return out


# deploy.sh 应该 apply 的全部清单
EXPECTED_MANIFESTS = _manifests_in(K8S, exclude=("namespace.yaml",))

# 真正的部署计划覆盖的应用清单（带 Deployment 的）
APP_MANIFESTS = [
    f for f in _manifests_in(K8S)
    if re.search(r"^\s*kind:\s*Deployment\s*$",
                 open(os.path.join(ROOT, f), encoding="utf-8").read(), re.M)
]

FAKE_KUBECTL = r"""#!/bin/bash
# 假 kubectl：把每次调用记到 $KUBECTL_LOG
echo "$*" >> "$KUBECTL_LOG"

# 注入失败：FAIL_MATCH 出现在参数里就报错退出
if [ -n "${FAIL_MATCH:-}" ] && [[ "$*" == *"$FAIL_MATCH"* ]]; then
  echo "error: injected failure for '$FAIL_MATCH'" >&2
  exit 1
fi

if [ "$1" = "create" ]; then
  echo "apiVersion: v1"
  echo "kind: Namespace"
  echo "metadata:"
  echo "  name: erp"
  exit 0
fi

if [ "$1" = "apply" ]; then
  if [ "$2" = "-f" ] && [ "$3" = "-" ]; then
    echo "namespace/erp applied"
    exit 0
  fi
  if [ -f "$3" ]; then
    echo "applied $3"
    exit 0
  fi
  # 📌 真实的 kubectl 在文件不存在时**会报错并退出非 0** ——
  #    这里必须如实模拟，否则测不出「静默失败」这个 bug。
  echo "error: the path \"$3\" does not exist" >&2
  exit 1
fi

exit 0
"""

FAKE_DOCKER = r"""#!/bin/bash
echo "$*" >> "$DOCKER_LOG"
if [ -n "${FAIL_MATCH:-}" ] && [[ "$*" == *"$FAIL_MATCH"* ]]; then
  echo "error: injected docker failure" >&2
  exit 1
fi
exit 0
"""


def write_fake(path, body):
    with open(path, "w", encoding="utf-8") as f:
        f.write(body)
    os.chmod(path, 0o755)


def run(script, cwd, env_extra=None, timeout=180):
    """跑脚本，返回 (exit_code, stdout+stderr)。"""
    # 📌 **每个场景必须清空日志** —— 否则后一个场景会读到前一个的调用记录，
    #    看起来像「结果不一致」，其实是自己的 bug。
    for name in ("kubectl.log", "docker.log"):
        p = os.path.join(os.environ["_TMP"], name)
        if os.path.exists(p):
            os.remove(p)
    env = dict(os.environ)
    env.update({
        "KUBECTL_LOG": os.path.join(env["_TMP"], "kubectl.log"),
        "DOCKER_LOG": os.path.join(env["_TMP"], "docker.log"),
        "PUSH": "false",          # build-images.sh 只构建不推送
        "FAIL_MATCH": "",         # 默认不注入失败
    })
    if env_extra:
        env.update(env_extra)
    p = subprocess.run(["bash", script], cwd=cwd, env=env,
                       capture_output=True, text=True, timeout=timeout)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def read_log(name):
    path = os.path.join(os.environ["_TMP"], name)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [ln.rstrip("\n") for ln in f if ln.strip()]


def applied_manifests():
    """从 kubectl 日志里取出 apply 的清单路径。"""
    out = []
    for line in read_log("kubectl.log"):
        parts = line.split()
        if len(parts) >= 3 and parts[0] == "apply" and parts[1] == "-f" and parts[2] != "-":
            out.append(parts[2])
    return out


def main():
    problems = []

    with tempfile.TemporaryDirectory() as tmp:
        os.environ["_TMP"] = tmp
        bindir = os.path.join(tmp, "bin")
        os.makedirs(bindir)
        write_fake(os.path.join(bindir, "kubectl"), FAKE_KUBECTL)
        write_fake(os.path.join(bindir, "docker"), FAKE_DOCKER)
        base_path = os.environ["PATH"]
        os.environ["PATH"] = bindir + os.pathsep + base_path

        deploy_sh = os.path.join(K8S, "deploy.sh")
        build_sh = os.path.join(K8S, "build-images.sh")

        # ── 检查 0：静态 —— 仓库里**所有** .sh 都必须有 set -e ──────────
        #    📌 为什么是「所有」：缺陷不是 k8s/deploy.sh 独有的 ——
        #    根目录的 deploy-preparation.sh / quick-start.sh / start-pricing.sh /
        #    start.sh / stop-test.sh / stop.sh 当时**也都没有**，
        #    同样会把「docker compose 起不来 / mvn 编译失败」静默吞掉。
        all_sh = []
        for d in (ROOT, K8S):
            for fn in sorted(os.listdir(d)):
                if fn.endswith(".sh"):
                    all_sh.append(os.path.join(d, fn))
        for script in all_sh:
            body = open(script, encoding="utf-8").read()
            if not re.search(r"^set\s+-[a-zA-Z]*e", body, re.M):
                problems.append(
                    f"{os.path.relpath(script, ROOT)}: 没有 `set -e`\n"
                    f"      → 任何一条命令失败（docker/mvn/kubectl）都会被吞掉，"
                    f"脚本照样报成功")
        # 📌 `start.sh` 曾经手写成 `mvn ...` + `if [ $? -ne 0 ]` ——
        #    有了 set -e 之后 mvn 失败会在**走到 if 之前**中止 → 友好提示丢失。
        #    必须转成 `if ! cmd; then`。
        for script in all_sh:
            body = open(script, encoding="utf-8").read()
            if re.search(r"^\s*if\s*\[\s*\$\?\s*-ne", body, re.M):
                problems.append(
                    f"{os.path.relpath(script, ROOT)}: 用了 `if [ $? -ne 0 ]` 手工判错\n"
                    f"      → 有了 `set -e` 之后，命令失败会在走到 if **之前**就中止\n"
                    f"      → 友好的错误提示丢失；应改成 `if ! cmd; then`")

        # ── 检查 1：全成功路径 + 清单集合 ────────────────────────────────
        rc, out = run(deploy_sh, cwd=K8S)          # 📌 从 k8s/ 跑 —— 指南的写法
        got = applied_manifests()
        if rc != 0:
            problems.append(
                f"k8s/deploy.sh 全成功场景却 exit={rc}\n"
                f"      输出尾部：{out.strip().splitlines()[-3:]}")
        missing = [m for m in EXPECTED_MANIFESTS if m not in got]
        if missing:
            problems.append(
                f"k8s/deploy.sh 没有 apply 这些清单: {missing}\n"
                f"      → 那些服务的 Deployment 在集群里**根本不存在**\n"
                f"      实际 apply 了: {got}")
        if "does not exist" in out:
            problems.append(
                "k8s/deploy.sh 运行时出现「文件不存在」错误（相对路径解析错了？）\n"
                f"      → 实际 apply: {got}\n"
                f"      📌 deploy.sh 必须能从**任意目录**跑（文档教的是 cd k8s && ./deploy.sh）")

        # ── 检查 2：失败路径 —— 必须 exit != 0，且不得报成功 ───────────────
        rc2, out2 = run(deploy_sh, cwd=K8S, env_extra={"FAIL_MATCH": "core-services.yaml"})
        if rc2 == 0:
            problems.append(
                "k8s/deploy.sh 在 apply 失败时仍然 exit 0\n"
                "      → 失败被静默吞掉，脚本照样打印「部署完成」\n"
                "      📌 必须有 `set -e`（或显式检查退出码）")
        if "部署完成" in out2:
            problems.append(
                "k8s/deploy.sh 在 apply 失败时仍然打印「部署完成」\n"
                "      → 运维会以为部署成功")

        # ── 检查 3：从仓库根跑，结果必须一致 ────────────────────────────
        rc3, out3 = run(deploy_sh, cwd=ROOT)
        got3 = applied_manifests()
        if rc3 != 0 or sorted(got3) != sorted(EXPECTED_MANIFESTS):
            problems.append(
                f"k8s/deploy.sh 从仓库根跑的结果和从 k8s/ 跑的不一致\n"
                f"      从 k8s/: exit={rc}, applied={len(got)}\n"
                f"      从根目录: exit={rc3}, applied={got3}\n"
                f"      📌 脚本必须自己 cd 到仓库根，不能依赖调用方的 CWD")

        # ── 检查 4：build-images.sh 构建的镜像 == 构建矩阵 ───────────────
        rc4, out4 = run(build_sh, cwd=ROOT)
        built = []
        for line in read_log("docker.log"):
            if line.startswith("build "):
                m = re.search(r"-t\s+(\S+)", line)
                if m:
                    built.append(m.group(1))
        if rc4 != 0:
            problems.append(f"k8s/build-images.sh 全成功场景却 exit={rc4}")
        matrix = subprocess.run(
            [sys.executable, os.path.join(ROOT, "scripts", "k8s-image-matrix.py")],
            capture_output=True, text=True, cwd=ROOT)
        try:
            want = {f"{r['image']}:latest" for r in json.loads(matrix.stdout).get("include", [])}
        except Exception as e:
            want = set()
            problems.append(f"读不到构建矩阵（k8s-image-matrix.py）：{e}")
        if want:
            got_tags = set(built)
            if got_tags != want:
                problems.append(
                    f"k8s/build-images.sh 构建的镜像集合 != 构建矩阵\n"
                    f"      少构建: {sorted(want - got_tags)}\n"
                    f"      多构建: {sorted(got_tags - want)}\n"
                    f"      → 少构建的镜像 CI 不会产出 → 部署时 ImagePullBackOff")

        # ── 检查 5：build-images.sh 失败必须中断 ─────────────────────────
        # 📌 FAIL_MATCH 必须匹配假 docker 收到的**真实参数** ——
        #    `docker build -f docker/Dockerfile.service ...` 里并没有 "docker build" 这个连续子串。
        rc5, out5 = run(build_sh, cwd=ROOT, env_extra={"FAIL_MATCH": "Dockerfile.service"})
        if rc5 == 0:
            problems.append(
                "k8s/build-images.sh 在 docker build 失败时仍然 exit 0\n"
                "      → 失败被吞掉，后面会继续打印「镜像构建完成」")

    if problems:
        print("🔴 部署脚本有问题：")
        for p in problems:
            print(f"   • {p}")
        print(f"\n   共 {len(problems)} 处问题")
        return 1

    print("✅ k8s/deploy.sh：")
    print(f"   • 全成功场景 exit 0，apply 了全部 {len(EXPECTED_MANIFESTS)} 份清单")
    print("   • 注入 apply 失败 → exit≠0，且不再打印「部署完成」")
    print("   • 从 k8s/ 跑和从仓库根跑结果一致")
    print("✅ k8s/build-images.sh：")
    print("   • 构建的镜像集合 == 构建矩阵")
    print("   • 注入 docker build 失败 → exit≠0")
    print("✅ 仓库里所有 .sh 都有 `set -e`，且没有 `$?` 手工判错")
    return 0


if __name__ == "__main__":
    sys.exit(main())
