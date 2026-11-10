#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""现状文档里的**可校验数字**必须与事实一致（规则 51 的文档侧）。

📌 **为什么需要这道闸门**：

规则 51 说「**手写结论会静默过期** —— 报告里的『重点结论』如果是手写的，
它说错话时**没有任何闸门会发现**（脚本只算『哪些接口是死的』，不检查『我关于这些接口的结论还对不对』）」。

本项目的 `check-stat-source-consistency` 查的是**代码内部**的统计口径（报条数的接口 vs 列数据的接口），
**不查文档里的数字** → 文档里的手写数字**没人守**。

📌 **本项目实测（2026-11-10 17:15）—— `docs/Deployment.md` 的 compose 服务数表**：

| 文件 | 文档写的 | 实际 | |
|---|---|---|---|
| `docker-compose.full.yml` | 16 | 16 | ✅ |
| `docker-compose.minimal.yml` | 7 | 7 | ✅ |
| `docker-compose.yml` | 30 | 30 | ✅ |
| **`docker-compose.prod.yml`** | **13** | **9** | ❌ **漂了 4** |
| 「仓库里有 N 个 compose 文件」 | **8** | **9** | ❌ **漂了 1** |

→ **没有任何闸门会发现** → 读文档的人会以为生产环境有 13 个服务，实际只有 9 个
→ **部署时才发现少服务** → 「按文档配的部署起不来」。

📌 **判据（极窄）**：

| 断言 | 内容 |
|---|---|
| **A** | 文档里形如 `\| \`docker-compose.xxx.yml\` \| N \|` 的表格行，N 必须等于该文件 `services:` 的**实际数量** |
| **B** | 文档里「仓库里有 N 个 compose 文件」，N 必须等于磁盘上 `docker-compose*.yml` 的**实际数量** |

📌 **为什么判据必须极窄**（规则 93 / 99）：
- 只认 **`docker-compose*.yml` + 表格行 + 紧跟的数字**这**一种形态**
- **不查**散文里的其它数字（服务总数、接口数…）—— 那些要么是历史快照，要么口径各异
  → 泛扫 = 假红 = 逼人放松闸门
- **豁免**历史快照类文档（文件名含 `FIX-` / `REPORT` / `SNAPSHOT`）—— 它们记录**当时的状态**

📌 **反向验证**（规则 40 / 75）：
① 把文档里 `prod.yml` 的 13 改成 14 → A 报红；
② 把「8 个 compose 文件」改成 7 → B 报红。

用法：
    python3 scripts/check-doc-compose-counts.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-doc-compose-counts.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build", ".idea"}

SNAPSHOT_RE = re.compile(r"(FIX-|REPORT|SNAPSHOT|COMPLETION|STATUS)", re.I)

# 表格行：| `docker-compose.xxx.yml` | 16 | ... |
TABLE_ROW_RE = re.compile(
    r"^\s*\|\s*`?((?:docker-compose[\w\-.]*\.yml)(?:\s*/\s*`?[\w\-.]*\.yml`?)*)`?\s*\|\s*(\d+)\s*\|",
    re.M,
)
# 散文：「仓库里有 8 个 compose 文件」
COUNT_FILES_RE = re.compile(r"(\d+)\s*个\s*compose\s*文件")


def compose_service_count(path):
    """读 compose 的 services 数量。

    📌 **优先用 PyYAML**（CI 的两个 workflow 都装了 pyyaml）→ 最准。
    📌 **纯文本兜底**（本地没 pyyaml 时）：只认 **2 空格缩进**的 `name:`，
       且必须落在 `services:` 块内（缩进 2）——
       ⚠️ **不能用「下一个顶格 key」来切块**：\`services:\` 常常就在文件末尾，
          而块内的 \`  mysql:\` 也是 2 空格缩进 → 会把块**立刻截断** → 全部算成 1（实测踩过）。
    """
    try:
        import yaml  # noqa: PLC0415
        d = yaml.safe_load(open(path, encoding="utf-8"))
        svcs = (d or {}).get("services") or {}
        if isinstance(svcs, dict) and svcs:
            return len(svcs)
    except Exception:
        pass  # 走纯文本兜底

    text = open(path, encoding="utf-8", errors="replace").read()
    m = re.search(r"^services:\s*$", text, re.M)
    if not m:
        return None
    names = []
    for line in text[m.end():].splitlines():
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        if indent == 0:
            break  # 顶格 key → services 块结束
        if indent == 2:
            km = re.match(r"^  ([A-Za-z_][\w\-]*):\s*$", line)
            if km and not km.group(1).startswith("<<"):
                names.append(km.group(1))
    return len(names) if names else None


def doc_files():
    out = []
    docs_dir = os.path.join(REPO_ROOT, "docs")
    for fn in sorted(os.listdir(docs_dir)):
        if fn.endswith(".md"):
            out.append(os.path.join(docs_dir, fn))
    readme = os.path.join(REPO_ROOT, "README.md")
    if os.path.exists(readme):
        out.append(readme)
    return out


def main() -> int:
    verbose = "--verbose" in sys.argv

    compose_files = sorted(
        fn for fn in os.listdir(REPO_ROOT)
        if fn.startswith("docker-compose") and fn.endswith(".yml")
    )
    if not compose_files:
        print("❌ 找不到 docker-compose*.yml —— 本闸门会变成瞎的")
        return 1
    actual_counts = {}
    for fn in compose_files:
        n = compose_service_count(os.path.join(REPO_ROOT, fn))
        if n is not None:
            actual_counts[fn] = n

    findings = []
    checked_rows = 0

    for path in doc_files():
        rel = os.path.relpath(path, REPO_ROOT)
        if SNAPSHOT_RE.search(os.path.basename(rel)):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()

        # ── A：表格行里的 compose 服务数 ─────────────────────────
        for m in TABLE_ROW_RE.finditer(text):
            raw = m.group(1)
            claimed = int(m.group(2))
            # 表格里可能一行列多个文件（`a.yml` / `b.yml`）→ 只有**单个**文件才判
            files = re.findall(r"(docker-compose[\w\-.]*\.yml)", raw)
            if len(files) != 1:
                continue  # 多文件合一行 / 或只是列举 → 不判（判据要窄）
            fn = files[0]
            if fn not in actual_counts:
                continue
            checked_rows += 1
            real = actual_counts[fn]
            if real != claimed:
                line = text[:m.start()].count("\n") + 1
                findings.append(
                    f"[A] `{rel}:{line}` 写 `{fn}` 有 **{claimed}** 个服务，"
                    f"但实际 `services:` 里有 **{real}** 个"
                    f" → **文档数字已漂**（规则 51：手写结论会静默过期）"
                )

        # ── B：「N 个 compose 文件」 ─────────────────────────────
        for m in COUNT_FILES_RE.finditer(text):
            claimed = int(m.group(1))
            checked_rows += 1
            real = len(compose_files)
            if real != claimed:
                line = text[:m.start()].count("\n") + 1
                findings.append(
                    f"[B] `{rel}:{line}` 说「**{claimed}** 个 compose 文件」，"
                    f"但磁盘上实际有 **{real}** 个"
                    f" → **文档数字已漂**（规则 51）"
                )

    if findings:
        print("❌ 文档数字一致性闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 规则 51：**手写结论会静默过期** —— 脚本只算事实，不检查『我关于这些事实的结论还对不对』。\n"
            "   后果：按文档配的部署**起不来**（文档说 13 个服务，实际只有 9 个）。"
        )
        return 1

    if verbose:
        for fn in compose_files:
            print(f"  {fn}: {actual_counts.get(fn, '?')} 个服务")
    print("✅ 文档数字：%d 处 compose 相关声明与实际完全一致（A/B）" % checked_rows)
    return 0


if __name__ == "__main__":
    sys.exit(main())
