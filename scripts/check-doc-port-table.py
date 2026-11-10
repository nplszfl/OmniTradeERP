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
| **D** | 有 `application.yml` 却**读不出** `server.port` 的模块 → **直接报红**（闸门对它失明 = 假绿） |

📌 **C 的覆盖面与边界**（写清楚，免得下一个人以为它管全部）：
- ✅ **所有 Markdown 表格**（不论表头是不是「端口」）里提到的 `erp-*` 服务 → **目录必须存在**
- ❌ **散文**里提到的服务名 → **不查**（判据要窄，规则 93 / 99）
- ❌ **脱离表格的裸行**（`| erp-xxx | 8888 |` 前后有空行）→ **不查**
  （它**不是表格的一部分**，语义上不算「声明」；markdown 也不会把它渲染成表格）
  ⚠️ 这是**有意为之**的边界，不是漏网 → 写在这里是为了让它可审计。

📌 **端口怎么读（这一段本身就是「不对称判据」的教训，规则 55 / 100）**：

2026-11-10 19:20 实测，原来的正则判据 `^\s*port:\s*(\d{4,5})\s*$` + `search()` 有**三个盲区**：

| 盲区 | 写法 | 后果 |
|---|---|---|
| **① 不限定在 `server:` 块内** | `spring.mail.port: 465` 出现在 `server:` **之前** | 取到**邮件端口**当服务端口 → 判据完全错位 |
| **② 只认 4-5 位裸数字** | `port: "8080"` / `port: ${SERVER_PORT:8080}` / `port: 808` | 该服务**从闸门视野里消失** → 文档写错也没人管 |
| **③ 多文档 YAML 只取第一个** | `application.yml` 用 `---` 分段（仓库里有 **3** 个：`anomaly-detection` / `product-recommendation` / `supplier`） | Spring Boot 里**后面的文档会覆盖前面的** → 只读第一个 = 可能读错 |

→ 现在的读法：**PyYAML `safe_load_all` 逐文档读**，`server.port` **以最后一个文档为准**（与 Spring Boot 一致），
  纯文本兜底**限定在顶层 `server:` 块内**，且**认引号 / 占位符默认值 / 1-5 位数字**。
  → 读不出来 → 报 **D**（不许静默省略，那会让该服务「消失」）。

📌 **为什么 B 要单列，而不是只看 A**：
只查 A 的话，**「漏掉的服务」根本不会触发任何一行** → 闸门全绿（规则 54 的镜像：
「逐条断言都成立」≠「结论是完整的」）。
→ 必须能核验**覆盖面**，而不只是核验每一条。

📌 **怎么定位「文档的端口表」**：
**按表头定位** —— 表头那一列必须**恰好**叫「端口」（`端口`/`port`/`端口号`/`服务端口`），
**不认**「旧端口」「新端口」这类 → 再找该列里形如 `erp-xxx-service` 的行。

📌 **为什么不能按「行形状」认**（实测踩过，2026-11-10 19:30）：
第一版用「`| erp-xxx | 数字 |`」的行形状去认 → `docs/CUSTOMER-RESPONSE-SHAPE.md` 里的
**接口数量表**（`| erp-user-service | 26 | 5 | 12 | 9 |`）被当成了端口表
→ **19 处假红** → 会逼下一个人去**放松闸门**（规则 99：假阳性比假阴性危险）。
→ 正解：**表头说了算**（语义定位，不是形状猜）。

📌 **豁免**：历史快照类文档（文件名含 `FIX-` / `REPORT` / 日期）**不查** ——
它们记录的是**当时的状态**，不是现状（规则 93：简称/历史快照要放过）。

📌 **反向验证**（规则 40 / 75）：
① 把 `erp-user-service` 的端口在文档里改成 9999 → A 报红；
② 删掉文档里 `erp-logistics-service` 那一行 → B 报红；
③ 文档里写一个不存在的服务名 → C 报红；
④ 把 `server.port` 写成 `"8080"` / 放到**第二个 YAML 文档**里 → 仍能正确读出（D 不报红）；
⑤ `server.port` 写成读不出来的形式 → **D 报红**（不许静默省略）。

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
# 📌 **纯文本兜底**：只认**顶层 `server:` 块内**、缩进 2 的 `port:`（规则 55：判据必须匹配代码实际怎么写）
TEXT_SERVER_BLOCK_RE = re.compile(r"^server:\s*$", re.M)
TEXT_PORT_RE = re.compile(r"^  port:\s*[\"']?([^\"'\s]+)[\"']?\s*$", re.M)


def _normalize_port(value):
    """把 `server.port` 的各种写法归一成**数字字符串**；认不出来返回 None。

    📌 **等价写法必须一起认**（规则 100）：
    - `8080`（裸数字）
    - `"8080"` / `'8080'`（带引号）
    - `${SERVER_PORT:8080}`（占位符 → 取默认值）
    """
    if isinstance(value, bool):          # True/False 不是端口
        return None
    if isinstance(value, int):
        return str(value)
    if isinstance(value, str):
        s = value.strip()
        m = re.fullmatch(r"\$\{[^:}]+:(\d+)\}", s)   # ${SERVER_PORT:8080} → 8080
        if m:
            return m.group(1)
        if re.fullmatch(r"\d{1,5}", s):
            return s
    return None


def _port_from_text(text):
    """纯文本兜底：扫**所有**顶层 `server:` 块，取**最后**一个 port（与 Spring Boot 覆盖语义一致）。"""
    found = None
    lines = text.splitlines()
    in_server = False
    for line in lines:
        stripped = line.strip()
        if line[:1] not in (" ", "\t") and stripped:   # 顶格 key
            in_server = stripped.rstrip(":") == "server"
            continue
        if in_server:
            m = TEXT_PORT_RE.match(line)
            if m:
                p = _normalize_port(m.group(1))
                if p:
                    found = p
    return found


def service_ports():
    """({模块名: 端口}, [读不出端口的模块]) —— 唯一事实来源 = 各模块 application.yml 的 server.port。

    📌 **多文档 YAML**（`---` 分段）：Spring Boot 的 YamlPropertySourceLoader 会把
       **所有**文档都加载，**后面的覆盖前面的** → 所以取**最后一个**文档里的 server.port。
    """
    out = {}
    unreadable = []
    for d in sorted(os.listdir(REPO_ROOT)):
        if not d.startswith("erp-"):
            continue
        p = os.path.join(REPO_ROOT, d, "src/main/resources", "application.yml")
        if not os.path.exists(p):
            continue
        text = open(p, encoding="utf-8", errors="replace").read()
        port = None
        try:
            import yaml  # noqa: PLC0415
            for doc in yaml.safe_load_all(text):   # 多文档
                if not isinstance(doc, dict):
                    continue
                srv = doc.get("server")
                if isinstance(srv, dict) and "port" in srv:
                    p2 = _normalize_port(srv.get("port"))
                    if p2:
                        port = p2   # 后面的文档覆盖前面的
        except Exception:
            port = None
        if port is None:
            port = _port_from_text(text)
        if port:
            out[d] = port
        else:
            unreadable.append(d)
    return out, unreadable


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


# 📌 **端口表必须按「表头」定位**（规则 99：假阳性比假阴性危险）
#    —— 实测踩过（2026-11-10 19:30）：第一版用「`| erp-xxx | 数字 |`」的行形状去认，
#    结果 `docs/CUSTOMER-RESPONSE-SHAPE.md` 里的**接口数量表**（`| erp-user-service | 26 | 5 | 12 | 9 |`）
#    被当成了端口表 → **19 处假红** → 会逼下一个人去**放松闸门**（规则 99）。
#    → 正解：**表头里那一列必须恰好叫「端口」**（不含「旧端口」/「新端口」这类）。
PORT_HEADER_RE = re.compile(r"^(?:端口|port|端口号|服务端口|服务端口号)$", re.I)
MODULE_RE = re.compile(r"^erp-[a-z0-9\-]+$")
SEP_ROW_RE = re.compile(r"^:?-{2,}:?$")


def _split_row(line):
    """把一行 markdown 表格切成单元格。"""
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    return [c.strip() for c in s.split("|")]


def _iter_tables(text):
    """产出 markdown 表格块：[(表头单元格列表, 数据行列表)]。

    📌 只有「有表头 + 有分隔行」的块才算表（markdown 语法要求）→ 判据要窄。
    """
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if lines[i].lstrip().startswith("|"):
            j = i
            while j < len(lines) and lines[j].lstrip().startswith("|"):
                j += 1
            block = lines[i:j]
            if len(block) >= 2:
                header = _split_row(block[0])
                sep = _split_row(block[1])
                seps = [c for c in sep if c]
                if seps and all(SEP_ROW_RE.match(c) for c in seps):
                    yield header, [_split_row(r) for r in block[2:]]
            i = j
        else:
            i += 1


def _clean_cell(c):
    """去掉 markdown 装饰：`` ` `` / `` `*` `` / 空格。"""
    return re.sub(r"[`*]", "", c).strip()


def main() -> int:
    verbose = "--verbose" in sys.argv
    ports, unreadable = service_ports()
    if not ports:
        print("❌ 读不到任何服务的 server.port —— 结构变了？本闸门会变成瞎的")
        return 1

    findings = []
    # 📌 **D：读不出端口的服务 = 闸门对它失明** → 直接报红（不许静默省略，
    #    否则这个服务会从「必须出现在文档里」的视野里**消失**，规则 43 / 55）
    for m in unreadable:
        findings.append(
            f"[D] `{m}` 有 `application.yml` 却**读不出** `server.port`"
            f" → 该服务对这道闸门**失明**（文档里写错端口也没人管）"
            f" → 请把它的 `server.port` 写成裸数字或 `\"8080\"` 形式（规则 55：判据要匹配代码实际怎么写）"
        )
    doc_ports = {}   # 模块 -> {(相对路径, 端口字符串)}
    port_tables = 0
    docs_checked = 0
    doc_mentions = {}   # 模块 -> {相对路径}  ← 表格里**提到过**的服务（用于 C）

    for path in doc_files():
        rel = os.path.relpath(path, REPO_ROOT)
        if SNAPSHOT_RE.search(os.path.basename(rel)):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()
        for header, rows in _iter_tables(text):
            # 📌 **C 的覆盖面：任何表格里提到的服务目录都必须存在**
            #    （实测：全仓 45 条「首列是 erp-*」的行**零假红** → 判据不宽）
            for row in rows:
                for c in row:
                    cm = MODULE_RE.match(_clean_cell(c))
                    if cm:
                        doc_mentions.setdefault(cm.group(0), set()).add(rel)
            # 📌 **按表头定位「端口」那一列** —— 不靠数字位数去猜
            col = None
            for idx, h in enumerate(header):
                if PORT_HEADER_RE.match(_clean_cell(h)):
                    col = idx
                    break
            if col is None:
                continue
            port_tables += 1
            docs_checked += 1
            for row in rows:
                module = None
                for c in row:
                    cm = MODULE_RE.match(_clean_cell(c))
                    if cm:
                        module = cm.group(0)
                        break
                if not module:
                    continue
                if col >= len(row):
                    continue
                raw = _clean_cell(row[col])
                doc_ports.setdefault(module, set()).add((rel, raw))

    # 📌 **一张端口表都没找到 → 闸门彻底失明** → 直接报红（不许静默通过）
    if port_tables == 0:
        findings.append(
            "[D] **整个 docs 里找不到一张「端口」表**（表头那一列必须恰好叫「端口」）"
            " → 这道闸门**什么都查不了**却会报绿 → 请确认 `docs/Architecture.md` 的端口表还在"
        )

    # ── C：文档提到磁盘上不存在的服务目录 ────────────────────────
    # 📌 **C 单独跑，不依赖「是不是端口表」** ——
    #    否则在表格**外**多写一行 `| erp-ghost-service | 8888 |` 就会被漏掉
    #    （实测踩过：表头定位后，表格外的行不再被解析 → 幽灵服务静默通过）。
    for module, rels in sorted(doc_mentions.items()):
        if module in ports:
            continue
        for rel in sorted(rels):
            if not os.path.isdir(os.path.join(REPO_ROOT, module)):
                findings.append(
                    f"[C] `{rel}` 里提到了服务 `{module}`，但仓库里**没有这个目录**"
                    f" → 文档提到了**不存在的东西**"
                    f"（规则 100：等价写法必须一起认；这里反过来 —— **不存在的东西也必须报出来**）"
                )

    # ── A：文档里的端口必须与 application.yml 一致 ────────────────
    for module, entries in sorted(doc_ports.items()):
        if module not in ports:
            continue  # 非服务模块 → C 已经查过了
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
