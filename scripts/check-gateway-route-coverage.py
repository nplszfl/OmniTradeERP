#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""网关路由 ↔ 后端 controller 接口 覆盖率巡检。

背景
----
`check-gateway-strip.py` 查的是「**配了路由但改写不对**」：
路由存在、但 StripPrefix/RewritePath 与 controller 根路径对不上 → 每个请求 404。

本脚本查的是**反方向**，而且是**端到端**的：

  前端 api/*.ts 的请求路径  ──►  网关路由  ──►  服务实际收到的路径  ──►  controller 接口

三段里**任何一段断掉**，用户看到的都是 **404** ——
而「前端没接这个接口」和「接了但 404」在监控上**长得一模一样**。

为什么现有闸门抓不到
--------------------
- `check-gateway-strip.py`：遍历**网关已有的路由**，问「改写对不对」
  → 只能发现「路由存在但改写错」，**发现不了「压根没这条路由」**
- `check-dead-endpoints.py`：问「有没有调用方」
  → 一个**有调用方但网关路由缺失**的接口，在它眼里是「活的」→ **漏报**

本脚本补的就是这个交叉盲区：
**有调用方（前端 api 层或服务间调用），但经过网关到不了。**

匹配语义（必须和 Spring Cloud Gateway 一致，否则全是假阳性）
----------------------------------------------------------
- `Path=/api/x/**` 能匹配 `/api/x`（**零段也匹配**）—— 已用 PathPattern 实测验证
- `Path=/api/x/**` 能匹配 `/api/x/y/z`
- `Path=/api/x/y` **不能**匹配 `/api/x` （精确路径）
- 一条路由可以有**多个 Path**（逗号分隔）

退出码
------
- 0 = 没有「有调用方但网关到不了」的接口
- 1 = 有（**fatal**：这是必然 404 的真缺陷）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATEWAY = ROOT / "erp-gateway/src/main/resources/application.yml"

# ══════════════════════════════════════════════════════════════════════
# 1. 解析网关路由
# ══════════════════════════════════════════════════════════════════════
text = GATEWAY.read_text(encoding="utf-8")

routes = []
cur = None
for line in text.split("\n"):
    m = re.match(r"^\s*-\s*id:\s*(\S+)", line)
    if m:
        if cur:
            routes.append(cur)
        cur = {"id": m.group(1), "uri": None, "path": None, "strip": None,
               "rewrite": None, "method": None}
        continue
    if cur is None:
        continue
    m = re.match(r"^\s*uri:\s*(\S+)", line)
    if m:
        cur["uri"] = m.group(1)
        continue
    m = re.match(r"^\s*-\s*Method=(\S+)", line)
    if m:
        # 📌 **Method 谓词必须参与匹配**：
        #    否则 POST /api/ai/assistant/knowledge 会匹配到
        #    那条「Method=GET」的路由 → 改写成了 /api/ai/knowledge/search
        #    → 报一个「接口不存在」的假阳性
        cur["method"] = m.group(1).upper()
        continue
    m = re.match(r"^\s*-\s*Path=(.+)$", line)
    if m:
        # 📌 一条路由可以有多个 Path（逗号分隔）：
        #    `Path=/api/purchase/orders/**,/api/purchase/suppliers`
        #    → 当成一个字符串解析会导致「明明能路由到、却报不可达」
        cur["path"] = m.group(1).strip()
        continue
    m = re.match(r"^\s*-\s*StripPrefix=(\d+)", line)
    if m:
        cur["strip"] = int(m.group(1))
        continue
    m = re.match(r"^\s*-\s*RewritePath=(.+)", line)
    if m:
        cur["rewrite"] = m.group(1).strip()
if cur:
    routes.append(cur)


def route_paths(route):
    """一条路由的所有 Path（逗号分隔要拆开）"""
    raw = route.get("path") or ""
    return [p.strip() for p in raw.split(",") if p.strip()]


def path_matches(pattern, path):
    """Spring Cloud Gateway 的 Path 匹配语义。

    - `/**` 结尾 → 匹配「该前缀 + 任意多段」，**包括零段**
    - `/*`  结尾 → 匹配「该前缀 + 恰好一段」
    - 其它     → 精确匹配
    """
    if pattern.endswith("/**"):
        prefix = pattern[:-3].rstrip("/")
        if prefix == "":
            return True
        return path == prefix or path.startswith(prefix + "/")
    if pattern.endswith("/*"):
        prefix = pattern[:-2].rstrip("/")
        rest = path[len(prefix) + 1:] if path.startswith(prefix + "/") else None
        return rest is not None and "/" not in rest
    return path == pattern


def apply_route(route, path, request_path):
    """把网关的改写规则应用到**具体请求路径**上，返回服务实际收到的路径。

    - RewritePath=<正则>, <替换>：用正则把 path 换成 替换目标
    - StripPrefix=N：去掉前 N 段
    - 都没有：原样
    """
    rewrite = route.get("rewrite")
    strip = route.get("strip")

    if rewrite:
        m = re.match(r"^([^,]+),\s*(.+)$", rewrite)
        if not m:
            return request_path
        pattern, target = m.group(1).strip(), m.group(2).strip()
        # 📌 **Java 的命名组语法 `(?<seg>.*)` Python 不认** → 要转成 `(?P<seg>.*)`
        pattern = re.sub(r"\(\?<([A-Za-z_][A-Za-z0-9_]*)>", r"(?P<\1>", pattern)
        try:
            rx = re.compile(pattern)
        except re.error:
            return request_path

        def sub_named(match):
            out = target
            for name, val in match.groupdict().items():
                # Spring 的占位符写法是 `$\{seg}`（YAML 里转义过）
                out = out.replace("$\\{%s}" % name, val or "")
                out = out.replace("${%s}" % name, val or "")
            return out

        return rx.sub(sub_named, request_path)

    if strip is not None:
        segs = [s for s in request_path.split("/") if s]
        kept = segs[strip:] if strip < len(segs) else []
        return "/" + "/".join(kept)

    return request_path


def target_prefix(route, path):
    """该 Path 转发后，**服务实际收到的路径前缀**"""
    rewrite = route.get("rewrite")
    strip = route.get("strip")
    if rewrite:
        target = rewrite.split(",", 1)[1].strip() if "," in rewrite else rewrite
        target = re.split(r"\$\\\{", target)[0]
        target = target.split("${")[0]
        return target.rstrip("/")
    if strip is not None:
        segs = [s for s in path.rstrip("*").rstrip("/").split("/") if s]
        kept = segs[strip:] if strip < len(segs) else []
        return "/" + "/".join(kept)
    return path.rstrip("*").rstrip("/")


# 服务 → 它在网关上的所有路由
service_routes = {}
for r in routes:
    uri = r.get("uri") or ""
    if not uri.startswith("lb://"):
        continue
    module = uri[5:]
    if not (ROOT / module).exists():
        continue
    service_routes.setdefault(module, []).append(r)

# ══════════════════════════════════════════════════════════════════════
# 2. 后端 controller 接口
# ══════════════════════════════════════════════════════════════════════
METHOD_RE = re.compile(
    r'@(Get|Post|Put|Delete|Patch)Mapping'
    r'(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\))?'
)


def controller_endpoints(module):
    out = []
    base = ROOT / module / "src/main/java"
    if not base.exists():
        return out
    for f in base.rglob("*Controller.java"):
        txt = f.read_text(encoding="utf-8")
        cm = re.search(r'@RequestMapping\(\s*"([^"]*)"', txt)
        root = cm.group(1) if cm else ""
        for m in METHOD_RE.finditer(txt):
            sub = m.group(2) or ""
            full = (root.rstrip("/") + "/" + sub.lstrip("/")).replace("//", "/")
            if not full.startswith("/"):
                full = "/" + full
            out.append((m.group(1).upper(), full.rstrip("/") or "/"))
    return out


# 每个服务：后端接口 → 是否有网关路由能把它送到
def gateway_can_reach(service, backend_path, method):
    """后端接口路径**+方法** 是否能被网关的某条路由送到。

    📌 **Method 谓词必须参与判断**（2026-11-04 修）：
    实测漏报案例 ——
      路由 `ai-knowledge-delete`：Method=DELETE, Path=/api/ai/assistant/knowledge/**
        → RewritePath 到 /api/ai/knowledge/documents/${id}
      → 它的 target prefix 是 `/api/ai/knowledge/documents`
      → 只比 prefix 的话，`POST /api/ai/knowledge/documents` 被误判为「可达」
      → **实际上那条路由只允许 DELETE，POST 会 405**
    → 前端方向我已经踩过这个坑（POST 匹配到 Method=GET 那条路由），
      后端方向**同一个坑**又踩了一次 —— 同一个语义要检查两遍，容易只改一处。
    """
    for r in service_routes.get(service, []):
        route_method = r.get("method")
        if route_method and route_method != method:
            continue
        for p in route_paths(r):
            prefix = target_prefix(r, p)
            if not prefix:
                continue
            # 后端路径必须在「转发后前缀」之下
            if backend_path == prefix or backend_path.startswith(prefix.rstrip("/") + "/"):
                return True
    return False


# ══════════════════════════════════════════════════════════════════════
# 3. 前端 api 层的调用点 → 找它们对应的后端接口
# ══════════════════════════════════════════════════════════════════════
FRONTEND_CALL = re.compile(
    r"request\.(get|post|put|delete|patch)(?:<[^>]*>)?\(\s*[`'\"]([^`'\"]+)")


def frontend_calls():
    """收集前端 api 层真实发出的请求路径。

    ⚠️ 只看 **api/*.ts**（axios 封装层），不看 views ——
    views 里可能压根没接这个函数（那属于「没调用方」，由 check-dead-endpoints 管）。
    """
    calls = []
    api_dir = ROOT / "erp-web/src/api"
    if not api_dir.exists():
        return calls
    for f in sorted(api_dir.glob("*.ts")):
        txt = f.read_text(encoding="utf-8")
        base = "/api"
        m = re.search(r"baseURL\s*:\s*['\"]([^'\"]+)['\"]", txt)
        if m:
            base = m.group(1)
        for mm in FRONTEND_CALL.finditer(txt):
            raw = mm.group(2)
            if not raw.startswith(base):
                raw = base.rstrip("/") + "/" + raw.lstrip("/")
            if not raw.startswith("/api"):
                continue
            probe = re.sub(r"\$\{[^}]+\}", "*", raw)
            calls.append((f.name, mm.group(1).upper(), probe))
    return calls


def gateway_external_match(probe, method):
    """前端请求路径+方法 能否被网关的某条路由匹配上"""
    for r in routes:
        route_method = r.get("method")
        if route_method and route_method != method:
            continue
        for p in route_paths(r):
            if path_matches(p, probe):
                return r
    return None


print("=" * 78)
print("网关路由 ↔ 后端 controller 接口 覆盖率巡检")
print("=" * 78)

problems = []
checked_calls = 0

for fname, method, probe in frontend_calls():
    checked_calls += 1
    route = gateway_external_match(probe, method)
    if route is None:
        problems.append((fname, method, probe, "网关没有任何 Path 能匹配这个请求路径"))
        continue

    uri = route.get("uri") or ""
    if not uri.startswith("lb://"):
        problems.append((fname, method, probe, f"匹配到的路由 uri 不是 lb:// 服务（{uri}）"))
        continue
    module = uri[5:]
    if not (ROOT / module).exists():
        continue  # 服务目录不存在 → 跳过（部署类问题由别的闸门管）

    # ── 把「前端请求路径」原样过一遍网关的改写规则 ──
    #    得到**服务实际收到的完整路径**，再拿它去比 controller 接口。
    #    （不能用「目标前缀 + 剩余段」拼 —— rewrite 是对整条路径做的）
    received = None
    for p in route_paths(route):
        if not path_matches(p, probe):
            continue
        received = apply_route(route, p, probe)
        break

    if received is None:
        problems.append((fname, method, probe, "路由匹配上了但改写失败"))
        continue

    endpoints = controller_endpoints(module)
    if not endpoints:
        continue

    # 服务收到的路径里，`*` 是变量段 → 跟后端的 `{id}` 对齐
    received_re = re.escape(received).replace(r"\*", r"[^/]+")
    matched = False
    for m_method, m_path in endpoints:
        if m_method != method:
            continue
        if re.fullmatch(received_re, m_path):
            matched = True
            break
        # 后端路径带 {xxx} 变量 → 用正则再比一次
        backend_re = "^" + re.escape(m_path).replace(r"\{", "{").replace(r"\}", "}") + "$"
        backend_re = re.sub(r"\{[^}]+\}", "[^/]+", backend_re)
        if re.fullmatch(backend_re, received):
            matched = True
            break

    if not matched:
        problems.append((
            fname, method, probe,
            f"网关能匹配（路由 {route.get('id')} → {module}），"
            f"服务收到 {received}，但没有对应接口"))

# ══════════════════════════════════════════════════════════════════════
# 4. 反方向：后端接口 → 网关有没有路由能送到
#    （这些接口**当前没有调用方**，所以不是「立刻 404」，
#      但一旦有人接前端/第三方，**必然 404** —— 属于埋着的雷）
# ══════════════════════════════════════════════════════════════════════
unreachable_backend = []
for module in sorted(service_routes.keys()):
    endpoints = controller_endpoints(module)
    if not endpoints:
        continue
    for m_method, m_path in endpoints:
        if not gateway_can_reach(module, m_path, m_method):
            unreachable_backend.append((module, m_method, m_path))

if not problems:
    print(f"\n✅ 前端 {checked_calls} 个 api 层调用点，全部能经过网关到达后端接口")
    if unreachable_backend:
        print(f"\n⚠️  另外有 {len(unreachable_backend)} 个后端接口**网关没有路由**")
        print("   （当前没有调用方 → 暂时不影响使用；")
        print("     但一旦有人接前端/第三方，**必然 404**）\n")
        by_mod = {}
        for mod, meth, path in unreachable_backend:
            by_mod.setdefault(mod, []).append((meth, path))
        for mod in sorted(by_mod):
            print(f"  📦 {mod}")
            for meth, path in sorted(set(by_mod[mod])):
                print(f"     {meth:6s} {path}")
            print()
        print("   💡 要么加网关路由，要么确认这个接口**不需要对外暴露**。")
    sys.exit(0)

print(f"\n❌ 有 {len(problems)} 个前端调用点**经过网关到不了后端**：\n")
for fname, method, probe, why in problems:
    print(f"  📄 {fname}")
    print(f"     {method} {probe}")
    print(f"     → {why}")
    print()

print("=" * 78)
print(f"合计 {len(problems)} 个调用点不可达")
print()
print("📌 这类缺陷为什么隐蔽：")
print("   - 后端接口写好了、单测也过了 → 看起来是好的")
print("   - 前端 api 层也写好了 → 看起来也接上了")
print("   - 但**经过网关就是 404**")
print("   - 而「前端没接」和「接了但 404」在监控上长得一模一样")
print()
print("💡 排查顺序：")
print("   1. 看网关 application.yml 有没有匹配这个路径的 Path")
print("   2. 有的话看 StripPrefix / RewritePath 改写后服务收到的路径对不对")
print("   3. 两边都对 → 检查 controller 的 @RequestMapping 根路径和方法映射")
print("=" * 78)
sys.exit(1)
