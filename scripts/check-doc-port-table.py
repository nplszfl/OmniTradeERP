#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档里的端口表 —— 必须与 `application.yml` **同源且完整**（规则 33 的文档侧）。

📌 **为什么需要这道闸门**：

规则 33 说「**端口唯一事实来源 = `application.yml`** —— 文档/编排层不要手抄端口号」，
而现有的 `check-port-consistency.py` 查了 **4 类部署描述**：
compose / k8s / Dockerfile /（application.yml 自身唯一性）
→ **唯独不查文档**。

📌 **于是文档里的手抄表就漂了**（实测 2026-11-10）：
`docs/Architecture.md` 的「核心模块」表只列了 **12** 个服务，
而仓库里实际有 **23** 个 → **漏了 11 个**（customer / inventory-alert / purchase / reporting /
tenant / social-commerce / analytics / supplier / logistics / anomaly-detection / product-recommendation）。

⚠️ **危险的不是「缺」，是「看起来对」**：
那 12 行的端口号**全都正确** → 读文档的人会以为这就是全部
→ 新人以为「加个服务不用更新文档」→ **缺口越滚越大**。

📌 **判据**：

| 断言 | 内容 |
|---|---|
| **A** | 文档里形如 `| <模块名> | <端口> |` 的表格行，**端口必须等于**该模块 `application.yml` 的 `server.port` |
| **B** | **每个**有 `server.port` 的模块，都**必须出现在**文档的端口表里（不许漏） |

📌 **为什么 B 要单列，而不是只看 A**：
只查 A 的话，**「漏掉的服务」根本不会触发任何一行** → 闸门全绿（规则 54 的镜像：
「逐条断言都成立」≠「结论是完整的」）。
→ 必须能核验**覆盖面**，而不只是核验每一条。

📌 **怎么定位「文档的端口表」**：
只认 `docs/*.md` 与 `README.md` 里的 **Markdown 表格行**（以 `|` 开头、含模块名 + 数字端口）。
→ 判据**窄**，避免误伤散文里的数字（规则 93 的教训：判据太宽 → 假红）。

📌 **豁免**：历史快照类文档（文件名含 `FIX-` / `REPORT` / 日期）**不查** ——
它们记录的是**当时的状态**，不是现状（规则 93：简称/历史快照要放过）。

📌 **反向验证**（规则 40 / 75）：
① 把 `erp-user-service` 的端口在文档里改成 9999 → A 报红；
② 删掉文档里 `erp-logistics-service` 那一行 → B 报红。

用法：
    python3 scripts/check-doc-port-table.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-doc-port-table.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", ".idea"}

# 📌 **历史快照类文档不查**（规则 93：历史快照要放过）
SNAPSHOT_RE = re.compile(r"(FIX-|REPORT|SNAPSHOT|COMPLETION|STATUS)", re.I)

# 表格行：`| erp-xxx-service | 8080 | 说明 |`
TABLE_ROW_RE = re.compile(
    r"^\s*\|\s*`?(erp-[a-z0-9\-]+)`?\s*\|\s*`?(\d{4,5})`?\s*\|", re.M
)
PORT_RE = re.compile(r"^\s*port:\s*(\d{4,5})\s*$", re.M)


def service_ports():
    """{模块名: 端口} —— 唯一事实来源 = 各模块 application.yml 的 server.port。"""
    out = {}
    for d in sorted(os.listdir(REPO_ROOT)):
        if not d.startswith("erp-"):
            continue
        for sub in ("src/main/resources",):
            p = os.path.join(REPO_ROOT, d, sub, "application.yml")
            if not os.path.exists(p):
                continue
            text = open(p, encoding="utf-8", errors="replace").read()
            m = PORT_RE.search(text)
            if m:
                out[d] = m.group(1)
    return out


def doc_files():
    out = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if not dirpath.startswith(os.path.join(REPO_ROOT, "docs")):
            continue
        for fn in filenames:
            if fn.endswith(".md"):
                out.append(os.path.join(dirpath, fn))
    readme = os.path.join(REPO_ROOT, "README.md")
    if os.path.exists(readme):
        out.append(readme)
    return sorted(out)


def main() -> int:
    verbose = "--verbose" in sys.argv
    ports = service_ports()
    if not ports:
        print("❌ 读不到任何服务的 server.port —— 结构变了？本闸门会变成瞎的")
        return 1

    findings = []
    doc_ports = {}   # 模块 -> 文档里写的端口（跨文档合并）
    docs_checked = 0

    for path in doc_files():
        rel = os.path.relpath(path, REPO_ROOT)
        if SNAPSHOT_RE.search(os.path.basename(rel)):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()
        rows = TABLE_ROW_RE.findall(text)
        if not rows:
            continue
        docs_checked += 1
        for module, port in rows:
            doc_ports.setdefault(module, set()).add((rel, port))

    # ── A：文档里的端口必须与 application.yml 一致 ────────────────
    for module, entries in sorted(doc_ports.items()):
        if module not in ports:
            # 📌 **C：文档提到磁盘上不存在的服务目录**（规则 100 的双向版）
            #    原来这里是 `continue`（静默跳过）→ 文档写 `erp-ghost-service | 8888`
            #    闸门**纹丝不动** → 假绿。而「照着文档找服务」的人会**找不到目录**。
            #    ⚠️ 只对**文档表格里的服务行**判 —— 不对散文里的名字判（判据要窄）。
            for rel, _port in sorted(entries):
                if not os.path.isdir(os.path.join(REPO_ROOT, module)):
                    findings.append(
                        f"[C] `{rel}` 里列了服务 `{module}`，但仓库里**没有这个目录**"
                        f" → 文档提到了**不存在的东西**"
                        f"（规则 100：等价写法必须一起认；这里反过来 —— **不存在的东西也必须报出来**）"
                    )
            continue  # 不是服务模块 → 不查端口
        for rel, port in sorted(entries):
            if port != ports[module]:
                findings.append(
                    f"[A] `{rel}` 里 `{module}` 写的端口是 **{port}**，"
                    f"但 `{module}/src/main/resources/application.yml` 里是 **{ports[module]}**"
                    f" → 文档端口**已漂移**（规则 33：唯一事实来源 = application.yml）"
                )

    # ── B：每个服务都必须在文档端口表里出现 ──────────────────────
    missing = sorted(set(ports) - set(doc_ports))
    if missing:
        for m in missing:
            findings.append(
                f"[B] `{m}`（端口 {ports[m]}）**没有出现在任何文档的端口表里**"
                f" → 文档里的表是**手抄的**，已经漂移"
                f"\n     ⚠️ 那张表里已列的端口**全都正确** → 读的人会以为它就是全部"
                f" → 缺口会越滚越大（规则 33 / 54）"
            )

    if findings:
        print("❌ 文档端口表闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 规则 33：**端口唯一事实来源 = `application.yml`** —— 文档不要手抄端口号。\n"
            "   危险的不是「写错」，是「**看起来对**」：\n"
            "   表里已列的端口全对 → 读的人以为那就是全部 → 缺口越滚越大。"
        )
        return 1

    if verbose:
        print("检查了 %d 个含端口表的文档" % docs_checked)
    print("✅ 文档端口表：%d 个服务的端口与 application.yml 完全一致，且**无遗漏**、**无幽灵服务**（A/B/C）" % len(ports))
    return 0


if __name__ == "__main__":
    sys.exit(main())
