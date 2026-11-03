#!/usr/bin/env python3
"""从 k8s 清单生成「要构建哪些镜像」的 GitHub Actions matrix。

📌 **为什么需要它**：
CI 原来的 `docker-build` job 只构建 **3 个镜像**（gateway / order / frontend），
但 `k8s/*.yaml` 引用了 **13 个 ERP 镜像**。
→ 一旦真的开始部署，另外 10 个镜像**从来没被构建过、也从来没被推送过**
→ `ImagePullBackOff` → Pod 起不来。

📌 **矩阵必须从 k8s 清单反推，不能手写** ——
手写一份模块清单 = 多一个会漂移的事实来源：
以后往 k8s 加一个服务，CI 不会自动跟着构建 → 回到老问题。
→ 本脚本直接读 k8s/*.yaml 的 `image:` → 缺什么自动补什么。

用法：
    python3 scripts/k8s-image-matrix.py            # 打印 JSON matrix
    python3 scripts/k8s-image-matrix.py --check    # 只校验，不输出 matrix
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S_DIR = os.path.join(ROOT, "k8s")

IMAGE_PREFIX = "nplszfl/"


def modules():
    pom = open(os.path.join(ROOT, "pom.xml"), encoding="utf-8").read()
    return set(re.findall(r"<module>([^<]+)</module>", pom))


def port_of(module):
    """模块的 server.port —— 唯一事实来源是 application.yml"""
    yml = os.path.join(ROOT, module, "src/main/resources/application.yml")
    if not os.path.exists(yml):
        return None
    m = re.search(r"^\s*port:\s*(\d+)", open(yml, encoding="utf-8").read(), re.M)
    return m.group(1) if m else None


def collect():
    """k8s 里引用的 ERP 镜像（去重、排序）"""
    names = set()
    for f in sorted(os.listdir(K8S_DIR)):
        if not f.endswith((".yaml", ".yml")):
            continue
        text = open(os.path.join(K8S_DIR, f), encoding="utf-8").read()
        for m in re.finditer(r"^\s*image:\s*[\"']?([^\s\"'#]+)", text, re.M):
            img = m.group(1)
            if not img.startswith(IMAGE_PREFIX):
                continue
            names.add(img.split("@")[0].split(":")[0])
    return sorted(names)


def main():
    check_only = "--check" in sys.argv
    mods = modules()
    rows = []
    problems = []

    for name in collect():
        module = name[len(IMAGE_PREFIX):]
        if module not in mods:
            problems.append(f"镜像 `{name}` → 模块 `{module}` 在根 pom 里不存在")
            continue
        port = port_of(module)
        if not port:
            problems.append(f"镜像 `{name}` → 模块 `{module}` 没有 application.yml / 没有端口")
            continue
        rows.append({"module": module, "port": port, "image": name})

    if problems:
        print("🔴 无法生成构建矩阵：")
        for p in problems:
            print(f"   {p}")
        return 1

    matrix = {"include": rows}

    if check_only:
        print(f"✅ k8s 引用的 {len(rows)} 个 ERP 镜像都能生成构建条目")
        for r in rows:
            print(f"   {r['image']}  MODULE={r['module']}  PORT={r['port']}")
        return 0

    # 📌 一行 JSON —— CI 里用 `echo "matrix=$(...)" >> $GITHUB_OUTPUT` 接收
    print(json.dumps(matrix, ensure_ascii=False, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
