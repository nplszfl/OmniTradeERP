#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""响应形状巡检：前端「怎么解包响应」 ↔ 后端「返回什么形状」交叉校验。

## 为什么需要这个脚本

后端 486 个接口的返回形状是**混用**的：

- 181 个返回 `Result<T>`（`{code, message, data, timestamp}`）
- 265 个返回**裸类型**（`IPage` / `List` / `Map` / 实体 / `ResponseEntity`）
- 全仓 **0 个** `ResponseBodyAdvice` → 裸类型接口不会被自动包装

前端 axios 拦截器是 `response => response.data`，所以 `res` 就是 HTTP 响应体本身。
于是「取 `res.data.records`」这种写法：

| 后端返回 | 前端写 `res.data` | 结果 |
|---|---|---|
| `Result<IPage>` | ✅ | 正常 |
| 裸 `IPage` | ❌ | `res.data` 是 undefined → **表格恒空、分页总数恒 0** |

**真实踩过的坑**（2026-11-03）：
`/customer/list`、`/supplier/list`、`/supplier/purchase/list` 三个接口后端都是裸 `IPage`，
前端写了 `res.data` → 客户列表页、供应商列表页、采购订单表**永远是空的**。
`mvn test` 全绿、后端接口实测返回 200 且带 3 条真实数据 —— 单测和后端都「没问题」，
问题只在「前端多剥了一层」。

## 本脚本查什么

对每个「前端调用点」判定它对后端返回形状的**隐含要求**：

- 用了 `x.data`          → 要求后端返回 `Result<T>`
- 用了 `x.records` / `x.total` → 要求后端返回裸分页对象
- 用了 `x.length` / `x.map(`  → 要求后端返回裸数组
- 用了 `unwrap(x)` / `unwrapPage(x)` → **两种形状都吃**（形状无关，不做要求）

然后跟后端 controller 的**实际返回类型**对照，对不上就报出来。

📌 端点返回类型是 `Object`（同一路径按入参返回两种形状，比如 `RoleController.list`）
   → 静态无法判定，**跳过**（宁可漏报也不误报）。

## 用法

    python3 scripts/check-response-shape.py          # 打印报告
    python3 scripts/check-response-shape.py --strict # 有问题就退出码 1
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_SRC = ROOT / "erp-web/src"
WEB_API = WEB_SRC / "api"
WEB_VIEWS = WEB_SRC / "views"

# 网关路由：前端 /api/xxx → 后端服务收到的路径
GATEWAY_YML = ROOT / "erp-gateway/src/main/resources/application.yml"

# 消费窗口：调用点之后多少个字符内算「这个变量的使用」
CONSUMPTION_WINDOW = 1200


# ---------------------------------------------------------------- 网关路由


def parse_gateway_routes():
    """返回 [(path_predicate, strip_prefix, rewrite_path, methods, target_service)]"""
    if not GATEWAY_YML.exists():
        return []
    text = GATEWAY_YML.read_text(encoding="utf-8")
    routes = []
    for block in re.split(r"\n\s*- id: ", text)[1:]:
        path = re.search(r"Path=([^\s]+)", block)
        strip = re.search(r"StripPrefix=(\d+)", block)
        rewrite = re.search(r"RewritePath=(.+)$", block, re.M)
        method = re.search(r"Method=([A-Z,]+)", block)
        uri = re.search(r"uri:\s*lb://([A-Za-z0-9_\-]+)", block)
        methods = set(method.group(1).split(",")) if method else None
        paths = [p.strip() for p in path.group(1).split(",")] if path else []
        for p in paths:
            routes.append((
                p,
                int(strip.group(1)) if strip else None,
                rewrite.group(1) if rewrite else None,
                methods,
                uri.group(1) if uri else None,
            ))
    return routes


def normalize(path):
    """模板变量 → 通配，便于匹配。"""
    path = re.sub(r"\$\{[^}]+\}", "{*}", path)
    path = re.sub(r":[A-Za-z_][A-Za-z0-9_]*", "{*}", path)
    return path


def path_matches(frontend_path, backend_path):
    """前端路径能否命中后端路径（后端路径是网关转发后服务真正收到的路径）。

    ⚠️ 网关谓词用的是 Ant 风格的 `**`，它能匹配**任意段数**的后续路径 ——
       `/api/customer/**` 要能命中 `/api/customer/list`（3 段），
       如果按「段数必须相等」来比，所有通配路由都会漏掉。
    """
    fp = normalize(frontend_path).strip("/").split("/")
    bp = normalize(backend_path).strip("/").split("/")
    fi = 0
    for b in bp:
        if b == "**":
            return fi < len(fp)
        if fi >= len(fp):
            return False
        f = fp[fi]
        if not (f == "{*}" or b == "{*}" or f == b):
            return False
        fi += 1
    return fi == len(fp)


def find_backend_shape(url, method, routes, shapes):
    """前端 URL → 后端返回形状。

    定位策略（从强到弱）：
      1. 精确相等 / 尾段相等（前端 '/customer/list' ↔ 后端 '/customer/list'）
      2. 末两段相等（前端 '/social/tiktok-shop' ↔ 后端 '/api/v1/tiktok-shop'）

    命中多个且形状不一致 → 返回 None（静态判不了，跳过）。
    """
    tail = url.strip("/")
    parts = tail.split("/")

    # 先用网关路由锁定目标服务（知道是哪个模块，匹配更准）
    services = set()
    full = "/api" + url if not url.startswith("/api") else url
    for pred, _strip, _rw, methods, svc in routes:
        if methods and method not in methods:
            continue
        if path_matches(full, pred) and svc:
            services.add(svc)

    def collect(predicate):
        out = []
        for (module, m, path), shape in shapes.items():
            if m != method:
                continue
            if services and module not in services:
                continue
            if predicate(normalize(path).strip("/")):
                out.append((module, path, shape))
        return out

    ntail = normalize(tail).strip("/")
    hits = collect(lambda p: p == ntail or p.endswith("/" + ntail))
    if not hits and len(parts) >= 2:
        tail2 = normalize("/".join(parts[-2:])).strip("/")
        hits = collect(lambda p: p == tail2 or p.endswith("/" + tail2))
    if not hits and parts:
        # 已经限定到单个服务后，最后一段足够定位（如 '/ai/assistant/chat' → '/api/v1/ai-assistant/chat'）
        tail1 = normalize(parts[-1]).strip("/")
        hits = collect(lambda p: p == tail1 or p.endswith("/" + tail1))
    if not hits:
        return None, "NO_BACKEND"
    shape_set = {s for _, _, s in hits}
    if len(shape_set) > 1:
        return None, "AMBIGUOUS"
    return shape_set.pop(), "OK"


# ---------------------------------------------------------------- 后端返回类型


def parse_backend_shapes():
    """返回 {(module, METHOD, path): shape}

    shape ∈ {'RESULT', 'BARE', 'FILE', 'NO_BODY', 'UNKNOWN'}
    """
    shapes = {}
    for java in ROOT.glob("erp-*-service/src/main/java/**/*Controller.java"):
        text = java.read_text(encoding="utf-8")
        module = next((p for p in java.parts if p.startswith("erp-") and p.endswith("-service")), "")
        class_pos = text.find("public class")
        base = ""
        if class_pos > 0:
            rms = list(re.finditer(r'@RequestMapping\(\s*"([^"]*)"', text[:class_pos]))
            if rms:
                base = rms[-1].group(1)
        # 逐个 mapping 注解 + 其后紧跟的方法签名
        for m in re.finditer(
            r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*)?\"([^\"]*)\"\s*\))?"
            r"(?P<between>(?:[^{};]|\n)*?)"
            r"public\s+(?P<ret>[A-Za-z_][\w.<>\[\],\s?]*?)\s+[A-Za-z_]\w*\s*\(",
            text,
        ):
            method = m.group(1).upper()
            sub = m.group(2) or ""
            path = (base.rstrip("/") + "/" + sub.lstrip("/")).replace("//", "/")
            if not path.startswith("/"):
                path = "/" + path
            shapes[(module, method, path)] = classify_return(m.group("ret"))
    return shapes


def classify_return(ret):
    """把 Java 返回类型归类。"""
    ret = ret.strip()
    base = ret.split("<")[0].strip()
    if base == "Result":
        return "RESULT"
    if base in ("void", "Void"):
        return "NO_BODY"
    if base == "ResponseEntity":
        inner = ret
        if "byte[]" in inner or "Resource" in inner or "InputStream" in inner:
            return "FILE"
        return "BARE"
    if base == "Object":
        # 同一路径按入参返回两种形状（RoleController.list 就是这种）→ 静态判不了，跳过
        return "UNKNOWN"
    return "BARE"


# ---------------------------------------------------------------- 前端 API 函数


def strip_strings(line):
    """去掉字符串字面量，避免把 URL 里的括号/花括号算进结构统计。"""
    return re.sub(r"(['\"`])(?:\\.|(?!\1).)*\1", "''", line)


def parse_api_functions():
    """返回 {函数全名: [(METHOD, url), ...]}，函数全名形如 `customerApi.getList`

    📌 解析方式：正则匹配完整的箭头函数
       `key: (参数) => request.get('/url')`
    参数里可能嵌套 `{}`（如 `(params: { current: number })`），
    但不含嵌套的 `()`，所以用 `(?:[^()]|\([^()]*\))*` 就能跨行吃下整个参数表。

    ⚠️ 不能用「逐行找 key:」的办法：多行参数表里的 `current: number`
       会被误当成对象成员 → 函数名错位 → 整份调用表失效。
    """
    funcs = {}
    arrow = re.compile(
        r"(?P<key>[A-Za-z_]\w*)\s*:\s*"
        r"\((?:[^()]|\([^()]*\))*\)\s*(?:=>|:\s*async\s*\([^()]*\)\s*=>)\s*(?:await\s+)?"
        r"request\.(?P<method>get|post|put|delete|patch)"
        r"(?:<(?:[^<>]|<[^<>]*>)*>)?\(\s*"
        r"(?P<q>[`'\"])(?P<url>[^`'\"]+)(?P=q)",
        re.S,
    )
    obj_re = re.compile(r"(?:export\s+)?const\s+([A-Za-z_]\w*)\s*=\s*\{")
    for ts in sorted(WEB_API.glob("*.ts")):
        text = ts.read_text(encoding="utf-8")
        for m in arrow.finditer(text):
            # 取这个函数所属的顶层对象名（最近的 `export const X = {`）
            objs = list(obj_re.finditer(text[:m.start()]))
            if not objs:
                continue
            obj = objs[-1].group(1)
            name = f"{obj}.{m.group('key')}"
            funcs.setdefault(name, []).append((m.group("method").upper(), m.group("url")))
    return funcs


# ---------------------------------------------------------------- 前端调用点


def parse_view_calls():
    """返回 [(视图文件, 行号, api函数全名, 变量名 or None, 消费要求集合)]"""
    calls = []
    pattern = re.compile(
        r"(?:const\s+(?P<var>\w+)\s*=\s*)?await\s+(?P<api>[A-Za-z_]\w*(?:\.\w+)*)\s*\("
    )
    for vue in sorted(WEB_VIEWS.rglob("*.vue")):
        text = vue.read_text(encoding="utf-8")
        for m in pattern.finditer(text):
            api = m.group("api")
            var = m.group("var")
            lineno = text[:m.start()].count("\n") + 1
            window = text[m.end(): m.end() + CONSUMPTION_WINDOW]
            calls.append((vue, lineno, api, var, window))
    return calls


def consumption_of(window, var):
    """判定这段代码对返回形状的要求。返回 (requirements:set, agnostic:bool)"""
    req = set()
    agnostic = False
    if not var:
        return req, agnostic

    # 形状无关：unwrap / unwrapPage 包了一层
    if re.search(rf"\bunwrap(Page)?\s*\(\s*{re.escape(var)}\b", window):
        agnostic = True

    candidates = {var}
    # 别名：`const pageData = res.data || {}` / `const rows = await ...`
    for am in re.finditer(rf"\bconst\s+(\w+)\s*=\s*([^;\n]*\b{re.escape(var)}\b[^;\n]*)", window):
        candidates.add(am.group(1))

    for c in candidates:
        c = re.escape(c)
        if re.search(rf"\b{c}\s*\.\s*data\b", window):
            req.add("RESULT")
        if re.search(rf"\b{c}\s*\.\s*(records|total|size|pages)\b", window):
            req.add("BARE")
        if re.search(rf"\b{c}\s*\.\s*(length|map|forEach|filter)\b", window):
            req.add("BARE")
        if re.search(rf"\b{c}\s*\.\s*message\b", window):
            req.add("RESULT")
        # `Object.assign(stats, res)` —— 把响应体直接铺到目标对象上 = 裸类型
        if re.search(rf"Object\.assign\s*\([^,]+,\s*{c}\b", window):
            req.add("BARE")
    return req, agnostic


# ---------------------------------------------------------------- 主流程


def main():
    strict = "--strict" in sys.argv
    routes = parse_gateway_routes()
    shapes = parse_backend_shapes()
    api_funcs = parse_api_functions()
    view_calls = parse_view_calls()

    print("=" * 78)
    print("响应形状巡检：前端解包方式 ↔ 后端返回类型")
    print("=" * 78)
    print(f"后端接口 {len(shapes)} 个 | 前端 API 函数 {len(api_funcs)} 个 | 前端调用点 {len(view_calls)} 处\n")

    problems = []
    checked = 0
    skipped = 0

    for vue, lineno, api, var, window in view_calls:
        # 视图里的调用可能是 `aiAssistantApi.knowledge.list(...)`（嵌套对象），
        # 而 api 文件里登记的是 `aiAssistantApi.list` → 取首段 + 末段来匹配。
        segs = api.split(".")
        candidates = [f"{segs[0]}.{segs[-1]}"]
        if len(segs) > 2:
            candidates.append(api)
        entries = None
        for cand in candidates:
            if cand in api_funcs:
                entries = api_funcs[cand]
                break
        if not entries:
            continue
        if len(entries) > 1:
            skipped += 1     # 同名函数多个 URL → 判不了
            continue
        method, url = entries[0]

        shape, status = find_backend_shape(url, method, routes, shapes)
        if status == "NO_BACKEND":
            skipped += 1
            continue
        if status == "AMBIGUOUS" or shape is None:
            skipped += 1   # 同路径多种形状，静态判不了
            continue
        if shape in ("FILE", "NO_BODY", "UNKNOWN"):
            skipped += 1
            continue

        req, agnostic = consumption_of(window, var)
        if agnostic or not req:
            continue

        checked += 1
        if shape == "RESULT" and "BARE" in req:
            problems.append((vue, lineno, api, url, shape, req,
                             "后端返回 Result<T>，前端却按裸分页对象/数组消费（少剥一层 .data）"))
        elif shape == "BARE" and "RESULT" in req:
            problems.append((vue, lineno, api, url, shape, req,
                             "后端返回裸类型，前端却取 .data（多剥一层 → 永远 undefined，列表页空白）"))

    print(f"已判定 {checked} 个调用点，跳过（返回类型无法静态判定）{skipped} 个\n")

    if problems:
        print("❌ 发现响应形状不匹配：\n")
        for vue, lineno, api, url, shape, req, msg in problems:
            rel = vue.relative_to(ROOT)
            print(f"  · {rel}:{lineno}  {api}()  →  {url}")
            print(f"      后端返回形状: {shape} | 前端消费要求: {sorted(req)}")
            print(f"      {msg}\n")
    else:
        print("✅ 响应形状全部匹配（前端解包方式与后端返回类型一致）")

    if problems and strict:
        print(f"\n退出码 1：{len(problems)} 处形状不匹配")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
