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
        text = java.read_text(encoding="utf-8", errors="ignore")
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
        problems.append((rel, service, f"返回值 @ {backend_path}", name,
                         f"泛型不一致：后端 {back_wrapper}<{back_inner}> "
                         f"vs Feign {feign_wrapper}<{feign_inner}>"))


def main():
    strict = "--strict" in sys.argv

    feigns = []
    for java in sorted(ROOT.glob("erp-*/src/main/java/**/*.java")):
        text = java.read_text(encoding="utf-8", errors="ignore")
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
