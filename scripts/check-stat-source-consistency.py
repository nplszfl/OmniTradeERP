#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「统计口径一致性」巡检：**同一个功能里，「报条数的接口」和「列数据的接口」必须查同一张表**。

## 为什么需要这个脚本

2026-11-04 在 AI 客服模块抓到一个**真缺陷**，而且是那种
「单测全绿、闸门全绿、接口通着、页面能开」却**界面自相矛盾**的缺陷：

    // 后端 KnowledgeController
    @GetMapping("/stats")
    public ResponseEntity<KnowledgeStats> stats() {
        return ResponseEntity.ok(ragService.stats());   // ← knowledge_document 表
    }

    // 前端 Assistant.vue（开场白）
    `您好，我是 AI 助手。基于 ${activeCount} 条行业知识库…`

    // 同一个页面的「知识库」页签列的是
    knowledgeBaseApi.list()      →  GET /ai/knowledge/base/list → knowledge_base 表

结果：
    开场白说「基于 **5** 条」（knowledge_document 的条数），
    而**同一个页面**的知识库列表里列着 **31** 条（knowledge_base），
    聊天真正检索的也是那 31 条
    → **数字和文案说的是两张表** → 界面自己打自己的脸。

📌 核心教训（规则 19 / 58）：
    - 「界面承诺的数字」必须来自**这个功能真正用的那份数据**；
    - 不要**借用一个数字**去装另一种含义（这里是借 knowledge_document 的条数
      去说 knowledge_base 的规模）。

## 本脚本查什么

**端到端**地核验一条链路：

    前端 api 层的「同一个功能命名空间」里的
        ├─ 报条数的接口   (stats / count / statistics)
        └─ 列数据的接口   (list / page / query / search)
    → 两者最终落到**同一张表**吗？

📌 **为什么按「前端命名空间」分组**（而不是按后端 controller）：
    - 后端一个 controller 可能管多张表（分不清哪个接口配哪个界面）；
    - 前端 api 层的命名空间（`knowledgeBaseApi`）**就是**一个功能域 ——
      同一个命名空间里的「条数」和「列表」**本来就应该说同一件事**。
    → 这样判据既够严（能抓住本例），又不会跨功能域乱比。

## 「落到哪张表」怎么算（静态追踪，不靠人写清单）

1. 前端 api 路径（加上 axios baseURL `/api`）
   → **网关路由**（Path / StripPrefix / RewritePath）
   → **后端 service 收到的路径**
2. 后端 controller 方法 → 它（及它调用的 service 方法）里出现的
   `xxxRepository.方法(...)` → repository 的实体 → 实体的
   `@Table(name=...)` / `@TableName("...")` → **表名**

⚠️ 追踪不到就**明说「追不到」**，绝不假装一致（规则 43）。

## 用法

    python3 scripts/check-stat-source-consistency.py
    python3 scripts/check-stat-source-consistency.py --strict   # 有问题就退出码 1

退出码：
    0 = 没发现口径不一致
    1 = 发现**同一功能里「条数」和「列表」查的不是同一张表**（fatal）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA_ROOT = ROOT
WEB_API = ROOT / "erp-web/src/api"
WEB_VIEWS = ROOT / "erp-web/src/views"
GATEWAY = ROOT / "erp-gateway/src/main/resources/application.yml"

BASE_URL = "/api"


# ══════════════════════════════════════════════════════════════════════
# 0. 通用工具
# ══════════════════════════════════════════════════════════════════════

def strip_comments(src):
    """**先去掉注释**再解析。

    📌 这是本项目踩过的坑（规则 45）：
    「注释里的代码要 strip」——
    javadoc 里写着 `{@code ragService.stats()}`，
    直接拿源码做正则匹配 → **先匹配到注释里的那个** →
    方法体被截成 26 个字符 → 整条调用失明 → 闸门显示全绿，
    但它**根本没看**那段代码。
    """
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


def norm_path(p):
    """路径归一：去 baseURL 前缀、去结尾斜杠。"""
    p = (p or "").strip()
    if p.startswith(BASE_URL + "/"):
        p = p[len(BASE_URL):]
    elif p == BASE_URL:
        p = ""
    if len(p) > 1 and p.endswith("/"):
        p = p[:-1]
    return p


# ══════════════════════════════════════════════════════════════════════
# 1. 网关路由
# ══════════════════════════════════════════════════════════════════════

def parse_gateway():
    """返回 [{id, paths, methods, uri, strip, rewrite}]"""
    if not GATEWAY.exists():
        return []
    lines = GATEWAY.read_text(encoding="utf-8").split("\n")
    routes = []
    cur = None
    in_routes = False
    for ln in lines:
        if re.match(r"^\s*routes:\s*$", ln):
            in_routes = True
            continue
        if in_routes and ln.strip() and not ln.startswith(" ") and not ln.startswith("\t"):
            break
        m = re.match(r"^\s*-\s*id:\s*(\S+)\s*$", ln)
        if m:
            if cur:
                routes.append(cur)
            cur = {"id": m.group(1), "paths": [], "methods": [],
                   "uri": None, "strip": None, "rewrite": None}
            continue
        if cur is None:
            continue
        m = re.match(r"^\s*uri:\s*(\S+)\s*$", ln)
        if m:
            cur["uri"] = m.group(1)
            continue
        m = re.match(r"^\s*-\s*Path=(.+?)\s*$", ln)
        if m:
            for piece in m.group(1).split(","):
                piece = piece.strip()
                if piece.startswith("/"):
                    cur["paths"].append(piece)
            continue
        m = re.match(r"^\s*-\s*Method=(.+?)\s*$", ln)
        if m:
            for piece in m.group(1).split(","):
                piece = piece.strip()
                if piece:
                    cur["methods"].append(piece.upper())
            continue
        m = re.match(r"^\s*-\s*StripPrefix=(\d+)\s*$", ln)
        if m:
            cur["strip"] = int(m.group(1))
            continue
        m = re.match(r"^\s*-\s*RewritePath=(.+?)\s*$", ln)
        if m:
            cur["rewrite"] = m.group(1).strip()
            continue
    if cur:
        routes.append(cur)
    return routes


def path_matches(pattern, path):
    """Spring Cloud Gateway 的 Path 谓词语义。

    - 精确路径 `/a/b` 只匹配 `/a/b`
    - 通配 `/a/**` 匹配 `/a`（**零段也匹配**）和 `/a/b/c`
    """
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        return path == prefix or path.startswith(prefix + "/")
    return pattern == path


def rewrite_target(route, path):
    """把网关收到的路径算成「后端实际收到的路径」。"""
    if route["rewrite"]:
        raw = route["rewrite"]
        # RewritePath=/a/x/(?<id>.*), /b/y/$\{id}
        parts = [x.strip() for x in raw.split(",")]
        src = parts[0]
        dst = parts[1] if len(parts) > 1 else src
        # Spring 的 {name} / $\{name} 写法
        def sub_var(m):
            return "<" + m.group(1) + ">"
        src_re = re.sub(r"\$\{(\w+)\}", sub_var, src)
        src_re = re.sub(r"\(\?<(\w+)>\.\*\)", r"(?P<\1>.*)", src_re)
        try:
            m = re.match("^" + src_re + "$", path)
        except re.error:
            return path
        if not m:
            return path
        out = dst
        for k, v in m.groupdict().items():
            out = out.replace("$\\{%s}" % k, v or "").replace("{%s}" % k, v or "")
        return out
    strip = route["strip"]
    if strip:
        parts = path.split("/")
        # StripPrefix=n 去掉前 n 段
        rest = "/".join(parts[1 + strip:]) if len(parts) > 1 + strip else ""
        return "/" + rest if rest else ""
    return path


# ══════════════════════════════════════════════════════════════════════
# 2. 后端：实体 / repository / controller
# ══════════════════════════════════════════════════════════════════════

def build_entity_tables():
    """实体类名 → 表名。

    ⚠️ 本项目**两种 ORM 混用**：
      - JPA：          `@Table(name = "knowledge_base")`
      - MyBatis-Plus： `@TableName("t_platform_config")`
    只认一种 → 90 个实体里只认出 5 个 →
    「追不到取数来源」的假阴性淹没真问题（规则 43）。
    """
    out = {}
    for p in JAVA_ROOT.glob("erp-*/src/main/java/**/entity/*.java"):
        try:
            src = strip_comments(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        cls = p.stem
        m = re.search(r'@Table\s*\(\s*(?:name\s*=\s*)?"([^"]+)"', src)
        if not m:
            m = re.search(r'@TableName\s*\(\s*(?:value\s*=\s*)?"([^"]+)"', src)
        if m:
            out[cls] = m.group(1)
    return out


def build_repository_entities():
    """Repository / Mapper 接口名 → 实体类名。

    ⚠️ 本项目**两种数据访问层混用**：
      - JPA：  `interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long>`
      - MP：   `interface InventoryMapper extends BaseMapper<Inventory>`
    只认前者 → 大量 service 用 Mapper 的就全成了「追不到取数来源」
    → 假阴性淹没真问题（规则 43）。
    """
    out = {}
    for p in JAVA_ROOT.glob("erp-*/src/main/java/**/repository/*.java"):
        try:
            src = strip_comments(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        m = re.search(r"interface\s+(\w+)\s+extends\s+[^\n]*?<\s*(\w+)\s*,", src)
        if m:
            out[m.group(1)] = m.group(2)
    for p in JAVA_ROOT.glob("erp-*/src/main/java/**/mapper/*.java"):
        try:
            src = strip_comments(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        m = re.search(r"interface\s+(\w+)\s+extends\s+[^\n]*?<\s*(\w+)\s*[,>\s]", src)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def build_field_maps():
    """(字段名 → Repository 类型名, 字段名 → 类型名)"""
    repo_map = {}
    type_map = {}
    for p in JAVA_ROOT.glob("erp-*/src/main/java/**/*.java"):
        try:
            src = strip_comments(p.read_text(encoding="utf-8"))
        except Exception:
            continue
        for m in re.finditer(
                r"(?:private|protected|public)\s+(?:final\s+)?([A-Z]\w+)\s+(\w+)\s*[;=]", src):
            type_map.setdefault(m.group(2), m.group(1))
            if m.group(1).endswith("Repository") or m.group(1).endswith("Mapper"):
                repo_map.setdefault(m.group(2), m.group(1))
    return repo_map, type_map


def extract_method_body(src, method_name, start_at=0):
    """取方法名之后的第一对花括号之间的内容。"""
    m = re.search(r"\b" + re.escape(method_name) + r"\s*\(", src[start_at:])
    if not m:
        return None
    i = src.find("{", start_at + m.end())
    if i < 0:
        return None
    depth = 0
    for j in range(i, len(src)):
        c = src[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[i:j + 1]
    return None


class Backend:
    def __init__(self):
        self.entity_tables = build_entity_tables()
        self.repos = build_repository_entities()
        self.repo_map, self.type_map = build_field_maps()
        self.services = self._index_services()
        self.service_base_entity = self._index_service_base_entity()
        self.endpoints = self._index_controllers()

    def _index_services(self):
        """service 类 → {方法名: 方法体}

        📌 **必须把 impl 类和接口名对上**：
        controller 注入的是**接口** `InventoryService`，
        但方法体写在 `InventoryServiceImpl` 里
        → 只按类名精确匹配 → 一个 service 都追不到
        → 「追不到取数来源」的假阴性会淹没真问题（规则 43）。
        → 这里对 `XxxServiceImpl` **额外注册一个 `XxxService` 别名**。
        """
        out = {}
        for p in JAVA_ROOT.glob("erp-*/src/main/java/**/service/**/*.java"):
            try:
                src = strip_comments(p.read_text(encoding="utf-8"))
            except Exception:
                continue
            if "@Service" not in src and "@Component" not in src:
                continue
            methods = {}
            for m in re.finditer(r"\b(?:public|protected)\s+[\w<>,\[\]\s\.]+?\s+(\w+)\s*\(", src):
                name = m.group(1)
                if name in ("if", "for", "while", "switch", "catch", "return", "new"):
                    continue
                body = extract_method_body(src, name, m.start())
                if body:
                    methods.setdefault(name, body)
            if not methods:
                continue
            cls = p.stem
            out.setdefault(cls, methods)
            # `FooServiceImpl` → 也注册为 `FooService`（接口名）
            if cls.endswith("Impl"):
                out.setdefault(cls[:-4], methods)
        return out

    def _index_service_base_entity(self):
        """service 类名 → 它继承自 `ServiceImpl<Mapper, Entity>` 的那个 Entity。

        📌 MyBatis-Plus 的 `IService` 提供**无接收者**的 `list()` / `count()` /
        `page()` —— 它们操作的就是那个实体表。
        不认这个 → 这类 service 的取数来源**完全追不到**（规则 43）。
        """
        out = {}
        for p in JAVA_ROOT.glob("erp-*/src/main/java/**/service/**/*.java"):
            try:
                src = strip_comments(p.read_text(encoding="utf-8"))
            except Exception:
                continue
            entity = self._service_base_entity(src)
            if entity:
                out[p.stem] = entity
                if p.stem.endswith("Impl"):
                    out.setdefault(p.stem[:-4], entity)
        return out

    def _service_base_entity(self, src):
        """`extends ServiceImpl<SupplierMapper, Supplier>` → 实体类名 `Supplier`。

        📌 MyBatis-Plus 的 `IService` 提供**无接收者**的 `list()` / `count()` /
        `page()` / `getOne()` —— 它们操作的就是那个实体表。
        不认这个 → 这类 service 的取数来源**完全追不到**（规则 43）。
        """
        m = re.search(r"extends\s+ServiceImpl\s*<\s*\w+\s*,\s*(\w+)\s*>", src)
        if m:
            return m.group(1)
        m = re.search(r"extends\s+IService\s*<\s*(\w+)\s*>", src)
        if m:
            return m.group(1)
        return None

    def _index_controllers(self):
        """(http, path) → {
            'tables': 该接口（间接）操作的所有表,
            'count_tables': 真正供出「条数」的那张表
        }"""
        out = {}
        for p in JAVA_ROOT.glob("erp-*/src/main/java/**/controller/*Controller.java"):
            try:
                src = strip_comments(p.read_text(encoding="utf-8"))
            except Exception:
                continue
            base = ""
            m = re.search(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"', src)
            if m:
                base = m.group(1)
            for em in re.finditer(
                    r'@(Get|Post|Put|Delete|Patch)Mapping\s*(\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?',
                    src):
                http = em.group(1).upper()
                sub = em.group(3) or ""
                tail = src[em.end(): em.end() + 500]
                mm = re.search(r"public\s+[\w<>,\[\]\s\.]+?\s+(\w+)\s*\(", tail)
                if not mm:
                    continue
                body = extract_method_body(src, mm.group(1), em.end()) or ""
                path = (base + sub).replace("//", "/")
                out[(http, path)] = {
                    "tables": self.tables_of(body),
                    "count_tables": self.count_source_tables(body),
                    "body": body,
                }
        return out

    def tables_of(self, body):
        """方法体（+ 它调用的 service 方法）操作的所有表。"""
        tables = set()
        for field, type_name in self.repo_map.items():
            if re.search(r"\b" + re.escape(field) + r"\s*\.", body):
                entity = self.repos.get(type_name)
                if entity and entity in self.entity_tables:
                    tables.add(self.entity_tables[entity])
        # 跟进一层 service
        for m in re.finditer(r"\b(\w+)\s*\.\s*(\w+)\s*\(", body):
            field, called = m.group(1), m.group(2)
            svc_cls = self.type_map.get(field)
            if not svc_cls or svc_cls not in self.services:
                continue
            mbody = self.services[svc_cls].get(called)
            if not mbody:
                continue
            for f2, t2 in self.repo_map.items():
                if re.search(r"\b" + re.escape(f2) + r"\s*\.", mbody):
                    entity = self.repos.get(t2)
                    if entity and entity in self.entity_tables:
                        tables.add(self.entity_tables[entity])
            # 📌 MyBatis-Plus `IService` 的**无接收者**方法
            #    （`list()` / `count()` / `page()`）——
            #    它们操作的是 service 继承的那个实体表。
            #    不认这个 → 这类 service 的表**完全追不到**（规则 43）。
            if re.search(r"(?<![\w.])(list|count|page|getOne)\s*\(", mbody):
                ent = self.service_base_entity.get(svc_cls)
                if ent and ent in self.entity_tables:
                    tables.add(self.entity_tables[ent])
        return tables

    def _repo_table(self, field):
        """repository/mapper 字段名 → 表名。"""
        type_name = self.repo_map.get(field)
        if not type_name:
            return None
        entity = self.repos.get(type_name)
        if entity and entity in self.entity_tables:
            return self.entity_tables[entity]
        return None

    def _collection_table(self, var, body, depth=0, svc=None):
        """变量 → 它那个**集合**来自哪张表。

        ```java
        List<Inventory> inventories = inventoryMapper.selectList(w);  // → erp_inventory
        List<InventoryLog> logs = inventoryLogMapper.selectList(w);   // → erp_inventory_log
        ```
        """
        if depth > 5:
            return None
        m = re.search(r"\b[\w<>,\[\]\s\.]*?\b" + re.escape(var)
                      + r"\s*=\s*([^;]+);", body)
        if not m:
            return None
        expr = m.group(1).strip()
        # `var = repo.method(...)`
        rm = re.match(r"(\w+)\s*\.\s*(\w+)\s*\(", expr)
        if rm:
            t = self._repo_table(rm.group(1))
            if t:
                return t
        # `var = list()` —— MyBatis-Plus IService 的无接收者方法
        bm = re.match(r"^(list|count|page|getOne|getList)\s*\(", expr)
        if bm and svc:
            ent = self.service_base_entity.get(svc)
            if ent and ent in self.entity_tables:
                return self.entity_tables[ent]
        # `var = otherVar`
        if re.fullmatch(r"[A-Za-z_$][\w$]*", expr):
            return self._collection_table(expr, body, depth + 1)
        return None

    def _count_in(self, expr, body, depth=0, svc=None):
        """表达式里**有没有真正的计数操作**（.size() / .count()）？有就返回那张表。

        📌 **计数 ≠ 求和**：
        `logs.stream().mapToInt(InventoryLog::getQuantity).sum()` 是求和，
        不是条数 → 不算「条数来源」（否则 inventory_log 会被误判）。
        """
        if depth > 5 or not expr:
            return None
        expr = expr.strip()
        # 拆掉强转 `(int) x` → `x`
        expr = re.sub(r"^\(\s*[\w<>,\[\]\s\.]+\s*\)\s*", "", expr).strip()

        # `repo.count()` / `repo.countByXxx()`
        for m in re.finditer(r"\b(\w+)\s*\.\s*(count[A-Za-z]*)\s*\(", expr):
            t = self._repo_table(m.group(1))
            if t:
                return t

        # `X.size()`
        for m in re.finditer(r"\b(\w+)\s*\.\s*size\s*\(", expr):
            t = self._collection_table(m.group(1), body, depth + 1, svc)
            if t:
                return t

        # `X.stream()....count()`（**必须真有 .count()**）
        for m in re.finditer(r"\b(\w+)\s*\.\s*stream\s*\(", expr):
            if not re.search(r"\.count\s*\(", expr[m.end():]):
                continue
            t = self._collection_table(m.group(1), body, depth + 1, svc)
            if t:
                return t

        # 纯变量：看它的定义式里有没有计数
        if re.fullmatch(r"[A-Za-z_$][\w$]*", expr):
            m = re.search(r"\b[\w<>,\[\]\s\.]*?\b" + re.escape(expr)
                          + r"\s*=\s*([^;]+);", body)
            if m:
                t = self._count_in(m.group(1), body, depth + 1, svc)
                if t:
                    return t
                # `long total = repo.count()` 型：变量本身就是一次计数
                rm = re.match(r"(\w+)\s*\.\s*(count[A-Za-z]*)\s*\(", m.group(1).strip())
                if rm:
                    t = self._repo_table(rm.group(1))
                    if t:
                        return t
        return None

    def count_source_tables(self, body, depth=0, svc=None):
        """**真正供出「条数」的那张表**（数据流追踪，不是「碰过的所有表」）。

        📌 **为什么不能直接用「碰过的所有表」**：
        统计接口常常**额外**查一张辅助表来算别的东西：
        ```java
        List<Inventory> inventories = inventoryMapper.selectList(w);   // ← 条数在这
        List<InventoryLog> logs = inventoryLogMapper.selectList(w);   // ← 只是算流量
        stats.setTotalSku(inventories.size());
        ```
        用「碰过的所有表」→ stats 的表 = {erp_inventory, erp_inventory_log}
        → 和列表的 {erp_inventory} **不相等** → 假阳性。
        → 真实判据是：**条数是从哪个集合的 `.size()` / `.count()` 来的**。
        """
        if depth > 3:
            return set()
        tables = set()

        # ---- A) 表达式直接是 .size()/.count() ----
        #   `stats.setTotalSku(inventories.size())`
        #   `result.put("total", list.size())`
        for m in re.finditer(r"\.\s*(?:set)?\w*\s*\(\s*([^()]*?)\s*\)", body):
            t = self._count_in(m.group(1), body, svc=svc)
            if t:
                tables.add(t)
        #   `long total = all.size();`
        for m in re.finditer(r"\b[\w<>,\[\]\s\.]*?\b(\w+)\s*=\s*([^;]+);", body):
            t = self._count_in(m.group(2), body, svc=svc)
            if t:
                tables.add(t)

        # ---- B) controller 转发 service 的情况 ----
        if not tables:
            for m in re.finditer(r"\b(\w+)\s*\.\s*(\w+)\s*\(", body):
                field, called = m.group(1), m.group(2)
                svc_cls = self.type_map.get(field)
                if not svc_cls or svc_cls not in self.services:
                    continue
                mbody = self.services[svc_cls].get(called)
                if not mbody:
                    continue
                t = self.count_source_tables(mbody, depth + 1, svc=svc_cls)
                if t:
                    tables |= t
                    break

        return tables


# ══════════════════════════════════════════════════════════════════════
# 3. 前端 api 层
# ══════════════════════════════════════════════════════════════════════

def parse_frontend_api():
    """命名空间 → {方法名: (http, 前端路径)}

    形如：
        export const knowledgeBaseApi = {
            list:   (p) => request.get('/ai/knowledge/base/list', { params: p }),
            stats:  () => request.get('/ai/knowledge/stats'),
        }
    """
    namespaces = {}
    for p in WEB_API.glob("*.ts"):
        try:
            src = p.read_text(encoding="utf-8")
        except Exception:
            continue
        # 按 `export const X = {` 切成命名空间
        parts = re.split(r"export\s+const\s+(\w+)\s*=\s*\{", src)
        for i in range(1, len(parts), 2):
            ns_name = parts[i]
            body = parts[i + 1]
            # 到下一个 `export const` 为止
            end = body.find("export const")
            if end >= 0:
                body = body[:end]
            funcs = {}
            for m in re.finditer(r"(\w+)\s*:\s*(?:async\s*)?", body):
                name = m.group(1)
                # 📌 只接受**合法标识符**：
                #    模板字符串里的 `/finance/flow/${id}` 会被当成「方法名」
                #    → 归类时乱入「列表类函数」→ 比出一个不存在的接口。
                if not re.fullmatch(r"[A-Za-z_$][\w$]*", name):
                    continue
                call = scan_request_call(body, m.end())
                if call:
                    funcs[name] = call
            if funcs:
                namespaces[ns_name] = funcs
    return namespaces


def scan_request_call(src, start):
    """从 start 开始找 `request.<method>(...)`，返回 (METHOD, 路径)。

    📌 **不能用简单正则抓 `request.get(...)`**：
    泛型参数里嵌套了 `Record<string, number>` → `<[^>]*>` 会在第一个 `>` 处截断
    → 后面真正的路径字符串**根本匹配不到**
    → 这个命名空间的 `stats` 方法**整个从索引里消失**
    → 闸门对最该查的那个接口**完全失明**（规则 55）。
    → 这里改用**扫描器**：跳过配平的泛型括号，再取第一个字符串字面量。
    """
    m = re.search(r"request\s*\.\s*(get|post|put|delete|patch)\b", src[start:start + 300])
    if not m:
        return None
    method = m.group(1).upper()
    # 📌 m.end() 是**切片内**的相对位置 → 必须加回 start 变成绝对位置。
    i = start + m.end()
    while i < len(src) and src[i] in " \t\r\n":
        i += 1
    if i < len(src) and src[i] == "<":
        depth = 0
        while i < len(src):
            if src[i] == "<":
                depth += 1
            elif src[i] == ">":
                depth -= 1
                if depth == 0:
                    i += 1
                    break
            i += 1
    while i < len(src) and src[i] in " \t\r\n":
        i += 1
    if i >= len(src) or src[i] != "(":
        return None
    # 📌 跳过 **调用左括号本身**，否则参数区里的第一个字符是 `(`，
    #    字符串字面量的正则永远匹配不上 → 每个接口都变成「没有路径」。
    rest = src[i + 1:i + 401]
    sm = re.match(r"\s*([`'\"][^`'\"]*[`'\"])", rest)
    if not sm:
        return None
    return (method, sm.group(1).strip("`'\""))


# ══════════════════════════════════════════════════════════════════════
# 4. 前端视图：谁在用哪个命名空间的哪个方法
# ══════════════════════════════════════════════════════════════════════

def parse_view_usage():
    """视图文件 → {(命名空间, 方法名)}"""
    out = {}
    for p in WEB_VIEWS.rglob("*.vue"):
        try:
            src = p.read_text(encoding="utf-8")
        except Exception:
            continue
        used = set()
        for m in re.finditer(r"\b(\w+)\s*\.\s*(\w+)\s*\(", src):
            used.add((m.group(1), m.group(2)))
        out[p] = used
    return out


# ══════════════════════════════════════════════════════════════════════
# 5. 分类
# ══════════════════════════════════════════════════════════════════════

COUNT_WORDS = ("stats", "statistics", "count", "total")
LIST_WORDS = ("list", "page", "query", "search", "records")


def is_count(name):
    n = name.lower()
    return any(w in n for w in COUNT_WORDS)


def is_list(name):
    n = name.lower()
    return any(w in n for w in LIST_WORDS)


# ══════════════════════════════════════════════════════════════════════
# 6. 主流程
# ══════════════════════════════════════════════════════════════════════

def main():
    strict = "--strict" in sys.argv

    backend = Backend()
    routes = parse_gateway()
    namespaces = parse_frontend_api()
    view_usage = parse_view_usage()

    print("=" * 72)
    print("「统计口径一致性」巡检（前端条数 vs 列表，必须同一张表）")
    print("=" * 72)
    print(f"后端接口索引：{len(backend.endpoints)} 个")
    print(f"网关路由：{len(routes)} 条")
    print(f"前端 api 命名空间：{len(namespaces)} 个")
    print(f"前端视图：{len(view_usage)} 个")
    print()

    # 前端路径 → 后端表集合
    def resolve(front_path, http):
        full = BASE_URL + front_path if front_path.startswith("/") else front_path
        for r in routes:
            if http and r["methods"] and http not in r["methods"]:
                continue
            for pat in r["paths"]:
                if path_matches(pat, full):
                    backend_path = rewrite_target(r, full)
                    key = (http, backend_path)
                    if key in backend.endpoints:
                        ep = backend.endpoints[key]
                        return ep, backend_path, r["id"]
                    return None, backend_path, r["id"]
        return None, full, None

    problems = []
    unverifiable = []
    checked_pairs = 0

    # ─────────────────────────────────────────────────────────────
    # 📌 **功能域 = 「同一个视图文件」**，不是「同一个前端命名空间」。
    #
    #    为什么：前端一个 api 命名空间往往**打包了多个不相干的功能**
    #    （`supplierApi` 里同时有供应商列表、采购单列表、评估列表）。
    #    按命名空间分组 → 「供应商条数」会去和「评估列表」比
    #    → 它们**本来就不该是同一张表** → 满屏假阳性。
    #
    #    按**视图文件**分组才对：**同一个页面**上同时出现的
    #    「说 N 条」和「列出 N 条」→ 它们**本来就应该说同一批数据**。
    #
    # 📌 **判定规则（重要）**：
    #    不是「条数接口必须和**某一个特定**列表接口同表」——
    #    那样「配对」本身就是个模糊判断（一张页面上有多个列表）。
    #    而是：**条数接口数的那张表，必须在这个页面的某个列表接口里出现**。
    #    → 也就是「这个数字说的事，页面上真的列得出来」。
    #    → 页面上的列表各自是不同表没关系（供应商列表 / 采购单列表 / 评估列表
    #      完全可以并存），只要**每个条数都能对上其中一个列表**。
    # ─────────────────────────────────────────────────────────────
    for vpath, used in view_usage.items():
        # 这个页面上用到的所有「条数接口」和「列表接口」（跨命名空间）
        counts = []
        lists = []
        for u_ns, u_fn in used:
            funcs = namespaces.get(u_ns)
            if not funcs or u_fn not in funcs:
                continue
            http, fpath = funcs[u_fn]
            ep, bpath, route = resolve(fpath, http)
            if ep is None:
                continue
            if is_count(u_fn):
                counts.append((u_ns, u_fn, fpath, ep))
            elif is_list(u_fn):
                lists.append((u_ns, u_fn, fpath, ep))

        if not counts or not lists:
            continue

        # 页面上的所有列表涉及的表
        list_tables = set()
        for _ns, _fn, _fp, ep in lists:
            list_tables |= ep["tables"]

        for c_ns, c_fn, c_path, c_ep in counts:
            c_tables = c_ep["count_tables"] or c_ep["tables"]
            if not c_tables:
                unverifiable.append((c_ns, c_fn, "-",
                                     "追不到取数来源（动态 SQL / 反射 / 缓存）"))
                continue
            checked_pairs += 1
            stray = c_tables - list_tables
            if stray:
                problems.append((vpath.name, c_ns, c_fn, c_path,
                                 sorted(c_tables), sorted(list_tables),
                                 sorted(stray)))

    # ---- 报告 ----
    if problems:
        print(f"🔴 发现「同一个功能里，条数与列表查的不是同一张表」（{len(problems)} 处）：\n")
        for view, ns, cf, cpath, ctab, ltables, stray in problems:
            print(f"  ❌ 视图 {view} / 命名空间 {ns}")
            print(f"       条数接口 : {cf}() → {cpath}")
            print(f"                  数的表 = {', '.join(ctab)}")
            print(f"       页面列表涉及的表: {', '.join(ltables) or '(追不到)'}")
            print(f"       ⚠️ 对不上   : {', '.join(stray)}")
            print(f"       → 同一个页面上「说 N 条」的数字，说的事**页面上根本列不出来**")
            print()
    else:
        print("✅ 没发现「条数与列表查的不是同一张表」")
        print()

    if unverifiable:
        print(f"⚠️ 以下 {len(unverifiable)} 组**追不到**取数来源"
              "（不是不一致，是本闸门看不见）：")
        for ns, cf, lf, why in unverifiable:
            print(f"  · {ns}.{cf}() vs {lf}()  —— {why}")
        print("  📌 追不到 ≠ 一致。")
        print()

    print("=" * 72)
    print(f"结论：已比对 {checked_pairs} 组（条数 × 列表），"
          f"口径不一致 {len(problems)} 处，无法核验 {len(unverifiable)} 处")
    print("=" * 72)

    if problems and strict:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
