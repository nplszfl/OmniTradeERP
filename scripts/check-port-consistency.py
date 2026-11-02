#!/usr/bin/env python3
"""端口一致性巡检：application.yml 是唯一事实来源，其余部署描述必须与之一致。

检查项：
1. application.yml 里的 server.port 全局唯一
2. docker-compose*.yml：
   - 每个应用服务的宿主端口 == 容器端口 == 该服务 application.yml 的 server.port
   - 同一份 compose 内宿主端口不重复
   - healthcheck 里的 localhost:<port> == 服务端口
   - build.args.MODULE 指向真实存在的模块目录，build.dockerfile 指向真实存在的文件
3. k8s/*.yaml：containerPort / 探针端口 / Service port+targetPort 全部等于服务端口
4. docker/Dockerfile.*：EXPOSE 与 healthcheck 端口等于对应模块的 server.port

用法：python3 scripts/check-port-consistency.py
退出码 0 = 全绿，1 = 有不一致
"""
import os
import re
import sys
import glob

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# compose 服务名 → Maven 模块目录
SERVICE_MODULE = {
    "gateway": "erp-gateway",
    "user-service": "erp-user-service",
    "product-service": "erp-product-service",
    "order-service": "erp-order-service",
    "platform-service": "erp-platform-service",
    "customer-service": "erp-customer-service",
    "inventory-service": "erp-inventory-service",
    "inventory-alert-service": "erp-inventory-alert-service",
    "inventory-prediction-service": "erp-inventory-prediction-service",
    "warehouse-service": "erp-warehouse-service",
    "finance-service": "erp-finance-service",
    "pricing-service": "erp-pricing-service",
    "ai-assistant-service": "erp-ai-assistant-service",
    "product-description-service": "erp-product-description-service",
    "social-commerce-service": "erp-social-commerce-service",
    "analytics-service": "erp-analytics-service",
    "reporting-service": "erp-reporting-service",
    "purchase-service": "erp-purchase-service",
    "tenant-service": "erp-tenant-service",
    "anomaly-detection-service": "erp-anomaly-detection-service",
    "product-recommendation-service": "erp-product-recommendation-service",
    # ⚠️ 这两个之前漏了 → 它们的端口映射/build.args 一直没被校验。
    #    补上之后如果端口写错，脚本会报出来。
    "logistics-service": "erp-logistics-service",
    "supplier-service": "erp-supplier-service",
}

# k8s / docker 文件里的服务名 → 模块目录
K8S_SERVICE_MODULE = {
    "erp-gateway": "erp-gateway",
    "erp-user-service": "erp-user-service",
    "erp-product-service": "erp-product-service",
    "erp-order-service": "erp-order-service",
    "erp-platform-service": "erp-platform-service",
    "erp-customer-service": "erp-customer-service",
    "erp-inventory-service": "erp-inventory-service",
    "erp-inventory-alert-service": "erp-inventory-alert-service",
    "erp-inventory-prediction-service": "erp-inventory-prediction-service",
    "erp-warehouse-service": "erp-warehouse-service",
    "erp-finance-service": "erp-finance-service",
    "erp-pricing-service": "erp-pricing-service",
    "erp-ai-assistant-service": "erp-ai-assistant-service",
    "erp-product-description-service": "erp-product-description-service",
    "erp-social-commerce-service": "erp-social-commerce-service",
    "erp-analytics-service": "erp-analytics-service",
    "erp-reporting-service": "erp-reporting-service",
    "erp-purchase-service": "erp-purchase-service",
    "erp-tenant-service": "erp-tenant-service",
    "erp-anomaly-detection-service": "erp-anomaly-detection-service",
    "erp-product-recommendation-service": "erp-product-recommendation-service",
    # ⚠️ 之前漏了 → erp-logistics-service 的 containerPort / 探针 / Service 端口
    #    从来没被校验过（k8s/core-services.yaml 里确实有它的 Deployment + Service）。
    "erp-logistics-service": "erp-logistics-service",
}

# docker/Dockerfile.<x> → 模块目录
DOCKERFILE_MODULE = {
    "Dockerfile.gateway": "erp-gateway",
    "Dockerfile.order": "erp-order-service",
    "Dockerfile.pricing": "erp-pricing-service",
    "Dockerfile.customer": "erp-customer-service",
    "Dockerfile.reporting": "erp-reporting-service",
    "Dockerfile.purchase": "erp-purchase-service",
    "Dockerfile.inventory-prediction": "erp-inventory-prediction-service",
    "Dockerfile.ai-assistant": "erp-ai-assistant-service",
    "Dockerfile.product-description": "erp-product-description-service",
    "Dockerfile.social-commerce": "erp-social-commerce-service",
}

errors = []
warns = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warns.append(msg)


def module_port(module):
    """读模块 application.yml 的 server.port。"""
    path = os.path.join(ROOT, module, "src", "main", "resources", "application.yml")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        docs = [d for d in yaml.safe_load_all(f) if isinstance(d, dict)]
    # 多文档 yml 里 server.port 可能出现在任一文档，取第一个带 server.port 的
    for data in docs:
        port = ((data.get("server") or {}).get("port"))
        if port is not None:
            return int(port)
    return None


def load_ports():
    ports = {}
    for module in sorted(os.listdir(ROOT)):
        if not module.startswith("erp-"):
            continue
        p = module_port(module)
        if p is not None:
            ports[module] = p
    return ports


def check_unique(ports):
    seen = {}
    for module, port in ports.items():
        if port in seen:
            err(f"[app] 端口冲突：{module} 与 {seen[port]} 都是 {port}")
        else:
            seen[port] = module


def check_compose(ports):
    for path in sorted(glob.glob(os.path.join(ROOT, "docker-compose*.yml"))):
        name = os.path.basename(path)
        with open(path, encoding="utf-8") as f:
            try:
                doc = yaml.safe_load(f)
            except yaml.YAMLError as e:
                err(f"[{name}] YAML 解析失败：{e}")
                continue
        services = (doc or {}).get("services") or {}
        host_ports = {}
        for svc, cfg in services.items():
            cfg = cfg or {}
            module = SERVICE_MODULE.get(svc)
            if module is None:
                continue
            want = ports.get(module)
            if want is None:
                err(f"[{name}] {svc} 对应模块 {module} 找不到 application.yml")
                continue
            # build.args
            build = cfg.get("build") or {}
            args = build.get("args") or {}
            if args.get("MODULE") and args["MODULE"] != module:
                err(f"[{name}] {svc} build.args.MODULE={args['MODULE']}，应为 {module}")
            if args.get("PORT") and int(args["PORT"]) != want:
                err(f"[{name}] {svc} build.args.PORT={args['PORT']}，应为 {want}")
            df = build.get("dockerfile")
            if df and not os.path.exists(os.path.join(ROOT, df)):
                err(f"[{name}] {svc} dockerfile {df} 不存在")
            # ports
            for mapping in cfg.get("ports") or []:
                if not isinstance(mapping, str) or ":" not in mapping:
                    continue
                host, _, container = mapping.partition(":")
                if not host.isdigit():
                    continue
                if int(host) != want or int(container) != want:
                    err(f"[{name}] {svc} 端口映射 {mapping}，应为 \"{want}:{want}\"")
                if int(host) in host_ports:
                    err(f"[{name}] 宿主端口 {host} 重复：{host_ports[int(host)]} 与 {svc}")
                else:
                    host_ports[int(host)] = svc
            # healthcheck
            hc = (cfg.get("healthcheck") or {}).get("test")
            if isinstance(hc, list):
                for item in hc:
                    if isinstance(item, str) and "localhost:" in item:
                        m = re.search(r"localhost:(\d+)", item)
                        if m and int(m.group(1)) != want:
                            err(f"[{name}] {svc} healthcheck 端口 {m.group(1)}，应为 {want}")
                        if "actactuator" in item:
                            err(f"[{name}] {svc} healthcheck 路径拼写错误：actactuator")
            elif isinstance(hc, str) and "localhost:" in hc:
                m = re.search(r"localhost:(\d+)", hc)
                if m and int(m.group(1)) != want:
                    err(f"[{name}] {svc} healthcheck 端口 {m.group(1)}，应为 {want}")


def check_k8s(ports):
    for path in sorted(glob.glob(os.path.join(ROOT, "k8s", "*.yaml"))):
        name = os.path.basename(path)
        with open(path, encoding="utf-8") as f:
            try:
                docs = [d for d in yaml.safe_load_all(f) if d]
            except yaml.YAMLError as e:
                err(f"[k8s/{name}] YAML 解析失败：{e}")
                continue
        for doc in docs:
            if not isinstance(doc, dict):
                continue
            kind = doc.get("kind")
            svc_name = (doc.get("metadata") or {}).get("name")
            module = K8S_SERVICE_MODULE.get(svc_name)
            if module is None:
                continue
            want = ports.get(module)
            if want is None:
                continue
            if kind == "Deployment":
                spec = (doc.get("spec") or {}).get("template") or {}
                pod = (spec.get("spec") or {})
                for c in pod.get("containers") or []:
                    for cp in c.get("ports") or []:
                        if int(cp.get("containerPort", 0)) != want:
                            err(f"[k8s/{name}] {svc_name} containerPort={cp.get('containerPort')}，应为 {want}")
                    for probe in ("livenessProbe", "readinessProbe", "startupProbe"):
                        hp = ((c.get(probe) or {}).get("httpGet") or {}).get("port")
                        if hp is not None and int(hp) != want:
                            err(f"[k8s/{name}] {svc_name} {probe} 端口={hp}，应为 {want}")
            elif kind == "Service":
                for p in ((doc.get("spec") or {}).get("ports") or []):
                    if not isinstance(p, dict):
                        continue
                    if int(p.get("port", 0)) != want:
                        err(f"[k8s/{name}] {svc_name} Service port={p.get('port')}，应为 {want}")
                    tp = p.get("targetPort")
                    if tp is not None and int(tp) != want:
                        err(f"[k8s/{name}] {svc_name} Service targetPort={tp}，应为 {want}")


def check_dockerfiles(ports):
    ddir = os.path.join(ROOT, "docker")
    if not os.path.isdir(ddir):
        return
    for fname in sorted(os.listdir(ddir)):
        if not fname.startswith("Dockerfile."):
            continue
        module = DOCKERFILE_MODULE.get(fname)
        if module is None:
            continue
        want = ports.get(module)
        if want is None:
            continue
        text = open(os.path.join(ddir, fname), encoding="utf-8").read()
        for m in re.finditer(r"^EXPOSE\s+(\d+)", text, re.M):
            if int(m.group(1)) != want:
                err(f"[docker/{fname}] EXPOSE {m.group(1)}，应为 {want}")
        for m in re.finditer(r"localhost:(\d+)", text):
            if int(m.group(1)) != want:
                err(f"[docker/{fname}] healthcheck 端口 {m.group(1)}，应为 {want}")


def check_docs(ports):
    """文档里的端口表必须与 application.yml 一致。"""
    alias = {}
    for mod in ports:
        alias[mod] = mod
        alias[mod.replace("erp-", "")] = mod
        alias["erp-test-" + mod.replace("erp-", "")] = mod
    # 中文/英文服务名 → 模块，覆盖 “- User Service (端口: 8081)” 这类写法
    name_alias = {
        "Gateway": "erp-gateway",
        "User Service": "erp-user-service",
        "Product Service": "erp-product-service",
        "Order Service": "erp-order-service",
        "Platform Service": "erp-platform-service",
        "Customer Service": "erp-customer-service",
        "Inventory Service": "erp-inventory-service",
        "Inventory Alert Service": "erp-inventory-alert-service",
        "Inventory Prediction Service": "erp-inventory-prediction-service",
        "Warehouse Service": "erp-warehouse-service",
        "Finance Service": "erp-finance-service",
        "Pricing Service": "erp-pricing-service",
        "AI Assistant Service": "erp-ai-assistant-service",
        "Product Description Service": "erp-product-description-service",
        "Social Commerce Service": "erp-social-commerce-service",
        "Analytics Service": "erp-analytics-service",
        "Reporting Service": "erp-reporting-service",
        "Purchase Service": "erp-purchase-service",
        "Tenant Service": "erp-tenant-service",
        "网关": "erp-gateway",
        "用户": "erp-user-service",
        "商品": "erp-product-service",
        "订单": "erp-order-service",
        "平台": "erp-platform-service",
        "客户": "erp-customer-service",
        "库存": "erp-inventory-service",
        "仓库": "erp-warehouse-service",
        "财务": "erp-finance-service",
        "智能定价": "erp-pricing-service",
        "库存预测": "erp-inventory-prediction-service",
        "AI 客服": "erp-ai-assistant-service",
        "产品描述": "erp-product-description-service",
        "报表": "erp-reporting-service",
        "采购": "erp-purchase-service",
        "租户": "erp-tenant-service",
    }
    name_re = re.compile(r"([A-Za-z][A-Za-z ]{2,30}?Service|Gateway)\s*[(（]?\s*端口[:：]\s*(\d{4})")
    cn_re = re.compile(r"([\u4e00-\u9fffA-Za-z ]{2,12}?)\s*[(（]?\s*端口[:：]\s*(\d{4})")
    doc_files = []
    for base in (ROOT, os.path.join(ROOT, "docs"), os.path.join(ROOT, "k8s")):
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base)):
            if name.endswith(".md"):
                doc_files.append(os.path.join(base, name))
    for module in sorted(os.listdir(ROOT)):
        p = os.path.join(ROOT, module, "README.md")
        if module.startswith("erp-") and os.path.exists(p):
            doc_files.append(p)
    for path in doc_files:
        rel = os.path.relpath(path, ROOT)
        with open(path, encoding="utf-8") as f:
            for i, line in enumerate(f, 1):
                # 形如 “| erp-xxx-service | 8080 | ... |” 或 “erp-xxx-service (:8080)”
                toks = {alias[t] for t in re.findall(r"erp-[a-z0-9-]+", line) if t in alias}
                if len(toks) == 1:
                    nums = [int(m.group(1)) for m in re.finditer(r"(?<![\d.])(\d{4})(?![\d.])", line)]
                    nums = [n for n in nums if 8000 <= n <= 8999]
                    if len(nums) == 1:
                        mod = toks.pop()
                        want = ports[mod]
                        if nums[0] != want:
                            err(f"[doc/{rel}:{i}] {mod} 写了 {nums[0]}，应为 {want}")
                # 形如 “- User Service (端口: 8081)”
                for m in list(name_re.finditer(line)) + list(cn_re.finditer(line)):
                    key = m.group(1).strip()
                    mod = name_alias.get(key)
                    if not mod or mod not in ports:
                        continue
                    got = int(m.group(2))
                    want = ports[mod]
                    if got != want:
                        err(f"[doc/{rel}:{i}] {key} 写了 {got}，应为 {want}")


def report_coverage(ports):
    """📌 报告「哪些东西没被检查到」。

    这个函数存在的原因：本脚本历史上多次出现「映射表漏了一个服务 →
    它的端口从未被校验，但报告仍然显示『检查通过』」。
    静默跳过比报错更危险：巡检要能报出「我什么都没查到」。
    """
    uncovered = []

    # 1. 有 application.yml 的模块，必须都出现在 compose 映射表里
    for module in sorted(ports):
        if module not in SERVICE_MODULE.values():
            uncovered.append(f"模块 {module} 不在 compose 映射表 SERVICE_MODULE 里 → compose 端口未校验")

    # 2. compose 里的服务（排除基础设施）必须有模块映射
    infra = {"mysql", "redis", "rabbitmq", "nacos", "sentinel", "certbot",
             "frontend", "nginx", "erp-network"}
    for path in sorted(glob.glob(os.path.join(ROOT, "docker-compose*.yml"))):
        name = os.path.basename(path)
        try:
            doc = yaml.safe_load(open(path, encoding="utf-8"))
        except Exception:
            continue
        for svc in ((doc or {}).get("services") or {}):
            if svc in infra or svc in SERVICE_MODULE:
                continue
            if svc.endswith("-data") or svc.endswith("-logs"):
                continue
            uncovered.append(f"{name} 的服务 {svc} 不在 SERVICE_MODULE 里 → 端口未校验")

    # 3. k8s 里的应用 Service（排除基础设施）必须有模块映射
    k8s_infra = {"erp-ingress", "mysql-service", "nacos-service",
                 "redis-service", "rabbitmq-service", "sentinel-service"}
    for path in sorted(glob.glob(os.path.join(ROOT, "k8s", "*.yaml"))):
        name = os.path.basename(path)
        try:
            docs = list(yaml.safe_load_all(open(path, encoding="utf-8")))
        except Exception:
            continue
        for doc in docs:
            if not isinstance(doc, dict) or doc.get("kind") != "Service":
                continue
            svc = (doc.get("metadata") or {}).get("name")
            if not svc or svc in k8s_infra or svc in K8S_SERVICE_MODULE:
                continue
            if svc == "erp-gateway-service":
                continue  # gateway 的别名 Service，与 erp-gateway 重复
            uncovered.append(f"k8s/{name} 的 Service {svc} 不在 K8S_SERVICE_MODULE 里 → 端口未校验")

    for u in uncovered:
        warn(u)
    if uncovered:
        warn(f"共 {len(uncovered)} 处「未被校验」的条目 —— 请补映射表，否则这些端口错了也不会被发现")
    return uncovered


def main():
    ports = load_ports()
    if not ports:
        err("没读到任何服务的 server.port")
    check_unique(ports)
    check_compose(ports)
    check_k8s(ports)
    check_dockerfiles(ports)
    check_docs(ports)
    report_coverage(ports)

    print(f"服务端口表（application.yml 为准，共 {len(ports)} 个服务）：")
    for module, port in sorted(ports.items(), key=lambda kv: kv[1]):
        print(f"  {port}  {module}")
    print()
    for w in warns:
        print(f"WARN  {w}")
    for e in errors:
        print(f"ERROR {e}")
    if errors:
        print(f"\n✗ {len(errors)} 处端口不一致")
        return 1
    print("✓ 端口一致性检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
