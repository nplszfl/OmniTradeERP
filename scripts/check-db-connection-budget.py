#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
闸门：数据库连接数预算 —— 「服务要用的连接」必须 ≤ 「MySQL 允许的连接」

🔴 **为什么需要这道闸门**（2026-11-14 实测推导 + 运行时复现）
--------------------------------------------------------------------
MySQL 8.0 的 `max_connections` 默认只有 **151**。而本项目的连接需求是：

    每个服务实例 = Hikari `maximum-pool-size`（+ JPA/MyBatis 的额外连接）
    k8s 清单里 `replicas: 2`

实测数据：22 个服务模块的 `maximum-pool-size` 合计 = **280**（单副本）
→ 全部按 2 副本 = **560 条** → 超出默认上限 **3.7 倍**。

运行时实证：探针环境 22 个服务并发启动时，
`erp-social-commerce-service` 直接 `Too many connections` → **进程起不来**。

📌 **「所有闸门全绿 + 1047 个用例全绿」也发现不了这个缺陷** ——
因为它只在「服务真的连上库」时才暴露，而 CI 里根本不连数据库。
→ 这类缺陷必须靠**静态推算 + 运行时证据**两条腿一起上。

📌 **口径说明（避免假红 / 假绿）**
- 池大小来源 = 各模块 `src/main/resources/application.yml` 的 `maximum-pool-size`；
  **没配就是 Hikari 默认 10**（不是 0，也不是「不占连接」）。
- 副本数来源 = `k8s/*.yaml` 里每个 Deployment 的 `replicas`。
- MySQL 上限来源 = `k8s/mysql.yaml` 的容器 `args` 里 `--max-connections=`。
  **没配 = MySQL 默认 151**（跟官方默认值一致，不猜）。
- 预算按「**所有服务的连接上限之和**」算，而不是按「同时在线的实际连接数」——
  因为 Hikari 是**懒加载**但**预热**的，滚动发布时新旧 Pod 会同时存在 → 取上限是对的。
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S_DIR = os.path.join(ROOT, "k8s")

# MySQL 8.0 的官方默认值（不配 --max-connections 就是它）
MYSQL_DEFAULT_MAX_CONNECTIONS = 151
# JPA + MyBatis 在 Hikari 之外还会额外开连接，按经验值给每个实例留一点余量
EXTRA_CONNECTIONS_PER_INSTANCE = 2

VIOLATIONS = []
NOTES = []


def service_modules():
    """列出所有带 application.yml 的服务模块。"""
    out = []
    for name in sorted(os.listdir(ROOT)):
        d = os.path.join(ROOT, name)
        if not os.path.isdir(d):
            continue
        if not name.startswith("erp-"):
            continue
        yml = os.path.join(d, "src", "main", "resources", "application.yml")
        if os.path.exists(yml):
            out.append((name, yml))
    return out


def pool_size(yml_path):
    """Hikari maximum-pool-size；没配 = Hikari 默认 10。"""
    text = open(yml_path, encoding="utf-8").read()
    m = re.search(r"maximum-pool-size:\s*(\d+)", text)
    return int(m.group(1)) if m else 10


def strip_comments(text):
    out = []
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("#"):
            continue
        out.append(line)
    return "\n".join(out)


def k8s_replicas():
    """k8s 清单里的 (服务名, replicas) 列表。"""
    pairs = []
    if not os.path.isdir(K8S_DIR):
        return pairs
    for fn in sorted(os.listdir(K8S_DIR)):
        if not fn.endswith((".yaml", ".yml")):
            continue
        path = os.path.join(K8S_DIR, fn)
        text = strip_comments(open(path, encoding="utf-8").read())
        # Deployment/StatefulSet 块里各取 name + replicas
        cur_name = None
        for line in text.splitlines():
            m = re.match(r"\s*-\s*name:\s*([A-Za-z0-9-]+)", line)
            if m and cur_name is None:
                cur_name = m.group(1)
            m2 = re.match(r"\s*replicas:\s*(\d+)", line)
            if m2 and cur_name:
                pairs.append((cur_name, int(m2.group(1)), fn))
                cur_name = None
    return pairs


def mysql_max_connections():
    """k8s/mysql.yaml 里 --max-connections= 的值；没配 = MySQL 默认 151。"""
    path = os.path.join(K8S_DIR, "mysql.yaml")
    if not os.path.exists(path):
        return None, "k8s/mysql.yaml 不存在"
    text = strip_comments(open(path, encoding="utf-8").read())
    m = re.search(r"--max-connections=(\d+)", text)
    if m:
        return int(m.group(1)), "k8s/mysql.yaml 的 --max-connections"
    return MYSQL_DEFAULT_MAX_CONNECTIONS, "MySQL 默认值（mysql.yaml 未配 --max-connections）"


def compose_budget(pool_by_module):
    """逐个 compose 文件算预算：应用服务数 × 池大小 vs 该文件的 MySQL 上限。"""
    import yaml

    results = []
    for fn in sorted(os.listdir(ROOT)):
        if not (fn.startswith("docker-compose") and fn.endswith((".yml", ".yaml"))):
            continue
        path = os.path.join(ROOT, fn)
        try:
            doc = yaml.safe_load(open(path, encoding="utf-8")) or {}
        except Exception as e:
            NOTES.append(f"{fn}: YAML 解析失败，跳过（{e}）")
            continue
        svcs = doc.get("services") or {}
        if not svcs:
            continue
        # 该 compose 里的 MySQL 上限
        limit = None
        my_name = None
        for sname, sdef in svcs.items():
            if not isinstance(sdef, dict):
                continue
            img = str(sdef.get("image", ""))
            if img.startswith("mysql"):
                my_name = sname
                cmd = sdef.get("command") or sdef.get("args") or []
                if isinstance(cmd, str):
                    cmd = [cmd]
                for c in cmd:
                    m = re.search(r"--max-connections=(\d+)", str(c))
                    if m:
                        limit = int(m.group(1))
                        break
        if limit is None:
            limit = MYSQL_DEFAULT_MAX_CONNECTIONS
        # 应用服务 → 池大小
        total = 0
        cnt = 0
        for sname, sdef in svcs.items():
            if not isinstance(sdef, dict):
                continue
            if "build" not in sdef and "image" not in sdef:
                continue
            if str(sdef.get("image", "")).startswith("mysql"):
                continue
            norm = sname.replace("_", "-")
            for mod, p in pool_by_module.items():
                if norm.endswith(mod.replace("erp-", "")) or mod.replace("erp-", "") in norm:
                    total += p + EXTRA_CONNECTIONS_PER_INSTANCE
                    cnt += 1
                    break
        if cnt == 0:
            continue
        results.append((fn, cnt, total, limit, my_name))
    return results


def main():
    mods = service_modules()
    if not mods:
        print("❌ 没找到任何服务模块（application.yml 一个都没扫到）—— 判据可能根本没在读仓库")
        return 1

    per_instance = 0
    detail = []
    for name, yml in mods:
        ps = pool_size(yml)
        per_instance += ps + EXTRA_CONNECTIONS_PER_INSTANCE
        detail.append((name, ps))
    pool_by_module = dict(detail)

    reps = k8s_replicas()
    if not reps:
        NOTES.append("k8s 清单里没读到 replicas → 按 1 副本预算算（可能低估）")
        max_rep = 1
    else:
        max_rep = max(r for _, r, _ in reps)

    limit, src = mysql_max_connections()
    if limit is None:
        VIOLATIONS.append(f"MySQL 连接上限无法确定：{src}")
        limit = 0

    need = per_instance * max_rep
    print(f"📋 服务模块 {len(mods)} 个，Hikari 池合计 + 每实例 {EXTRA_CONNECTIONS_PER_INSTANCE} 条额外连接")
    print(f"   单副本连接上限 = {per_instance} × 副本数 {max_rep} = **{need}**")
    print(f"📋 MySQL 上限 = **{limit}**（来源：{src}）")
    print(f"   k8s 清单 replicas 最大值 = {max_rep}")

    if need > limit:
        VIOLATIONS.append(
            f"连接需求 **{need}** > MySQL 上限 **{limit}**（{src}）→ 必然 `Too many connections`"
        )

    if limit == MYSQL_DEFAULT_MAX_CONNECTIONS:
        VIOLATIONS.append(
            f"k8s/mysql.yaml 没显式配置 --max-connections → 沿用默认 **{limit}**，"
            f"而需求是 {need} → 生产部署必炸"
        )

    # ---- compose 侧 ----
    print("\n📋 compose 文件预算：")
    cres = compose_budget(pool_by_module)
    if not cres:
        print("   （没扫到带应用服务的 compose）")
    for fn, cnt, tot, lim, myn in cres:
        mark = "✅" if tot <= lim else "❌"
        print(f"   {mark} {fn:32} 应用 {cnt:>2} 个 → 需求 {tot:>4} vs 上限 {lim:>4}"
              + ("" if lim != MYSQL_DEFAULT_MAX_CONNECTIONS else "（默认值）"))
        if tot > lim:
            VIOLATIONS.append(
                f"{fn}：连接需求 **{tot}** > MySQL 上限 **{lim}**"
                + ("（用默认值，必须显式配 --max-connections）" if lim == MYSQL_DEFAULT_MAX_CONNECTIONS else "")
                + " → 启动期必然 Too many connections"
            )

    if VIOLATIONS:
        print(f"\n❌ 发现 {len(VIOLATIONS)} 处连接预算隐患：")
        for v in VIOLATIONS:
            print(f"   ❌ {v}")
        return 1

    print(f"\n✅ 连接预算充足（{need} ≤ {limit}，余量 {limit - need}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
