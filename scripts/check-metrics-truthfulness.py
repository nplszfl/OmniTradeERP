#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""指标真实性巡检 —— 「声明了指标」≠「指标在说真话」。

## 为什么需要这个脚本

本项目踩过的坑（2026-11-10 抓到）：

`erp-common` 的 `MetricsConfig` 声明了 5 个 Micrometer 指标，并且通过
`CommonMetricsAutoConfiguration`（`AutoConfiguration.imports`）**注册进所有带 Actuator 的服务**：

| 指标 | 声称 | 实际 |
|---|---|---|
| `order.create.count` | 订单创建总数 | 没有任何地方 `.increment()` → **永远是 0** |
| `order.sync.count` | 订单同步总数 | 没有任何地方 `.increment()` → **永远是 0** |
| `order.status` | 各状态订单数量 | supplier 直接 `return 0;` → **永远是 0** |
| `platform.api.call.count` | 平台API调用总次数 | 没有任何地方 `.increment()` → **永远是 0** |
| `platform.api.error.count` | 平台API调用失败次数 | 没有任何地方 `.increment()` → **永远是 0** |

后果：`/actuator/prometheus` 抓到的全是假事实 ——
「订单创建总数 = 0」，而系统明明在创建订单；
「平台 API 失败次数 = 0」，而平台同步失败时**监控完全看不见**。

更隐蔽的一层：**这些指标在 erp-common 里**。common 是公共库，**它拿不到订单表、也拿不到平台 API**，
所以在 common 里声明业务域指标，**从设计上就只能写死** → 假事实是必然的，不是疏忽。

这跟「静默失败」是同一族问题：**数字在，但它一直在骗人**（见 MEMORY.md 规则 18 / 19）。

## 本脚本查五类

- **A. 计数器没人写**：声明了 `Counter`，但全仓找不到 `.increment()` → 恒为 0。
- **B. Gauge 恒为常量**：Gauge 的 supplier 只 `return` 一个数字字面量 → 恒为 0。
- **C. 公共库声明业务域指标**：erp-common 里出现 `order.*` / `platform.*` 指标 → 公共库拿不到这数据。
- **E. 写入点在永不触发的切面里**：指标唯一的写入点在一个「拦截的注解全仓无人使用」的切面里
  → 切面永不触发 → 指标恒为 0。（**实测**：老 `ApiMonitorAspect` 拦截 `@ApiMonitor`，
  而 `@ApiMonitor` 全仓无人用 → `api.calls.total` / `api.errors.total` 永远是 0。）
- **D. 描述与形状不符**：description 说「各状态 / 各类型」，但指标没有对应的 tag →
  一个数表达不了一份分布。

## 怎么用

    python3 scripts/check-metrics-truthfulness.py

退出码：0 = 干净，1 = 有假事实指标。

## 维护约定（判据依赖它，所以要写在代码里）

判据 A 依赖「**指标名（字面量或常量）必须出现在 `.increment(` 附近**」。
所以：**不要把指标名藏进一个跟 increment 无关的地方**。
**三种**写法都支持：

    // 写法 1：字面量直接跟在 increment 旁边
    Counter.builder("platform.api.call.count").tag("platform", p).register(registry).increment();

    // 写法 2：常量 + 紧邻的 increment（脚本会展开常量定义）
    private static final String M = "platform.api.call.count";
    ...
    Counter.builder(M).register(registry).increment();

    // 写法 3：经 MeterRegistry 直接取（Micrometer 最标准的写法）
    meterRegistry.counter("platform.api.call.count").increment();
    meterRegistry.counter("platform.api.call.count", "platform", p).increment();

⚠️ **写法 3 是曾经漏掉的一种**（2026-11-10 02:30 修复）——
判据原本只认 `Counter.builder(` / `MultiGauge.builder(` / `.timer(`，
**完全不认 `.counter(`** → 同一个 API 的两种等价写法，只认一种 → **双向失明**：
  - **假阴性**：用 `meterRegistry.counter("x")` 声明、且真没人写的计数器 → 闸门**完全看不见** → 真·假事实漏网；
  - **假阳性**：用 `meterRegistry.counter("x").increment()` 写入的计数器 → 被误报成「没人写 → 永远是 0」
    → **合法代码被判死** → 假阳性比假阴性危险（会逼下一个人去放松闸门，规则 99）。
→ 现在**声明点**和**写入点**两边都补了 `.counter(`。
→ **反向验证**：① 合法写法（registry.counter 写入）→ 绿；② 真·假事实（registry.counter 声明、没人写）→ 红；
  ③ 回归：原始缺陷（erp-common 的 5 个假事实指标）→ 仍报 13 处。

## 已知豁免

- `@Timed(value="X")` 产生的指标由 `PerformanceMonitorAspect` 统一写入 → 不查。
- Micrometer 自带指标（jvm.* / http.server.requests 等）→ 不查。
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {'target', 'node_modules', '.git', 'dist', 'build'}

# 公共库里**不允许**出现的业务域指标前缀
COMMON_FORBIDDEN_PREFIXES = ('order.', 'platform.', 'inventory.', 'purchase.',
                              'customer.', 'supplier.', 'finance.', 'warehouse.')

# Micrometer / Spring 自带指标前缀（不是我们自己声明的）
BUILTIN_PREFIXES = ('jvm.', 'http.', 'process.', 'system.', 'executor.', 'logback.')

# 描述里的「分布/明细」语义关键词 —— 出现它就说明指标应该带维度 tag
DISTRIBUTION_WORDS = ('各状态', '各类型', '各平台', '各店铺', '各分类', '分布', '明细')

# 「各X」→ 该维度在 tag 里应该出现的 key（子串匹配，大小写不敏感）
DIMENSION_TAG_KEYS = {
    '各状态': ('status',),
    '各类型': ('type', 'platform'),
    '各平台': ('platform',),
    '各店铺': ('shop',),
    '各分类': ('category', 'type'),
}


def java_files():
    """所有 */src/main/java 下的 .java（含包名子目录）。

    ⚠️ 踩过的坑：早期版本写成「dirpath.endswith('src/main/java')」，
    只会拿到 `src/main/java` 这一层（里面一个 .java 都没有，Java 文件都在包名子目录里）
    → **一个文件都没扫到 → 闸门全绿**（假绿，比没有闸门更坏）。
    → 判据必须是「路径里**包含** src/main/java 这一段」。
    """
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        parts = dirpath.split(os.sep)
        if 'src' not in parts or 'main' not in parts or 'java' not in parts:
            continue
        for fn in filenames:
            if fn.endswith('.java'):
                yield Path(dirpath) / fn


def strip_comments(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    return src


def read_sources():
    """返回 [(path, 原始文本, 去注释文本)]"""
    out = []
    for p in java_files():
        try:
            raw = p.read_text(encoding='utf-8')
        except Exception:
            continue
        out.append((p, raw, strip_comments(raw)))
    return out


def collect_string_constants(sources):
    """常量名 → 字面量值（只收 String 常量）

    ⚠️ 踩过的坑（实测）：只收「裸常量名」时，**跨类常量**解析不出来
    → 「声明用字面量 + 递增用 `ClassName.CONST`」的指标会被**误报成假事实**（实测假阳性一例）。
    → 所以这里**额外**收集「限定名.常量名」→ 值。
    ⚠️ 同时要记住**同名常量的歧义**：两个类里同名常量值不同 → 裸名解析**不能瞎猜**
    （猜错 = 把「真的有人写」当成「没人写」→ 假绿）。→ 记进 `ambiguous`。
    """
    consts = {}
    qualified = {}
    ambiguous = set()
    for _p, _raw, src in sources:
        cls = _class_name_of(src)
        for m in re.finditer(
                r'(?:public|private|protected|static|final|\s)*String\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"]*)"',
                src):
            name, val = m.group(1), m.group(2)
            if name in consts and consts[name] != val:
                ambiguous.add(name)
            consts.setdefault(name, val)
            if cls:
                qualified.setdefault('%s.%s' % (cls, name), val)
    return consts, qualified, ambiguous


def _class_name_of(src):
    """从源码里取主类名（取不到返回 None）"""
    m = re.search(r'\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)', src)
    return m.group(1) if m else None


def resolve_name(expr, consts, qualified=None, ambiguous=None, scope=None):
    """把 builder(...) 里的参数解析成字面量指标名；解析不出来返回 None

    支持三种写法：
      ① 字面量      → `Counter.builder("x")`
      ② 裸常量名    → `Counter.builder(X)`（X 在本仓某处 `String X = "..."`）
      ③ **跨类常量** → `Counter.builder(SomeClass.X)`  ← 之前解析不了 → **会误报假事实**
    ⚠️ 裸名若有歧义（多个类里同名常量、值不同）→ **返回 None**（宁可「查不到」也不猜错）
    """
    expr = expr.strip()
    if expr.startswith('"') and expr.endswith('"'):
        return expr[1:-1]
    m = re.match(r'^([A-Za-z_$][\w$]*)\.([A-Z][A-Z0-9_]*)$', expr)
    if m and qualified:
        hit = qualified.get('%s.%s' % (m.group(1), m.group(2)))
        if hit:
            return hit
        return None
    if ambiguous and expr in ambiguous:
        # 同名常量、值不同 → 裸名**不能瞎猜**（猜错 = 假绿）。
        # 但**当前文件所属的类**里若有同名常量 → 作用域能定死 → 用它（不漏报）。
        if scope:
            hit = qualified.get('%s.%s' % (scope, expr)) if qualified else None
            if hit:
                return hit
        return None
    if expr in consts:
        return consts[expr]
    return None


def find_call_args(src, call_re):
    """找形如 `Builder.call(...)` 的调用，返回 [(start_offset, 全部参数文本)]。

    ⚠️ 踩过的坑（两个）：
    1. 早期版本只返回「**第一个逗号之后**」的文本 → 指标名（第一个参数）被丢掉
       → `Gauge.builder("x", supplier)` 的名字解析不出来 → **判据 B 静默失效**。
    2. 单参数调用（`Counter.builder("x")`，最常见的写法）**没有逗号**
       → 早期版本 `for/else: continue` 直接跳过 → **判据 A 完全没跑**。
    → 现在返回**完整参数串**，由调用方自己 split。
    """
    results = []
    for m in re.finditer(call_re, src):
        start = m.end()
        depth = 1
        i = start
        while i < len(src) and depth:
            if src[i] == '(':
                depth += 1
            elif src[i] == ')':
                depth -= 1
            i += 1
        if depth:
            continue  # 括号不配对，跳过
        results.append((m.start(), src[start:i - 1].strip()))
    return results


def split_args(inner):
    """按**顶层**逗号切分参数（忽略字符串/括号/尖括号里的逗号）"""
    parts = []
    cur = []
    depth = 0
    in_str = False
    i = 0
    while i < len(inner):
        ch = inner[i]
        if in_str:
            cur.append(ch)
            if ch == '\\':
                if i + 1 < len(inner):
                    cur.append(inner[i + 1])
                    i += 1
            elif ch == '"':
                in_str = False
            i += 1
            continue
        if ch == '"':
            in_str = True
            cur.append(ch)
        elif ch in '([{<':
            depth += 1
            cur.append(ch)
        elif ch in ')]}>':
            depth -= 1
            cur.append(ch)
        elif ch == ',' and depth == 0:
            parts.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(ch)
        i += 1
    tail = ''.join(cur).strip()
    if tail:
        parts.append(tail)
    return parts


def method_body(src, method_name):
    """找 `private double methodName(...) { ... }` 的方法体（去注释）"""
    pat = re.compile(
        r'(?:public|private|protected|static|final|\s)+[\w<>\[\],.\s]+?\b' + re.escape(method_name) +
        r'\s*\([^)]*\)\s*\{')
    m = pat.search(src)
    if not m:
        return None
    start = m.end()
    depth = 1
    i = start
    while i < len(src) and depth:
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
        i += 1
    return src[start:i - 1]


def body_is_constant(body):
    """方法体是否「只返回一个数字字面量」"""
    if body is None:
        return False
    lines = [l.strip() for l in body.split('\n') if l.strip()]
    if not lines:
        return False
    if len(lines) > 1:
        # 允许多行注释/空行被 strip 掉后只剩一条
        pass
    if len(lines) != 1:
        return False
    return bool(re.fullmatch(r'return\s+[-+]?\d+(\.\d+)?[dDfFlL]?;', lines[0]))


def body_is_inert(body):
    """方法体是否「不依赖任何外部状态」—— 常量、字面量、空集合等"""
    if body is None:
        return True
    lines = [l.strip() for l in body.split('\n') if l.strip()]
    if not lines:
        return True
    # 允许 return <常量/字面量>，以及 return null / List.of() / Map.of()
    for l in lines:
        if not (l.startswith('return ') or l.startswith('throw ')):
            # 有别的语句 → 可能依赖外部状态
            if l not in ('}', '{'):
                return False
    joined = ' '.join(lines)
    if re.search(r'\b(this\.|\w+\.|\w+\()', joined) and 'return' in joined:
        # 有字段访问或方法调用 → 依赖外部状态
        if re.search(r'return\s+[^;]*[\w]\s*\.', joined) or re.search(r'return\s+\w+\(', joined):
            return False
    return True


def find_incremented_metrics(lines, consts, qualified=None, ambiguous=None, scope=None):
    """返回本文件里**确实被 `.increment()` 写过**的指标名集合。

    做法：对每一处 `.increment(`，**向上回溯到语句开头**（直到遇到上一条语句的 `;`
    或 `{` / `}`），拿到完整语句，再从语句里提取指标名：
      - 语句里直接带字面量 → `Counter.builder("x").…​.increment()` → 名字就是 "x"
      - 语句里带常量名   → 展开常量定义
      - 语句里是变量     → `xxx.increment()` → 去本文件里找 `Counter xxx = …builder("y")` 的声明

    ⚠️ 踩过的坑（两个）：
      1. 早期版本用「±4 行窗口」找名字 → `api.calls.total` 名字在第 66 行、
         `.increment()` 在第 70 行 → **真的写了**却被判成「没人写」。
      2. 早期版本用「全局行表 + 每文件行号」切窗口 → 窗口切到别的文件去了。
    → 结论：**不要用行窗口**，要用**语句边界**。
    """
    found = set()
    # 变量名 → 指标名（本文件内）
    # ⚠️ 声明写法要覆盖全，否则会把「真的写了」误判成「没人写」：
    #   ① `Counter c = Counter.builder("x")`     —— 局部变量
    #   ② `this.c = Counter.builder("x")`           —— 字段赋值
    #   ③ `c = Counter.builder("x")`                —— 字段赋值（无 this）
    var_to_metric = {}
    for m in re.finditer(r'(?:\w+\.)?(\w+)\s*=\s*Counter\.builder\s*\(\s*([^)]*)\)',
                         '\n'.join(lines)):
        name = resolve_name(m.group(2).split(',')[0], consts, qualified, ambiguous, scope)
        if name:
            var_to_metric[m.group(1)] = name

    n = len(lines)
    for i, line in enumerate(lines):
        if '.increment(' not in line:
            continue
        # 向上回溯到语句开头
        j = i
        while j > 0:
            prev = lines[j - 1].strip()
            if prev.endswith(';') or prev.endswith('{') or prev.endswith('}'):
                break
            j -= 1
        stmt = '\n'.join(lines[j:i + 1])
        # ① 语句里 **builder(...) 的第一个参数**才是指标名
        #    ⚠️ 不能把语句里所有字符串字面量都当指标名 —— `.tag("result", "success")`
        #    里的 "success" 是 **tag 值**，不是指标 → 会造成误报。
        for m in re.finditer(r'\.builder\s*\(\s*([^,)]*)', stmt):
            found.add(resolve_name(m.group(1), consts, qualified, ambiguous, scope) or '')
        # ② `registry.timer("x", ...)` 这种写法
        for m in re.finditer(r'\.timer\s*\(\s*"([^"]+)"', stmt):
            found.add(m.group(1))
        # ②b `meterRegistry.counter("x", ...)` —— Micrometer **最标准**的取计数器写法。
        #     ⚠️ 漏了它会**双向失明**：
        #       假阴性 → 用它声明且没人写的计数器 → 闸门完全看不见（真·假事实漏网）
        #       假阳性 → 用它写入的计数器 → 被误报成「没人写 → 永远是 0」（合法代码被判死）
        for m in re.finditer(r'\.counter\s*\(\s*"([^"]+)"', stmt):
            found.add(m.group(1))
        # ③ 语句里带常量名
        for m in re.finditer(r'\.builder\s*\(\s*([\w$]+\.[A-Z][A-Z0-9_]*|[A-Z][A-Z0-9_]{2,})\s*[,)]', stmt):
            nm = resolve_name(m.group(1), consts, qualified, ambiguous, scope)
            if nm:
                found.add(nm)
        # ④ 语句里是变量 → 查变量声明
        for m in re.finditer(r'(\w+)\s*\.\s*increment\s*\(', stmt):
            v = m.group(1)
            if v in var_to_metric:
                found.add(var_to_metric[v])
    found.discard('')
    return found


def find_dead_aspects(sources):
    """找出「永远不会触发」的切面：@Aspect 类的切点匹配某个注解，但**该注解全仓无人使用**。

    返回 [(aspect_path, aspect_class, trigger_annotation_fqn)]
    """
    all_src = '\n'.join(src for _p, _r, src in sources)
    dead = []
    for p, raw, src in sources:
        if '@Aspect' not in src:
            continue
        # 类名
        mcls = re.search(r'(?:public\s+)?class\s+(\w+)', src)
        if not mcls:
            continue
        cls = mcls.group(1)
        # 切点里匹配的注解：@annotation(包名.注解名)
        for m in re.finditer(r'@annotation\s*\(\s*([\w.]+)\s*\)', src):
            fqn = m.group(1)
            simple = fqn.split('.')[-1]
            # 全仓有没有人用这个注解（@SimpleName 或 @fqn）
            used = re.search(r'@\s*' + re.escape(simple) + r'\b', all_src)
            if used:
                # 排除注解自己声明处（@interface Xxx / import）
                hits = [mm for mm in re.finditer(r'@\s*' + re.escape(simple) + r'\b', all_src)]
                real = [mm for mm in hits
                        if not all_src[max(0, mm.start() - 40):mm.start()].rstrip().endswith('@interface')
                        and not all_src[max(0, mm.start() - 30):mm.start()].strip().startswith('import')]
                if real:
                    continue
            dead.append((p.relative_to(ROOT), cls, fqn))
    return dead


def metrics_written_in(path_rel, sources, consts, qualified=None, ambiguous=None):
    """某个文件里被 `.increment()` 写过的指标名"""
    for p, raw, src in sources:
        if p.relative_to(ROOT) == path_rel:
            return find_incremented_metrics(src.split('\n'), consts, qualified, ambiguous, _class_name_of(src))
    return set()


def main():
    sources = read_sources()
    consts, qualified, ambiguous = collect_string_constants(sources)

    # 所有源文件的行集合（用于判据 A：谁写了它）
    file_lines = {}
    for p, raw, src in sources:
        file_lines[p] = src.split('\n')
    blob = '\n'.join('\n'.join(v) for v in file_lines.values())

    timed_names = set(re.findall(r'@Timed\s*\([^)]*value\s*=\s*"([^"]+)"', blob))

    findings = []  # (kind, path, lineno, detail)

    counters = {}   # metric name -> [(path, lineno, alias)]
    gauges = []     # (metric name, path, lineno, supplier_text, body)

    for p, raw, src in sources:
        rel = p.relative_to(ROOT)
        scope = _class_name_of(src)
        # ---- Counter ----
        for start, inner in find_call_args(src, r'Counter\.builder\s*\('):
            args = split_args(inner)
            if not args:
                continue
            name = resolve_name(args[0], consts, qualified, ambiguous, scope)
            if not name:
                continue
            lineno = src[:start].count('\n') + 1
            counters.setdefault(name, []).append((rel, lineno, args[0].strip()))

        # ---- Counter（经 MeterRegistry 直接取的写法）----
        # `meterRegistry.counter("x")` / `registry.counter("x", "tag", "v")`
        # → 这也是**声明**（注册进 registry），而且是 Micrometer 最标准的写法。
        # ⚠️ 漏了它 → 用这种写法声明、且真没人写的计数器 → 闸门完全看不见（假阴性）。
        for m in re.finditer(r'(?i)\b\w*registry\w*\s*\.\s*counter\s*\(\s*"([^"]+)"', src):
            name = m.group(1)
            lineno = src[:m.start()].count('\n') + 1
            counters.setdefault(name, []).append((rel, lineno, name))

        # ---- Gauge ----
        for start, inner in find_call_args(src, r'Gauge\.builder\s*\('):
            args = split_args(inner)
            if len(args) < 2:
                continue
            name = resolve_name(args[0], consts, qualified, ambiguous, scope)
            if not name:
                continue
            lineno = src[:start].count('\n') + 1
            supplier = args[1].strip()
            gauges.append((name, rel, lineno, supplier, src))

        # ---- MultiGauge ----
        for start, inner in find_call_args(src, r'MultiGauge\.builder\s*\('):
            args = split_args(inner)
            if not args:
                continue
            name = resolve_name(args[0], consts, qualified, ambiguous, scope)
            if not name:
                continue
            lineno = src[:start].count('\n') + 1
            counters.setdefault(name, []).append((rel, lineno, args[0].strip()))

    # ---------- 判据 A：Counter 没人写 ----------
    # 「谁写了它」的判定：**包含 `.increment(` 的那条语句**里能不能找到这个指标名。
    # ⚠️ 踩过的坑：早期版本用「±4 行窗口 + 全局行表」→ 窗口切错了位置
    # → 把 `api.calls.total`（名字在第 66 行、increment 在第 70 行，**真的写了**）误判成「没人写」。
    # → 现在改成：**先把 increment 所属的语句完整取出来**（向上回溯到语句开头），
    #    再看指标名在不在里面；名字是变量时，再去解析那个变量的声明。
    written_names = set()
    for p, flines in file_lines.items():
        written_names |= find_incremented_metrics(flines, consts, qualified, ambiguous, _class_name_of('\n'.join(flines)))

    for name, defs in counters.items():
        if name in timed_names:
            continue
        if name.startswith(BUILTIN_PREFIXES):
            continue
        if name in written_names:
            continue
        rel, lineno, _alias = defs[0]
        findings.append(('A', rel, lineno,
                         f'指标 `{name}` 声明为计数器，但全仓找不到 `.increment()` → **永远是 0**（假事实）'))

    # ---------- 判据 B：Gauge 恒为常量 ----------
    for name, rel, lineno, supplier, src in gauges:
        if name.startswith(BUILTIN_PREFIXES):
            continue
        body = None
        m = re.match(r'(?:this::)?(\w+)\s*$', supplier)
        if m and '->' not in supplier:
            body = method_body(src, m.group(1))
        elif supplier.startswith('() ->'):
            body = supplier[5:].strip().strip('{}').strip()
        if body is not None and body_is_constant(body):
            findings.append(('B', rel, lineno,
                             f'Gauge `{name}` 的 supplier 只 `return` 一个常量 → **永远是 0**（假事实）'))
        elif body is not None and body_is_inert(body):
            findings.append(('B', rel, lineno,
                             f'Gauge `{name}` 的 supplier 不依赖任何外部状态 → 值恒定（请确认是否假事实）'))

    # ---------- 判据 C：公共库声明业务域指标 ----------
    for p, raw, src in sources:
        rel = p.relative_to(ROOT)
        if rel.parts[:2] != ('erp-common', 'src'):
            continue
        for m in re.finditer(r'(?:Counter|Gauge|MultiGauge|Timer|DistributionSummary)\.builder\s*\(\s*"([^"]+)"', src):
            name = m.group(1)
            if name.startswith(COMMON_FORBIDDEN_PREFIXES):
                lineno = src[:m.start()].count('\n') + 1
                findings.append(('C', rel, lineno,
                                 f'公共库 erp-common 声明了业务域指标 `{name}` → '
                                 f'common 拿不到这数据，**只能写死** → 必然是假事实（应放到拥有数据的那个服务）'))

    # ---------- 判据 D：描述说「分布」但指标没有维度 tag ----------
    # ⚠️ 判据要分清「**有 tag**」和「**有该维度的 tag**」：
    #   `order.status` 的描述是「各状态订单数量」，但它只挂了 `module=orders`
    #   → **一个数照样表达不了一份分布** → 「有 tag」不能算过。
    for p, raw, src in sources:
        rel = p.relative_to(ROOT)
        for m in re.finditer(
                r'(?:Counter|Gauge|MultiGauge|Timer)\.builder\s*\(\s*"([^"]+)"[^;]{0,400}?\.description\s*\(\s*"([^"]+)"',
                src, re.S):
            name, desc = m.group(1), m.group(2)
            segment = src[m.start():m.start() + 400]
            tag_keys = [k.lower() for k in re.findall(r'\.tag\s*\(\s*"([^"]+)"', segment)]
            need = None
            for word, keys in DIMENSION_TAG_KEYS.items():
                if word in desc:
                    need = keys
                    break
            if need is None:
                continue
            if not any(any(k in tk for k in need) for tk in tag_keys):
                lineno = src[:m.start()].count('\n') + 1
                findings.append(('D', rel, lineno,
                                 f'指标 `{name}` 的描述说「{desc}」，但没有对应的维度 tag'
                                 f'（现有 tag: {tag_keys or "无"}，期望含 {need}）→ '
                                 f'一个数表达不了一份分布（应改成 MultiGauge + tag）'))

    # ---------- 判据 E：写入点永远不会执行 ----------
    # 「有 `.increment()` 代码」**不等于**「指标会被写」。
    # 实测：`ApiMonitorAspect` 里老老实实写了 `api.calls.total` / `api.errors.total`，
    # 但它拦截的注解 `@ApiMonitor` **全仓没有任何方法用它** → 切面永远不触发 → 指标恒为 0。
    # → 判据：指标名若**只**在一个「永不触发的切面」里被写 → 报「写入点是死的」。
    dead_aspects = find_dead_aspects(sources)
    for rel, cls, fqn in dead_aspects:
        dead_metrics = metrics_written_in(rel, sources, consts)
        if not dead_metrics:
            continue
        for name in sorted(dead_metrics):
            if name.startswith(BUILTIN_PREFIXES):
                continue
            # 别处有没有写？
            written_elsewhere = False
            for p2, raw2, src2 in sources:
                if p2.relative_to(ROOT) == rel:
                    continue
                if name in find_incremented_metrics(src2.split('\n'), consts, qualified, ambiguous, _class_name_of(src2)):
                    written_elsewhere = True
                    break
            if written_elsewhere:
                continue
            findings.append(('E', rel, 1,
                             f'指标 `{name}` 的**唯一写入点**在切面 `{cls}` 里，'
                             f'而该切面拦截的注解 `@{fqn.split(".")[-1]}` **全仓无人使用** '
                             f'→ 切面永不触发 → 指标**永远是 0**（假事实）'))

    if not findings:
        print('✅ 未发现假事实指标（无人递增的计数器 / 恒为常量的 Gauge / 公共库声明业务域指标 / 描述与形状不符）')
        return 0

    print(f'⚠️  发现 {len(findings)} 处假事实指标：\n')
    by_kind = {}
    for kind, path, lineno, detail in findings:
        by_kind.setdefault(kind, []).append((path, lineno, detail))
    titles = {
        'A': 'A. 计数器无人递增（永远是 0）',
        'B': 'B. Gauge 恒为常量（永远是 0）',
        'C': 'C. 公共库声明业务域指标（拿不到数据，只能写死）',
        'D': 'D. 描述与形状不符（说「分布」却只有一个数）',
        'E': 'E. 写入点在永不触发的切面里（永远是 0）',
    }
    for kind in sorted(by_kind):
        items = by_kind[kind]
        print(f'── {titles.get(kind, kind)} ({len(items)}) ──')
        for path, lineno, detail in items:
            print(f'   {path}:{lineno}  {detail}')
        print()
    print('修法：')
    print('  A/B → 把指标移到「真正拥有这份数据」的服务，在真实的写入点 increment / 取值；')
    print('  C   → 指标归属数据所在服务，别放在 erp-common；')
    print('  D   → 用 MultiGauge + tag 表达分布，或把描述改成它实际能表达的东西；')
    print('  E   → 切面的触发注解得真的有人用（或者删掉这个切面和它的指标）。')
    return 1


if __name__ == '__main__':
    sys.exit(main())
