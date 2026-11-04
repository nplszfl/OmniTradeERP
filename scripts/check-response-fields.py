#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""响应字段名巡检：**视图实际读了哪些字段** ↔ 后端 DTO 里有没有。

## 为什么需要这个脚本

项目里有 3 道「响应形状」闸门（check-response-shape.py / check-api-contract.py /
check-dead-endpoints.py），它们查的是**形状**：
  - 裸数组 还是 `Result<T>` 包装
  - 路径能不能命中后端接口
  - HTTP 方法对不对

但它们**没有一个查字段名**。

于是 2026-11-04 这一类缺陷就整个穿过去了：

    // erp-web/src/api/ai.ts
    chat: (data) => request.post<any, { reply: string; sessionId: string }>('/ai/assistant/chat', data)

    // erp-web/src/views/ai/Assistant.vue
    messages.value.push({ role: 'bot', content: r.reply })   // ← r.reply 恒为 undefined

后端 `ChatResponse` 里**根本没有 `reply` 字段**（只有 `content`），
但 `check-response-shape.py` 说「裸 DTO，形状 ✅」，
`check-api-contract.py` 说「路径命中 ✅，方法匹配 ✅」，
单测也全绿 —— **只有真实点一次聊天才会发现气泡泡是空的**。

## 为什么查「视图读了什么」，而不是「前端 interface 声明了什么」

⚠️ **只比 API 层的 interface 会产生大量假问题**：
本项目很多视图在**视图层手动映射**字段，例如：

    // erp-web/src/views/product/index.vue
    productList.value = (pageData.records || []).map((item: any) => ({
      internalSku: item.sku || '',        // ← 后端叫 sku，前端叫 internalSku
      productName: item.name || '',        // ← 后端叫 name，前端叫 productName
    }))

前端 interface 写 `productName`，后端 DTO 写 `name` —— **这是设计如此，不是 bug**。
真去报它，就是制造噪音，让人开始无视这道闸门（→ 闸门变哑）。

**真正的 bug 是「视图直接读了一个后端根本没给的字段」** ——
因为**没有任何映射层**能救它，那一行代码必然拿到 `undefined`。
`r.reply` 就是这一类。

所以本脚本的判据是：

    视图里 `const x = await someApi.method(...)` 之后，
    代码直接读 `x.字段`  →  这个字段必须在后端 DTO 里存在。

中间只要经过了 `.map(item => ({...}))` 这种**显式映射**，
映射右边读的字段也要查；映射左边（造出来的新字段）不查。

## 覆盖的写法

    const r = await aiAssistantApi.chat(...)        // → 查 r.field
    const res = await orderApi.getOrders(...)       // → 查 res.field
    const rows = await xApi.list(...)               // → 查 rows[0].field / rows.map(i => i.field)
    const { a, b } = await xApi.detail(...)         // → 查 a / b
    const pageData = unwrapPage(res)                // → 查 pageData.records 里的元素字段
    res.data.records.map(i => i.field)              // → 查 field

## 用法

    python3 scripts/check-response-fields.py          # 打印报告
    python3 scripts/check-response-fields.py --strict # 有问题就退出码 1
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_API = ROOT / "erp-web/src/api"
WEB_VIEWS = ROOT / "erp-web/src"
GATEWAY_YML = ROOT / "erp-gateway/src/main/resources/application.yml"

BASE_URL = "/api"

PAGE_FIELDS = {"records", "total", "current", "size", "pages"}
RESULT_FIELDS = {"code", "message", "data", "timestamp"}


# ---------------------------------------------------------------- 网关路由


def parse_gateway_routes():
    """返回 [(route_id, uri, path, strip_prefix, rewrite_path, methods)]"""
    text = GATEWAY_YML.read_text(encoding="utf-8")
    routes = []
    blocks = re.split(r"\n\s*- id: ", text)
    for block in blocks[1:]:
        rid = block.split("\n", 1)[0].strip()
        uri = re.search(r"uri:\s*(\S+)", block)
        path = re.search(r"Path=([^\s]+)", block)
        strip = re.search(r"StripPrefix=(\d+)", block)
        rewrite = re.search(r"RewritePath=(.+)$", block, re.M)
        method = re.search(r"Method=([A-Z,]+)", block)
        methods = set(method.group(1).split(",")) if method else None
        paths = [p.strip() for p in path.group(1).split(",")] if path else []
        for p in paths:
            routes.append((
                rid,
                uri.group(1) if uri else "",
                p,
                int(strip.group(1)) if strip else None,
                rewrite.group(1) if rewrite else None,
                methods,
            ))
    return routes


def apply_rewrite(full, src, dst):
    dst = dst.replace("$\\", "$")
    names = re.findall(r"\(\?<([A-Za-z_][A-Za-z0-9_]*)>", src)
    py_src = re.sub(r"\(\?<([A-Za-z_][A-Za-z0-9_]*)>", r"(?P<\1>", src)
    if not names:
        return re.sub(py_src, lambda m: dst, full)
    m = re.match(py_src, full)
    if not m:
        return dst
    out = dst
    for name in names:
        out = out.replace("${" + name + "}", m.group(name) or "")
    return out


# ---------------------------------------------------------------- 后端


def parse_backend_endpoints():
    """返回 {模块: {完整路径: [(METHOD, 返回类型)]}}"""
    endpoints = {}
    for java in ROOT.glob("erp-*-service/src/main/java/**/*Controller.java"):
        text = java.read_text(encoding="utf-8")
        module = next((p for p in java.parts if p.startswith("erp-") and p.endswith("-service")), "")
        base = ""
        class_pos = text.find("public class")
        if class_pos > 0:
            head = text[:class_pos]
            rms = list(re.finditer(r'@RequestMapping\(\s*"([^"]*)"', head))
            if rms:
                base = rms[-1].group(1)
        bucket = endpoints.setdefault(module, {})
        for m in re.finditer(
            r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*)?\"([^\"]*)\"\s*\))?",
            text,
        ):
            method = m.group(1).upper()
            sub = m.group(2) or ""
            path = (base.rstrip("/") + "/" + sub.lstrip("/")).replace("//", "/")
            if not path.startswith("/"):
                path = "/" + path
            tail = text[m.end():m.end() + 800]
            sig = re.search(
                r"public\s+(?:ResponseEntity\s*<)?([A-Za-z_][\w\.]*(?:\s*<[^;{]*?>)?)\s+\w+\s*\(",
                tail,
            )
            ret = sig.group(1).strip() if sig else ""
            bucket.setdefault(path, []).append((method, ret))
    return endpoints


def _class_body(text, class_name):
    m = re.search(r"\b(?:public\s+)?(?:final\s+)?class\s+" + re.escape(class_name) + r"\b[^{]*\{", text)
    if not m:
        return None
    start = m.end()
    depth = 1
    i = start
    while i < len(text) and depth:
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        i += 1
    return text[start:i - 1]


def parse_dto_fields():
    """返回 {(模块, 类名): set(字段名)}"""
    fields = {}
    for java in ROOT.glob("erp-*-service/src/main/java/**/*.java"):
        module = next((p for p in java.parts if p.startswith("erp-") and p.endswith("-service")), "")
        text = java.read_text(encoding="utf-8")
        for cm in re.finditer(r"\b(?:public\s+)?(?:final\s+)?class\s+(\w+)", text):
            name = cm.group(1)
            body = _class_body(text, name)
            if body is None:
                continue
            fs = set()
            for fm in re.finditer(
                r"^\s*(?:private|protected|public)\s+(?:static\s+|final\s+|transient\s+)*"
                r"[\w\.<>\[\],\s]+?\s+(\w+)\s*(?:=[^;]*)?;",
                body,
                re.M,
            ):
                fs.add(fm.group(1))
            fields[(module, name)] = fs
        for rm in re.finditer(r"\brecord\s+(\w+)\s*\(([^)]*)\)", text, re.S):
            name = rm.group(1)
            fs = set()
            for part in rm.group(2).split(","):
                part = part.strip()
                if not part:
                    continue
                # record 分量：`int total` / `BigDecimal totalAmount` / `String foo`
                # ⚠️ 正则必须允许**原始类型**（int/long/double/boolean），
                #    否则 `int total` 这种匹配不上 → 整个 record 的字段全丢
                fm = re.match(r"[\w\.<>\[\],\s]+?\s+(\w+)$", part)
                if fm:
                    fs.add(fm.group(1))
            fields[(module, name)] = fs
    return fields


def unwrap_generic(java_type):
    """剥掉传输壳和集合壳。

    返回 (内层类型名, 形态)
      形态 ∈ {'element' 单个对象, 'array' 数组元素, 'page' 分页元素, 'map' 未知 Map}
    """
    if not java_type:
        return None, "map"
    t = java_type.strip()
    for shell in ("ResponseEntity", "Mono", "Flux"):
        m = re.match(r"^" + shell + r"\s*<\s*(.+?)\s*>$", t)
        if m:
            t = m.group(1).strip()

    kind = "element"
    # 先剥 Result<X>
    m = re.match(r"^Result\s*<\s*(.+?)\s*>$", t)
    if m:
        t = m.group(1).strip()
    # 分页
    for page_t in ("IPage", "Page", "PageResult"):
        m = re.match(r"^" + page_t + r"\s*<\s*(.+?)\s*>$", t)
        if m:
            t = m.group(1).strip()
            kind = "page"
            break
    # 集合
    m = re.match(r"^(?:List|Collection|Iterable|Set|ArrayList)\s*<\s*(.+?)\s*>$", t)
    if m:
        t = m.group(1).strip()
        kind = "array" if kind == "element" else kind
    # Map<String, Object> → 未知
    if re.match(r"^Map\s*<", t):
        return None, "map"
    if not re.match(r"^[\w\.]+$", t):
        return None, "map"
    return t.split(".")[-1], kind


# ---------------------------------------------------------------- 前端 API 层


def parse_api_methods():
    """解析 erp-web/src/api/*.ts，返回 {调用表达式: (路径, 期望类型)}

    调用表达式形如 `aiAssistantApi.chat` / `aiAssistantApi.knowledge.list`。
    """
    methods = {}
    for ts in sorted(WEB_API.glob("*.ts")):
        text = strip_comments(ts.read_text(encoding="utf-8"))
        # 跟踪 export const X = { ... } 及其嵌套对象
        for om in re.finditer(r"export const (\w+)\s*=\s*\{", text):
            obj_name = om.group(1)
            # 找到这个对象的闭合括号
            depth = 1
            i = om.end()
            while i < len(text) and depth:
                c = text[i]
                if c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                i += 1
            body = text[om.end():i - 1]
            _collect_api_entries(body, obj_name, methods)
    return methods


def _collect_api_entries(body, prefix, methods):
    """递归收集对象里的方法名 → (路径, 泛型类型)。

    ⚠️ **方法签名可能跨行、且参数类型里带嵌套花括号**（2026-11-04 实测）：
        getOrders: (params: {
          current: number
          size: number
          platform?: string
        }) => request.get<any, PageResult<Order>>('/order/list', { params }),
    原来的正则 `\([^)]*\)` 会在第一行就截断 → **整个方法被漏掉**
    → 闸门对这条调用 **完全失明**。
    → 改成「先找 `=> request.`，再往前回溯到配平的 `)`」。
    """
    for m in re.finditer(
        r"request\.(get|post|put|delete|patch)\s*<\s*any\s*,\s*([\w\[\]<>,\s]*?)\s*>\s*\(\s*[`'\"]([^`'\"]+)",
        body,
    ):
        # 往前回溯：找 `name:` 与 `=>` 之间的配平括号
        # ⚠️ **必须找离 request 最近的 `=>`**（2026-11-04 实测）：
        #    同一个 api 对象里有很多方法，`rfind("=>")` 会跳到**下一个**方法
        #    的箭头上去 → 回溯到错误的括号 → 方法名被认成别的 → 漏报。
        #    → 改成从 request 位置往前逐字符扫，遇到 `>` 且前一个字符是 `=` 就停。
        arrow = -1
        for i in range(m.start() - 1, 0, -1):
            if body[i] == ">" and body[i - 1] == "=":
                arrow = i - 1
                break
        if arrow == -1:
            continue
        # 在 arrow 之前找配平的 ')'
        # ⚠️ **回溯逻辑**：倒着走，遇 ')' 加深，遇 '(' 变浅。
        #    当深度**变浅到 0** 时，当前这个 '(' 就是签名开始括号。
        #    （写成 `depth < 0` 是错的 —— 那样会跳过真正匹配的那个 '(' ）
        depth = 0
        close = -1
        for i in range(arrow - 1, -1, -1):
            c = body[i]
            if c == ")":
                depth += 1
            elif c == "(":
                depth -= 1
                if depth == 0:
                    close = i
                    break
        if close == -1:
            continue
        # 再往前找方法名
        nm = re.search(r"(\w+)\s*:\s*$", body[:close].rstrip())
        if not nm:
            nm = re.search(r"(\w+)\s*:\s*$", body[:close])
        if not nm:
            continue
        methods[f"{prefix}.{nm.group(1)}"] = (m.group(3), m.group(2))
    # 递归嵌套对象
    for om in re.finditer(r"(\w+)\s*:\s*\{", body):
        depth = 1
        i = om.end()
        while i < len(body) and depth:
            c = body[i]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
            i += 1
        _collect_api_entries(body[om.end():i - 1], f"{prefix}.{om.group(1)}", methods)


# ---------------------------------------------------------------- 路径匹配


def normalize(path):
    path = re.sub(r"\$\{[^}]+\}", "{*}", path)
    path = re.sub(r":[A-Za-z_][A-Za-z0-9_]*", "{*}", path)
    path = re.sub(r"\{[^}]+\}", "{*}", path)
    return path


def path_match_kind(frontend_path, backend_path):
    fp = normalize(frontend_path)
    bp = normalize(backend_path)
    f_parts = fp.strip("/").split("/")
    b_parts = bp.strip("/").split("/")
    if len(f_parts) != len(b_parts):
        return None
    strong = True
    for f, b in zip(f_parts, b_parts):
        f_wild = f == "{*}"
        b_wild = b == "{*}"
        if f_wild and b_wild:
            continue
        if f_wild or b_wild:
            strong = False
            continue
        if f != b:
            return None
    return "strong" if strong else "weak"


def pick_best_match(frontend_path, backend_paths):
    strong, weak = [], []
    for p in backend_paths:
        k = path_match_kind(frontend_path, p)
        if k == "strong":
            strong.append(p)
        elif k == "weak":
            weak.append(p)
    pool = strong or weak
    if not pool:
        return None
    return sorted(pool, key=lambda p: (p.count("{*}"), -len(p)))[0]


def resolve_endpoint(full_path, method, routes, backend):
    """前端路径 → (模块, DTO名, 形态)"""
    matched = None
    for rid, uri, path, strip, rewrite, methods in routes:
        if not path:
            continue
        if methods and method not in methods:
            continue
        prefix = path.rstrip("*").rstrip("/")
        if full_path == prefix or full_path.startswith(prefix + "/"):
            matched = (rid, uri, path, strip, rewrite)
            break
    if not matched:
        return None
    _rid, uri, _path, strip, rewrite = matched
    if rewrite:
        m = re.match(r"^([^,]+),\s*(.+)$", rewrite)
        forwarded = apply_rewrite(full_path, m.group(1), m.group(2))
    elif strip is not None:
        forwarded = "/" + "/".join(full_path.strip("/").split("/")[strip:])
    else:
        forwarded = full_path

    target_module = uri[5:] if uri.startswith("lb://") else None
    pool = backend.get(target_module, {})
    best = pick_best_match(forwarded, list(pool.keys()))
    if best is None:
        return None
    entries = [e for e in pool[best] if e[0] == method] or pool[best]
    for _m, ret in entries:
        dto_name, kind = unwrap_generic(ret)
        if dto_name:
            return target_module, dto_name, kind
    return None


def dto_field_set(module, dto_name, dto_fields):
    fs = dto_fields.get((module, dto_name))
    if fs is not None:
        return fs
    cands = [v for (_m, n), v in dto_fields.items() if n == dto_name]
    if len(cands) == 1:
        return cands[0]
    return None


# ---------------------------------------------------------------- 视图扫描

# 视图里对响应变量的典型读法
FIELD_ACCESS = r"\b{var}\.([a-zA-Z_][a-zA-Z0-9_]*)"
# 这些是 JS/TS 自带的方法，不是后端字段
JS_MEMBERS = {
    "length", "map", "filter", "forEach", "find", "some", "every", "reduce",
    "slice", "splice", "push", "pop", "shift", "unshift", "concat", "join",
    "split", "trim", "toString", "valueOf", "toFixed", "includes", "indexOf",
    "sort", "reverse", "keys", "values", "entries", "get", "set", "has",
    "then", "catch", "finally", "size", "name", "message", "code", "data",
    "stack", "call", "apply", "bind", "prototype", "constructor",
}


def strip_comments(text):
    """去掉 JS/TS/Vue 注释。

    ⚠️ **必须去注释**（2026-11-04 实测）：
    视图里写着
        //    之前读 `r.reply` 恒为 undefined → **每次聊天气泡都是空的**
    这是**修复说明**，不是代码 → 但正则会把 `r.reply` 当成真的字段访问
    → 闸门去修完 bug 后**自己还报同样的错** → 假红 → 没人信这道闸门。
    """
    # 行注释
    text = re.sub(r"//[^\n]*", "", text)
    # 块注释
    text = re.sub(r"/\*.*?\*/", "", text)
    # Vue 模板注释
    text = re.sub(r"<!--.*?-->", "", text)
    return text


def scan_view(text, api_methods, routes, backend, dto_fields, problems, rel_path):
    """扫描一个视图文件里「响应变量直接读了哪些字段」。"""
    text = strip_comments(text)
    # 1) 找 `const|let x = await apiObj.method(...)`
    #    也认 `await someApi.method(...)`（无赋值）→ 直接检查 `.field` 链
    for m in re.finditer(
        r"(?:const|let)\s+(?:\{([^}]*)\}|(\w+))\s*=\s*await\s+(\w+(?:\.\w+)*)\s*\(",
        text,
    ):
        destructured = m.group(1)
        var = m.group(2)
        call = m.group(3)
        if call not in api_methods:
            continue
        path, _generic = api_methods[call]
        full = BASE_URL + path if path.startswith("/") else path
        method = "GET"
        # 从 api 层拿 HTTP 方法：这里简化，用所有方法轮询解析
        resolved = None
        for mth in ("GET", "POST", "PUT", "DELETE"):
            r = resolve_endpoint(full, mth, routes, backend)
            if r:
                resolved = r
                break
        if not resolved:
            continue
        module, dto_name, kind = resolved
        fs = dto_field_set(module, dto_name, dto_fields)
        if fs is None:
            continue
        allowed = set(fs) | PAGE_FIELDS | RESULT_FIELDS

        if destructured:
            names = [d.split(":")[0].strip().split("=")[0].strip() for d in destructured.split(",")]
            for nm in names:
                if nm and nm not in allowed and nm not in JS_MEMBERS:
                    problems.append({
                        "file": rel_path,
                        "call": call,
                        "dto": f"{module}:{dto_name}",
                        "field": nm,
                        "where": f"解构 {nm}",
                    })
            continue

        if not var:
            continue

        # 2) 该变量被直接读了哪些字段
        #    ⚠️ **必须限定作用域**（2026-11-04 实测）：
        #    同一个视图里可能有**多个同名变量**（如 `const stats = reactive(...)`
        #    和 `const stats = await supplierApi.getPurchaseStats()`），
        #    不限定作用域 → 两者混在一起 → **报一堆假问题**。
        #    做法：只取「从声明处往后，到下一个同级 `const <var>` 声明之前」的片段。
        scope = _variable_scope(text, m.end(), var)

        # 2a) **沿着链往下追**：`const pageData = unwrapPage(res)` → pageData.records[0].x
        #     ⚠️ 分页外壳（records/total/...）是 unwrapPage 拆出来的，
        #        **元素字段仍然要查** —— 后端元素 DTO 没有的字段，
        #        `pageData.records[0].xxx` 恒为 undefined。
        for chain_var, chain_fields in _chained_element_fields(scope, var):
            for fname in chain_fields:
                if fname in JS_MEMBERS:
                    continue
                if fname not in allowed:
                    problems.append({
                        "file": rel_path,
                        "call": call,
                        "dto": f"{module}:{dto_name}",
                        "field": fname,
                        "where": f"{chain_var}.{fname}",
                    })

        for fm in re.finditer(FIELD_ACCESS.format(var=re.escape(var)), scope):
            fname = fm.group(1)
            if fname in JS_MEMBERS:
                continue
            # 判断这个访问是不是在「映射表达式」里 —— 那是显式映射，不算漂移
            if _inside_explicit_mapping(scope, fm.start(), var):
                continue
            if fname not in allowed:
                problems.append({
                    "file": rel_path,
                    "call": call,
                    "dto": f"{module}:{dto_name}",
                    "field": fname,
                    "where": f"{var}.{fname}",
                })


def _chained_element_fields(scope, var):
    """追踪「响应 → 分页外壳 → 元素」这条链上的字段读取。

    典型写法：
        const res = await orderApi.getOrders(...)
        const pageData = unwrapPage(res)
        pageData.records[0].nonexistentField        // ← 要查

        const rows = await xApi.list(...)
        rows.map(r => r.someField)                 // ← 要查
        rows[0].someField                          // ← 要查

    返回 [(显示用变量名, [字段名...]), ...]
    """
    out = []
    # 形如 `const pageData = unwrapPage(res)` / `unwrap(res)` 的二次赋值
    for cm in re.finditer(
        r"(?:const|let)\s+(\w+)\s*=\s*(?:unwrapPage|unwrap)\s*\(\s*" + re.escape(var) + r"\s*\)",
        scope,
    ):
        chain_var = cm.group(1)
        # 在 chain_var 的作用域里找 `.records[...]` / `[0]` 后面的字段
        cscope = _variable_scope(scope, cm.end(), chain_var)
        for am in re.finditer(
            re.escape(chain_var) + r"\.records\s*\[\s*\d*\s*\]\s*\.\s*([A-Za-z_]\w*)",
            cscope,
        ):
            out.append((f"{chain_var}.records[0]", [am.group(1)]))
        for am in re.finditer(
            re.escape(chain_var) + r"\.records\s*\.\s*map\s*\(\s*\w+\s*=>\s*\w+\.([A-Za-z_]\w*)",
            cscope,
        ):
            out.append((f"{chain_var}.records.map", [am.group(1)]))

    # 直接对数组响应取下标 / map
    for am in re.finditer(re.escape(var) + r"\s*\[\s*\d*\s*\]\s*\.\s*([A-Za-z_]\w*)", scope):
        out.append((f"{var}[0]", [am.group(1)]))
    for am in re.finditer(
        re.escape(var) + r"\s*\.\s*map\s*\(\s*\w+\s*=>\s*\w+\.([A-Za-z_]\w*)", scope
    ):
        out.append((f"{var}.map", [am.group(1)]))
    return out


def _variable_scope(text, start, var):
    """从 `start` 开始、取到「下一个同名变量声明」之前的文本片段。

    ⚠️ 同名遮蔽（shadowing）是前端视图里的常态：
    `const stats = reactive(...)` 在外层作用域，
    `const stats = await api.xxx()` 在函数里 —— 两者字段完全不同。
    不区分就会把两个变量的字段混在一起比 → 假问题。
    """
    rest = text[start:]
    # 下一个同名声明（同作用域）
    nxt = re.search(r"\b(?:const|let|var)\s+" + re.escape(var) + r"\b", rest)
    if nxt:
        rest = rest[:nxt.start()]
    # 函数体结束（粗略）：下一个顶层 `}` 缩进层
    return rest


def _inside_explicit_mapping(text, pos, var):
    """访问位置是否落在一个 `.map(item => ({...}))` 的**对象字面量右边**里。

    这种写法是**显式映射**（后端字段名 → 前端字段名），
    右边读的字段**仍然要查**（读错了就是 undefined），
    但左边造出来的新字段不算漂移。

    这里只做「保守判定」：若访问点处在 `.map(...)` 调用内部，
    认为它是映射的一部分，不报。
    """
    # 往前找最近的 `.map(` 且看它是否还没闭合
    prefix = text[:pos]
    idx = prefix.rfind(".map(")
    if idx == -1:
        return False
    # 从 .map( 往后做括号配平
    depth = 0
    for i in range(idx + 4, len(text)):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return pos < i
    return False


def main():
    strict = "--strict" in sys.argv
    routes = parse_gateway_routes()
    backend = parse_backend_endpoints()
    dto_fields = parse_dto_fields()
    api_methods = parse_api_methods()

    print("=" * 78)
    print("响应字段名巡检：视图实读字段 ↔ 后端 DTO")
    print("=" * 78)
    print(
        f"前端 api 方法 {len(api_methods)} 个 | 后端 DTO {len(dto_fields)} 个 | "
        f"前端接口总数 {sum(len(v) for v in backend.values())}\n"
    )

    problems = []
    views_scanned = 0
    for src in sorted(WEB_VIEWS.rglob("*.vue")):
        if "/api/" in str(src):
            continue
        text = src.read_text(encoding="utf-8")
        views_scanned += 1
        scan_view(text, api_methods, routes, backend, dto_fields, problems, str(src.relative_to(ROOT)))
    for src in sorted((WEB_VIEWS / "utils").glob("*.ts")):
        text = src.read_text(encoding="utf-8")
        views_scanned += 1
        scan_view(text, api_methods, routes, backend, dto_fields, problems, str(src.relative_to(ROOT)))

    print(f"\n扫描视图 {views_scanned} 个")
    print(f"\n❌ 视图读了后端 DTO 里没有的字段（必然是 undefined） ({len(problems)})")
    if not problems:
        print("   （无）")
    for p in problems:
        print(f"   - {p['file']}: {p['where']}")
        print(f"       {p['call']}  →  后端返回 {p['dto']}，但没有字段 '{p['field']}'")

    print("\n" + "=" * 78)
    print(f"发现问题 {len(problems)} 处")
    print("=" * 78)

    if strict and problems:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
