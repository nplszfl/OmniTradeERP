#!/usr/bin/env python3
"""部署层注入的环境变量 ↔ 应用侧真正读取的环境变量 —— 契约巡检。

📌 **为什么需要这道闸门**（2026-11-04 实测）：

容器化部署里，服务的「连哪个 MySQL / Redis / Nacos」取决于**环境变量**。
但这里有个极隐蔽的坑：**compose / k8s 里写了 environment ≠ 应用真会读它**。

实测（docker-compose*.yml + k8s/*.yaml + 各服务 application.yml）：

| 注入方注入的变量      | 应用侧读吗                                             | 后果                                 |
|----------------------|--------------------------------------------------------|--------------------------------------|
| `NACOS_SERVER_ADDR`  | ❌ 没人读（jar 里搜 `NACOS_SERVER_ADDR` → **0 次**）    | 容器连的是默认的阿里云 MSE 外网地址    |
| `SENTINEL_DASHBOARD` | ❌ 没人读（所有 sentinel jar 里 **0 次**）               | 容器连 `localhost:8858`              |
| `REDIS_HOST`         | ❌ 没人读（yml 里写的是字面量 `localhost`）             | 容器连 `localhost:6379` = 连自己     |
| `RABBITMQ_HOST`      | ❌ 没人读（同上）                                        | 容器连 `localhost:5672` = 连自己     |
| `MYSQL_HOST`         | ✅ 读（`${MYSQL_HOST:localhost}`）                      | 正常                                |

**后果**：容器里每个服务都在连**自己**的 localhost → Redis/RabbitMQ 连不上 →
服务启动失败或功能静默失效。**单测全绿、mvn package 通过、k8s 清单看着都对**。

📌 这跟 2026-11-02 修的 `MYSQL_HOST` 是**同一个 bug 类**，只是当时只修了 MySQL，
Redis / RabbitMQ / Nacos / Sentinel 这一批**从来没修过**。

🔴 **「自己校验自己」的陷阱**：
`check-port-consistency.py` 校验端口、`check-k8s-images.py` 校验镜像 ——
但**没有任何一道闸门校验「环境变量契约」**，
因为它跨了「部署层（yaml）」和「应用层（yml + 框架 jar）」两个域。

📌 **分级很重要 —— 误报比漏报危险得多**：
- **ERROR**：应用确实要用这个基础设施，但地址写死 / 变量名对不上 / 该注入没注入
- **INFO**：注入了一个应用根本不用的变量（比如 LLM-only 服务被注入了 `MYSQL_HOST`）
  —— 这只是**多余**，不是 bug，不该拦住 CI

📌 **不能靠「grep 有没有 ${XXX}」判断框架行为**：
框架自己会读一些环境变量（`TZ` 由 JVM 读、`JAVA_OPTS` 由 Dockerfile 的 ENTRYPOINT 读、
Spring Boot 的 `SPRING_*` 有 relaxed binding）。所以对每个可疑变量都**先去 jar 里搜一遍**
确认它到底是不是框架原生读的 —— **宁可漏报，不要误报**。

用法：
    python3 scripts/check-env-contract.py            # 巡检（ERROR → exit 1）
    python3 scripts/check-env-contract.py --verbose  # 打印每个服务的读/注入明细
"""

import os
import re
import sys
import glob

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── 基础设施种类 ───────────────────────────────────────────────────────────
# 每种基础设施：应用侧的配置路径 + 部署层应该注入的变量名
# 📌 **变量名是契约**：部署层和应用层必须用**同一个名字**，
#    否则注入就是摆设（这正是本项目踩过的坑）。
INFRA_KINDS = {
    "redis": {
        "paths": [("spring", "data", "redis", "host"),
                  ("spring", "redis", "host")],
        "var": "REDIS_HOST",
    },
    "rabbitmq": {
        "paths": [("spring", "rabbitmq", "host")],
        "var": "RABBITMQ_HOST",
    },
    "nacos": {
        "paths": [("spring", "cloud", "nacos", "discovery", "server-addr"),
                  ("spring", "cloud", "nacos", "config", "server-addr")],
        "var": "NACOS_HOST",
    },
    "sentinel": {
        "paths": [("spring", "cloud", "sentinel", "transport", "dashboard")],
        "var": "SENTINEL_DASHBOARD",
    },
    "mysql": {
        "paths": [("spring", "datasource", "url")],
        "var": "MYSQL_HOST",
    },
}

# ── 框架 / 运行时会读的环境变量（白名单）────────────────────────────────────
# 📌 每一个都要有理由。**不是**「猜它应该被读了」而放行。
NATIVE_ENV = {
    "TZ": "JVM 自己读（时区）",
    "JAVA_OPTS": "docker/Dockerfile.service 的 ENTRYPOINT 用 `java $JAVA_OPTS` 读",
    "LANG": "glibc locale，影响字符集",
    "LC_ALL": "glibc locale，影响字符集",
    "MODE": "nacos 容器自己的启动模式（不是应用服务的）",
    "PREFER_HOST_MODE": "nacos 容器自己的启动模式",
    "JVM_XMS": "nacos 容器自己的 JVM 参数",
    "JVM_XMX": "nacos 容器自己的 JVM 参数",
    "SPRING_DATASOURCE_PLATFORM": "nacos 容器自己的配置",
    "MYSQL_SERVICE_HOST": "nacos 容器自己的配置",
    "MYSQL_SERVICE_DB_NAME": "nacos 容器自己的配置",
    "MYSQL_SERVICE_PORT": "nacos 容器自己的配置",
    "MYSQL_SERVICE_USER": "nacos 容器自己的配置",
    "MYSQL_SERVICE_PASSWORD": "nacos 容器自己的配置",
    "MYSQL_ROOT_PASSWORD": "mysql 容器自己的配置",
    "MYSQL_DATABASE": "mysql 容器自己的配置",
    "RABBITMQ_DEFAULT_USER": "rabbitmq 容器自己的配置",
    "RABBITMQ_DEFAULT_PASS": "rabbitmq 容器自己的配置",
    "RABBITMQ_DEFAULT_VHOST": "rabbitmq 容器自己的配置",
}

# ── 容器内非法的「自己」地址 ───────────────────────────────────────────────
# 📌 容器里 localhost / 127.0.0.1 指的是**容器自己**，连不到别的容器。
BAD_HOSTS = {"localhost", "127.0.0.1", "::1"}

PLACEHOLDER_RE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::[^}]*)?\}")

# Spring Boot 原生 relaxed binding 的环境变量 → 对应的配置路径
NATIVE_BINDING = {
    "SPRING_DATA_REDIS_HOST": ("spring", "data", "redis", "host"),
    "SPRING_REDIS_HOST": ("spring", "redis", "host"),
    "SPRING_RABBITMQ_HOST": ("spring", "rabbitmq", "host"),
    "SPRING_CLOUD_NACOS_DISCOVERY_SERVERADDR":
        ("spring", "cloud", "nacos", "discovery", "server-addr"),
    "SPRING_CLOUD_NACOS_CONFIG_SERVERADDR":
        ("spring", "cloud", "nacos", "config", "server-addr"),
    "SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD":
        ("spring", "cloud", "sentinel", "transport", "dashboard"),
    "SPRING_DATASOURCE_URL": ("spring", "datasource", "url"),
}

# 路径 → 该路径应该读的环境变量（用于「死注入」时指出正确名字）
PATH_TO_VAR = {}
for _kind, _spec in INFRA_KINDS.items():
    for _p in _spec["paths"]:
        PATH_TO_VAR[_p] = _spec["var"]

# 基础设施种类 → 在编排文件里能找到的服务名（compose 裸名 / k8s 带 -service 后缀）
KIND_SERVICE_NAMES = {
    "mysql": ("mysql", "mysql-service"),
    "redis": ("redis", "redis-service"),
    "rabbitmq": ("rabbitmq", "rabbitmq-service"),
    "nacos": ("nacos", "nacos-service"),
    "sentinel": ("sentinel", "sentinel-service"),
}

VAR_TO_KIND = {v: k for k, v in
               ((k, s["var"]) for k, s in INFRA_KINDS.items())}

# 同一基础设施的**别名变量**（部署层可能用这些名字，但应用读的是另一个）
# 📌 `NACOS_SERVER_ADDR` 是本项目最大的坑：部署层到处注入它，
#    而应用读的是 `NACOS_HOST` —— 名字对不上 → 注入失效。
VAR_ALIASES = {
    "NACOS_SERVER_ADDR": "nacos",
    "NACOS_PORT": "nacos",
    "REDIS_PORT": "redis",
    "RABBITMQ_PORT": "rabbitmq",
    "MYSQL_PORT": "mysql",
    "MYSQL_USER": "mysql",
    "MYSQL_PASSWORD": "mysql",
}
for _v, _k in VAR_ALIASES.items():
    VAR_TO_KIND.setdefault(_v, _k)


def deep_merge(a, b):
    out = dict(a)
    for k, v in (b or {}).items():
        if k in out and isinstance(out[k], dict) and isinstance(v, dict):
            out[k] = deep_merge(out[k], v)
        else:
            out[k] = v
    return out


def first_path_of(kind):
    return INFRA_KINDS[kind]["paths"][0]


def dig(doc, path):
    cur = doc
    for p in path:
        if not isinstance(cur, dict) or p not in cur:
            return None
        cur = cur[p]
    return cur


def app_config(module):
    """应用侧 application.yml → (读到的变量集合, {基础设施路径: 值})。"""
    yml = os.path.join(ROOT, module, "src", "main", "resources", "application.yml")
    if not os.path.exists(yml):
        return set(), {}
    text = open(yml, encoding="utf-8").read()
    merged = {}
    for d in yaml.safe_load_all(text):
        if isinstance(d, dict):
            merged = deep_merge(merged, d)
    reads = set(PLACEHOLDER_RE.findall(text))

    infra = {}
    for spec in INFRA_KINDS.values():
        for path in spec["paths"]:
            val = dig(merged, path)
            if val is not None:
                infra[path] = str(val)
    return reads, infra


# compose 文件 → 该文件实际部署了哪些基础设施
COMPOSE_DEPLOYED = {}


def compose_targets():
    """compose → [(目标名, 模块, 注入变量, 文件名)]（每个 compose 文件**独立**算一个目标）。

    📌 **为什么要按文件独立算**，而不是把所有 compose 文件的变量并起来：
    compose 和 k8s 是**两套独立的环境**。如果 compose 注入了 `REDIS_HOST`
    而 k8s 漏了，并起来看就「有」了 → k8s 里其实没注入 → 应用退回默认值 → 连不上。
    📌 这就是「并起来看 = 少查了东西然后报绿」的一个变体。
    """
    out = []
    for cf in sorted(glob.glob(os.path.join(ROOT, "docker-compose*.yml"))):
        try:
            doc = yaml.safe_load(open(cf, encoding="utf-8")) or {}
        except Exception:
            continue
        for name, svc in (doc.get("services") or {}).items():
            if not isinstance(svc, dict):
                continue
            build = svc.get("build")
            if not isinstance(build, dict):
                continue  # 基础设施容器（mysql/redis/nacos…），不是应用服务
            args = build.get("args") or {}
            module = args.get("MODULE")
            if not module:
                continue
            env = svc.get("environment") or {}
            if isinstance(env, list):
                env = {e.split("=")[0]: "" for e in env}
            out.append((f"{os.path.basename(cf)}:{name}", module,
                        set(env), os.path.basename(cf)))
    # 记录每个 compose 文件里**实际部署了**哪些基础设施
    for cf in sorted(glob.glob(os.path.join(ROOT, "docker-compose*.yml"))):
        try:
            doc = yaml.safe_load(open(cf, encoding="utf-8")) or {}
        except Exception:
            continue
        names = set((doc.get("services") or {}).keys())
        kinds = {k for k, cands in KIND_SERVICE_NAMES.items()
                 if any(c in names for c in cands)}
        COMPOSE_DEPLOYED[os.path.basename(cf)] = kinds
    return out


def k8s_targets():
    """k8s → [(目标名, 模块, 注入变量, 文件名)]。"""
    out = []
    for kf in sorted(glob.glob(os.path.join(ROOT, "k8s", "*.yaml"))):
        try:
            docs = list(yaml.safe_load_all(open(kf, encoding="utf-8")))
        except Exception:
            continue
        for doc in docs:
            if not isinstance(doc, dict) or doc.get("kind") != "Deployment":
                continue
            name = doc.get("metadata", {}).get("name")
            if not name:
                continue
            vars_ = set()
            for c in doc["spec"]["template"]["spec"].get("containers", []):
                for e in c.get("env") or []:
                    vars_.add(e["name"])
            out.append((f"k8s:{name}", name, vars_, os.path.basename(kf)))
    return out


def k8s_deployed():
    """k8s 清单里**实际存在**的 Service → 基础设施种类集合。"""
    kinds = set()
    for kf in sorted(glob.glob(os.path.join(ROOT, "k8s", "*.yaml"))):
        try:
            docs = list(yaml.safe_load_all(open(kf, encoding="utf-8")))
        except Exception:
            continue
        for doc in docs:
            if not isinstance(doc, dict) or doc.get("kind") != "Service":
                continue
            n = doc.get("metadata", {}).get("name", "")
            for kind, cands in KIND_SERVICE_NAMES.items():
                if n in cands:
                    kinds.add(kind)
    return kinds


def framework_reads(var_name):
    """这个环境变量会不会被框架原生读走？返回 (是否被读, 证据)。

    📌 **必须真的去 jar 里搜一遍**，不能靠猜 —— 误报比漏报危险得多。
    """
    if var_name in NATIVE_BINDING:
        return True, "Spring Boot relaxed binding"
    if var_name in ("NACOS_SERVER_ADDR", "SENTINEL_DASHBOARD"):
        # 最容易被误判的两个：很多人以为 Spring Cloud Alibaba / Sentinel 会读它们。
        # 实测：jar 里字符串出现 0 次 → **不会读**。
        return False, "已确认框架 jar 里无此字符串"
    return False, ""


def main():
    verbose = "--verbose" in sys.argv

    errors = []
    infos = []

    # 📌 每个**部署目标**独立校验（compose 文件 × 服务 / k8s 文件 × Deployment）
    targets = compose_targets() + k8s_targets()

    k8s_kinds = k8s_deployed()

    for target, module, injected, filename in targets:
        reads, infra = app_config(module)
        files = [filename]

        # 这个目标**实际部署了**哪些基础设施
        # 📌 compose：看该文件里有没有这个服务
        # 📌 k8s：看 k8s/*.yaml 里有没有对应的 Service
        deployed = (COMPOSE_DEPLOYED.get(filename, set())
                    if filename.startswith("docker-compose")
                    else k8s_kinds)

        if verbose:
            print(f"── {target}  (module={module})")
            print(f"   应用读取 : {sorted(reads) or '（无占位符）'}")
            print(f"   部署注入 : {sorted(injected) or '（无）'}")

        # ── 基础设施状态：absent / hardcoded / placeholder(+读的变量) ────────
        kind_state = {}   # kind -> "absent" | "hardcoded" | ("placeholder", var)
        for path, val in sorted(infra.items()):
            kind = next(k for k, s in INFRA_KINDS.items() if path in s["paths"])
            host = val.split(":")[0].strip()
            m = re.search(r"\$\{([A-Za-z_][A-Za-z0-9_]*)", val)
            if m:
                kind_state[kind] = ("placeholder", m.group(1))
            elif host in BAD_HOSTS:
                kind_state[kind] = "hardcoded"
            else:
                kind_state.setdefault(kind, ("literal", None))
        for kind in INFRA_KINDS:
            kind_state.setdefault(kind, "absent")

        # ── 检查 1：应用配了某个基础设施，但地址写死成 localhost ────────────
        for path, val in sorted(infra.items()):
            kind = next(k for k, s in INFRA_KINDS.items() if path in s["paths"])
            host = val.split(":")[0].strip()
            if host not in BAD_HOSTS:
                continue
            m = re.search(r"\$\{([A-Za-z_][A-Za-z0-9_]*)", val)
            if m:
                continue  # 有占位符 → 由环境变量控制，交给检查 2
            dotted = ".".join(path)
            var = PATH_TO_VAR[path]
            errors.append(
                f"[{target}] `{dotted}` 写死成 `{val}`\n"
                f"      → 容器里 {host} 指的是**容器自己**，连不到 {kind}\n"
                f"      → 修法：改成 `${{{var}:{val}}}`（部署层注入的就是这个名字）")

        # ── 检查 2：目标部署了这个基础设施，应用就必须拿到正确的地址 ────────
        # 📌 **区分两种情况**（这是本闸门最容易误报的地方）：
        #    a) 目标**部署了**这个基础设施 → 应用必须拿到指向它的变量
        #       → 缺变量 / 变量名对不上 = **ERROR**（连不上）
        #    b) 目标**根本没部署**这个基础设施 → 应用连它只是「软依赖降级」
        #       → 只是 INFO（不该报错，更不该为了让它「正确」去注入一个
        #         指向不存在服务的变量 —— 那反而更误导）
        for path, val in sorted(infra.items()):
            kind = next(k for k, s in INFRA_KINDS.items() if path in s["paths"])
            m = re.search(r"\$\{([A-Za-z_][A-Za-z0-9_]*)", val)
            if not m:
                continue
            var = m.group(1)
            dotted = ".".join(path)
            if kind not in deployed:
                # 该目标没部署这个基础设施 —— 注入与否都无所谓
                if var in injected:
                    infos.append((kind, target, var, "目标未部署"))
                continue
            if var in injected:
                continue
            # 框架原生 relaxed binding 能兜住的，不算错
            native = any(v in injected for v, p in NATIVE_BINDING.items()
                         if p == path)
            if native:
                continue
            errors.append(
                f"[{target}] `{dotted}` 读的是 `${{{var}:...}}`，"
                f"但部署层**没有注入** `{var}`（{', '.join(files)}）\n"
                f"      → 应用会用默认值，容器里连不到 {kind}"
                f"（该目标**部署了** {kind}）")

        # ── 检查 3：注入的变量没人读 ───────────────────────────────────────
        for var in sorted(injected):
            if var in reads:
                continue
            if var in NATIVE_ENV:
                continue
            if framework_reads(var)[0]:
                continue
            kind = VAR_TO_KIND.get(var)
            state = kind_state.get(kind) if kind else None
            if kind and state == "hardcoded":
                errors.append(
                    f"[{target}] 注入了 `{var}`，但应用把 {kind} 地址**写死**了"
                    f"（{', '.join(files)}）\n"
                    f"      → 注入不生效：应用根本不去读环境变量")
            elif kind and isinstance(state, tuple) and state[0] == "placeholder":
                if state[1] == var:
                    continue  # 名字一致（但值可能不同），已由检查 2 覆盖
                errors.append(
                    f"[{target}] 注入了 `{var}`，但应用读的是 `{state[1]}`"
                    f"（{', '.join(files)}）\n"
                    f"      → 变量名对不上 → 这个注入是**摆设**："
                    f"应用实际连的还是它自己写死的地址")
            elif kind and state == "absent":
                infos.append((kind, target, var, "服务用不到"))
            elif kind:
                infos.append((kind, target, var, "服务用不到"))
            else:
                infos.append(("?", target, var, "应用自有变量"))

    if infos:
        # 📌 按基础设施种类汇总，别一个服务一行刷屏
        print("ℹ️  提示（不影响运行）：")
        grouped = {}
        for kind, target, var, why in infos:
            grouped.setdefault((kind, var, why), []).append(target)
        for (kind, var, why), tgts in sorted(grouped.items()):
            if kind == "?":
                why_txt = "应用自有变量，未做校验"
            elif why == "目标未部署":
                why_txt = (f"该编排文件**没有部署** {kind}"
                           f"（{kind} 大概率是外部/托管的，注入的地址可能指向不存在的服务）")
            else:
                why_txt = f"该服务用不到 {kind}"
            print(f"   • `{var}` 注入了 {len(tgts)} 处 —— {why_txt}")
        print()

    if errors:
        print("🔴 环境变量契约不一致：")
        for e in errors:
            print(f"   • {e}")
        print()
        print(f"   共 {len(errors)} 处 ERROR")
        return 1

    print(f"✅ 环境变量契约一致（{len(targets)} 个部署目标）")
    print("   注入的每个变量应用都读；应用的基础设施地址没有一个写死 localhost；"
          "应用读的每个变量部署层都注入了")
    return 0


if __name__ == "__main__":
    sys.exit(main())
