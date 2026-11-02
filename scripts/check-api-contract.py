#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""API 契约巡检：前端 API 路径 ↔ 后端 Controller 映射 ↔ 网关路由 三方交叉校验。

## 为什么需要这个脚本

本项目踩过好几次「看起来配好了，实际调不通」的坑，而且单测 / `mvn test` 全绿也发现不了：

1. **网关路由缺失**：前端 `request.get('/analytics/xxx')` 走的是 axios baseURL `/api`，
   最终打到 `/api/analytics/xxx`，但网关根本没有 `/api/analytics/**` 这条路由 →
   404。这不是业务 bug，是**配置层面的空洞**。
2. **`lb://` 指向不存在的服务**：网关配了 `lb://erp-logistics-service`，
   但项目里根本没有这个模块 → 路由永远解析不到实例。
3. **服务没注册到 Nacos**：网关用 `lb://` 靠 Nacos 服务发现，
   服务如果没配 `spring.cloud.nacos.discovery`，就永远注册不上去。
4. **服务压根没有 application.yml**：端口会退回 8080（跟网关撞车），
   `spring.application.name` 也没有 → 连注册名都是错的。

这四类问题只有「真起一套环境点一遍」才能发现，成本太高。
本脚本把三方拉平做静态交叉校验，输出可直接看的差异清单，**退出码非 0 即失败**，
可以挂到 CI 或提交前检查里。

## 用法

    python3 scripts/check-api-contract.py          # 打印报告
    python3 scripts/check-api-contract.py --strict # 有差异就退出码 1
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_API = ROOT / "erp-web/src/api"
GATEWAY_YML = ROOT / "erp-gateway/src/main/resources/application.yml"

# 前端 axios baseURL
BASE_URL = "/api"

# ---------------------------------------------------------------- 读取网关路由


def parse_gateway_routes():
    """返回 [(route_id, uri, path_predicate, strip_prefix, rewrite_path)]"""
    text = GATEWAY_YML.read_text(encoding="utf-8")
    routes = []
    # 按 "- id: xxx" 切块
    blocks = re.split(r"\n\s*- id: ", text)
    for block in blocks[1:]:
        rid = block.split("\n", 1)[0].strip()
        uri = re.search(r"uri:\s*(\S+)", block)
        path = re.search(r"Path=([^\s]+)", block)
        strip = re.search(r"StripPrefix=(\d+)", block)
        rewrite = re.search(r"RewritePath=(.+)$", block, re.M)
        routes.append((
            rid,
            uri.group(1) if uri else "",
            path.group(1) if path else "",
            int(strip.group(1)) if strip else None,
            rewrite.group(1) if rewrite else None,
        ))
    return routes


# ---------------------------------------------------------------- 读取后端接口


def parse_backend_endpoints():
    """扫描所有服务的 Controller，返回 {完整路径: (模块, Controller, 方法名)}"""
    endpoints = {}
    for java in ROOT.glob("erp-*-service/src/main/java/**/*Controller.java"):
        text = java.read_text(encoding="utf-8")
        class_m = re.search(r"@RequestMapping\(\"([^\"]*)\"\)\s*\n\s*(?:@\w+(?:\([^)]*\))?\s*\n\s*)*public class", text)
        base = class_m.group(1) if class_m else ""
        module = java.parts[len(ROOT.parts) + 1]
        for m in re.finditer(
            r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*)?\"([^\"]*)\"\s*\))?",
            text,
        ):
            method = m.group(1).upper()
            sub = m.group(2) or ""
            path = (base + sub) or "/"
            endpoints.setdefault(path, []).append((module, method, java.name))
    return endpoints


# ---------------------------------------------------------------- 读取前端调用


def parse_frontend_calls():
    """扫描 erp-web/src/api/*.ts，返回 [(文件, 方法, 路径)]"""
    calls = []
    pattern = re.compile(
        r"request\.(get|post|put|delete|patch)(?:<[^>]*>)?\(\s*[`'\"]([^`'\"]+)")
    for ts in sorted(WEB_API.glob("*.ts")):
        text = ts.read_text(encoding="utf-8")
        for m in pattern.finditer(text):
            calls.append((ts.name, m.group(1).upper(), m.group(2)))
    return calls


def normalize(path):
    """把前端路径里的模板变量换成通配，便于匹配后端路径。"""
    path = re.sub(r"\$\{[^}]+\}", "{*}", path)
    path = re.sub(r":[A-Za-z_][A-Za-z0-9_]*", "{*}", path)
    return path


def path_matches(frontend_path, backend_path):
    """前端路径是否能命中某个后端接口（后端路径是网关转发后服务真正收到的路径）。"""
    fp = normalize(frontend_path)
    bp = normalize(backend_path)
    f_parts = fp.strip("/").split("/")
    b_parts = bp.strip("/").split("/")
    if len(f_parts) != len(b_parts):
        return False
    for f, b in zip(f_parts, b_parts):
        if f == "{*}" or b == "{*}" or f == b:
            continue
        return False
    return True


# ---------------------------------------------------------------- 主流程


def main():
    strict = "--strict" in sys.argv
    routes = parse_gateway_routes()
    backend = parse_backend_endpoints()
    frontend = parse_frontend_calls()

    print("=" * 78)
    print("API 契约巡检：前端 ↔ 后端 ↔ 网关")
    print("=" * 78)
    print(f"网关路由 {len(routes)} 条 | 后端接口 {len(backend)} 个 | 前端调用 {len(frontend)} 处\n")

    problems = {"no_route": [], "no_backend": [], "dead_route": [], "no_nacos": [], "no_yml": []}

    # 1) 网关 lb:// 指向的服务是否真实存在
    known_services = set()
    for module_dir in ROOT.glob("erp-*-service"):
        if (module_dir / "pom.xml").exists():
            known_services.add(module_dir.name)
    for rid, uri, path, strip, rewrite in routes:
        if not uri.startswith("lb://"):
            continue
        target = uri[5:]
        if target not in known_services:
            problems["dead_route"].append((rid, uri))

    # 2) 服务是否缺 application.yml / 缺 nacos 配置（lb:// 路由的前提）
    for rid, uri, path, strip, rewrite in routes:
        if not uri.startswith("lb://"):
            continue
        target = uri[5:]
        module_dir = ROOT / target
        if not module_dir.exists():
            continue
        yml = module_dir / "src/main/resources/application.yml"
        if not yml.exists():
            problems["no_yml"].append((target, "缺少 application.yml（端口/服务名/Nacos 全是默认值）"))
            continue
        yml_text = yml.read_text(encoding="utf-8")
        if "nacos" not in yml_text:
            problems["no_nacos"].append((target, "未配置 spring.cloud.nacos.discovery，无法注册到 Nacos"))

    # 3) 前端调用 → 网关路由 → 后端接口
    for fname, method, raw_path in frontend:
        full = BASE_URL + raw_path if raw_path.startswith("/") else raw_path
        matched = None
        for rid, uri, path, strip, rewrite in routes:
            if not path:
                continue
            prefix = path.rstrip("*").rstrip("/")
            if full == prefix or full.startswith(prefix + "/"):
                # ⚠️ 网关是按声明顺序匹配，命中第一条就停 —— 脚本必须同样逻辑，
                #    否则会报出「其实被前一条路由吃掉了」的假问题
                matched = (rid, uri, path, strip, rewrite)
                break
        if not matched:
            problems["no_route"].append((fname, method, full))
            continue

        rid, uri, path, strip, rewrite = matched
        # 网关转发后，服务真正收到的路径
        if rewrite:
            m = re.match(r"^([^,]+),\s*(.+)$", rewrite)
            src, dst = m.group(1), m.group(2)
            seg = re.search(r"\(\?<seg>\.\*\)", src)
            tail = full[len(src.split("(")[0]):]
            if seg:
                tail = tail.lstrip("/")
            forwarded = re.sub(r"\$\\\{seg\}", tail, dst)
        elif strip is not None:
            forwarded = "/".join(full.strip("/").split("/")[strip:])
            forwarded = "/" + forwarded
        else:
            forwarded = full

        hit = [p for p in backend if path_matches(forwarded, p)]
        if not hit:
            problems["no_backend"].append((fname, method, full, forwarded, rid))

    # ---------------------------------------------------------------- 输出
    def section(title, items, fmt):
        print(f"\n❌ {title} ({len(items)})")
        if not items:
            print("   （无）")
            return
        for item in items:
            print("   - " + fmt(item))

    section(
        "网关路由指向不存在的服务（lb:// 永远解析不到实例）",
        problems["dead_route"],
        lambda x: f"{x[0]} → {x[1]}",
    )
    section(
        "服务缺少 application.yml",
        problems["no_yml"],
        lambda x: f"{x[0]}: {x[1]}",
    )
    section(
        "服务未配置 Nacos discovery（lb:// 路由前提）",
        problems["no_nacos"],
        lambda x: f"{x[0]}: {x[1]}",
    )
    section(
        "前端调用没有匹配的网关路由",
        problems["no_route"],
        lambda x: f"{x[0]}: {x[1]} {x[2]}",
    )
    section(
        "前端调用有网关路由但后端没有对应接口",
        problems["no_backend"],
        lambda x: f"{x[0]}: {x[1]} {x[2]}  →  转发后 {x[3]}（路由 {x[4]}）",
    )

    total = sum(len(v) for v in problems.values())
    print("\n" + "=" * 78)
    print(f"合计问题 {total} 项")
    print("=" * 78)

    if strict and total:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
