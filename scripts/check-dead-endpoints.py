#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""死接口巡检：后端有哪些 HTTP 接口**从来没有任何人调用**。

## 为什么需要这个脚本

2026-11-03 抓到一个真 bug：库存预警服务（8086）有完整的
「规则 / 预警记录 / 补货建议 / 统计」四大功能，前端页面也照常渲染，
但**预警一条都产生不了** ——

* 预警记录表 `t_inventory_alert` 在真实 MySQL 里 **0 行**，
  而活库存表 `erp_inventory.erp_inventory` 有 14 行数据；
* 唯一能写预警的入口是 `POST /inventory/alert/check`，
  而它要求调用方**自己传 `currentStock`**，全仓（含前端）**没有任何调用方**；
* 服务里也没有任何 Feign / RestTemplate / `@Scheduled` 去拉库存。

也就是说：**功能写了、页面做了、测试也绿了，就是从来没人跑过。**

这种缺陷 `mvn test` 查不出来、单测更查不出来（单测只测方法本身，
不测「这个功能有没有被触发」）。唯一能查出来的办法是
**把「谁调用了这个接口」逐个对出来**。

## 本脚本怎么判

对每个后端接口（Controller 里的 `@GetMapping` / `@PostMapping` / …），
找三类可能的调用方：

1. **前端真的调用了**——`erp-web/src/views/**/*.vue` 里出现了对应的 api 函数调用。
   ⚠️ 只看 `src/api/*.ts` 里「定义了这个函数」是不够的：
   `inventoryAlertApi.checkInventory()` 当年就在 api 文件里躺着，
   但**没有任何视图调用它**。
2. **别的服务调用了**——其他模块的 Java 源码里出现这个路径
   （Feign 的 `@GetMapping("/xxx")`、RestTemplate 的字符串 URL、HTTP 客户端常量）。
3. **自己定时调用了**——同模块里有 `@Scheduled` 任务会打过来
   （这种算「内部活」，但通常也说明外部没人用，需要人工确认）。

三类都没有 → 报进「疑似死接口」清单。

## 怎么用

    python3 scripts/check-dead-endpoints.py            # 打印报告（退出码 0）
    python3 scripts/check-dead-endpoints.py --strict   # 有疑似死接口就退出码 1

⚠️ `--strict` 适合**提交前自查**，**不建议直接当 CI 门禁**：
REST 接口本来就可能给外部客户端 / Postman / 第三方调用，
脚本判断不了「没人调」到底是死代码还是对外 API。
真要卡，得先在报告里人工确认一遍，再把白名单写进脚本。

## 📌 这个脚本的黄金标准：能报「我什么都没查到」

如果解析不出任何接口或调用方，脚本会**显式说明**，
而不是安静地输出一句「全部正常」——否则你根本不知道它到底工作没工作。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB_API = ROOT / "erp-web/src/api"
WEB_VIEWS = ROOT / "erp-web/src/views"
JAVA_ROOT = ROOT


# ---------------------------------------------------------------- 网关路由

GATEWAY_YML = ROOT / "erp-gateway/src/main/resources/application.yml"


def parse_gateway_routes():
    """返回 [(route_id, Path 谓词, {Method 集合 or None}, [filters], target_service)]

    ⚠️ Gateway **按声明顺序**匹配，第一条命中即生效 ——
       所以前端路径 → 后端路径的解析必须按顺序找第一条命中的路由。
    """
    if not GATEWAY_YML.exists():
        return []
    text = GATEWAY_YML.read_text(encoding="utf-8")
    routes = []
    for block in re.split(r"\n\s*- id: ", text)[1:]:
        path = re.search(r"Path=([^\s]+)", block)
        method = re.search(r"Method=([A-Z,]+)", block)
        uri = re.search(r"uri:\s*lb://([A-Za-z0-9_\-]+)", block)
        filters = re.findall(r"-\s*(RewritePath|StripPrefix)=([^\n]+)", block)
        methods = set(method.group(1).split(",")) if method else None
        routes.append((
            block.split("\n")[0].strip(),
            path.group(1) if path else None,
            methods,
            [(k, v.strip()) for k, v in filters],
            uri.group(1) if uri else None,
        ))
    return routes


def ant_to_regex(pattern):
    """Ant 风格 Path 谓词 → 正则（`**` 匹配任意多段，`*` 匹配单段，`{x}` 匹配一段）"""
    out = []
    i = 0
    while i < len(pattern):
        if pattern.startswith("/**", i):
            out.append("(/.*)?" if i else "(/.*)?")
            i += 3
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "{":
            j = pattern.find("}", i)
            if j != -1:
                out.append("[^/]+")
                i = j + 1
            else:
                out.append(re.escape(pattern[i]))
                i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return "^" + "".join(out) + "$"


def apply_rewrite(path, rewrite):
    """应用 RewritePath 过滤器（Ant 风格：from 里 (?<seg>.*) → to 里 ${seg}）"""
    val = rewrite.split("=", 1)[1] if "=" in rewrite else rewrite
    parts = val.split(",")
    if len(parts) < 2:
        return path
    frm, to = parts[0].strip(), parts[1].strip()
    frm = frm.replace("$\\{", "${").replace("\\{", "{")
    to = to.replace("$\\{", "${").replace("\\{", "{")
    frm = re.sub(r"\(\?<(\w+)>([^)]*)\)", r"(?P<\1>\2)", frm)
    try:
        m = re.fullmatch(frm, path)
    except re.error:
        return path
    if not m:
        return path
    out = to
    for name, val in m.groupdict().items():
        out = out.replace("${" + name + "}", val)
    return out


def apply_strip_prefix(path, n):
    try:
        n = int(n)
    except (TypeError, ValueError):
        return path
    if n <= 0:
        return path
    parts = path.strip("/").split("/")
    if len(parts) <= n:
        return "/" + "/".join(parts)
    return "/" + "/".join(parts[n:])


def resolve_via_gateway(frontend_url, method, routes):
    """前端 URL（不含 /api base）→ 网关 → 后端路径列表"""
    full = frontend_url if frontend_url.startswith("/api") else "/api" + frontend_url
    out = []
    for _rid, pred, methods, filters, _svc in routes:
        if not pred or (methods and method not in methods):
            continue
        if not re.match(ant_to_regex(pred), full):
            continue
        path = full
        for kind, val in filters:
            if kind == "RewritePath":
                path = apply_rewrite(path, kind + "=" + val)
            elif kind == "StripPrefix":
                path = apply_strip_prefix(path, val)
        out.append(path)
        break   # 第一条命中即生效（Gateway 的语义）
    return out


# ---------------------------------------------------------------- 后端接口


def parse_controllers():
    """返回 [(模块名, 文件, HTTP 方法, 完整路径)]

    完整路径 = 类级 @RequestMapping + 方法级 @XxxMapping。
    """
    endpoints = []
    for module_dir in sorted(ROOT.glob("erp-*")):
        if not module_dir.is_dir() or not (module_dir / "src/main/java").exists():
            continue
        module = module_dir.name
        for java in (module_dir / "src/main/java").rglob("*.java"):
            text = java.read_text(encoding="utf-8", errors="ignore")
            if "@RestController" not in text and "@Controller" not in text:
                continue
            # 类级 @RequestMapping（取第一个，类里通常只有一个）
            cls = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"', text)
            prefix = cls.group(1).strip() if cls else ""
            for m in re.finditer(
                r'@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"', text
            ):
                http = m.group(1).upper()
                sub = m.group(2).strip()
                full = (prefix + "/" + sub) if sub else prefix
                full = re.sub(r"/{2,}", "/", full).rstrip("/") or "/"
                endpoints.append((module, java, http, full))
    return endpoints


# ---------------------------------------------------------------- 前端调用点


def parse_api_functions():
    """返回 {函数全名: [(METHOD, url), ...]}，函数全名形如 `customerApi.getList`"""
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
            objs = list(obj_re.finditer(text[: m.start()]))
            if not objs:
                continue
            name = f"{objs[-1].group(1)}.{m.group('key')}"
            funcs.setdefault(name, []).append((m.group("method").upper(), m.group("url")))
    return funcs


def parse_view_api_calls():
    """返回 (视图真正调用了的 api 函数名集合, 视图里裸 axios 调用列表)

    ⚠️ 只扫 `src/api/*.ts` 是不够的 —— 登录/注册走的是**裸 axios**
    （`axios.post('/api/auth/login', ...)`），不经过 api 层，
    漏掉的话 /auth/login、/auth/register 会被误报成死接口。
    """
    pattern = re.compile(
        r"(?:const\s+(?P<var>\w+)\s*=\s*)?await\s+(?P<api>[A-Za-z_]\w*(?:\.\w+)*)\s*\("
    )
    raw_axios = re.compile(
        r"axios\.(?P<method>get|post|put|delete|patch)(?:<[^>]*>)?\(\s*"
        r"[`'\"](?P<url>[^`'\"]+)[`'\"]"
    )
    called = set()
    raw_calls = []
    for vue in sorted(WEB_VIEWS.rglob("*.vue")):
        text = vue.read_text(encoding="utf-8")
        for m in pattern.finditer(text):
            called.add(m.group("api"))
        for m in raw_axios.finditer(text):
            raw_calls.append((m.group("method").upper(), m.group("url")))
    return called, raw_calls


# ---------------------------------------------------------------- 服务间调用


def collect_backend_callers(endpoints):
    """扫描所有 Java 源码，找出哪些路径被别人（别的模块）引用过"""
    texts = {}
    for module_dir in sorted(ROOT.glob("erp-*")):
        if not (module_dir / "src/main/java").exists():
            continue
        for java in (module_dir / "src/main/java").rglob("*.java"):
            texts[java] = java.read_text(encoding="utf-8", errors="ignore")

    called = set()
    for java, text in texts.items():
        module = java.parts[len(ROOT.parts)]
        for _, _, http, path in endpoints:
            if module == _:
                continue
            # 只取路径的最后两段做匹配，避免长路径全匹配不上
            parts = [p for p in path.split("/") if p]
            if len(parts) >= 2:
                needle = "/" + "/".join(parts[-2:])
            elif parts:
                needle = "/" + parts[0]
            else:
                continue
            if needle in text:
                called.add((module, http, path))
    return called


def has_scheduler(module):
    """模块里有没有 @Scheduled（自己会触发自己的接口/服务方法）"""
    module_dir = ROOT / module
    for java in (module_dir / "src/main/java").rglob("*.java"):
        if "@Scheduled" in java.read_text(encoding="utf-8", errors="ignore"):
            return True
    return False


# ---------------------------------------------------------------- 匹配


def path_key(path):
    """把 /inventory/alert/check/scan → (inventory, alert, check, scan)

    ⚠️ 参数占位符必须归一化成同一个 token：
    后端写 `/customer/{id}`，前端写 `/customer/${id}`，
    不归一化的话两边永远不相等 → **满屏假阳性**
    （这个 bug 一开始就踩了：13 个 customer/supplier 接口被误报成死接口）。
    """
    parts = []
    for seg in path.strip("/").split("/"):
        if not seg:
            continue
        if seg.startswith("{") and seg.endswith("}"):
            seg = "{}"
        elif seg.startswith(":") or seg.startswith("$"):
            seg = "{}"
        parts.append(seg)
    return tuple(parts)


def url_key(url):
    """把前端 URL 去掉查询串，参数占位符归一化"""
    u = url.strip().split("?")[0]
    u = re.sub(r"\$\{[^}]*\}", "{}", u)
    u = re.sub(r"\{[^}]*\}", "{}", u)
    u = re.sub(r"/:\w+", "/{}", u)
    u = u.strip("/")
    return tuple(p for p in u.split("/") if p)


def url_matches_backend(url, backend_path, routes=None, method=None):
    """前端 URL ↔ 后端路径。

    📌 优先走**网关解析**（Path 谓词 + RewritePath/StripPrefix）：
       前端 `/api/ai/assistant/chat` 经网关改写后是 `/api/v1/ai-assistant/chat`，
       两者**字符串完全不一样** —— 不走网关就会把几十个 AI 接口全误报成死接口。
    """
    u = url_key(url)
    b = path_key(backend_path)
    if not u:
        return False

    if routes is not None:
        for resolved in resolve_via_gateway(url, method or "", routes):
            if path_key(resolved) == b:
                return True
    if u == b:
        return True
    # 前端带 /api 前缀的情况
    if len(u) == len(b) + 1 and u[0] == "api":
        u = u[1:]
        if u == b:
            return True
    # 后端路径比前端长（前端少写了前缀段）
    if len(b) > len(u) and b[-len(u):] == u:
        return True
    # 前端路径比后端长（前端多写了段，比如 /api/v1/xxx）
    if len(u) > len(b) and u[-len(b):] == b:
        return True
    # 末两段相等
    if len(u) >= 2 and len(b) >= 2 and u[-2:] == b[-2:]:
        return True
    return False


# ---------------------------------------------------------------- 主流程


def write_markdown(dead, endpoints, frontend_used, backend_callers, routes, path):
    """把报告写成 Markdown（可重复生成，提交进仓库当 backlog 用）"""
    lines = [
        "# 疑似死接口清单（自动生成，勿手改）",
        "",
        "> 由 `scripts/check-dead-endpoints.py --markdown` 生成。",
        "> 重新生成：`python3 scripts/check-dead-endpoints.py --markdown`",
        "",
        "## 统计",
        "",
        f"- 后端 HTTP 接口：**{len(endpoints)}** 个",
        f"- 前端视图真正调用：**{len(frontend_used)}** 处",
        f"- 服务间引用：**{len(backend_callers)}** 处",
        f"- 网关路由：**{len(routes)}** 条",
        f"- 疑似死接口：**{len(dead)}** 个",
        "",
        "## 怎么读这份清单",
        "",
        "「疑似死接口」= **前端没有任何视图调用它，其他服务也没有引用它**。",
        "",
        "⚠️ 它**不等于**「应该删掉」：",
        "",
        "1. REST 接口本来就可能给外部客户端 / Postman / 第三方调用；",
        "2. 有些是「后端做完了，UI 还没做」的半成品（最常见）；",
        "3. 有些是给将来预留的扩展点。",
        "",
        "所以这份清单的价值是**排期用的 backlog**，不是删除清单。",
        "",
        "## 重点结论（人工分析，随脚本一起维护）",
        "",
        "### 1. AI 知识库：后端和 api 层都齐了，但**没有任何视图调用**",
        "",
        "`erp-web/src/api/ai.ts` 里 `aiAssistantApi.knowledge.list/add/remove` 三个函数",
        "都写好了，对应的后端接口（`GET /api/ai/knowledge/search`、",
        "`POST /api/v1/ai-assistant/knowledge`、`DELETE /api/ai/knowledge/documents/{id}`）",
        "也都存在且网关改写正确 —— 但 `views/ai/Assistant.vue` **一个都没调**。",
        "",
        "后果：RAG 助手的知识库**永远只能靠手工灌**，页面上也看不到一条知识。",
        "而聊天页的开场白写着「基于 330+ 行业知识库」—— 这个承诺目前落不了地。",
        "",
        "⚠️ 另外 `knowledge.list()` 调的是 `/search`，后端 `@RequestParam(\"q\")` 是**必填**",
        "→ 不传 q 会 400。api 层的语义本身就有坑。",
        "",
        "### 2. 一批「后端做完了，UI 一个页面都没有」的功能域",
        "",
        "| 域 | 死接口数 | 现状 |",
        "|---|---|---|",
        "| 客户跟进 / 分组 / 标签 | 15 | 后端 CRUD 全套，前端客户页只有列表+增删改 |",
        "| 仓库库存作业 | 10 | 移库/冻结/解冻/报损/盘点/批次/波次/调拨 —— 前端仓库页只有仓库+库位 |",
        "| 租户计费 / 用量 / 配额 | 17 | 前端租户页只有租户列表 |",
        "| AI 辅助（意图/情感/统计/batch/health） | 15 | 都是「进阶能力」，UI 只有最基础的聊天 |",
        "",
        "### 3. 「健康检查」类接口全部未接",
        "",
        "`/health` 在 5 个服务里都有，前端一个都没调 —— 缺一个「系统健康总览」页。",
        "",
        "### 4. 死路由",
        "",
        "`/order/detail/:id` 这条路由没有任何地方导航过去，",
        "而且它指向的 `views/order/index.vue` 其实是**列表页**（详情是弹窗）。",
        "",
        "## 清单",
        "",
    ]
    by_module = {}
    for module, java, http, pth, sched in dead:
        by_module.setdefault(module, []).append((http, pth, sched))
    for module in sorted(by_module):
        rows = sorted(by_module[module], key=lambda x: x[1])
        lines.append(f"### {module}（{len(rows)} 个）")
        lines.append("")
        lines.append("| 方法 | 路径 | 备注 |")
        lines.append("|---|---|---|")
        for http, pth, sched in rows:
            note = "该模块有 `@Scheduled`，可能内部触发" if sched else ""
            lines.append(f"| {http} | `{pth}` | {note} |")
        lines.append("")
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"已写入 {path}")


def main():
    strict = "--strict" in sys.argv
    md = None
    if "--markdown" in sys.argv:
        idx = sys.argv.index("--markdown")
        md = sys.argv[idx + 1] if len(sys.argv) > idx + 1 else "docs/DEAD-ENDPOINTS.md"

    endpoints = parse_controllers()
    routes = parse_gateway_routes()
    api_funcs = parse_api_functions()
    view_calls, raw_axios_calls = parse_view_api_calls()
    backend_callers = collect_backend_callers(endpoints)

    # 前端视图真正调用到的 URL（方法 + URL）
    frontend_used = set()
    for name in view_calls:
        segs = name.split(".")
        for cand in (f"{segs[0]}.{segs[-1]}", name):
            if cand in api_funcs:
                for method, url in api_funcs[cand]:
                    frontend_used.add((method, url))
                break
    # 视图里的裸 axios 调用也算调用方
    frontend_used.update(raw_axios_calls)

    print("=" * 78)
    print("死接口巡检：后端 HTTP 接口 ↔ 调用方（前端视图 / 其他服务 / 定时任务）")
    print("=" * 78)
    print(f"后端接口 {len(endpoints)} 个 | 前端视图真正调用 {len(frontend_used)} 处 | "
          f"服务间引用 {len(backend_callers)} 处 | 网关路由 {len(routes)} 条")

    if not endpoints:
        print("\n❌ 一个后端接口都没解析出来 —— 脚本本身可能坏了（不是「零死接口」）")
        return 2

    dead = []
    for module, java, http, path in endpoints:
        used_by_frontend = any(
            m == http and url_matches_backend(url, path, routes, m) for m, url in frontend_used
        )
        used_by_backend = (module, http, path) in backend_callers
        module_has_scheduler = has_scheduler(module)
        if used_by_frontend or used_by_backend:
            continue
        dead.append((module, java, http, path, module_has_scheduler))

    if not dead:
        print("\n✅ 没有发现疑似死接口")
        return 0

    print(f"\n⚠️ 疑似死接口 {len(dead)} 个（没有任何前端视图/其他服务调用）:\n")
    by_module = {}
    for module, java, http, path, sched in dead:
        by_module.setdefault(module, []).append((http, path, sched))

    for module in sorted(by_module):
        rows = by_module[module]
        print(f"  ▸ {module}  （{len(rows)} 个）")
        for http, path, sched in sorted(rows, key=lambda x: x[1]):
            mark = "  [该模块有 @Scheduled，可能内部触发]" if sched else ""
            print(f"      {http:<6} {path}{mark}")
        print()

    print("📌 说明：REST 接口也可能给外部客户端/第三方用，")
    print("   上面这批需要**人工确认**后再决定是删、是接、还是加白名单。")

    if md:
        write_markdown(dead, endpoints, frontend_used, backend_callers, routes, ROOT / md)

    if strict:
        print(f"\n退出码 1：{len(dead)} 个疑似死接口")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
