#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从各模块 `application.yml` **生成**文档里的服务端口表（规则 33 的正解）。

📌 **为什么要有这个脚本**：

规则 33：**端口唯一事实来源 = `application.yml`** —— 文档/编排层**不要手抄**端口号。
现实是：文档里的表**全是手抄的** → 一定漂移。

⚠️ **本项目实测（2026-11-10）**：`docs/Architecture.md` 的「核心模块」表只列了 **12** 个服务，
实际有 **23** 个 → **漏了 11 个**（customer / inventory-alert / purchase / reporting / tenant /
social-commerce / analytics / supplier / logistics / anomaly-detection / product-recommendation）。
→ 那 12 行的端口**全都正确** → **看起来完全没问题** → 这才是最危险的形态。

📌 **正解不是「再手抄一次」**（那只是把漂移推迟到下次），而是**让文档表格由脚本生成**：

    python3 scripts/gen-doc-port-table.py          # 打印生成的表格
    python3 scripts/gen-doc-port-table.py --write  # 写回 docs/Architecture.md

⚠️ **为什么生成器必须和校验器并存**：
`check-doc-port-table.py` 负责「**发现漂移**」，本脚本负责「**修复漂移**」。
→ 两个都用「同一份事实来源」（`application.yml`）→ 生成的和校验的**天然一致**。

📌 **生成表的稳定性**：
按**端口升序**排列（而不是目录顺序）→ 端口变了，表里的**行顺序不变**，diff 干净。

用法：
    python3 scripts/gen-doc-port-table.py           # 打印
    python3 scripts/gen-doc-port-table.py --write   # 写回 Architecture.md
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PORT_RE = re.compile(r"^\s*port:\s*(\d{4,5})\s*$", re.M)

# 服务的简短说明（人工维护，只用于**可读性**，不影响正确性）
DESCRIPTIONS = {
    "erp-gateway": "API网关",
    "erp-order-service": "订单管理",
    "erp-platform-service": "平台对接",
    "erp-product-service": "商品管理",
    "erp-user-service": "用户/权限",
    "erp-customer-service": "客户管理",
    "erp-inventory-alert-service": "库存预警",
    "erp-pricing-service": "智能定价(AI)",
    "erp-inventory-prediction-service": "库存预测(AI)",
    "erp-ai-assistant-service": "AI客服(AI)",
    "erp-finance-service": "财务管理",
    "erp-product-description-service": "产品描述(AI)",
    "erp-warehouse-service": "仓库管理",
    "erp-purchase-service": "采购管理",
    "erp-inventory-service": "库存管理",
    "erp-tenant-service": "多租户",
    "erp-social-commerce-service": "社交电商",
    "erp-analytics-service": "数据分析(AI)",
    "erp-reporting-service": "报表导出",
    "erp-supplier-service": "供应商管理",
    "erp-logistics-service": "物流管理",
    "erp-anomaly-detection-service": "异常检测(AI)",
    "erp-product-recommendation-service": "商品推荐(AI)",
}

MARKER_START = "<!-- PORT_TABLE:START -->"
MARKER_END = "<!-- PORT_TABLE:END -->"
TARGET = os.path.join(REPO_ROOT, "docs", "Architecture.md")


def service_ports():
    out = {}
    for d in sorted(os.listdir(REPO_ROOT)):
        if not d.startswith("erp-"):
            continue
        p = os.path.join(REPO_ROOT, d, "src", "main", "resources", "application.yml")
        if not os.path.exists(p):
            continue
        text = open(p, encoding="utf-8", errors="replace").read()
        m = PORT_RE.search(text)
        if m:
            out[d] = m.group(1)
    return out


def render_table(ports):
    lines = [
        "| 服务 | 端口 | 说明 |",
        "|------|------|------|",
    ]
    for module, port in sorted(ports.items(), key=lambda kv: int(kv[1])):
        desc = DESCRIPTIONS.get(module, "")
        lines.append(f"| {module} | {port} | {desc} |")
    return "\n".join(lines)


def main() -> int:
    ports = service_ports()
    if not ports:
        print("❌ 读不到任何服务的 server.port")
        return 1
    table = render_table(ports)

    if "--write" not in sys.argv:
        print(table)
        print(f"\n（{len(ports)} 个服务）加 --write 可写回 {os.path.relpath(TARGET, REPO_ROOT)}")
        return 0

    if not os.path.exists(TARGET):
        print(f"❌ 找不到 {TARGET}")
        return 1
    text = open(TARGET, encoding="utf-8").read()
    if MARKER_START not in text or MARKER_END not in text:
        print(
            f"❌ `{TARGET}` 里找不到标记 {MARKER_START} / {MARKER_END}\n"
            f"   → 请先在「核心模块」章节的表格位置加上这两行标记，再重跑。"
        )
        return 1

    new = re.sub(
        re.escape(MARKER_START) + r".*?" + re.escape(MARKER_END),
        MARKER_START + "\n" + table + "\n" + MARKER_END,
        text,
        flags=re.S,
    )
    if new == text:
        print("✅ 表格已是最新，无需改动")
        return 0
    open(TARGET, "w", encoding="utf-8").write(new)
    print(f"✅ 已更新 {os.path.relpath(TARGET, REPO_ROOT)}（{len(ports)} 个服务）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
