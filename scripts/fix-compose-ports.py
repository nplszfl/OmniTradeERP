#!/usr/bin/env python3
"""把 docker-compose*.yml 的服务定义统一到「application.yml 为唯一事实来源」的端口约定。

做三件事（逐行解析，保留注释与排版）：
1. build.dockerfile → docker/Dockerfile.service，并把 build.args 规范成 MODULE/PORT
   （已有 args 块就整体替换，避免出现重复 key）
2. ports 映射 → "<app_port>:<app_port>"
3. healthcheck 里的 localhost:<port> → app_port（顺带修 "actactuator" 拼写错误）

用法：python3 scripts/fix-compose-ports.py [--dry-run]
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# compose 服务名 → (Maven 模块目录, server.port)
CANON = {
    "gateway":                      ("erp-gateway", 8080),
    "user-service":                 ("erp-user-service", 8084),
    "product-service":              ("erp-product-service", 8083),
    "order-service":                ("erp-order-service", 8081),
    "platform-service":             ("erp-platform-service", 8082),
    "customer-service":             ("erp-customer-service", 8085),
    "inventory-service":            ("erp-inventory-service", 8094),
    "inventory-alert-service":      ("erp-inventory-alert-service", 8086),
    "inventory-prediction-service": ("erp-inventory-prediction-service", 8088),
    "warehouse-service":            ("erp-warehouse-service", 8092),
    "finance-service":              ("erp-finance-service", 8090),
    "pricing-service":              ("erp-pricing-service", 8087),
    "ai-assistant-service":         ("erp-ai-assistant-service", 8089),
    "product-description-service":  ("erp-product-description-service", 8091),
    "social-commerce-service":      ("erp-social-commerce-service", 8096),
    "analytics-service":            ("erp-analytics-service", 8097),
    "reporting-service":            ("erp-reporting-service", 8098),
    "purchase-service":             ("erp-purchase-service", 8093),
    "tenant-service":               ("erp-tenant-service", 8095),
    "anomaly-detection-service":    ("erp-anomaly-detection-service", 8138),
    "product-recommendation-service": ("erp-product-recommendation-service", 8139),
}

SERVICE_RE = re.compile(r"^  ([A-Za-z0-9_.-]+):\s*$")
KEY_RE = re.compile(r"^(\s+)([A-Za-z0-9_.-]+):(\s.*)?$")
PORTMAP_RE = re.compile(r'^(\s*-\s*)"?(\d+):(\d+)"?\s*$')


def indent_of(line):
    return len(line) - len(line.lstrip(" "))


def rewrite(text):
    lines = text.split("\n")
    out = []
    cur_service = None
    # build 块状态：记录 build 块的 key 及其缩进
    i = 0
    n = len(lines)
    changed = False

    while i < n:
        line = lines[i]

        m = SERVICE_RE.match(line)
        if m:
            cur_service = m.group(1)
            out.append(line)
            i += 1
            continue

        svc = CANON.get(cur_service)

        # ---- build 块处理 ----
        if svc and line.strip() == "build:":
            module, port = svc
            build_indent = indent_of(line)
            out.append(line)
            i += 1
            # 收集 build 块的所有子行
            block = []
            while i < n:
                nxt = lines[i]
                if nxt.strip() and indent_of(nxt) <= build_indent:
                    break
                block.append(nxt)
                i += 1
            new_block = transform_build(block, module, port)
            if new_block != block:
                changed = True
            out.extend(new_block)
            continue

        # ---- ports 列表处理 ----
        if svc and line.strip() == "ports:":
            module, port = svc
            ports_indent = indent_of(line)
            out.append(line)
            i += 1
            while i < n:
                nxt = lines[i]
                if nxt.strip() and indent_of(nxt) <= ports_indent:
                    break
                m2 = PORTMAP_RE.match(nxt)
                if m2:
                    out.append(f'{m2.group(1)}"{port}:{port}"')
                    changed = True
                else:
                    out.append(nxt)
                i += 1
            continue

        # ---- healthcheck 端口 ----
        if svc and "localhost:" in line and "actuator/health" in line:
            module, port = svc
            new = re.sub(r"localhost:\d+/", f"localhost:{port}/", line)
            new = new.replace("actactuator", "actuator")
            if new != line:
                changed = True
            out.append(new)
            i += 1
            continue

        out.append(line)
        i += 1

    return "\n".join(out), changed


def transform_build(block, module, port):
    """build 子块：dockerfile 指向通用镜像，args 规范成 MODULE/PORT（整块替换，不重复 key）。"""
    result = []
    i = 0
    n = len(block)
    saw_dockerfile = False
    while i < n:
        line = block[i]
        stripped = line.strip()
        m = KEY_RE.match(line)
        if m and m.group(2) == "dockerfile":
            indent = m.group(1)
            if not saw_dockerfile:
                result.append(f"{indent}dockerfile: docker/Dockerfile.service")
                saw_dockerfile = True
            # 跳过原有 dockerfile 行
            i += 1
            continue
        if m and m.group(2) == "args":
            indent = m.group(1)
            # 跳过整个旧 args 块，稍后统一插入新的
            i += 1
            while i < n:
                nxt = block[i]
                if nxt.strip() and indent_of(nxt) <= len(indent):
                    break
                i += 1
            continue
        result.append(line)
        i += 1

    # 在 build 块最后插入 args（dockerfile 已改为通用镜像）
    if saw_dockerfile:
        # 找到 dockerfile 行的缩进
        for idx, ln in enumerate(result):
            if ln.strip().startswith("dockerfile:"):
                indent = ln[: len(ln) - len(ln.lstrip(" "))]
                result[idx + 1: idx + 1] = [
                    f"{indent}args:",
                    f"{indent}  MODULE: {module}",
                    f'{indent}  PORT: "{port}"',
                ]
                break
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    files = sorted(f for f in os.listdir(ROOT)
                   if f.startswith("docker-compose") and f.endswith(".yml"))
    for name in files:
        path = os.path.join(ROOT, name)
        src = open(path, encoding="utf-8").read()
        dst, changed = rewrite(src)
        if not changed:
            print(f"skip   {name} (already consistent)")
            continue
        if args.dry_run:
            print(f"would change {name}")
        else:
            open(path, "w", encoding="utf-8").write(dst)
            print(f"updated {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
