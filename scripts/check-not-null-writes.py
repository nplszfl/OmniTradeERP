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


def java_files():
    for svc_dir in sorted(ROOT.glob("erp-*-service")):
        for p in svc_dir.glob("src/main/java/**/*.java"):
            yield p


def json_bound_entities():
    """返回 @RequestBody 直接绑定的实体类名集合。

    📌 **关键**：@RequestBody 绑定的实体，Jackson 会在反序列化时
    **直接写字段**（不走 setter）→ 所以**根本不存在 setXxx() 调用**。
    对这类实体用「有没有 setter 调用」判定会**误报**。

    📌 判定方式：扫 Controller 里形参类型是实体类、且带 @RequestBody 的方法。
    """
    bound = set()
    for p in java_files():
        if "/controller/" not in str(p):
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # @RequestBody XxxEntity xxx
        for fm in re.finditer(
                r'@RequestBody\s+(?:@\w+\s+)*([A-Z]\w*)\s+\w+', text):
            bound.add(fm.group(1))
    return bound


def count_null_guards(field, entity_cls=None, entity_fqn=None):
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
    for p in java_files():
        text = p.read_text(encoding="utf-8", errors="ignore")
        if entity_fqn and not imports_entity(text, entity_fqn):
            continue
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if f".{getter}()" not in line:
                continue
            if not re.search(
                    rf"\.{getter}\(\s*\)\s*(==|!=)\s*null"
                    rf"|null\s*(==|!=)\s*\.{getter}\(\s*\)", line):
                continue
            # 往后看几行：有没有 throw
            for j in range(i, min(len(lines), i + 6)):
                if "throw " in lines[j]:
                    total += 1
                    break
    return total


def count_write_setters(field, entity_cls=None, entity_fqn=None):
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
    for p in java_files():
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
        for i, line in enumerate(lines):
            is_setter = f".{setter}(" in line
            is_builder = False
            if builder_call in line and builder_re:
                # 往前回溯几行，看 builder() 链是否在这一行附近
                for j in range(max(0, i - 12), i + 1):
                    if builder_re.search(lines[j]):
                        is_builder = True
                        break
            if not (is_setter or is_builder):
                continue
            # 排除「纯搬运」：`data.setCurrencyCode(order.getCurrencyCode())`
            # 📌 这是**把实体的值复制到 DTO**（导出操作），
            #    **不是给实体赋值** → 不能算作写路径的赋值。
            #    📌 关键判据：**目标变量名**（data/vo/dto/resp/result/map/…）
            #    和**来源变量名**（order/entity/obj/…）不同，
            #    且来源是某个 .getXxx() → 这是「A 复制到 B」，不是赋值给实体。
            #    📌 反过来 `entity.setXxx(vo.getXxx())`（VO → 实体）**是真实写入**，
            #       所以不能一刀切排除所有 .get 来源。
            # 📌 正则要抓的是**接收者**和**来源**：
            #    `data.setCurrencyCode(order.getCurrencyCode())`
            #    → 接收者 = data（setter 前面那个），来源 = order
            #    📌 之前写成 `\.{setter}\(...\.get...` 会把 group(1) 抓成**参数里的变量**
            #       （=order），于是「目标=order」→ 判定成「同名读自己」→ 被排除
            #       → 结果真正的赋值点也被误杀了（我踩了 3 次才定位到）。
            m = re.search(rf"\b(\w+)\s*\.{setter}\(\s*(\w+)\.get(\w+)\(\s*\)", line)
            if m:
                target = m.group(1)
                src_host = m.group(2)
                src = m.group(3)
                # 📌 **「同名读自己」不等于「纯搬运」**！
                #    `item.setTotalAmount(item.getUnitPrice().multiply(...))`
                #    接收者和来源是同一个变量，但这是**真·写入**（拿单价算总价），
                #    不是把值原样搬回去。
                #    真正的搬运是「搬的是**同一个字段**」：
                #    `data.setCurrencyCode(order.getCurrencyCode())` → 搬 currencyCode。
                #    📌 之前写成「target == src_host 就排除」→
                #    把 `item.setTotalAmount(item.getUnitPrice()...)` 这种**真写入**误杀了，
                #    导致修复后脚本仍然误报（我踩了 1 次）。
                same_field = src.lower() == field.lower()
                if target.lower() == src_host.lower() and same_field:
                    # 同名 + 同字段 → 纯搬运
                    continue
                # 📌 **只排除「同字段名的搬运」**：`data.setCurrencyCode(order.getCurrencyCode())`
                #    —— 搬的是**同一个字段**，目标是 DTO/VO → 是导出，不是写实体。
                # 📌 **不能按「目标变量名」一刀切排除**！
                #    之前把 "detail" / "item" / "row" 加进前缀黑名单 →
                #    `detail.setInboundOrderId(order.getId())` 这种**真正的写入**
                #    被误杀 → 4 个真·高危列被判成「从没赋值」（我踩了 1 次）。
                #    判据应该是「搬的字段名 == 目标字段名」，而不是「目标长得像不像 DTO」。
                if same_field and target.lower().startswith(
                        ("data", "dto", "vo", "resp", "response", "result",
                         "map", "bean", "view", "export")):
                    continue
                # 📌 目标是「实体/订单/明细」这类业务对象名 → 算真实写入
                #    （entity.setXxx(vo.getXxx()) / item.setXxx(...)）
            # 排除在 VO/DTO/Response/Export 类里的赋值
            # 往上找最近的 class 声明
            cls = ""
            for j in range(i, -1, -1):
                cm = re.search(r"\bclass\s+(\w+)", lines[j])
                if cm:
                    cls = cm.group(1)
                    break
            if any(h in cls for h in READONLY_CLASS_HINT):
                continue
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
    cols = live_notnull_cols()
    if cols is None:
        print("⚠️  连不上 MySQL 容器 —— 跳过")
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
            # 实体被 @RequestBody 直接绑定 → 服务层必须自己兜底
            n = count_write_setters(field, entity_cls=cls,
                                    entity_fqn=entity_fqn_of(svc, table))
            # 📌 也认可「入口处显式校验并报错」这种兜底方式
            g = count_null_guards(field, entity_cls=cls,
                                  entity_fqn=entity_fqn_of(svc, table))
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
