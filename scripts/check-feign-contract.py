#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Feign 契约巡检：Feign 客户端的方法 ↔ 目标服务的 Controller 映射。

## 为什么需要这个脚本

2026-11-03 抓到 `erp-platform-service` → `erp-order-service` 的订单同步链路
**三层契约全错**，但 `mvn test` 全绿、单测全过、代码看着完全正常：

1. **路径前缀**：Feign 写 `path = "/internal/orders"`，
   而订单服务的 controller 根是 `@RequestMapping("/order")` ——
   订单服务**根本没有 `/internal` 这套路径** → 每次调用都是 404。
2. **请求体字段名**：后端 `CreateOrderRequest` 是 `order` + `orderItems`，
   Feign 的 record 叫 `order` + `items` → JSON 字段对不上。
3. **返回值包装**：后端返回 `Result<Order>`，Feign 声明 `Order`
   → 反序列化出一个**字段全空**的 Order → `existingOrder != null` 恒成立
   → 每一次同步都「订单已存在，跳过」，一单都存不进来。

而且异常被 `catch (Exception e) { log.error(...) }` 吞成一行日志 ——
定时任务每 10 分钟「跑过了」，实际一单没同步。

**为什么单测查不出来**：Feign 接口在单测里是被 mock 的，
测的是「我传了什么参数」，不是「对方真的有这么个接口」。

本脚本把「Feign 调的路径在目标服务里存不存在」「返回值包装对不对」
做成静态交叉校验。

## 用法

    python3 scripts/check-feign-contract.py            # 打印报告
    python3 scripts/check-feign-contract.py --strict   # 有差异就退出码 1
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def _strip_java_comments(text):
    """去掉 Java 注释（// 行注释 与 /* */ 块注释）。

    📌 **必须去注释再匹配** —— 巡检脚本自己的解析缺陷曾经让整个闸门**静默失效**：

    2026-11-04 实测：给 Feign 方法加了一段说明注释（写在 @PostMapping 和方法签名之间），
    而 MAPPING_RE 原本要求「注解后面紧跟返回类型」→ **createOrder 整条方法被跳过**→
    报告里「方法数」从 2 变成 1 → 闸门照样输出「✅ 所有契约都对得上」。

    📌 **这就是最危险的失败模式**：闸门不是报错，而是**少查了东西然后报绿**。
    与「假绿」相比，它更有迷惑性 —— 因为报告里「方法数」变了，但没人会去数。

    📌 教训：闸门报告里**必须带「检查了多少」**，少查了要能看出来。

    📌 实现要点：**不能用正则直接去注释** —— 正则分不清
    「字符串里的 // 」和「真注释里的 // 」，一视同仁地删会把
    `@PostMapping("/create")` 里的路径一起删掉 → 反而解析不出方法。
    所以这里用**逐字符状态机**：先认字符串，再认注释。
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        # 字符串字面量（含转义）：原样保留
        if c == '"' or c == "'":
            quote = c
            out.append(c)
            i += 1
            while i < n:
                if text[i] == "\\":
                    out.append(text[i:i + 2])
                    i += 2
                    continue
                out.append(text[i])
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        # 块注释
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            out.append(" ")
            continue
        # 行注释
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            out.append(" ")
            continue
        out.append(c)
        i += 1
    return "".join(out)


MAPPING_RE = re.compile(
    r"@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?\"([^\"]*)\"\s*\)\s*"
    r"(?:public\s+)?([A-Za-z_][\w<>,.\[\]\s]*?)\s+([A-Za-z_]\w*)\s*\(",
    re.S,
)
FEIGN_RE = re.compile(
    r"@FeignClient\(\s*name\s*=\s*\"([^\"]+)\"(?:[^)]*?path\s*=\s*\"([^\"]*)\")?[^)]*\)",
    re.S,
)


def norm(path):
    """归一化路径：占位符统一成 {}"""
    parts = []
    for seg in (path or "").strip("/").split("/"):
        if not seg:
            continue
        if (seg.startswith("{") and seg.endswith("}")) or seg.startswith(":"):
            seg = "{}"
        parts.append(seg)
    return "/" + "/".join(parts)


def parse_controller_endpoints(module_dir):
    """返回 {模块: {(HTTP方法, 归一化路径): 返回类型}}"""
    out = {}
    java_root = module_dir / "src/main/java"
    if not java_root.exists():
        return out
    for java in java_root.rglob("*.java"):
        text = _strip_java_comments(java.read_text(encoding="utf-8", errors="ignore"))
        if "@RestController" not in text and "@Controller" not in text:
            continue
        cls = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"', text)
        prefix = cls.group(1).strip() if cls else ""
        for m in MAPPING_RE.finditer(text):
            http = m.group(1).upper()
            sub = m.group(2).strip()
            ret = " ".join(m.group(3).split())
            full = norm((prefix + "/" + sub) if sub else prefix)
            out[(http, full)] = ret
    return out


def unwrapped(ret):
    """Result<X> / ResponseEntity<X> → X；裸类型 → 原样"""
    r = ret.strip()
    for wrapper in ("Result", "ResponseEntity"):
        m = re.match(rf"^{wrapper}\s*<\s*(.+?)\s*>$", r)
        if m:
            return m.group(1).strip(), wrapper
    return r, None


# 📌 **数字型与 String 的互转不算契约不一致** ——
#
#    本项目的 ID 出口约定：**所有 ID 在 HTTP 层都是字符串**
#    （雪花 ID 19 位，JS Number 承载不了 → Result.successId() 把 Long 转成 String）。
#    而 Feign 客户端是 **Java ↔ Java**，Java 的 Long 没有精度问题，
#    所以声明成 `Result<Long>` 是**故意**的：
#    - 后端 `Result.successId(orderId)` → JSON `{"data":"2117546519450398722"}`
#    - Feign 声明 `Result<Long>` → Jackson 把 JSON 字符串**隐式转成 Long**
#    - 这一步是**精确**的（Long 能装下 19 位），有真实解码测试证明：
#      erp-platform-service/.../OrderServiceClientDecodeTest.java
#
#    📌 **所以 `Result<String>` vs `Result<Long>` 不是 bug，而是本项目的既定契约。**
#    闸门如果把它报成「泛型不一致」→ 就是**误报**。
#    ⚠️ 误报比漏报更危险：闸门一旦学会误报，真正的问题就会被一起忽略
#       （本项目实测：就是因为这条误报，差点把一处正确的 Feign 声明改成错的）。
NUMERIC_TYPES = {"long", "int", "integer", "short", "byte", "bigdecimal", "double", "float"}


def _is_numeric(t):
    return t.strip().lower() in NUMERIC_TYPES


def _coercion_pair(a, b):
    """a、b 是否属于「数字 ↔ 字符串」这一对可互转的类型。"""
    a, b = a.strip(), b.strip()
    if a.lower() == "string" and _is_numeric(b):
        return True
    if b.lower() == "string" and _is_numeric(a):
        return True
    return False



def _check_return(problems, rel, service, hit, name, feign_ret=None):
    """检查 Feign 声明的返回类型与后端实际返回是否对得上"""
    backend_path, backend_ret = hit
    if feign_ret is None:
        return
    feign_inner, feign_wrapper = unwrapped(feign_ret)
    back_inner, back_wrapper = unwrapped(backend_ret)
    if back_wrapper and not feign_wrapper:
        problems.append((rel, service, f"返回值 @ {backend_path}", name,
                         f"后端返回 {back_wrapper}<{back_inner}>，"
                         f"Feign 声明的是裸 {feign_inner} → "
                         f"会把 {back_wrapper} 外壳反序列化成字段全空的对象"))
    elif back_wrapper and feign_wrapper and feign_inner != back_inner:
        # 📌 数字 ↔ String 的隐式互转**不算不一致**（见 _coercion_pair 的说明）
        if not _coercion_pair(feign_inner, back_inner):
            problems.append((rel, service, f"返回值 @ {backend_path}", name,
                             f"泛型不一致：后端 {back_wrapper}<{back_inner}> "
                             f"vs Feign {feign_wrapper}<{feign_inner}>"))


def main():
    strict = "--strict" in sys.argv

    feigns = []
    for java in sorted(ROOT.glob("erp-*/src/main/java/**/*.java")):
        text = _strip_java_comments(java.read_text(encoding="utf-8", errors="ignore"))
        fm = FEIGN_RE.search(text)
        if not fm:
            continue
        service = fm.group(1)
        base = fm.group(2) or ""
        methods = []
        for m in MAPPING_RE.finditer(text):
            methods.append((m.group(1).upper(), m.group(2).strip(),
                            " ".join(m.group(3).split()), m.group(4)))
        feigns.append((java, service, base, methods))

    print("=" * 78)
    print("Feign 契约巡检：Feign 方法 ↔ 目标服务 Controller 映射")
    print("=" * 78)

    if not feigns:
        print("\n❌ 一个 @FeignClient 都没解析出来 —— 脚本本身可能坏了（不是「零问题」）")
        return 2

    problems = []
    total_methods = 0
    for java, service, base, methods in feigns:
        module_dir = ROOT / service
        rel = java.relative_to(ROOT)
        if not module_dir.is_dir():
            problems.append((rel, service, "-", "-", f"目标服务模块不存在：{service}"))
            continue
        endpoints = parse_controller_endpoints(module_dir)
        if not endpoints:
            problems.append((rel, service, "-", "-",
                             f"目标服务 {service} 里没有解析到任何 Controller 接口"))
            continue

        for http, sub, ret, name in methods:
            total_methods += 1
            full = norm((base or "") + "/" + sub if sub else (base or ""))
            hit = None
            loose = None
            for (eh, ep), eret in endpoints.items():
                if eh != http:
                    continue
                if ep == full or ep.endswith(full) or full.endswith(ep):
                    hit = (ep, eret)
                    break
                # 兜底：路径对不上时，按「最后一段相同」找一个，
                # 这样同一次运行里还能把**返回值包装**的问题一起报出来
                if loose is None and ep.split("/")[-1] == full.split("/")[-1]:
                    loose = (ep, eret)
            if not hit:
                problems.append((rel, service, f"{http} {full}", name,
                                 "目标服务没有这个映射（404）"))
                # 路径错了也把返回值包装的问题一起报出来（一次跑完所有问题）
                if loose:
                    _check_return(problems, rel, service, loose, name, ret)
                continue

            backend_path, backend_ret = hit
            _check_return(problems, rel, service, (backend_path, backend_ret), name, ret)

    print(f"Feign 客户端 {len(feigns)} 个 | 方法 {total_methods} 个")

    if not problems:
        print("\n✅ 所有 Feign 调用的路径和返回值包装都对得上")
        return 0

    print(f"\n❌ 发现 {len(problems)} 处契约不一致:\n")
    for rel, service, path, name, why in problems:
        print(f"  · {rel}")
        print(f"      目标服务: {service} | {path} | {name}")
        print(f"      问题: {why}")
        print()
    print("📌 这些问题单测查不出来 —— Feign 在单测里是 mock 的。")

    if strict:
        print(f"\n退出码 1：{len(problems)} 处契约不一致")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
