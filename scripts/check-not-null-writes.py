#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""巡检「NOT NULL 无默认值的列，在写路径上从没赋值」→ 插入必失败。

## 为什么需要这个脚本

2026-11-03 抓到 `t_order.currency_code` 的真缺陷：
它是 `NOT NULL` 且**无默认值**，而 MyBatis-Plus 的 insert 默认
`FieldStrategy.NOT_NULL` 会**跳过 null 字段**
→ INSERT 语句里根本没有这一列
→ MySQL: `Field 'currency_code' doesn't have a default value`
→ **整个订单插入失败**。

📌 关键点：**「实体里有这个字段」根本不代表安全**。
`OrderServiceImpl` 里有一处 `setCurrencyCode(...)`，
但那是**导出 DTO 的读操作**（`data.setCurrencyCode(order.getCurrencyCode())`），
**写路径上从没赋过值** → 照样 100% 失败。

📌 所以本脚本查的是**写路径信号**：
对每个 NOT NULL 无默认值的列，找出对应实体字段，
然后在整个项目里找**「new 实体 → setXxx → insert/save」**的赋值调用。
找不到任何写入侧的赋值 → 判为高危。

## 判定规则

对 (表, 列) → 实体字段 `field`：
1. 找到 `set<Field>(` 的所有调用点
2. 排除「读操作误判」：
   - `data.setXxx(entity.getXxx())` 这种**纯搬运**（右括号里紧跟 `.get`）
   - 排除在**导出/VO/Response/DTO** 类里的赋值
3. 剩下的赋值点数量 = 0 → 🔴 高危（没有任何写路径赋过值）

## 用法

    python3 scripts/check-not-null-writes.py

退出码：0 = 没有「写路径从不赋值」的列，1 = 有
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

DB_ALIAS = {
    "erp_order": "erp-order-service",
    "erp_customer": "erp-customer-service",
    "erp_supplier": "erp-supplier-service",
    "erp_product": "erp-product-service",
    "erp_user": "erp-user-service",
    "erp_inventory": "erp-inventory-service",
    "erp_finance": "erp-finance-service",
    "erp_logistics": "erp-logistics-service",
    "erp_warehouse": "erp-warehouse-service",
    "erp_purchase": "erp-purchase-service",
    "erp_ai_assistant_db": "erp-ai-assistant-service",
    "erp_anomaly": "erp-anomaly-detection-service",
}

CONTAINER = os.environ.get("MYSQL_CONTAINER", "erp-fresh-mysql")
PASSWORD = os.environ.get("MYSQL_PASSWORD", "root")

_ENTITY_CLASSES = None

FRAMEWORK_COLUMNS = {
    "id", "create_time", "update_time", "create_by", "update_by",
    "deleted", "version", "tenant_id",
}

# 这些类名里的赋值是「读搬运」或「VO 装配」，不算数据入库的写路径
READONLY_CLASS_HINT = (
    "VO", "DTO", "Response", "Request", "Export", "Import",
    "Query", "View", "Bo", "BO", "VO_", "Result",
)


def mysql(query):
    cmd = ["docker", "exec", CONTAINER, "mysql", "-uroot", f"-p{PASSWORD}",
           "-N", "-B", "-e", query]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except Exception:
        return None
    if out.returncode != 0:
        return None
    return [line.split("\t") for line in out.stdout.strip().split("\n") if line]


def live_notnull_cols():
    rows = mysql(
        "SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
        "WHERE IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL "
        "AND EXTRA NOT LIKE '%auto_increment%' "
        "AND TABLE_SCHEMA LIKE 'erp_%' ORDER BY TABLE_SCHEMA, TABLE_NAME;")
    if rows is None:
        return None
    return [(r[0], r[1], r[2]) for r in rows if len(r) >= 3]


def entity_field_map():
    """{(svc, table): {column: java_field}}"""
    out = {}
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        svc = svc_dir.name
        for ent in sorted(svc_dir.glob("src/main/java/**/entity/*.java")):
            text = ent.read_text(encoding="utf-8", errors="ignore")
            tm = re.search(r'@TableName\(\s*["\']([^"\']+)["\']', text)
            if not tm:
                continue
            table = tm.group(1)
            colmap = {}
            # 📌 @TableField("col") 的列 → **Java 字段名**（不是列名！）
            #    必须同时拿到属性名，否则 setter 名会算成 setProduct_name（不存在）
            for fm in re.finditer(
                    r'@TableField\(\s*(?:value\s*=\s*)?["\']([^"\']+)["\']\s*\)'
                    r'(?:\s*,\s*[^)]*)?\s*'
                    r'(?:/\*\*(?:[^*]|\*(?!/))*\*/\s*)?'
                    r'private\s+[\w<>,\.\[\]]+\s+(\w+)\s*;', text, re.S):
                colmap[fm.group(1)] = fm.group(2)
            annotated = set()
            for fm in re.finditer(
                    r'@TableField\([^)]*\)\s*(?:/\*\*(?:[^*]|\*(?!/))*\*/\s*)?'
                    r'private\s+[\w<>,\.\[\]]+\s+(\w+)\s*;', text, re.S):
                annotated.add(fm.group(1))
            for fm in re.finditer(r"private\s+[\w<>,\.\[\]]+\s+(\w+)\s*;", text):
                name = fm.group(1)
                if name in annotated:
                    continue
                snake = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
                colmap.setdefault(snake, name)
            out[(svc, table)] = colmap
    return out


def module_of(path):
    """文件所属的模块名（erp-xxx-service）"""
    try:
        return path.relative_to(ROOT).parts[0]
    except (ValueError, IndexError):
        return ""


def java_files():
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        for p in svc_dir.glob("src/main/java/**/*.java"):
            yield p


def json_bound_entities():
    """返回 {实体类名: 所属模块} —— @RequestBody **可达**的实体。

    📌 **关键**：@RequestBody 绑定的实体，Jackson 会在反序列化时
    **直接写字段**（不走 setter）→ 所以**根本不存在 setXxx() 调用**。
    对这类实体用「有没有 setter 调用」判定会**误报**。

    📌 判定方式：扫 Controller 里形参类型是实体类、且带 @RequestBody 的方法。

    📌 **嵌套实体（踩了 1 次才补上）**：
    订单接口的形参不是实体本身，而是**包装 DTO**：
        `@RequestBody CreateOrderRequest request`
        → CreateOrderRequest { Order order; List<OrderItem> orderItems; }
    → 真正的实体 `Order` / `OrderItem` **藏在 DTO 里面**，
      直接扫 @RequestBody 形参**扫不到它们** → 漏判（我实测 4 个真缺陷被漏掉）。
    → 所以要**顺着 DTO 的字段再往下找一层**，把嵌套实体也纳入。

    📌 **返回模块名**：因为 setter 统计必须**限定在拥有该写路径的模块内**
       （见 count_write_setters 的 module_scope 参数）——
       否则别的模块（platform 同步适配器）的赋值会把本模块的漏洞掩盖掉。
    """
    # 实体类名 → 所属模块（用于判断「嵌套实体是不是实体」）
    entity_classes = set()
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        for p in svc_dir.glob("src/main/java/**/entity/*.java"):
            cm = re.search(r'\bclass\s+(\w+)',
                           p.read_text(encoding="utf-8", errors="ignore"))
            if cm:
                entity_classes.add(cm.group(1))

    bound = {}
    for p in java_files():
        if "/controller/" not in str(p):
            continue
        module = p.parts[len(ROOT.parts)] if ROOT in p.parents else ""
        text = p.read_text(encoding="utf-8", errors="ignore")
        for fm in re.finditer(
                r'@RequestBody\s+(?:@\w+\s+)*([A-Z]\w*)\s+\w+', text):
            dto = fm.group(1)
            bound.setdefault(dto, module)
            # 📌 顺着 DTO 的字段找**嵌套实体**
            for nested in nested_entities_of(dto, entity_classes):
                bound.setdefault(nested, module)
    return bound


def nested_entities_of(dto_cls, entity_classes):
    """找出 DTO 里嵌套的实体类（字段类型是实体，或 List<实体>）。

    📌 **为什么需要**：包装 DTO 里的实体同样是 Jackson 直接写字段，
       一样没有 setter 调用 → 一样需要服务层兜底。
    """
    out = set()
    for p in java_files():
        cm = re.search(rf'\bclass\s+{re.escape(dto_cls)}\b', p.read_text(
            encoding="utf-8", errors="ignore"))
        if not cm:
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # `private Order order;` / `private List<OrderItem> orderItems;`
        for fm in re.finditer(
                r'private\s+(?:List<\s*)?([A-Z]\w*)(?:>)?\s+\w+\s*;', text):
            typ = fm.group(1)
            if typ in entity_classes:
                out.add(typ)
        break
    return out



def java_methods(text):
    """把一个 Java 文件切成 [(方法名, 方法体文本)]。

    📌 **为什么要按方法切**：只认 setter 调用会**跨路径串味** ——
    `t_order.status` 的 setter 在 `updateOrderStatus()` 里（**更新路径**），
    但 insert 路径没设 → 脚本以为「有兜底」→ 漏报（我实测发现的）。
    → 判定必须落在「**真正执行 insert 的那个方法**」上。
    """
    out = []
    lines = text.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        # 方法签名：修饰符 + 返回类型 + 名字 + ( ... ) {
        if re.match(r"\s*(?:public|protected|private|static|final|synchronized|\s)+"
                    r"[\w<>,\.\[\]?\s]+\s+(\w+)\s*\([^;]*\)\s*(?:throws[\w,\s.]+)?\{",
                    line):
            name = re.search(r"([\w]+)\s*\([^;]*\)\s*(?:throws[\w,\s.]+)?\{",
                             line).group(1)
            depth = line.count("{") - line.count("}")
            body = [line]
            j = i + 1
            while j < len(lines) and depth > 0:
                body.append(lines[j])
                depth += lines[j].count("{") - lines[j].count("}")
                j += 1
            out.append((name, "\n".join(body)))
            i = j
            continue
        i += 1
    return out


def write_scope_texts(entity_cls, module, entity_fqn):
    """返回该模块内**真正执行 insert 的方法**的文本（含它调用的私有辅助方法）。

    📌 **判据**：方法体里出现 `save(` / `insert(` / `saveOrUpdate(`，
       且该文件 import 了目标实体 → 这个方法就是写路径。
    📌 **加上它调用的私有辅助方法**：
       有些代码把字段准备拆到 `prepareOrder(order)` 里，
       只看 insert 方法体会误报 → 把同文件里被它调用到的私有方法也纳入。
    """
    scopes = []
    insert_re = re.compile(r"(?:\w+Mapper|baseMapper)\.insert\s*\(|"
                           r"(?:^|[^.\w])save(?:OrUpdate|Batch)?\s*\(")
    insert_names = set()
    per_file = []
    for p in java_files():
        if module_of(p) != module:
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        if entity_fqn and not imports_entity(text, entity_fqn):
            continue
        methods = java_methods(text)
        per_file.append((p, methods))
        for name, body in methods:
            if insert_re.search(body):
                insert_names.add(name)

    for p, methods in per_file:
        for name, body in methods:
            if name not in insert_names:
                continue
            scope = body
            # 把同文件里被调用到的私有辅助方法也纳入
            for hname, hbody in methods:
                if hname == name or hbody is body:
                    continue
                if re.search(rf"\b{re.escape(hname)}\s*\(", body):
                    scope += "\n" + hbody
            scopes.append(scope)

    # 📌 **还要纳入「调用 insert 方法的上游方法」**（踩了 1 次才补上）
    #
    #    📌 **为什么**：项目里很常见「**Controller 装配实体 → Service 负责 insert**」：
    #        Controller: `order.setSupplierId(request.getSupplierId());`
    #                     `if (request.getSupplierId() == null) throw ...`
    #        Service:    `purchaseOrderService.createOrder(order, items);` → insert
    #    → 守卫和赋值都在 **Controller**，insert 在 **Service**
    #    → 只看 insert 方法体 → 看不到守卫 → **误报**（supplier_id 就是这么被误报的）。
    #    📌 写路径 = insert 方法 **+ 调用它的方法**（上游 2 层，够用了）。
    for p, methods in per_file:
        for name, body in methods:
            if name in insert_names:
                continue
            if not any(re.search(rf"\b{re.escape(n)}\s*\(", body)
                       for n in insert_names):
                continue
            scope = body
            for hname, hbody in methods:
                if hname == name or hbody is body:
                    continue
                if re.search(rf"\b{re.escape(hname)}\s*\(", body):
                    scope += "\n" + hbody
            scopes.append(scope)
    return scopes


def count_null_guards(field, entity_cls=None, entity_fqn=None, module_scope=None,
                      scope_texts=None):
    """统计 field 的**空值守卫**次数（`if (x.getYyy() == null) throw ...`）。

    📌 **为什么需要它**：NOT NULL 且无默认值的列，有两种安全的补法：
       ① 服务层自己推导/兜底（setter 赋值）
       ② **入口处显式校验并报错**（`if (x.getYyy() == null) throw ...`）
    📌 ② **不产生任何 setter 调用** → 只认 setter 会把它误判成「从没赋值」
       → 我加完校验后脚本仍然误报（踩了 1 次）。
    📌 判据：文件 import 了该实体，且存在
       `xxx.getYyy() == null`（或 `null == xxx.getYyy()`），
       且紧接着（几行内）有 `throw`。
    """
    getter = "get" + field[0].upper() + field[1:]
    total = 0
    if scope_texts is not None:
        # 📌 **写入路径作用域**：只看真正执行 insert 的方法体
        for text in scope_texts:
            total += _guards_in_text(text, field, getter)
        return total
    for p in java_files():
        # 📌 **只在本模块内找守卫**（见 count_write_setters 的 module_scope 说明）
        if module_scope and module_of(p) != module_scope:
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        if entity_fqn and not imports_entity(text, entity_fqn):
            continue
        total += _guards_in_text(text, field, getter)
    return total


def _guards_via_source(text, field, getter):
    """「实体字段的值来自 DTO 字段，且该 DTO 字段有空值守卫」→ 算覆盖。

    📌 **为什么需要（踩了 1 次才补上）**：
    Controller 里的常见写法是**从 DTO 装配实体**：
        `order.setSupplierId(request.getSupplierId());`
    而守卫写在 **DTO 那一侧**：
        `if (request.getSupplierId() == null) throw new BizException("供应商不能为空");`
    → 守卫**确实保护了 insert**（DTO 为 null 时根本走不到 setSupplierId）
    → 但脚本只认「对实体字段本身守卫」→ **误报** supplier_id。

    📌 判据：写路径里存在 `实体.setField(dto.getField())`，
       且 `dto.getField()` 在同一段代码里有 null 守卫。
    """
    setter = "set" + field[0].upper() + field[1:]
    src_vars = set()
    for m in re.finditer(
            rf"\b(\w+)\s*\.\s*{setter}\s*\(\s*(\w+)\s*\.\s*{getter}\s*\(\s*\)\s*\)",
            text):
        src_vars.add(m.group(2))
    if not src_vars:
        return 0
    total = 0
    lines = text.split("\n")
    for i, line in enumerate(lines):
        for v in src_vars:
            if f"{v}.{getter}()" not in line:
                continue
            if not re.search(
                    rf"{re.escape(v)}\.{getter}\(\s*\)\s*(==|!=)\s*null"
                    rf"|null\s*(==|!=)\s*{re.escape(v)}\.{getter}\(\s*\)", line):
                continue
            for j in range(i, min(len(lines), i + 6)):
                if "throw " in lines[j]:
                    total += 1
                    break
    return total


def _guards_in_text(text, field, getter):
    """在一段文本里数该字段的空值守卫次数。"""
    total = _guards_via_source(text, field, getter)
    if True:
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if f".{getter}()" not in line:
                continue
            # 📌 **判定要按语义，不要按固定形态**（踩了 1 次才补上）
            #
            #    空值守卫有两种写法：
            #      ① 直接比较：`if (order.getShopId() == null) throw ...`
            #      ② **包在辅助方法里**：`if (isBlank(order.getPlatform())) throw ...`
            #         / `if (StringUtils.hasText(x.getY())) throw ...`
            #    📌 只认 ① 会把 ② 误判成「没守卫」→ 误报
            #       （我加完校验后脚本仍然误报 platform / platform_order_no）。
            #    📌 辅助方法名的判据：名字里带 blank / empty / null / text
            #       —— 项目里实际用的是 isEmpty(144) / hasText(68) / isBlank(67)。
            direct_null = re.search(
                rf"\.{getter}\(\s*\)\s*(==|!=)\s*null"
                rf"|null\s*(==|!=)\s*\.{getter}\(\s*\)", line)
            helper_null = re.search(
                rf"\b(?:is|has)[A-Za-z]*(?:Blank|Empty|Null|Text)\s*\("
                rf"[^;]*{getter}\(\s*\)", line)
            if not (direct_null or helper_null):
                continue
            # 往后看几行：有没有 throw
            for j in range(i, min(len(lines), i + 6)):
                if "throw " in lines[j]:
                    total += 1
                    break
    return total


def count_write_setters(field, entity_cls=None, entity_fqn=None, module_scope=None,
                        scope_texts=None):
    """统计 field 在**写路径**上的赋值次数（排除纯搬运和 VO/DTO 装配）。

    📌 **必须按「实体类型」而不是「模块」限定**：
    - `erp-platform-service` 的 `AmazonOrderSync` 里
      `order.setCurrencyCode(...)` 赋的就是
      `com.crossborder.erp.order.entity.Order` —— **同一个实体类**，
      只是住在另一个模块里（platform 通过 Feign 把订单发给 order 服务）。
    - 📌 所以按模块限定会**漏掉真正的赋值点**（shopId / unitPrice 全在 platform 里）。
    - 📌 但也不能完全不限定：别处的同名 DTO 会污染统计。
      → 做法：**该赋值语句所在的文件必须 import 了这个实体的 FQN**。
    """
    setter = "set" + field[0].upper() + field[1:]
    total = 0
    if scope_texts is not None:
        # 📌 **写入路径作用域**：只认「真正执行 insert 的那个方法」里的赋值
        for text in scope_texts:
            total += _setters_in_text(text, field, setter, entity_cls)
        return total
    for p in java_files():
        # 📌 **module_scope：只统计拥有该写路径的模块内的赋值**（踩了 1 次才补上）
        #
        #    📌 **为什么必须限定**：实测 4 个真缺陷被漏掉 ——
        #       `t_order.order_amount` / `status` / `t_order_item.unit_price` / `quantity`
        #       在 **erp-platform-service** 的同步适配器（AmazonOrderSync）里**都有赋值**，
        #       但 **erp-order-service**（真正接收 @RequestBody 的模块）里**没有**。
        #       → 「全模块统计」会把 platform 的赋值算成 order 的兜底
        #       → REST 接口漏传字段照样 500（我真起服务实测出来的）。
        #
        #    📌 注意：**不加 module_scope 反而更准**的场景是
        #       「跨模块同步」—— 但那类写路径是 Feign 调用同一个 service 方法，
        #       最终还是落到拥有写路径的模块里 → 所以限定模块是对的。
        if module_scope and module_of(p) != module_scope:
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # 📌 只有 import 了该实体（或用通配符 import 了它所在的包）的文件，
        #    才算在给这个实体赋值
        if entity_fqn and not imports_entity(text, entity_fqn):
            continue
        lines = text.split("\n")
        # 📌 **Builder 模式**：`InventoryCheck.builder().checkNo(...)`
        #    → 没有 setter 调用，但值照样被赋上了！
        #    → 只认 setXxx 会把 Builder 写的表全误判成「从没赋值」。
        # 📌 **Builder 模式**：`InventoryCheck.builder().checkNo(...)`
        #    → 没有 setter 调用，但值照样被赋上了！
        #    → 只认 setXxx 会把 Builder 写的表全误判成「从没赋值」。
        #    判定：这一行（或前面几行）出现 `<Entity>.builder()`，
        #          且该行有 `.字段名(` —— 就算一次赋值。
        builder_call = f".{field}("
        builder_re = (re.compile(rf'\b{entity_cls}\s*\.\s*builder\s*\(')
                      if entity_cls else None)
        total += _setters_in_text(text, field, setter, entity_cls)
    return total



def _guard_line_matches(line, getter):
    """这行是不是「对该字段的空值/空白检查」？

    📌 两种形态：
      ① 直接比较：`if (order.getStatus() == null)`
      ② 包在辅助方法里：`if (isBlank(order.getStatus()))`
         / `if (StringUtils.hasText(x.getY()))`
    """
    if re.search(rf"\.{re.escape(getter)}\(\s*\)\s*(==|!=)\s*null"
                 rf"|null\s*(==|!=)\s*\.{re.escape(getter)}\(\s*\)", line):
        return True
    if re.search(rf"\b(?:is|has)[A-Za-z]*(?:Blank|Empty|Null|Text)\s*\("
                 rf"[^;]*\.{re.escape(getter)}\(\s*\)", line):
        return True
    return False


def _guard_block(lines, i):
    """返回第 i 行守卫所覆盖的代码块（含守卫行），以及块的结束行号。

    📌 两种形态：
      ① 带花括号的多行块：`if (x.getF() == null) { ... }`
      ② 单语句（无花括号）：`if (x.getF() == null) entity.setF(default);`
    """
    line = lines[i]
    if "{" in line:
        depth = line.count("{") - line.count("}")
        block = [line]
        j = i + 1
        while j < len(lines) and depth > 0:
            block.append(lines[j])
            depth += lines[j].count("{") - lines[j].count("}")
            j += 1
        return "\n".join(block), j
    # 无花括号 → 取到第一个分号为止
    block = [line]
    j = i + 1
    while j < len(lines) and j < i + 5 and ";" not in lines[j - 1]:
        block.append(lines[j])
        j += 1
    return "\n".join(block), j


def _guard_then_default_count(text, field, setter, getter):
    """数「**守卫 → 兜底赋值**」的次数。

    📌 **这是唯一能真正保护 NOT NULL 列的 setter 形态**：
        `if (order.getStatus() == null) { order.setStatus("PENDING"); }`
       —— 走到 insert 时该字段**一定非 null**。

    📌 **为什么不能「有 setter 调用就算覆盖」（踩了 1 次才补上）**：
        `po.setSku(item.getSku())` —— 这是把 **DTO 的可能为 null 的值原样搬过来**，
        DTO 没传就是 null → insert 照样失败。
        → 「调用了 setter」≠「列非 null」，必须有**守卫**才算覆盖。
       （实测：supplier 的 t_purchase_order_item.sku / quantity 就是这么漏掉的。）

    📌 另外覆盖一种内联兜底：`setF(a == null ? b : a)`（三元）
    """
    lines = text.split("\n")
    total = 0
    i = 0
    while i < len(lines):
        if not _guard_line_matches(lines[i], getter):
            i += 1
            continue
        block, end = _guard_block(lines, i)
        if re.search(rf"\.{setter}\s*\(", block):
            total += 1
        i = max(end, i + 1)
    # 内联三元兜底：`x.setF(a == null ? b : a)`
    total += len(re.findall(
        rf"\.{setter}\s*\([^;)]*==\s*null\s*\?", text))
    return total



def _dto_vars(text):
    """找出这个方法里「**来源可能是 null 的 DTO 变量**」。

    📌 **判据**：
      ① `@RequestBody XxxRequest request` → `request` 本身
      ② 遍历 DTO 集合的 lambda 参数：`request.getItems().stream().map(item -> ...)`
         → `item` 也算（它是 DTO 元素）
      ③ for-each 的元素变量

    📌 **为什么要区分 DTO 变量和其他变量**：
        `order.setSupplierId(request.getSupplierId())` → 来源是 DTO → **可能为 null**
        `item.setOrderId(order.getId())`            → 来源是实体 → **主键，必非 null**
       把两者一视同仁就会误报（我改严语义后第一版就是这样，全是误报）。

    📌 **DTO 变量的赋值若没有守卫** → 该列仍可能为 null → insert 仍会失败。
    """
    out = set()
    for m in re.finditer(
            r"@RequestBody\s+(?:@\w+\s+)*[A-Z]\w*\s+(\w+)", text):
        out.add(m.group(1))
    if not out:
        return out
    # 集合遍历出的元素变量
    for m in re.finditer(r"\.map\(\s*(\w+)\s*->", text):
        out.add(m.group(1))
    # 📌 **for-each 的元素变量**：`for (ItemRequest item : reqItems)`
    #    📌 **踩了 1 次才补上**：我原来写的是「取整条 for 语句的最后��个单词」
    #       → 抓到的是 `reqItems`（集合名）而不是 `item`（元素变量）
    #       → 元素变量没被认成 DTO 变量 → `po.setSku(item.getSku())` 被判成安全
    #       → **漏报**（实测把供应商的 sku/quantity 守卫删掉，脚本没报）。
    #       → 正确判据：**冒号前面**的那个标识符才是元素变量。
    for m in re.finditer(
            r"for\s*\(\s*(?:final\s+)?[\w<>,\.\[\]\s]+?\s+(\w+)\s*:", text):
        out.add(m.group(1))

    # 📌 **局部变量声明**：`ItemRequest item = reqItems.get(i);`
    #    📌 **踩了 1 次才补上**：我原来只处理 for-each，
    #       但项目里更多用的是**下标循环**：
    #         for (int i = 0; i < reqItems.size(); i++) {
    #             ItemRequest item = reqItems.get(i);
    #             po.setSku(item.getSku());
    #         }
    #       → `item` 是**局部变量声明** → 没被认成 DTO 变量 → 漏报。
    #
    #    📌 **判据**：声明的类型**不是实体类** → 就是「DTO 侧的数据」。
    #       `ItemRequest item` → DTO（可能为 null）
    #       `PurchaseOrderItem po` → 实体（主键/已算好的值 → 安全）
    #       `String s` / `Integer q` → JDK 类型 → 忽略
    ents = entity_class_names()
    jdk_types = {"String", "Integer", "Long", "Double", "Boolean", "BigDecimal",
                 "BigInteger", "Object", "LocalDateTime", "LocalDate", "Date",
                 "List", "Map", "Set", "Optional", "Result", "Page", "IPage"}
    for m in re.finditer(
            r"^\s*(?:final\s+)?([A-Z]\w*)\s+(\w+)\s*=", text, re.M):
        typ, var = m.group(1), m.group(2)
        if typ in jdk_types or typ in ents:
            continue
        out.add(var)
    return out


def entity_class_names():
    """全项目实体类名集合（用来区分「实体变量」和「DTO 变量」）。"""
    global _ENTITY_CLASSES
    if _ENTITY_CLASSES is not None:
        return _ENTITY_CLASSES
    names = set()
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        for p in svc_dir.glob("src/main/java/**/entity/*.java"):
            cm = re.search(r"\bclass\s+(\w+)",
                           p.read_text(encoding="utf-8", errors="ignore"))
            if cm:
                names.add(cm.group(1))
    _ENTITY_CLASSES = names
    return names


def _setter_calls(text, field, setter):
    """列出所有 `x.setField(来源)` 形式的调用：(行号, 来源变量名 或 None)。

    📌 来源为 None 表示「参数不是简单的 `var.getXxx()`」
       （常量、计算表达式、方法调用结果）→ 视为**安全**（值不由 DTO 直接决定）。
    """
    lines = text.split("\n")
    out = []
    for i, line in enumerate(lines):
        for m in re.finditer(rf"\b(\w+)\s*\.\s*{re.escape(setter)}\s*\(", line):
            rest = line[m.end():]
            src = re.match(r"\s*(\w+)\s*\.\s*get\w*\s*\(", rest)
            out.append((i, src.group(1) if src else None))
    return out


def _setters_in_text(text, field, setter, entity_cls):
    """数「真正保护 NOT NULL 列的赋值」次数。

    📌 **三种安全形态**：
      ① 守卫 → 兜底赋值：`if (x.getF() == null) { x.setF(default); }`
      ② 内联三元兜底：`x.setF(a == null ? b : a)`
      ③ 赋值来源**不是可空的 DTO 字段**：
           `item.setOrderId(order.getId())`   ← 来源是实体主键，必非 null
           `order.setTotalAmount(计算式)`      ← 来源是计算结果
         → 这两种都算覆盖。

    📌 **不安全的形态**（正是要抓的）：
           `po.setSku(item.getSku())`  ← item 是 DTO 元素，DTO 没传就是 null
       → 只有当**该 DTO 字段本身有守卫**时才算覆盖。

    📌 **语义修正史**：
       - v1「有任何 setter 调用就算覆盖」→ **漏报**（DTO 漏传照样 insert 失败）
       - v2「只认守卫 → 兜底赋值」→ **误报**（实体主键、计算结果全被误判）
       - v3（本版）「来源是否可空」→ 两者都对
    """
    getter = "get" + field[0].upper() + field[1:]
    total = _guard_then_default_count(text, field, setter, getter)
    dto_vars = _dto_vars(text)

    # 📌 **Builder 形态**：`InventoryCheck.builder().checkNo("IC-" + ...)....build()`
    #
    #    📌 **踩了 1 次才补上**：上一版只认「Builder 参数里带三元兜底」，
    #       结果 wms_inventory_check.check_no / wms_inventory_check_result.check_id
    #       全被误报 —— 但它们**明明有值**：
    #         `.checkNo("IC-" + now.format(...) + "-" + (warehouseId % 1000))`
    #         `.checkId(check.getId())`
    #       → **Builder 的参数只要不是「可空的 DTO 字段」就是安全的**。
    #       → 判据和 setter 一致：来源是不是可空的 DTO 变量。
    if entity_cls:
        for chain in re.finditer(
                rf'\b{re.escape(entity_cls)}\s*\.\s*builder\s*\(\s*\)(.*?)'
                rf'\.build\s*\(\s*\)',
                text, re.S):
            body = chain.group(1)
            fm = re.search(
                rf'\.{re.escape(field)}\s*\(\s*(\w+)\s*\.\s*get\w*\s*\(\s*\)',
                body)
            if fm and fm.group(1) in dto_vars:
                # 来源是可空 DTO 字段 → 要看该 DTO 字段有没有守卫
                if _guards_via_source(text, field, getter):
                    total += 1
            else:
                # 参数是常量 / 计算式 / 实体方法 → 安全
                total += 1

    # 📌 来源可空性判定
    for _i, src in _setter_calls(text, field, setter):
        if src is None:
            # 参数不是简单的 `var.getXxx()` → 常量/计算式 → 安全
            total += 1
            continue
        if src not in dto_vars:
            # 来源是实体/局部对象（主键、已计算好的值）→ 安全
            total += 1
            continue
        # 📌 来源是 DTO 字段 → 必须该 DTO 字段自己也有守卫
        if _guards_via_source(text, field, getter):
            total += 1
    return total


def entity_class_name(svc, table):
    """找到映射到这张表的实体类名"""
    for p in ROOT.glob(f"{svc}/src/main/java/**/entity/*.java"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        tm = re.search(r'@TableName\(\s*["\']([^"\']+)["\']', text)
        if tm and tm.group(1) == table:
            cm = re.search(r'\bclass\s+(\w+)', text)
            if cm:
                return cm.group(1)
    return None


def mapper_used(svc, table):
    """这个表的 Mapper 有没有被业务代码引用（区分「活表」和「死表」）"""
    cls = entity_class_name(svc, table)
    if not cls:
        return False
    mapper = cls + "Mapper"
    for p in java_files():
        if "/mapper/" in str(p):
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # 排除 entity/vo 目录下的同名引用
        if re.search(rf'\b{mapper}\b', text):
            if "/entity/" in str(p) or "/vo/" in str(p):
                continue
            return True
    return False


def imports_entity(text, fqn):
    """文件是否 import 了这个实体类（支持显式 import 和通配符 import）"""
    if fqn in text:
        return True
    pkg, _, cls = fqn.rpartition(".")
    # import com.x.y.entity.*;  →  覆盖该包下的所有类
    if re.search(rf'import\s+{re.escape(pkg)}\.\*\s*;', text):
        return True
    # import com.x.y.entity.InventoryAlert;
    if re.search(rf'import\s+{re.escape(fqn)}\s*;', text):
        return True
    return False


def entity_fqn_of(svc, table):
    """实体的全限定名（package + 类名），用来确认赋值点赋的是同一个类"""
    for p in ROOT.glob(f"{svc}/src/main/java/**/entity/*.java"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        tm = re.search(r'@TableName\(\s*["\']([^"\']+)["\']', text)
        if tm and tm.group(1) == table:
            pm = re.search(r'package\s+([\w.]+)\s*;', text)
            cm = re.search(r'\bclass\s+(\w+)', text)
            if pm and cm:
                return f"{pm.group(1)}.{cm.group(1)}"
    return None


def mapper_insert_used(svc, table):
    """这张表**会不会被写入**。

    📌 **关键区分**：
    - `permissionMapper.selectList(...)` / `.delete(...)` → 只是读/删，**不写**
    - 线上 31 行 t_permission 全部来自**种子数据**，代码从来不 insert
      → NOT NULL 无默认值**永远不会触发**

    📌 **三种写入方式都要认**：
    1. `xxxMapper.insert(entity)` —— 直接调 Mapper
    2. `service.save(entity)` / `saveOrUpdate` / `saveBatch`
       —— ServiceImpl 的继承方法，**内部**调 baseMapper.insert，
          源码里根本看不到 `xxxMapper.insert`！
    3. 📌 **变量名 ≠ 类名**：`orderItemMapper` 是 `OrderItemMapper` 的实例，
       `recordMapper` 是 `FinanceRecordMapper` 的实例。
       所以必须**按声明的类型**反查，不能拿类名去匹配变量名。
       （我前两版都踩了这个坑：漏 ① → 78 张表误判成只读表；
         漏 ③ → t_order_item / fin_record / erp_inventory 仍被误判）
    """
    cls = entity_class_name(svc, table)
    if not cls:
        return False
    mapper = cls + "Mapper"

    # 先收集所有「类型为 XxxMapper 的变量名」
    var_names = set()
    for p in java_files():
        if "/entity/" in str(p) or "/vo/" in str(p):
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # private final XxxMapper xxxMapper;
        for fm in re.finditer(rf'\b{mapper}\s+(\w+)\s*[;=]', text):
            var_names.add(fm.group(1))
        # private XxxMapper xxxMapper;
        for fm in re.finditer(rf'@Autowired\s+(?:private\s+)?{mapper}\s+(\w+)\s*;', text):
            var_names.add(fm.group(1))
    if not var_names:
        var_names.add(mapper)

    var_re = "|".join(re.escape(v) for v in var_names)

    for p in java_files():
        if "/mapper/" in str(p) or "/entity/" in str(p) or "/vo/" in str(p):
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # ① 直接调 Mapper 写入
        if re.search(rf'\b(?:{var_re})\s*\.\s*'
                     rf'(insert|save|saveBatch|insertOrUpdate|insertBatchSome)\s*\(', text):
            return True
        # ② ServiceImpl 的继承写入：save / saveOrUpdate / saveBatch
        if not re.search(rf'extends\s+ServiceImpl<\s*{mapper}\s*,', text):
            continue
        # 📌 `save(order)` 是**无接收者**的裸调用（继承自 ServiceImpl），
        #    正则必须允许没有前缀的点号
        if re.search(r'(^|[^.\w])(save|saveOrUpdate|saveBatch|saveOrUpdateBatch)\s*\(',
                     text, re.M):
            return True
    return False


def main():
    # 📌 **--strict**：连不上库时**直接失败**，而不是「跳过 + 绿灯」
    #    📌 **为什么必须**：闸门在 CI 里跑，如果 MySQL 没起来，
    #    静默跳过 = **假装检查过了** → 盲区直接溜过去。
    #    这跟「巡检脚本永远不报错 = 白写」是同一个病。
    strict = "--strict" in sys.argv

    cols = live_notnull_cols()
    if cols is None:
        if strict:
            print(f"❌ 连不上 MySQL 容器 `{CONTAINER}` —— 检查无法进行（strict 模式）")
            print(f"   先跑：scripts/start-audit-mysql.sh")
            return 1
        print(f"⚠️  连不上 MySQL 容器 `{CONTAINER}` —— 跳过")
        print(f"   （要真查请先跑 scripts/start-audit-mysql.sh，或加 --strict）")
        return 0

    ents = entity_field_map()
    json_bound = json_bound_entities()
    risky = []
    json_bound_missing = []
    checked = 0
    seed_only = []

    for db, table, col in cols:
        if col.lower() in FRAMEWORK_COLUMNS:
            continue
        svc = DB_ALIAS.get(db)
        if not svc:
            continue
        key = (svc, table)
        if key not in ents:
            continue
        field = ents[key].get(col)
        if not field:
            continue  # 实体里没这个字段 → 由 check-not-null-defaults.py 负责
        checked += 1

        # 📌 @RequestBody 绑定的实体：Jackson 直接写字段，不走 setter
        #    → 没有 setter 调用是**正常的**，不能判为高危
        #    📌 但反过来：这种实体如果某个 NOT NULL 列**只能靠调用方传**，
        #    而服务层又没兜底 → 调用方一漏传就是 500 + 原始 SQL 栈泄露。
        #    这类要单独查（见下面的 json_bound_missing）。
        cls = entity_class_name(svc, table)
        if cls and cls in json_bound:
            # 实体被 @RequestBody 直接绑定（或藏在包装 DTO 里）→ 服务层必须自己兜底
            # 📌 **module_scope**：只认「拥有该写路径的模块」里的赋值/校验 ——
            #    别的模块（platform 同步适配器）的赋值不算数，
            #    否则 REST 接口漏传字段照样 500（真起服务实测出来的）。
            scope = json_bound[cls]
            # 📌 **写入路径作用域**：只看「真正执行 insert 的那个方法」，
            #    避免「更新路径的 setter」把「插入路径的漏洞」掩盖掉。
            #    📌 例：t_order.status 的 setter 在 updateOrderStatus() 里，
            #       insert 路径没设 → 不限定方法就会漏报（我实测发现的）。
            scope_texts = write_scope_texts(cls, scope, entity_fqn_of(svc, table))
            n = count_write_setters(field, entity_cls=cls,
                                    entity_fqn=entity_fqn_of(svc, table),
                                    module_scope=scope,
                                    scope_texts=scope_texts or None)
            # 📌 也认可「入口处显式校验并报错」这种兜底方式
            g = count_null_guards(field, entity_cls=cls,
                                  entity_fqn=entity_fqn_of(svc, table),
                                  module_scope=scope,
                                  scope_texts=scope_texts or None)
            if n == 0 and g == 0:
                json_bound_missing.append((db, table, col, field, cls))
            continue

        # 📌 死表：Mapper 压根没人用 → 这张表压根不会被写
        if not mapper_used(svc, table):
            continue

        # 📌 **只读表**：Mapper 只被用来 select/delete，从不 insert/save
        #    → 数据全来自种子数据 → NOT NULL 无默认值永远不会触发
        if not mapper_insert_used(svc, table):
            seed_only.append((db, table))
            continue

        n = count_write_setters(field, entity_cls=cls, entity_fqn=entity_fqn_of(svc, table))
        g = count_null_guards(field, entity_cls=cls, entity_fqn=entity_fqn_of(svc, table))
        if n == 0 and g == 0:
            risky.append((db, table, col, field))

    if seed_only:
        print(f"ℹ️  {len(seed_only)} 张表是**只读/种子表**（代码从不 insert，"
              f"数据全来自种子脚本）—— NOT NULL 约束不会触发：")
        for db, table in sorted(set(seed_only)):
            print(f"   [{db}.{table}]")
        print()

    if json_bound_missing:
        print("🔴 NOT NULL 无默认值的列，实体被 @RequestBody 直接绑定 —— "
              "且服务层**没有兜底**：")
        for db, table, col, field, cls in json_bound_missing:
            print(f"   [{db}.{table}]  `{col}` ← 实体字段 {field}（{cls}）")
            print(f"      调用方漏传这个字段 → INSERT 失败（500）+ 原始 SQL 栈泄露")
        print()
        print("提示：服务层要么自己推导/兜底这个字段，"
              "要么在 Controller 层显式校验并报错。")
        print()

    if risky:
        print("🔴 NOT NULL 无默认值，且**写路径上从没赋值** —— 插入必失败：")
        for db, table, col, field in risky:
            print(f"   [{db}.{table}]  `{col}` ← 实体字段 {field}，"
                  f"全项目没有任何写入侧 set{field[0].upper()}{field[1:]}(...) 调用")
        print()
        print("提示：找到真正写这张表的地方，补上赋值；"
              "或在 DDL 里给该列加 DEFAULT。")
        return 1

    if json_bound_missing:
        return 1

    print(f"✅ 没有「写路径从不赋值」的 NOT NULL 列"
          f"（检查了 {checked} 个列/实体组合）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
