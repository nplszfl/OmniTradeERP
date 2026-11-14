#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「内存存储冒充数据库」巡检 —— 第 75 道闸门。

📌 **为什么需要这道闸门**（2026-11-14 实测踩出来的坑）：

`erp-pricing-service` 的 `PriceHistoryServiceImpl` 原来长这样：

```java
// 模拟数据库存储
private final List<PriceHistory> priceHistoryList = new CopyOnWriteArrayList<>();

@Override
public void recordPriceChange(PriceHistory history) {
    // TODO: 保存到数据库
    priceHistoryList.add(history);
}

@Override
public List<PriceHistory> getPriceHistory(Long productId) {
    // TODO: 从数据库查询
    return priceHistoryList.stream()...toList();
}
```

**实体（`PriceHistory`）、表（`price_history`）、Mapper（`PriceHistoryMapper`）全都在**，
SQL 种子数据也全都在 —— **只是没人调用它**。

后果**不是「数据不准」，而是「服务重启即丢数据」**：

| 症状 | 根因 |
|---|---|
| 「价格变更历史」页永远是空的 | 查的是内存，进程一起来就是空的 |
| 「价格趋势分析」永远返回 null | 基于空列表 → 趋势图永远是平的 |
| 多副本部署时数据互相看不见 | 每个副本各有一份内存 |
| 单测全绿、闸门全绿 | 单测用 mock 的 Mapper，静态扫描看不出「没用持久层」 |

📌 **更隐蔽的一层**：`CompetitorScrapeServiceImpl` 里的
`generateMockCompetitor` 用 `Random` 生成**随机价格/评分/评论数**，
`getCompetitorProducts` **每次调用现场生成 3 条新随机数** →
同一个 productId 连查两次返回**不同的价格** → 前端「竞品均价」每次刷新都在跳，
定价算法基于它算出的建议价**完全不可复现**。
这类「内存 + 随机数」的组合，**比单纯的内存 List 更难被发现**。

## 本脚本查什么

扫全部 `**/src/main/**/*.java`，找**「服务层的字段是内存集合」**这一信号：

1. 字段类型是 `List` / `Map` / `Set` / `CopyOnWriteArrayList` / `ConcurrentHashMap` /
   `ConcurrentLinkedQueue` / `LinkedBlockingQueue` / `ArrayDeque` …
2. 且字段**不是** `static final` 常量（常量是配置，不是存储）
3. 且字段**不是** `final` 且**没有**在构造器里被赋值（构造器注入的是**依赖**，不是存储）
4. 且字段所在类**注入了某个 Mapper**（说明这个类**本该**走持久层，却自己开了内存存储）
   —— 或者字段所在类**没有**任何 Mapper / Repository / Dao 依赖
   （= 这个类**完全没接持久层**）

⚠️ **判据必须窄，否则会误伤一整类合法代码**（规则 99：假阳性比假阴性危险）：

| 合法写法 | 为什么不能报 |
|---|---|
| `private static final List<String> ALLOWED = List.of(...)` | 常量配置，不是存储 |
| `private final List<OrderItem> items;`（构造器注入的**依赖**） | 不是「存储」，是别人给的 |
| `private final Map<Long, String> cache = new ConcurrentHashMap<>();` | **缓存**是合法用途（有 TTL/有淘汰） |
| `private List<Order> orders = new ArrayList<>();`（**局部变量**） | 局部变量不是字段 |
| `private final List<String> children = new ArrayList<>();`（**VO/DTO**） | VO 的子节点集合是数据结构，不是存储 |

→ 所以判据只针对：**服务实现类**（文件名以 `Service` / `ServiceImpl` / `Manager` /
`Repository` / `Handler` / `Processor` 结尾）+ **字段** + **非 final 或 final 但就地 new**。

📌 **另外单独查一类**：`Random` 用来生成**业务数据**（价格/库存/订单/评分），
而不是用来做**技术性随机**（负载均衡抖动、采样窗口、退避重试）。
—— 这类「随机数假数据」是「假数据」的一种，危害等同于内存 mock。

## 反向验证（规则 40 / 75）

`--self-test` 会在**副本**上做差分验证：
① 注入一个「内存 List 存储」的 Service → 必须报红；
② 注入一个「构造器注入依赖」的 Service → 必须**不**报红；
③ 注入一个「缓存」→ 必须**不**报红；
④ 注入一个「用 Random 生成价格」的 Service → 必须报红。

用法：
    python3 scripts/check-in-memory-storage.py           # 巡检（有问题 → exit 1）
    python3 scripts/check-in-memory-storage.py --verbose  # 打印明细
    python3 scripts/check-in-memory-storage.py --self-test
退出码：0 = 干净，1 = 有问题
"""

from __future__ import annotations

import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# ---------------------------------------------------------------------------
# 内存集合类型（当存储用的那些）
# 内存集合类型（当存储用的那些）
# 📌 **接口名也算** —— 声明成 `List<DemoEntity>` 但就地 new 一个 CopyOnWriteArrayList
#    是最常见的写法（自检实测踩到：只认实现类 → 漏报）。
MEMORY_COLLECTION_TYPES = [
    # 接口
    "List", "Map", "Set", "Collection", "Queue", "Deque", "SortedMap", "SortedSet",
    "NavigableMap", "NavigableSet",
    # 实现类
    "CopyOnWriteArrayList",
    "CopyOnWriteArraySet",
    "ConcurrentHashMap",
    "ConcurrentLinkedQueue",
    "ConcurrentLinkedDeque",
    "ConcurrentSkipListMap",
    "ConcurrentSkipListSet",
    "LinkedBlockingQueue",
    "LinkedBlockingDeque",
    "ArrayBlockingQueue",
    "PriorityBlockingQueue",
    "DelayQueue",
    "SynchronousQueue",
    "ArrayDeque",
    "ArrayList",
    "HashMap",
    "HashSet",
    "LinkedList",
    "LinkedHashMap",
    "LinkedHashSet",
    "TreeMap",
    "TreeSet",
    "Vector",
    "Hashtable",
]

# 基本包装类型 → 出现在泛型参数里说明这是「缓存 / 索引」，不是「存储」
PRIMITIVE_WRAPPERS = {
    "String", "Integer", "Long", "Double", "Float", "Short", "Byte",
    "Boolean", "Character", "BigDecimal", "BigInteger", "Object", "Number",
    "LocalDate", "LocalDateTime", "LocalTime", "Date", "Instant",
}

# 字段声明：private/protected + [static] + [final] + 类型 + 名字
# 📌 **`\s` 不能跨行** —— 否则 `^\s*` 会把换行吃掉，
#    m.start() 落到**上一行末尾** → 取行文本时得到空串（实测踩到）。
FIELD_RE = re.compile(
    r"^[ \t]*(?:private|protected)[ \t]+(?:(?:static)[ \t]+)?(?:(?:final)[ \t]+)?"
    r"(?P<type>[A-Za-z_][A-Za-z0-9_.]*)[ \t]*(?:<[^;{]*>)?[ \t]+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)[ \t]*(?:=|;)",
    re.MULTILINE,
)

# Mapper / Repository / Dao 的依赖声明
PERSISTENCE_HINT_RE = re.compile(
    r"\b[A-Za-z_][A-Za-z0-9_]*(?:Mapper|Repository|Dao|DAO)\b")

# 构造器注入的 final 字段（本类不拥有它 → 不是「存储」）
CTOR_ASSIGN_RE = re.compile(
    r"\bthis\.(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*=")

# 业务数据里用 Random 生成（不是技术性随机）
RANDOM_USAGE_RE = re.compile(
    r"\b(?:random|randomizer|rnd)\.(?:nextInt|nextDouble|nextLong|nextFloat|nextBoolean|ints)\s*\(")
RANDOM_FIELD_RE = re.compile(
    r"\bRandom\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*[;=]")

# 业务字段名（这些字段名 + Random → 一定是「随机数假业务数据」）
BUSINESS_FIELD_HINT_RE = re.compile(
    r"price|amount|money|cost|salary|score|rating|quantity|stock|inventory|"
    r"revenue|sales|profit|discount|order|customer|user_name|email|phone",
    re.IGNORECASE)

# 服务类文件名（这类类「本该」有持久层）
SERVICE_FILE_RE = re.compile(
    r"(?:Service|ServiceImpl|Manager|Repository|Handler|Processor|Collector)\w*\.java$",
    re.IGNORECASE)

# 数据结构类（VO/DTO/Entity）—— 它们的集合字段是**数据结构**，不是存储
DATACLASS_FILE_RE = re.compile(
    r"(?:VO|DTO|Entity|Request|Response|Param|Query|BO|PO)\w*\.java$",
    re.IGNORECASE)

# 📌 **dto / vo / model / entity 包下的类一律排除** ——
#    `dto/ServiceStatistics.java` 里的 `List recentErrors` 是**返回结构**，不是存储。
DATACLASS_PACKAGE_RE = re.compile(
    r"/src/main/java/.*?/(?:dto|vo|model|entity|domain|pojo|bo)/[^/]+\.java$",
    re.IGNORECASE)

# 常量集合：全大写命名的字段 → 是「配置常量」，不是存储
CONST_NAME_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")

# 技术性随机的白名单（这些场景用 Random 是合理的）
BENIGN_RANDOM_OWNERS = {
    "retry": "重试退避抖动属于技术性随机",
    "backoff": "退避策略属于技术性随机",
    "jitter": "抖动属于技术性随机",
    "shard": "分片选择属于技术性随机",
    "loadbalance": "负载均衡属于技术性随机",
}


def strip_comments(text: str) -> str:
    """去掉注释，避免注释里的示例代码被当成真代码。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def strip_comments_keep_lines(text: str) -> str:
    """去掉注释，但**保留所有换行** → 行号与原文完全一致。

    📌 之前用 `re.sub(r"/\\*.*?\\*/", "", text, flags=re.S)` 会把整块注释
    替换成空串 → **行号整体前移**（实测踩到：报 41 行，实际 52 行）。
    → 现在把块注释的每个非换行字符替换成空格，换行原样保留。
    """
    def blank_block(m):
        return "".join(ch if ch == "\n" else " " for ch in m.group(0))

    text = re.sub(r"/\*.*?\*/", blank_block, text, flags=re.S)
    # 行注释：只去掉注释部分，保留换行
    text = re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), text)
    return text


def java_files():
    out = []
    for dirpath, dirnames, files in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for f in files:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dirpath, f)
            if "/src/main/" not in p.replace(os.sep, "/"):
                continue
            out.append(p)
    return sorted(out)


def analyze(path: str):
    """返回该文件里的问题列表。"""
    raw = open(path, encoding="utf-8", errors="replace").read()
    text = strip_comments_keep_lines(raw)
    fname = os.path.basename(path)
    rel = os.path.relpath(path, ROOT)

    problems = []

    # 📌 行号直接用 strip 后文本的位置 —— 因为 strip 保留了所有换行，
    #    行号与原文**完全一致**（之前用「替换成空串」的方式导致行号前移）。
    def real_line(pos: int) -> int:
        return text[:pos].count("\n") + 1

    def line_of_field(name: str, fallback_pos: int) -> int:
        """按**字段名**精确定位声明行。

        📌 为什么要这么做：泛型跨行时（如
        `private final Map<String,\\n        List<ChatMessage>> sessionHistory = ...`）
        正则匹配的起点在**上一行** → 报出来的行号偏小。
        → 改成找「字段名 + 紧跟 = 或 ;」的那一行。
        """
        for fm in re.finditer(r"\b%s\b\s*(?:=|;)" % re.escape(name), text):
            return real_line(fm.start())
        return real_line(fallback_pos)

    # ── VO/DTO/Entity 跳过（集合字段是数据结构，不是存储）──────────────
    if DATACLASS_FILE_RE.search(fname):
        return problems
    if DATACLASS_PACKAGE_RE.search(rel.replace(os.sep, "/")):
        return problems

    is_service = bool(SERVICE_FILE_RE.search(fname))
    has_persistence = bool(PERSISTENCE_HINT_RE.search(text))

    # ── 判据 A：内存集合字段当存储 ──────────────────────────────────────
    if is_service:
        for m in FIELD_RE.finditer(text):
            ftype = m.group("type")
            fname2 = m.group("name")
            simple = ftype.split(".")[-1]
            if simple not in MEMORY_COLLECTION_TYPES:
                continue
            line = text[m.start():text.find("\n", m.start()) if "\n" in text[m.start():] else len(text)]
            if "static" in line and "final" in line:
                continue   # 常量配置
            # 📌 **全大写命名的字段 → 配置常量**（STOPWORDS / ALLOWED_TYPES）
            if CONST_NAME_RE.match(fname2):
                continue
            # final 且在构造器里 this.x = 赋值 → 构造器注入的依赖，不是「存储」
            if "final" in line:
                ctor_assigns = set()
                for cm in CTOR_ASSIGN_RE.finditer(text):
                    ctor_assigns.add(cm.group("name"))
                if fname2 in ctor_assigns:
                    continue
            # 📌 **泛型参数是基本类型 → 这是「缓存 / 索引」，不是「存储」**
            #    （Map<String, String> / Map<Long, String> 都是合法的缓存；
            #      Map<Long, OrderEntity> / List<OrderEntity> 才是「拿内存当数据库」）
            generics = re.search(r"<([^>]*)>", line)
            if generics:
                args = [a.strip().split(".")[-1] for a in generics.group(1).split(",")]
                if args and all(a in PRIMITIVE_WRAPPERS for a in args if a):
                    continue
            problems.append({
                "kind": "memory-storage",
                "file": rel,
                "line": line_of_field(fname2, m.start()),
                "detail": "字段 `%s %s` 是内存集合 —— 若它承担「持久化存储」职责，"
                          "则服务重启即丢数据（且与 Mapper 并存时会出现两套真相）"
                          % (simple, fname2),
                "snippet": line.strip(),
            })
    # ── 判据 B：用 Random 生成业务数据 ──────────────────────────────────
    if is_service and RANDOM_USAGE_RE.search(text):
        # 找到 Random 字段名
        rand_names = {m.group("name") for m in RANDOM_FIELD_RE.finditer(text)}
        # 找到随机结果被赋给了哪个变量
        for m in re.finditer(
                r"\b(?P<var>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*[^;]*?"
                r"\b(?:random|randomizer|rnd)\.(?:nextInt|nextDouble|nextLong|nextFloat)\s*\([^;]*;",
                text):
            var = m.group("var")
            if not BUSINESS_FIELD_HINT_RE.search(var):
                continue
            owner = var.lower()
            if any(b in owner for b in BENIGN_RANDOM_OWNERS):
                continue
            problems.append({
                "kind": "random-fake-data",
                "file": rel,
                "line": real_line(m.start()),
                "detail": "变量 `%s` 的值来自 Random —— 业务数据用随机数生成 = 假数据"
                          "（同一输入两次调用结果不同 → 不可复现、不可测试）" % var,
                "snippet": text[m.start():text.find(";", m.start()) + 1].strip(),
            })

    # ── 判据 C：Service 里出现「模拟/内存存储」的注释 ────────────────────
    if is_service:
        for m in re.finditer(
                r"(?://|/\*|\*)\s*[^\n]*?(模拟数据库|内存存储|内存 mock|内存mock|"
                r"假装数据库|暂时存在内存|先存在内存)", text):
            problems.append({
                "kind": "memory-comment",
                "file": rel,
                "line": real_line(m.start()),
                "detail": "注释里承认用了内存存储/模拟数据库 —— "
                          "src/main 不允许（规则：真实实现）",
                "snippet": m.group(0).strip()[:120],
            })

    return problems


def collect(root: str = ROOT):
    """在给定 root 下收集问题。"""
    global ROOT
    old_root = ROOT
    ROOT = root
    try:
        all_problems = []
        for p in java_files():
            all_problems.extend(analyze(p))
        return all_problems
    finally:
        ROOT = old_root


# ---------------------------------------------------------------------------
# 自检（差分验证）
# ---------------------------------------------------------------------------
SELF_TEST_BAD_MEMORY = """
package com.crossborder.erp.demo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class BadMemoryStorageServiceImpl {
    /** 模拟数据库存储 */
    private final List<DemoEntity> rows = new CopyOnWriteArrayList<>();
    private final DemoMapper demoMapper;
    public void save(DemoEntity e) { rows.add(e); }
}
"""

SELF_TEST_OK_INJECTED = """
package com.crossborder.erp.demo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OkInjectedServiceImpl {
    private final List<DemoEntity> rows;
    public OkInjectedServiceImpl(List<DemoEntity> rows) { this.rows = rows; }
    public int size() { return rows.size(); }
}
"""

SELF_TEST_OK_CACHE = """
package com.crossborder.erp.demo;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OkCacheServiceImpl {
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    public String get(String k) { return cache.get(k); }
}
"""

SELF_TEST_BAD_RANDOM = """
package com.crossborder.erp.demo;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class BadRandomPriceServiceImpl {
    private final Random random = new Random();
    public double price() {
        double currentPrice = 100 + random.nextInt(50);
        return currentPrice;
    }
}
"""

SELF_TEST_OK_RANDOM_TECH = """
package com.crossborder.erp.demo;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class OkRandomRetryServiceImpl {
    private final Random random = new Random();
    public long backoffMillis() {
        return random.nextInt(100);
    }
}
"""


def run_self_test() -> int:
    tmp = tempfile.mkdtemp(prefix="check-in-memory-storage-")
    try:
        base = os.path.join(tmp, "erp-demo", "src", "main", "java", "com", "crossborder", "erp", "demo")
        os.makedirs(base, exist_ok=True)

        # 📌 每个用例的文件名必须**符合 Service 命名** —— 判据是按
        #    「文件名像 Service 实现」来圈定范围的（避免误伤 VO/DTO/工具类）。
        cases = [
            ("BadMemoryStorageServiceImpl.java", "坏例-内存存储", SELF_TEST_BAD_MEMORY, True),
            ("OkInjectedServiceImpl.java", "好例-构造器注入依赖", SELF_TEST_OK_INJECTED, False),
            ("OkCacheServiceImpl.java", "好例-缓存", SELF_TEST_OK_CACHE, False),
            ("BadRandomPriceServiceImpl.java", "坏例-Random 造价格", SELF_TEST_BAD_RANDOM, True),
            ("OkRandomRetryServiceImpl.java", "好例-技术性随机", SELF_TEST_OK_RANDOM_TECH, False),
        ]

        failures = []
        for fname, name, content, should_fail in cases:
            f = os.path.join(base, fname)
            with open(f, "w", encoding="utf-8") as fh:
                fh.write(content)
            problems = collect(tmp)
            got = len(problems) > 0
            status = "✅" if got == should_fail else "❌"
            print(f"  {status} {name}: 期望{'报红' if should_fail else '不报红'}，实际{'报红' if got else '不报红'}")
            if got != should_fail:
                failures.append(name)
                for p in problems:
                    print(f"       └─ {p['kind']}: {p['file']}:{p['line']} {p['detail']}")
            os.remove(f)

        if failures:
            print(f"\n❌ 自检失败：{len(failures)} 个用例判据不对 → {failures}")
            return 1
        print("\n✅ 自检通过：判据能区分「内存存储」/「注入依赖」/「缓存」/「随机假数据」")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> int:
    if "--self-test" in sys.argv:
        print("🧪 「内存存储冒充数据库」闸门自检")
        return run_self_test()

    verbose = "--verbose" in sys.argv

    problems = collect(ROOT)

    if not problems:
        print("✅ 没发现「内存集合当持久化存储」或「Random 造业务数据」的缺陷")
        return 0

    by_kind = {}
    for p in problems:
        by_kind.setdefault(p["kind"], []).append(p)

    print("❌ 「内存存储冒充数据库」巡检：发现 %d 处\n" % len(problems))
    kind_titles = {
        "memory-storage": "内存集合当持久化存储",
        "random-fake-data": "用 Random 造业务数据",
        "memory-comment": "注释里承认用了内存存储",
    }
    for kind, items in sorted(by_kind.items()):
        print("── %s（%d 处）" % (kind_titles.get(kind, kind), len(items)))
        for p in items:
            print("  %s:%d" % (p["file"], p["line"]))
            print("     %s" % p["detail"])
            if verbose:
                print("     > %s" % p["snippet"][:160])
            print()
    print("📌 src/main 必须是真实实现：持久化走 Mapper，外部数据走真实调用。")
    print("   （内存 mock / 随机数假数据 = 假数据，规则 72 / 80 / 82）")
    return 1


if __name__ == "__main__":
    sys.exit(main())
