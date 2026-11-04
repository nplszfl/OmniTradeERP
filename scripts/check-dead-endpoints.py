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

## 📌 但「重点结论过期」**不需要** --strict 就报错

上面说的是「疑似死接口」—— 那是**排期 backlog**，脚本判断不了该不该删，
所以默认 exit 0。

但报告里还有一段**手写**的「重点结论」，它是**提交进仓库的文档**，
它说的每一句都可能被后续改动推翻。2026-11-04 实测：
上一版写着「知识库没有任何视图调用」，而上一轮已经把知识库管理页做完了
→ **文档在骗人，而且没有任何闸门会发现**。

所以结论里用 `<!-- dead: METHOD /path -->` 声明它断言为死的接口，
脚本**当场核验**：这些接口若已经有调用方 → **结论过期 → 退出码 1**
（无论有没有 `--strict`）。

📌 **这正是知识库里那条**：
    「手写清单和自己新写的闸门都会犯同一个错 ——
      真相来源必须是目录/应用配置本身，不是抄一份列表」
→ 手写结论也一样：**让结论自己声明断言，由脚本核验**。

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


LEAF_RE = re.compile(
    r"(?P<key>[A-Za-z_]\w*)\s*:\s*"
    r"\((?:[^()]|\([^()]*\))*\)\s*(?:=>|:\s*async\s*\([^()]*\)\s*=>)\s*(?:await\s+)?"
    r"request\.(?P<method>get|post|put|delete|patch)"
    r"(?:<(?:[^<>]|<[^<>]*>)*>)?\(\s*"
    r"(?P<q>[`'\"])(?P<url>[^`'\"]+)(?P=q)",
    re.S,
)
OBJ_RE = re.compile(r"(?:export\s+)?const\s+([A-Za-z_]\w*)\s*=\s*\{")


def _match_brace(text, open_idx):
    """返回与 text[open_idx] 这个 '{' 配对的 '}' 的下标（跳过字符串和注释）"""
    depth = 0
    i = open_idx
    n = len(text)
    while i < n:
        c = text[i]
        if c in "'\"`":
            quote = c
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == quote:
                    break
                i += 1
        elif c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j == -1 else j + 2
            continue
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return n - 1


def _blank(text, start, end):
    """把 [start, end) 区间替换成空格（保留长度和偏移）"""
    return text[:start] + " " * (end - start) + text[end:]


def parse_api_functions():
    """返回 {函数全名: [(METHOD, url), ...]}

    📌 **必须处理嵌套对象**：
       `socialApi.liveStream.list` 和 `socialApi.tiktokShop.list` 的叶子 key 都叫 `list`，
       只按「最近的 export const」取名字会把它们全压成 `socialApi.list`，
       URL 归属全错 → 死接口清单整片误判。
    """
    funcs = {}

    def walk(body, stack):
        # 先找嵌套对象：key: { ... }
        nested = []
        for m in re.finditer(r"(?<![\w$])([A-Za-z_]\w*)\s*:\s*\{", body):
            open_idx = m.end() - 1
            close_idx = _match_brace(body, open_idx)
            nested.append((m.group(1), open_idx, close_idx))
        # 叶子函数要在**去掉嵌套对象体**之后才找，避免把嵌套里的函数算到外层
        outside = body
        for _key, open_idx, close_idx in reversed(nested):
            outside = _blank(outside, open_idx, close_idx + 1)
        for m in LEAF_RE.finditer(outside):
            name = ".".join(stack + [m.group("key")])
            funcs.setdefault(name, []).append((m.group("method").upper(), m.group("url")))
        # 递归进嵌套对象
        for key, open_idx, close_idx in nested:
            walk(body[open_idx + 1:close_idx], stack + [key])

    for ts in sorted(WEB_API.glob("*.ts")):
        text = ts.read_text(encoding="utf-8")
        for m in OBJ_RE.finditer(text):
            open_idx = m.end() - 1
            close_idx = _match_brace(text, open_idx)
            walk(text[open_idx + 1:close_idx], [m.group(1)])
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


FEIGN_RE = re.compile(
    r"@FeignClient\s*\(\s*name\s*=\s*[\"'](?P<name>[\w-]+)[\"']"
    r"(?:\s*,\s*path\s*=\s*[\"'](?P<path>[^\"']*)[\"'])?",
    re.S,
)
MAPPING_RE = re.compile(
    r"@(Get|Post|Put|Delete|Patch)Mapping\s*\(\s*(?:value\s*=\s*)?[\"'](?P<p>[^\"']*)[\"']"
)
REST_TEMPLATE_RE = re.compile(
    r"restTemplate\s*\.\s*(?:getForObject|postForObject|putForObject|deleteForObject|exchange)"
    r"\s*\(\s*[\"'](?P<url>[^\"']+)[\"']",
    re.S,
)


def collect_backend_callers(endpoints):
    """扫描所有 Java 源码，找出哪些路径被**别的模块**真的调用过。

    📌 **2026-11-04 重写**：原来用「路径最后两段的字面量在源文件里出现过」
    来判断谁在调谁 —— **这个判据不成立**：

    1. **自身模块从来没被排除掉**（真 bug）：
       `for _, _, http, path in endpoints:` 里 `_` 被**连续绑定两次**，
       最终拿到的是**文件路径**（PosixPath）而不是模块名（str）
       → `module == _` 永远是 str == PosixPath → 永远为假
       → 任何模块自己的 javadoc 里写一句
         「GET /api/v1/pricing/health」就会让该端点
         **从死接口清单里消失**（假绿）。

    2. **判据本身会误判**：needle 取「最后两段」太短，
       `/{id}/confirm` 这种会到处撞；而 javadoc 里的
       `@PostMapping("/{id}/confirm")` **不是**跨服务调用。

    → 改成**只认真实的调用点**：
       - **Feign 客户端**：`@FeignClient(name="erp-order-service", path="/order")`
         + 接口方法上的 `@PostMapping("/create")` → 真的在调 erp-order-service 的 /order/create
       - **RestTemplate 字面量 URL**：`restTemplate.getForObject("/xxx", ...)`
         → 但只认**指向本仓内部服务**的路径
    """
    by_module_path = {}
    for module, _java, http, path in endpoints:
        by_module_path.setdefault(module, []).append((http, path))

    texts = {}
    for module_dir in sorted(ROOT.glob("erp-*")):
        if not (module_dir / "src/main/java").exists():
            continue
        for java in (module_dir / "src/main/java").rglob("*.java"):
            texts[java] = java.read_text(encoding="utf-8", errors="ignore")

    called = set()
    for java, text in texts.items():
        caller_module = java.parts[len(ROOT.parts)]

        # ---- 1) Feign 客户端 ----
        for fm in FEIGN_RE.finditer(text):
            target_service = fm.group("name")
            base_path = (fm.group("path") or "").rstrip("/")
            target_module = target_service  # 服务名 = 模块名（erp-xxx-service）
            if target_module not in by_module_path:
                continue
            # 只取该 @FeignClient 之后的接口方法映射
            tail = text[fm.end():]
            for mm in MAPPING_RE.finditer(tail):
                sub = mm.group("p")
                full = f"{base_path}/{sub.lstrip('/')}" if sub else base_path
                full = full.rstrip("/") or "/"
                method = mm.group(1).upper()
                for http, path in by_module_path[target_module]:
                    if _path_matches(path, full):
                        called.add((caller_module, http, path))

        # ---- 2) RestTemplate 字面量 URL（只认指向内部服务路径的）----
        for rm in REST_TEMPLATE_RE.finditer(text):
            url = rm.group("url")
            if not url.startswith("/"):
                continue  # 外部 URL（LLM / TikTok / 亚马逊）不是内部服务
            for module, _j, http, path in endpoints:
                if module == caller_module:
                    continue
                if _path_matches(path, url):
                    called.add((caller_module, http, path))

    return called


def _path_matches(backend_path, wanted):
    """后端路径是否与「调用的目标路径」一致（忽略 /api 前缀差异）。"""
    def norm(p):
        segs = [s for s in p.split("/") if s]
        if segs and segs[0] == "api":
            segs = segs[1:]
        return "/" + "/".join(segs)
    return norm(backend_path) == norm(wanted)


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


# ==============================================================
# 「重点结论」—— 手写结论，但**每条断言都由脚本当场核验**
#
# 用 `<!-- dead: METHOD /path -->` 声明「我断言它是死接口」，
# 脚本会核验这些断言是否还成立 → 结论过期会**自动报警**。
#
# 📌 为什么不能光靠手写：2026-11-04 实测，上一版写着
# 「知识库没有任何视图调用」，而上一轮已经做完了 → **文档在骗人**。
# ==============================================================
ANALYSIS_LINES = [
    "## 重点结论（**每条结论都由脚本当场核验**，结论过期会自动报警）",
    "",
    "> 📌 这些结论是**手写的**，但**不能信** —— 手写结论会静默过期",
    "> （2026-11-04 实测：上一版写着「知识库没有任何视图调用」，",
    ">   结果上一轮已经把知识库管理页做完了 → 文档在骗人）。",
    "> 所以每条结论后面用 `<!-- dead: <路径> -->` 声明它断言为死的接口，",
    "> **脚本会当场核验**：这些接口若已经有调用方 → 报告变红并提示结论已过期。",
    "",
    "### 1. 知识库：UI 已做完，剩的是 RAG 侧的 ask / stats",
    "",
    "上一版这里写着「知识库没有任何视图调用」—— **那已经过期了**：",
    "上一轮已经把知识库管理页做进 `views/ai/Assistant.vue` 的「知识库」tab",
    "（`knowledgeBaseApi.list/categories/add/remove` 都在调）。",
    "",
    "现在真正还没接的是**知识库问答**接口：",
    "<!-- dead: POST /api/ai/knowledge/ask -->",
    "",
    "（`GET /api/ai/knowledge/stats` 2026-11-04 已接上 ——",
    "  聊天页开场白的「基于 N 条行业知识库」改成动态取真实条数，",
    "  原来写死「330+」而真实只有 31 条 → 界面在承诺不存在的东西。）",
    "",
    "⚠️ **同日的第二个真缺陷**：这个 `stats` 接口原先返回的是",
    "`knowledge_document` 表的条数，而**聊天检索**和**知识库列表**用的都是",
    "`knowledge_base` 表 → 开场白说 5、页签列 31 → **界面自相矛盾**。",
    "2026-11-04 已修：`stats` 改数 `knowledge_base`，",
    "向量文档侧的统计另开 `GET /api/ai/knowledge/documents/stats`（两个口径用 URL 区分）。",
    "→ 新增闸门 `scripts/check-stat-source-consistency.py` 防止复发。",
    "",
    "⚠️ **api 层有个命名陷阱**（不是死接口，但容易踩）：",
    "`aiAssistantApi.knowledge.list/add/remove` 和 `knowledgeBaseApi.list/add/remove`",
    "**指向同一批 URL**，但注释声称它们是**两张不同的表**",
    "（`knowledge_document` vs `knowledge_base`）。",
    "实际后端 `addKnowledge()` 写的是 `knowledge_base` —— 注释与实现对不上。",
    "→ 视图里能用的只有 `knowledgeBaseApi` 那一套。",
    "",
    "### 2. 一批「后端做完了，UI 一个页面都没有」的功能域",
    "",
    "| 域 | 现状 |",
    "|---|---|",
    "| 客户跟进 / 分组 / 标签 | 后端 CRUD 全套，前端客户页只有列表+增删改 |",
    "| 仓库库存作业 | 移库/冻结/解冻/报损/盘点/批次/波次/调拨 —— 前端仓库页只有仓库+库位 |",
    "| 租户计费 / 用量 / 配额 | 前端租户页只有租户列表 |",
    "| AI 辅助（意图/情感/统计/batch/health） | 都是「进阶能力」，UI 只有最基础的聊天 |",
    "",
    "### 3. 「健康检查」类接口全部未接",
    "",
    "`/health` 在 8 个服务里都有（共 9 条声明，`/api/v1/pricing/health` 被两个 controller 各写了一遍），",
    "前端一个都没调 —— 缺一个「系统健康总览」页。",
    "<!-- dead: GET /api/v1/ai-assistant/health -->",
    "<!-- dead: GET /api/v1/anomaly/health -->",
    "<!-- dead: GET /api/v1/inventory-prediction/health -->",
    "<!-- dead: GET /api/v1/product-description/health -->",
    "<!-- dead: GET /api/v1/recommend/health -->",
    "<!-- dead: GET /api/v1/analytics/health -->",
    "<!-- dead: GET /api/v1/pricing/health -->",
    "<!-- dead: GET /logistics/health -->",
    "",
    "### 4. 死路由",
    "",
    "`/order/detail/:id` 这条路由没有任何地方导航过去，",
    "而且它指向的 `views/order/index.vue` 其实是**列表页**（详情是弹窗）。",
    "",
]


def extract_claims(lines):
    """从「重点结论」里抽出 `<!-- dead: METHOD /path -->` 形式的断言。

    📌 **为什么要有这个**（2026-11-04 实测）：
    「重点结论」是**手写的**，而上一版写着「知识库没有任何视图调用」——
    结果上一轮已经把知识库管理页做完了 → **文档在骗人，而且没人发现**。

    📌 **手写清单和自己新写的闸门都会犯同一个错** ——
    真相来源必须是**目录/应用配置本身**，不是抄一份列表。
    → 所以结论里声明的每个「死接口」，脚本都要**当场核验**：
      若它其实已经有调用方 → 结论已过期 → 报告变红。
    """
    claims = []
    for line in lines:
        m = re.search(r"<!--\s*dead:\s*(GET|POST|PUT|DELETE|PATCH)\s+(\S+)\s*-->", line)
        if m:
            claims.append((m.group(1), m.group(2)))
    return claims


def verify_claims(claims, dead, frontend_used, backend_callers, endpoints):
    """核验每条「我断言它是死接口」的结论是否还成立。

    返回 [(http, path, 原因)] —— 空的表示全部成立。
    """
    dead_keys = {(h, p) for (_m, _j, h, p, _s) in dead}
    all_endpoints = {(h, p) for (_m, _j, h, p) in endpoints}
    stale = []
    for http, path in claims:
        # 断言的接口得**存在**（否则是凭空写的）
        if (http, path) not in all_endpoints:
            stale.append((http, path, "结论里引用的接口在后端已经不存在了"))
            continue
        # 断言「它是死的」—— 若它不在死接口清单里，说明已经有调用方了
        if (http, path) not in dead_keys:
            reason = "现在已经有调用方了（前端视图 / 其他服务）"
            for m, url in frontend_used:
                if m == http and url_matches_backend(url, path, None, m):
                    reason = f"前端视图已经在调它了（{url}）"
                    break
            stale.append((http, path, reason))
    return stale


def verify_exhaustive(claims, endpoints, dead):
    """核验「这一类全部未接」的断言是否**列举完整**。

    📌 **为什么要查这一项**（2026-11-04 实测）：
    结论 #3 写着「`/health` 在多个服务里都有，前端一个都没调」，
    并声明了 5 条 `<!-- dead: GET .../health -->` 断言。
    但后端实际有 **8** 个 health 端点 → **漏了 3 个没声明**。

    📌 **「逐条断言都对」≠「结论是完整的」** ——
    上面那 5 条断言确实都还成立（没过期），
    但结论本身漏了 3 个 → **闸门全绿，结论却在骗人**。

    → 所以凡是带 `<!-- dead: ... -->` 的「全称型」结论，
      必须把它覆盖的**整个类别**都列出来。

    判据：以 `/health` 结尾的端点为类别（可扩展）。
    """
    health_claims = {c for c in claims if c[1].rstrip("/").endswith("/health")}
    if not health_claims:
        return []
    all_health = {(h, p) for (_m, _j, h, p) in endpoints
                  if p.rstrip("/").endswith("/health")}
    missing = sorted(all_health - health_claims)
    return [(h, p, "结论说「这一类全部未接」，但这条没在断言里列出来 → 结论不完整")
            for h, p in missing]


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
        *ANALYSIS_LINES,
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
    return lines


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

    # 📌 **核验「重点结论」是否已过期** ——
    #    手写结论会静默过期（2026-11-04 实测：上一版说「知识库没有任何视图调用」，
    #    结果上一轮已经做完了 → 文档在骗人）。
    #    → 结论里用 `<!-- dead: METHOD /path -->` 声明它断言为死的接口，
    #      这里**当场核验**：若这些接口已经有调用方 → 结论过期 → 报告变红。
    claims = extract_claims(ANALYSIS_LINES)
    stale = verify_claims(claims, dead, frontend_used, backend_callers, endpoints)
    # 📌 **还要查「结论是否列举完整」** ——
    #    「逐条断言都成立」≠「结论是完整的」：
    #    结论说「health 这类全部未接」却只列了 5 个（共 8 个）→ 闸门全绿，结论在骗人。
    incomplete = verify_exhaustive(claims, endpoints, dead)

    if md:
        write_markdown(dead, endpoints, frontend_used, backend_callers, routes, ROOT / md)

    problems = stale + incomplete
    if problems:
        if stale:
            print(f"\n❌ 「重点结论」已过期（{len(stale)} 条断言不再成立）:")
            for http, pth, reason in stale:
                print(f"   - {http} {pth}")
                print(f"       {reason}")
        if incomplete:
            print(f"\n❌ 「重点结论」列举不完整（{len(incomplete)} 条没被断言覆盖）:")
            for http, pth, reason in incomplete:
                print(f"   - {http} {pth}")
                print(f"       {reason}")
        print()
        print("   📌 手写结论会静默过期 —— 请同步更新 scripts/check-dead-endpoints.py")
        print("      里的「重点结论」段落（并更新它声明的 `<!-- dead: ... -->` 断言）。")
        # 📌 **过期/不完整的结论必须让脚本失败**（即使没加 --strict）——
        #    「疑似死接口」是**排期 backlog**（REST 接口可能给外部客户端用，
        #    脚本判断不了该不该删）→ 保持 advisory，exit 0。
        #    但「重点结论」是**提交进仓库的文档**，
        #    它说错话 = 文档在骗人 → **必须红**。
        #    📌 2026-11-04 实测：上一版结论「知识库没有任何视图调用」，
        #       而上一轮已经把页面做完了 —— 没人发现，因为没人核验。
        print(f"\n退出码 1：{len(stale)} 条「重点结论」已过期、{len(incomplete)} 条列举不完整")
        return 1

    if strict:
        print(f"\n退出码 1：{len(dead)} 个疑似死接口")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
