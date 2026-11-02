#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""网关路径改写 ↔ 后端 controller 根路径 一致性巡检。

背景：Spring Cloud Gateway 的 StripPrefix=N 会把前 N 段路径去掉再转发给服务。
如果 N 跟服务里 @RequestMapping 的实际根对不上，路由看起来配了、也查得到，
但每个请求都会 404 —— 单测和 mvn test 全绿都发现不了。

例：/api/platform/config/list + StripPrefix=2 → 服务收到 /config/list，
    而 controller 根是 /platform/config → 404。

RewritePath 同理：
    RewritePath=/api/pricing/(?<seg>.*), /api/v1/pricing/$\{seg}
把 /api/pricing/xxx 改写成 /api/v1/pricing/xxx，
所以「服务收到的路径前缀」是改写后的目标前缀，不是原始 Path。
"""
import re
import sys
from pathlib import Path

# ⚠️ 不要写死绝对路径 —— 换台机器 / CI runner 上会直接读不到文件。
#    按脚本自身位置往上找仓库根（scripts/ 的上一级）。
ROOT = Path(__file__).resolve().parent.parent
GATEWAY = ROOT / "erp-gateway/src/main/resources/application.yml"

text = GATEWAY.read_text(encoding="utf-8")

# 解析 routes 列表
routes = []
cur = None
for line in text.split("\n"):
    m = re.match(r"^\s*-\s*id:\s*(\S+)", line)
    if m:
        if cur:
            routes.append(cur)
        cur = {"id": m.group(1), "uri": None, "path": None, "strip": None, "rewrite": None}
        continue
    if cur is None:
        continue
    m = re.match(r"^\s*uri:\s*(\S+)", line)
    if m:
        cur["uri"] = m.group(1)
        continue
    m = re.match(r"^\s*-\s*Path=(\S+)", line)
    if m:
        cur["path"] = m.group(1)
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


def controller_roots(module):
    """收集服务里所有 controller 的 @RequestMapping 根路径"""
    roots = set()
    base = ROOT / module / "src/main/java"
    if not base.exists():
        return roots
    for f in base.rglob("*Controller.java"):
        txt = f.read_text(encoding="utf-8")
        cls = re.search(r'@RequestMapping\(\s*"([^"]*)"', txt)
        root = cls.group(1) if cls else ""
        for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"', txt):
            full = (root.rstrip("/") + "/" + m.group(2).lstrip("/")).replace("//", "/")
            roots.add(full if full.endswith("/") is False else full)
        # 类级 @RequestMapping 本身也算一个可访问路径（无方法映射的情况少见）
        if root:
            roots.add(root)
    return roots


def frontend_calls():
    """收集前端真实发出的请求路径（作为探测样本）。

    前端 axios 统一配了 baseURL: '/api'，所以源码里写的是 '/pricing/suggest/${sku}'
    这种相对路径，真实请求路径是 '/api' + 相对路径。
    """
    calls = []
    api_dir = ROOT / "erp-web/src/api"
    if not api_dir.exists():
        return calls
    pattern = re.compile(r"request\.(get|post|put|delete|patch)(?:<[^>]*>)?\(\s*[`'\"]([^`'\"]+)")
    for f in sorted(api_dir.glob("*.ts")):
        txt = f.read_text(encoding="utf-8")
        # 逐文件确定 baseURL（默认 /api）
        base = "/api"
        m = re.search(r"baseURL\s*:\s*['\"]([^'\"]+)['\"]", txt)
        if m:
            base = m.group(1)
        for mm in pattern.finditer(txt):
            raw = mm.group(2)
            # axios 会把 baseURL 和 url 拼接：base='/api' + '/platform/config/list'
            # → 真实请求路径 '/api/platform/config/list'
            if not raw.startswith(base):
                raw = base.rstrip("/") + "/" + raw.lstrip("/")
            if not raw.startswith("/api"):
                continue
            # 去掉 ${...} 变量段，转成通配段
            probe = re.sub(r"\$\{[^}]+\}", "*", raw)
            calls.append((f.name, probe))
    return calls


FRONTEND = frontend_calls()


print("=" * 78)
print("网关路径改写 ↔ 后端 controller 根路径 一致性巡检")
print("=" * 78)

problems = []
for r in routes:
    uri = r.get("uri") or ""
    path = r.get("path")
    strip = r.get("strip")
    if not uri.startswith("lb://") or not path:
        continue
    module = uri[5:]
    if not (ROOT / module).exists():
        continue

    roots = controller_roots(module)
    if not roots:
        continue

    # 网关转发后，服务收到的路径前缀
    segs = path.rstrip("*").rstrip("/").split("/")
    segs = [s for s in segs if s]

    rewrite = r.get("rewrite")

    def apply_filters(p_segments):
        """把网关的路径改写规则应用到具体请求路径上"""
        if rewrite:
            # RewritePath=/api/pricing/(?<seg>.*), /api/v1/pricing/$\{seg}
            parts = rewrite.split(",", 1)
            if len(parts) != 2:
                return p_segments
            src, dst = parts[0].strip(), parts[1].strip()
            src_segs = [s for s in src.strip("/").split("/") if s]
            # 源模板最后一段可能是正则通配（如 /api/social/(?<seg>.*)），
            # 它不是字面路径段，不能计入「前缀段数」
            if src_segs and ("(?<" in src_segs[-1] or src_segs[-1] == "*"):
                src_segs = src_segs[:-1]
            if len(p_segments) < len(src_segs):
                return p_segments
            rest = p_segments[len(src_segs):]
            dst_segs = [s for s in dst.replace("$\\{seg}", "*").replace("${seg}", "*").strip("/").split("/") if s]
            out = []
            for d in dst_segs:
                if d == "*":
                    out.extend(rest)
                else:
                    out.append(d)
            return out
        if strip is not None:
            return p_segments[strip:] if len(p_segments) > strip else []
        return p_segments

    # ⚠️ 候选 controller 根必须与网关 Path 的末段对齐。
    #    Path=/api/auth/** 只能落到 /auth* 的 controller 上；
    #    若把 /department、/user 等也当候选，那么「去掉 2 段恰好落到 /department」
    #    这种错误配置也会被判为命中 → 真 bug 被放过。
    #    Path 末段是通配（**）时无法据此筛选，退回全部候选。
    # 候选 controller 根 = 全部（controller 根可能是多段的：
    # /api/tenant/dashboard、/api/v1/anomaly、/inventory/alert、/purchase …，
    # 不能按「Path 末段 == controller 根首段」来筛，那是错的假设）。
    cand_roots = list(roots)
    if not cand_roots:
        # Path 末段与任何 controller 根都对不上 → 必然不匹配，直接记问题
        problems.append((r["id"], path, strip if strip is not None else rewrite,
                         "/", module, sorted(roots)[:5], [], []))
        continue

    # 服务端 controller 根路径都长什么样
    root_firsts = sorted({r_.split("/")[1] for r_ in roots if r_.count("/") >= 1 and len(r_) > 1})

    def reaches(segments, root=None):
        """服务收到这些段后，能否命中 controller 根。

        ⚠️ root 参数很重要：必须针对**这条路由本该落到的那个** controller 根判定。
           若对「所有 root」判定，一个服务的多个 controller（erp-user-service 就有
           /auth、/user、/role、/department、/permission 五个）会互相掩盖错误 ——
           错误的 StripPrefix 落到隔壁 controller 的前缀下，也算「命中」，
           于是真 bug 被放过。
        """
        svc = "/" + "/".join(segments)
        targets = [root] if root else cand_roots
        for rt in targets:
            rp = rt.rstrip("/")
            if svc == rp or svc.startswith(rp + "/"):
                return True
            # controller 根里有 {xxx} 变量段时，用通配匹配
            cand = re.sub(r"\{[^}]+\}", "[^/]+", rp)
            if re.match("^" + cand + "(/|$)", svc):
                return True
        return False

    # 探测样本：优先用前端真实请求路径；没有则用合成路径
    probes = []
    prefix_segs = segs
    for fname, raw in FRONTEND:
        if not raw.startswith(path.rstrip("*").rstrip("/")):
            continue
        ps = [s for s in raw.split("/") if s]
        # 前端样本：controller 根未知 → 用 None（对所有 root 判定）
        probes.append((fname, ps, None))

    if not probes:
        # 无前端样本时，用「能到达该 controller 的理想客户端路径」作为探测。
        #
        # 📌 正确的构造方式：
        #    客户端路径 = 网关 Path 的字面段 + (controller 根去掉与 Path 重叠的前缀)
        #    例：Path=/api/auth/** + controller 根 /auth/login
        #        → 客户端路径 /api/auth/login
        #        → StripPrefix=1 → 服务收到 /auth/login ✅ 命中该 controller
        #    再例：Path=/api/tenant/** + controller 根 /api/tenant/dashboard
        #        → Path 段 ['api','tenant'] 与根段 ['api','tenant','dashboard'] 重叠
        #        → 客户端路径 /api/tenant/dashboard
        #        → StripPrefix=0 → 服务收到 /api/tenant/dashboard ✅
        #
        # ⚠️ 这里必须逐个 controller 根构造并**按该根判定**，
        #    否则一个服务的多个 controller 会互相掩盖 StripPrefix 配错。
        prefix_segs = list(segs)
        for rt in sorted(roots):
            rt_segs = [s for s in rt.split("/") if s]
            # 去掉与 Path 前缀重叠的部分。
            # ⚠️ 重叠是**后缀对齐**，不是前缀对齐：
            #    Path=/api/auth/** 的段是 ['api','auth']，controller 根 /auth 的段是 ['auth']，
            #    它们重叠的是**末尾**的 'auth'（Path 的最后一段就是 controller 根的起点）。
            #    若按前缀对齐（k 从 0 比），'api' vs 'auth' 不等 → k=0
            #    → 探测路径变成 /api/auth/auth/... → 重复了一段。
            k = 0
            while (k < len(prefix_segs) and k < len(rt_segs)
                   and prefix_segs[len(prefix_segs) - 1 - k] == rt_segs[len(rt_segs) - 1 - k]):
                k += 1
            # 重叠的 k 段**已经被 Path 前缀覆盖**，客户端路径里不能再出现一次。
            # 例：Path=/api/auth/** + 根 /auth → k=1（'auth' 重叠）
            #     → 客户端路径 = /api/auth + (根剩下的段 = 空) + 子段 = /api/auth/__probe__
            # 例：Path=/api/auth/** + 根 /auth/login → k=1（'auth' 重叠）
            #     → 客户端路径 = /api/auth + ['login'] + 子段 = /api/auth/login/__probe__
            rest = rt_segs[len(rt_segs) - k:] if k < len(rt_segs) else []
            probes.append(("(理想路径)", prefix_segs + rest + ["__probe__"], rt))

    # 每个 probe 带上它对应的 controller 根：
    # 前端样本的 root 未知（None → 对所有 root 判定），
    # 理想路径样本的 root 就是它自己 —— 必须按 root 判定，
    # 否则一个服务的多个 controller 会互相掩盖 StripPrefix 配错。
    hits = []
    for fname, ps, root in probes:
        svc_segs = apply_filters(ps)
        hits.append((fname, ps, svc_segs, reaches(svc_segs, root)))

    if hits and any(h[3] for h in hits):
        continue

    # 不匹配：报告最接近的候选
    candidates = sorted(roots)[:5]
    samples = [f"{f}: {'/'.join(s)} → /{'/'.join(sv)}" for f, s, sv, _ in hits[:4]]
    problems.append((r["id"], path, strip if strip is not None else rewrite,
                     "/" + "/".join(hits[0][2]) if hits else "/",
                     module, candidates, root_firsts, samples))

if not problems:
    print("\n✓ 所有路由的路径改写与后端 controller 根路径一致")
else:
    print(f"\n❌ 路径改写与后端 controller 根路径不匹配（{len(problems)} 条路由）\n")
    for rid, path, rule, fwd, module, cands, firsts, samples in problems:
        print(f"   - 路由 {rid}: Path={path} 转发规则={rule}")
        print(f"     网关转发后服务收到: {fwd}")
        print(f"     服务 {module} 的 controller 根: {cands}")
        print(f"     根路径首段: {firsts}")
        for s in samples:
            print(f"     前端请求 {s}")
        print()

print("=" * 78)
sys.exit(1 if problems else 0)
