#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 67 道闸门：用户可见文档里的**死链/假承诺**。

📌 **为什么需要这道闸门**（2026-11-13 11:15 实测踩出来的真缺陷）：

README 曾经写着：

    [🖥️ 在线预览](http://erp.demo.com) · [📖 详细文档](https://docs.omnitradeerp.com)

→ **两个域名都没有 DNS 记录**（`dig +short` 无 A 记录）→ **点开就是死链**；
→ 而 `erp.demo.com` **不是我们拥有的域名** → 点开会走到**别人家**去。
→ 同期 GitHub issue #15「老哥，你的连接打不开了」——**用户在反馈的就是这个死链**。
📌 根因：那两个链接是 `d997f18 docs: 优化README提升收藏吸引力` 加进去的
→ **为了好看加的，不是为了能用加的**。

📌 **同类缺陷已有先例**（同一族的「宣称与实际不符」）：
    规则 126：对外声称的 AI/ML 能力必须有真实依赖兜底
    规则 146：「CI 双绿」≠「系统已部署」
    → 本闸门管的是**文档里的链接版本**。

📌 **判据（两条，都不依赖网络 → 不会 flaky）**：

| 断言 | 内容 |
|---|---|
| **C1** | 用户可见文档里的 **markdown 链接**（`[文字](url)`）→ 其 host 必须 ∈ `docs/.allowed-domains`，或指向**仓库内相对路径**。**我们不拥有的地址不许对外承诺**。|
| **C2** | **保留/占位域名直接禁**（RFC 2606 / RFC 6761）：`example.com|.net|.org`、`*.example`、`*.test`、`*.invalid`、`*.localhost`、`*.local`、`yourdomain.com`、`mydomain.com`…… → **即使加进白名单也报红**（那是占位符，不是承诺）。|
| **C3** | **白名单域名下的「死路径」也要禁**（2026-11-13 14:40 补）—— host 在白名单里**不等于页面存在**：`github.com` 在白名单 → `.../discussions` 一律放行 → **假绿**；而本仓库 `has_discussions=false` → 该端点 **HTTP 410 Gone**。✅ 路径黑名单 = `docs/.disabled-endpoints`。|
| **C4** | **仓库内相对链接必须真的指向存在的文件**（2026-11-13 21:20 补）—— **GitHub 不会自动补 `.md`**：`[快速开始](Quick-Start)` → 打开 `docs/Quick-Start` → **HTTP 404**（实测）；必须写 `Quick-Start.md` 才 200。📌 实测：文档里 **26 条相对链接有 23 条在 GitHub 上是 404** → 死链。✅ 判据 = 目标文件**精确存在**（GitHub 语义，不自动补扩展名）。|

📌 **为什么不查 DNS**：
    DNS 会**网络抖动** → 闸门时红时绿 → **假红会逼下一个人去「放松闸门」**（规则 99）。
    ✅ 静态判据 + 人工维护的**权威白名单**（`docs/.allowed-domains`，改动要在 PR 里被看见）。
    📌 白名单是**「我们承诺的地址」**这个事实的权威来源 —— 不是抄一份散落在文档里的列表（规则 33）。

📌 **只看 markdown 链接，不看正文里的裸 URL**：
    `docs/SECURITY.md` 里的 `https://erp.example.com/api/...` 是**攻击示例的 curl 命令**
    （故意用保留域名占位）→ **不是对外承诺** → 不该报红。
    📌 同理：**代码块**内的内容一律不判。

📌 **已知边界（诚实记录，不藏）**：
    - 只判 **markdown 链接语法**（`[文字](url)`）→ 裸 URL / HTML 里的 href / 引用块里的链接**不判**。
    - 扫**根目录所有 `*.md` + `docs/*.md`**（2026-11-13 14:40 从「只有 README.md」扩过来 ——
      原范围漏掉了 `CONTRIBUTING.md`，而它**恰好就有一个 404 死链**）；**模块 README 仍不在范围**
      （当前实测它们没有外部链接）。
    - **不检查 DNS**（见上）→ 白名单里的域名**是否真的活着**，靠人工在加白名单时确认。
    - **禁用端点是人工维护的路径黑名单** → 仓库**新禁用**某个端点时，**要手动加一行**到
      `docs/.disabled-endpoints`（本闸门**不会自动发现**新的 410）。
    - **不检查 git 历史**。
    - **C4 只判「看起来像路径」的相对链接**（`./` / `../` 开头，或含 `/` 或带已知文档扩展名）→
      纯锚点（`#section`）与 `mailto:` 不判。

用法：
    python3 scripts/check-doc-links.py
退出码：0 = 通过；1 = 发现问题。
"""

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 用户可见文档的范围（📌 模块 README 不在范围，见「已知边界」）
DOC_GLOBS = ["*.md", "docs/*.md"]

ALLOWLIST_PATH = os.path.join(REPO_ROOT, "docs", ".allowed-domains")
DISABLED_PATHS_FILE = os.path.join(REPO_ROOT, "docs", ".disabled-endpoints")

# ── RFC 2606 / 6761 保留域名 + 通用占位词 ──────────────────────────────
#    📌 这些**永远不可能**是真实对外承诺 → 直接禁，加白名单也不放过（防止有人图省事）。
RESERVED_HOST_PATTERNS = [
    r"^.*\.example$",         # foo.example
    r"^example$",
    r"^example\.(com|net|org)$",
    r"^.*\.test$",            # foo.test
    r"^.*\.invalid$",         # foo.invalid
    r"^.*\.localhost$",
    r"^.*\.local$",
    r"^(your|my|our)[-_.]?(domain|site|host|company)[-_.]?(com|net|org|cn|io)?$",
    r"^.*\.(yourdomain|mydomain|mysite)\.(com|net|org)$",
    r"^x{3,}\.(com|net|org)$",      # xxx.com / xxxx.com
    r"^demo\.(com|net|org)$",        # demo.com —— 真实注册但**不是我们的**
    r"^.*-demo\.(com|net|org)$",
    r"^sample\.(com|net|org)$",
    r"^placeholder\.(com|net|org)$",
]

# markdown 链接：[文字](url)  —— 只取 http(s) 的
MD_LINK = re.compile(r"\[[^\]\n]*\]\(\s*<?(https?://[^\s)>]+)>?(?:\s+[\"'][^\"']*[\"'])?\s*\)")
# 📌 **C4 用的相对链接正则**（2026-11-13 21:30 补）——
#    ⚠️ **上面那个 MD_LINK 只匹配 `https?://`** → **相对链接根本不会被解析到**
#    → 就算判据写对了也永远不会触发（规则 55 的第 7 个实例：判据写了，但压根没在读那类代码）。
#    → 所以要**单独**一条正则：排除 scheme 与 mailto，其余都算相对链接。
MD_LINK_REL = re.compile(
    r"\[[^\]\n]*\]\(\s*<?((?!https?://|mailto:)[^)\s>]+)>?(?:\s+[\"'][^\"']*[\"'])?\s*\)"
)


def load_allowlist():
    """读白名单（每行一个 host，# 之后是备注）。"""
    hosts = set()
    if not os.path.exists(ALLOWLIST_PATH):
        return hosts
    with open(ALLOWLIST_PATH, encoding="utf-8") as f:
        for line in f:
            line = line.split("#", 1)[0].strip().lower()
            if not line:
                continue
            hosts.add(line)
    return hosts


def load_disabled_paths():
    """读 `docs/.disabled-endpoints` —— 白名单管不到的**死路径**黑名单。

    📌 **为什么需要它（2026-11-13 14:40 实测踩出来的盲区）**：
    白名单只管 **host** → `github.com` 在白名单里 → `.../discussions` 这种
    **白名单域名下的死路径**一律放行 → **假绿**。
    而本仓库 `has_discussions=false` → 该端点返回 **410 Gone** → 链接点开就是死的。
    ✅ 所以：**路径**也要查 —— host 合法 ≠ 页面存在。
    """
    out = []
    try:
        with open(DISABLED_PATHS_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.split("#", 1)[0].strip()
                if line:
                    out.append(line)
    except FileNotFoundError:
        return out
    return out


def user_visible_docs():
    out = []
    for pattern in DOC_GLOBS:
        if "*" in pattern:
            import glob
            base = os.path.join(REPO_ROOT, os.path.dirname(pattern))
            name = os.path.basename(pattern)
            for p in sorted(glob.glob(os.path.join(base, name))):
                out.append(os.path.relpath(p, REPO_ROOT))
        else:
            p = os.path.join(REPO_ROOT, pattern)
            if os.path.exists(p):
                out.append(pattern)
    return out


def strip_code_blocks(text):
    """去掉围栏代码块（``` ... ```）与行内代码 → 里面的链接不是对外承诺。"""
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"`[^`\n]*`", "", text)
    return text


def host_of(url):
    m = re.match(r"https?://([^/:?#]+)", url, re.I)
    return m.group(1).lower() if m else None


def is_reserved(host):
    for pat in RESERVED_HOST_PATTERNS:
        if re.match(pat, host):
            return True
    return False


# ── C4：仓库内相对链接必须真的指向存在的文件 ────────────────────────────
#    📌 **GitHub 不会自动补 `.md`**（实测 2026-11-13 21:20）：
#       `https://github.com/<o>/<r>/blob/master/docs/Quick-Start`      → **HTTP 404**
#       `https://github.com/<o>/<r>/blob/master/docs/Quick-Start.md`   → HTTP 200
#    → 所以 wiki 风格的**无扩展名**相对链接在 GitHub 上**全是死链**。
#    📌 之前 C1 只说「或指向仓库内相对路径」→ **只查 host，不管文件在不在** → 假绿。
#    📌 **判据用「精确存在」**（GitHub 语义）：目录也算存在（GitHub 会渲染目录页）。
#    📌 **「看起来像路径」的判断要**包含**无扩展名的 wiki 风格链接** ——
#       那**正是**要抓的缺陷形态（`[快速开始](Quick-Start)`）→ 早先用「必须含 `/` 或带扩展名」
#       来筛 → **恰好把要抓的形态全漏掉** → 闸门假绿（规则 55：判据只认一种写法 = 双向失明）。
#    📌 唯一豁免：GitHub 的**路由型**相对链接（`issues/15`、`pull/7`、`wiki/...`）→ 它们不是文件引用。
GITHUB_ROUTE_PREFIXES = ("issues/", "pull/", "wiki/", "blob/", "tree/", "releases/", "actions/", "labels/")
DOC_EXT_RE = re.compile(r"\.(md|markdown|png|jpe?g|gif|svg|pdf|txt|ya?ml|json|html?)$", re.I)


def looks_like_relative_path(url: str) -> bool:
    """这条相对链接**指向仓库里的某个东西**吗？（纯锚点 / mailto / GitHub 路由不算）"""
    if not url or url.startswith("#"):
        return False
    if "://" in url or url.startswith("mailto:"):
        return False
    path_part = url.split("?", 1)[0].split("#", 1)[0]
    if not path_part:
        return False
    if path_part.startswith(GITHUB_ROUTE_PREFIXES):
        return False
    return True


def relative_target_exists(doc_rel: str, url: str) -> bool:
    """相对链接的目标文件是否存在（相对**该文档所在目录**）。"""
    path_part = url.split("?", 1)[0].split("#", 1)[0]
    if not path_part:
        return True  # 纯锚点
    base_dir = os.path.dirname(os.path.join(REPO_ROOT, doc_rel))
    target = os.path.normpath(os.path.join(base_dir, path_part))
    # 防止 `../` 逃出仓库
    if not target.startswith(os.path.abspath(REPO_ROOT)):
        return True
    return os.path.exists(target)


def main() -> int:
    allow = load_allowlist()
    disabled = load_disabled_paths()
    docs = user_visible_docs()
    if not docs:
        print("ℹ️  取不到用户可见文档 → 本闸门无法工作（不是「通过」）")
        return 1

    findings = []
    total_links = 0
    for rel in docs:
        path = os.path.join(REPO_ROOT, rel)
        with open(path, encoding="utf-8") as f:
            raw = f.read()
        for lineno, line in enumerate(raw.split("\n"), 1):
            # 逐行判：围栏代码块内的行跳过
            if line.lstrip().startswith("```"):
                continue
            clean = re.sub(r"`[^`\n]*`", "", line)
            # ── C4：相对链接（MD_LINK 只匹配 http(s) → 相对链接要单独一条正则）
            for m in MD_LINK_REL.finditer(clean):
                url = m.group(1)
                if not looks_like_relative_path(url):
                    continue
                total_links += 1
                if not relative_target_exists(rel, url):
                    findings.append((rel, lineno, "(相对链接)", url,
                                     "相对链接指向的文件**不存在** → GitHub 上点开就是 404"
                                     "（**GitHub 不会自动补 `.md`**）"))
            for m in MD_LINK.finditer(clean):
                url = m.group(1)
                host = host_of(url)
                if not host:
                    # ── C4：相对链接 → 目标文件必须存在 ──
                    if looks_like_relative_path(url) and not relative_target_exists(rel, url):
                        total_links += 1
                        findings.append((rel, lineno, "(相对链接)", url,
                                         "相对链接指向的文件**不存在** → GitHub 上点开就是 404"
                                         "（**GitHub 不会自动补 `.md`**）"))
                    continue
                total_links += 1
                if is_reserved(host):
                    findings.append((rel, lineno, host, url,
                                     "保留/占位域名 → 永远不可能是真实承诺（加进白名单也不放行）"))
                elif host not in allow:
                    findings.append((rel, lineno, host, url,
                                     "不在 `docs/.allowed-domains` → 我们不拥有这个地址，不许对外承诺"))
                else:
                    # host 在白名单里 → **但路径可能仍然是死的**（410/404）
                    upath = url.split("?", 1)[0].split("#", 1)[0]
                    for bad in disabled:
                        if upath.endswith(bad) or bad in upath:
                            findings.append((rel, lineno, host, url,
                                             f"路径命中 `docs/.disabled-endpoints`（`{bad}`）→ "
                                             f"该端点对本仓库**不存在/已禁用** → 链接是死链"))
                            break

    print(f"ℹ️  扫了 {len(docs)} 个用户可见文档，{total_links} 个外部 markdown 链接")
    print(f"ℹ️  白名单（docs/.allowed-domains）= {len(allow)} 个域名")
    print(f"ℹ️  禁用端点（docs/.disabled-endpoints）= {len(disabled)} 条路径")

    if not findings:
        print("✅ 用户可见文档里的外部链接都在白名单内，且没有保留/占位域名/禁用端点/死相对链接")
        return 0

    print(f"❌ 发现 {len(findings)} 处对外承诺了「不存在 / 不属于我们」的地址：")
    for rel, lineno, host, url, why in findings:
        print(f"   {rel}:{lineno} [{host}]")
        print(f"        {url}")
        print(f"        → {why}")
    print()
    print("✅ 修法：")
    print("   ① 换成**仓库内相对路径**（如 `docs/Home.md`）→ 永远有效")
    print("   ② 真要用外部域名 → 先确认**它真的能解析**（`dig +short <host>` 有 A 记录），")
    print("      再加进 `docs/.allowed-domains`（PR 里能看见）")
    print("   ③ **不许**用保留/占位域名（example.com / *.example / demo.com / xxx.com）")
    print("   ④ **相对链接必须带 `.md` 扩展名** → GitHub 不会自动补 → 无扩展名 = 404")
    return 1


if __name__ == "__main__":
    sys.exit(main())
