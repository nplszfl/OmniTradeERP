#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 69 道闸门：含占位符的清单，不得被**原样** apply 到集群。

📌 **为什么需要这道闸门**（2026-11-14 03:50 实测踩出来的真缺陷）：

`k8s/gateway.yaml` 的 Ingress host 从写死的 `erp.example.com` 改成了**占位符**
`__INGRESS_HOST__`（写死保留域名 → 真实域名请求匹配不到规则 → **全站 404**，
而且 kubectl 不报错 → 部署照样打印「✅ 部署完成！」）。

`k8s/deploy.sh` 会把占位符**渲染**成真实域名再 apply —— 但 **CI 的 apply 路径没有**：
`.github/workflows/ci-cd.yml` 里原来直接 `kubectl apply -f k8s/gateway.yaml`
→ 占位符**原样进集群** → 真实域名的请求匹配不到 Ingress 规则 → **全站 404**。

📌 **同一缺陷的兄弟分支只修一条 = 白修**（规则 107）：
`deploy.sh`（人工路径）修好了，**CI（自动路径）没修** —— 两条路径通向同一个集群。

📌 **本闸门的判据**：

| 断言 | 内容 |
|---|---|
| **C1** | 含占位符的清单**不得**被**原样** apply（含字面量 `kubectl apply -f <清单>`，以及「`for f in <清单列表>` → `kubectl apply -f "$f"`」这种**引用不在同一行**的写法） |
| **C2** | 必须存在**渲染步骤**：用该清单生成另一个文件，并把**那个文件** apply 掉 |
| **C3** | 渲染用的值必须 **fail-closed** —— 不允许 `:-默认值` 兜底（默认值只能是保留域名 → 又把坑写回集群） |

📌 **为什么用 YAML 解析而不是 grep**（规则 96）：
`k8s/gateway.yaml` 里 `__INGRESS_HOST__` 出现在 **3 处**，其中 **2 处在注释里**
→ 纯 grep 会把注释当占位符 → 判据与事实不同源。
✅ 所以：**用 `yaml.safe_load_all` 解析后的【值】**来找占位符（注释天然被丢掉）。

📌 **C1 为什么要专门处理 `for f in` 循环**（规则 172 的同族）：
`kubectl apply -f "$f"` 里**根本看不到**清单路径 —— 路径在**上一行**的 `for` 列表里。
只 grep `kubectl apply -f <路径>` → **命中 0** → 假绿（这正是本次的原始缺陷）。

用法：
    python3 scripts/check-placeholder-manifest-apply.py
退出码：0 = 通过；1 = 发现问题。
"""

import os
import re
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S = os.path.join(ROOT, "k8s")
WORKFLOWS = os.path.join(ROOT, ".github", "workflows")

# 占位符形态：`__UPPER_SNAKE__`
PLACEHOLDER_RE = re.compile(r"__[A-Z][A-Z0-9_]*__")

# `for <var> in <列表>; do`
FOR_RE = re.compile(r"for\s+([A-Za-z_]\w*)\s+in\s+([^\n;]+);?\s*do")

# 字面量 apply：`kubectl apply -f <路径>`
APPLY_LITERAL_RE = re.compile(r"kubectl\s+apply\s+(?:-\S+\s+)*-f\s+([^\s\"']+)")

# apply 一个变量：`kubectl apply -f "$f"`
APPLY_VAR_RE = re.compile(r"kubectl\s+apply\s+(?:-\S+\s+)*-f\s+\"?\$\{?([A-Za-z_]\w*)\}?\"?")

# 渲染：读源清单 → 写目标文件
RENDER_RE = re.compile(r"(\S+\.(?:yaml|yml))\s+(\S+\.(?:yaml|yml))")

# 📌 渲染产物的形态：`.yaml` 文件名 **或** shell 变量（`$RENDERED` / `${RENDERED}`）
RENDER_TARGET_RE = re.compile(r"(\$\{?[A-Za-z_]\w*\}?|[A-Za-z0-9_.-]+\.ya?ml)")


def iter_yaml_files():
    if not os.path.isdir(K8S):
        return
    for fn in sorted(os.listdir(K8S)):
        if fn.endswith((".yaml", ".yml")):
            yield os.path.join(K8S, fn)


def placeholders_in_values(path):
    """返回该清单**解析后的值**里出现的占位符集合。

    📌 **只用 YAML 解析后的值** —— 注释里的占位符**不算**（规则 96）。
    📌 解析失败 → 返回 None（调用方要区别对待：解析失败 ≠ 没有占位符）。
    """
    try:
        with open(path, encoding="utf-8") as f:
            docs = list(yaml.safe_load_all(f))
    except (yaml.YAMLError, OSError):
        return None
    found = set()

    def walk(node):
        if isinstance(node, str):
            found.update(PLACEHOLDER_RE.findall(node))
        elif isinstance(node, dict):
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    for d in docs:
        walk(d)
    return found


def automation_files():
    """所有「自动化路径」的文件：CI workflow + k8s 下的 shell 脚本。"""
    out = []
    if os.path.isdir(WORKFLOWS):
        for fn in sorted(os.listdir(WORKFLOWS)):
            if fn.endswith((".yml", ".yaml")):
                out.append(os.path.join(WORKFLOWS, fn))
    if os.path.isdir(K8S):
        for fn in sorted(os.listdir(K8S)):
            if fn.endswith(".sh"):
                out.append(os.path.join(K8S, fn))
    return out


def rel(path):
    return os.path.relpath(path, ROOT)


def raw_applies_of(path, text):
    """该文件里把 `path` **原样** apply 的位置。

    📌 **两种形态都要覆盖**：
      ① 字面量：`kubectl apply -f k8s/gateway.yaml`
      ② **循环变量**：`for f in k8s/gateway.yaml ...; do` + `kubectl apply -f "$f"`
         —— 路径**不在 apply 那行**（规则 172 同族）→ 只查 ① 会**假绿**。
    """
    hits = []
    relpath = rel(path)
    base = os.path.basename(path)

    for i, line in enumerate(text.split("\n"), 1):
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        m = APPLY_LITERAL_RE.search(line)
        if m and (m.group(1).rstrip("/").endswith(relpath) or
                  m.group(1).rstrip("/").endswith(base)):
            hits.append((i, stripped))

    # 形态 ②：for 循环里的清单列表
    lines = text.split("\n")
    for i, line in enumerate(lines, 1):
        m = FOR_RE.search(line)
        if not m:
            continue
        var, listing = m.group(1), m.group(2)
        if not (relpath in listing or re.search(r"(^|\s)" + re.escape(base) + r"(\s|$)", listing)):
            continue
        # 循环体里是否原样 apply 了这个变量？
        body = "\n".join(lines[i:i + 25])
        am = APPLY_VAR_RE.search(body)
        if not am or am.group(1) != var:
            continue
        # 📌 **守卫例外**：如果循环体里**先跳过含占位符的清单**，就不算「原样 apply」。
        #    （`k8s/deploy.sh` 就是这么做的 —— 它会 skip 掉含占位符的清单。）
        if re.search(r"__\[A-Z_\]\*__|__\[A-Z_\]\+__|grep\s+-q\s+['\"]__", body) and \
           re.search(r"continue|skip|跳过", body):
            continue
        hits.append((i, stripped))
    return hits


def render_targets_of(path, text):
    """该文件里「用 `path` 渲染出另一个文件」的证据行。

    📌 **两种真实写法都要认**：
      ① `python3 - "$HOST" k8s/gateway.yaml gateway.rendered.yaml`   ← CI
      ② `python3 - "$HOST" k8s/gateway.yaml "$RENDERED"`             ← deploy.sh
    📌 **不能只认 ①**（规则 100）—— 只认一种 = 双向失明。
    📌 **不能把 `for f in <清单列表>` 误判成渲染** —— 那是**引用**，不是**生成**。
    """
    base = os.path.basename(path)
    relpath = rel(path)
    out = []
    for i, line in enumerate(text.split("\n"), 1):
        s = line.strip()
        if s.startswith("#"):
            continue
        if base not in line and relpath not in line:
            continue
        # 📌 `for ... in` 是**引用清单列表**，不是渲染
        if FOR_RE.search(line):
            continue
        # 📌 `kubectl apply -f <清单>` 是**用**，不是生成
        if "kubectl" in line and "apply" in line:
            continue
        # 清单之后出现的另一个「文件 token」= 渲染产物
        idx = min([p for p in (line.find(base), line.find(relpath)) if p >= 0] or [-1])
        if idx < 0:
            continue
        for mm in RENDER_TARGET_RE.finditer(line[idx:]):
            tok = mm.group(1)
            if tok in (base, relpath):
                continue
            out.append((i, s))
            break
    return out


def applied_artifacts(text):
    """该文件里 apply 的**渲染产物**（不是仓库里现成的清单）。

    📌 **为什么要区分**：渲染产物（如 `gateway.rendered.yaml` / `$RENDERED`）
    **不是仓库里的清单** → 它必须由**同一条路径**生成 → 否则 apply 的是不存在的文件。
    """
    out = []
    for i, line in enumerate(text.split("\n"), 1):
        s = line.strip()
        if s.startswith("#") or "kubectl" not in line or "apply" not in line:
            continue
        for mm in APPLY_LITERAL_RE.finditer(line):
            tok = mm.group(1).rstrip("/")
            if tok == "-" or tok.startswith("-"):
                continue
            # 仓库里现成的清单 → 不是渲染产物
            if os.path.isfile(os.path.join(ROOT, tok)):
                continue
            out.append((i, s, tok))
        for mm in APPLY_VAR_RE.finditer(line):
            out.append((i, s, "$" + mm.group(1)))
    return out


def main() -> int:
    errors = []
    notes = []

    if not os.path.isdir(K8S):
        print(f"🔴 找不到 {rel(K8S)} → 本闸门无法工作（不是「通过」）")
        return 1

    # ── 找出所有含占位符的清单 ────────────────────────────────────────
    targets = {}
    for path in iter_yaml_files():
        phs = placeholders_in_values(path)
        if phs is None:
            errors.append(f"[{rel(path)}] YAML 解析失败 → 无法判断有没有占位符"
                          f"（解析失败 ≠ 没有占位符，不能放行）")
            continue
        if phs:
            targets[path] = phs

    print("📋 巡检「含占位符的清单被原样 apply 到集群」")
    print(f"ℹ️  扫描目录：{rel(K8S)}")
    print(f"ℹ️  含占位符的清单：{len(targets)} 份")
    for path, phs in targets.items():
        print(f"    · {rel(path)} → {', '.join(sorted(phs))}")
    print()

    if not targets:
        print("✅ 没有含占位符的清单 → 无需渲染")
        return 0

    files = automation_files()
    print(f"ℹ️  自动化路径文件：{len(files)} 个\n")

    for path, phs in targets.items():
        rendered_anywhere = False
        base = os.path.basename(path)
        relpath = rel(path)
        for f in files:
            try:
                with open(f, encoding="utf-8", errors="ignore") as fh:
                    text = fh.read()
            except OSError:
                continue

            # ── C1：不得原样 apply ────────────────────────────────────
            for ln, snippet in raw_applies_of(path, text):
                errors.append(
                    f"[{rel(f)}:{ln}] **原样 apply** 了含占位符的清单 `{rel(path)}`\n"
                    f"      → {snippet[:110]}\n"
                    f"      → 占位符 {', '.join(sorted(phs))} 会**原样进集群** →\n"
                    "        Ingress 的 host 变成非法域名 → 真实域名的请求**匹配不到规则** → **全站 404**\n"
                    "        （而且 kubectl 不报错 → 部署照样打印「✅ 部署完成！」）\n"
                    "      → ✅ 修法：先渲染成真实值再 apply（见 `k8s/deploy.sh` 的「渲染 Ingress 域名」步骤）"
                )

            # ── C2：apply 渲染产物的路径，**必须自己渲染** ───────────────
            # 📌 **按路径逐条判**，不能只看「有没有人渲染过」——
            #    否则「A 路径渲染、B 路径 apply 产物」= 产物根本不存在 → apply 必然失败。
            renders = render_targets_of(path, text)
            if renders:
                rendered_anywhere = True
                notes.append(f"[{rel(f)}] ✅ 有渲染步骤（{rel(path)} → 另一个文件）")

            artifacts = applied_artifacts(text)
            if artifacts and not renders:
                for ln, snippet, tok in artifacts:
                    errors.append(
                        f"[{rel(f)}:{ln}] apply 了**渲染产物** `{tok}`，但这条路径**没有渲染步骤**\n"
                        f"      → {snippet[:110]}\n"
                        f"      → `{tok}` 不是仓库里的清单 → 必须由**本路径**先渲染出来\n"
                        "      → 否则 apply 的是**不存在的文件** → kubectl 报 not found → 部署失败"
                    )

            # 📌 **C3 与「渲染检测」解耦** —— 理由：渲染写法的形态太多
            #    （`sed` / `python3` / `envsubst` / 变量 / 临时文件…），
            #    靠正则穷举 = **规则 100 的双向失明**。
            #    ✅ 只要这个文件**引用了**含占位符的清单，就检查它有没有给该值配**非空默认值**。
            if base in text or relpath in text:
                for ph in phs:
                    env_name = ph.strip("_")
                    for mm in re.finditer(
                            re.escape(env_name) + r"\s*:-\s*([^\s'\"}]+)", text):
                        if mm.group(1):
                            errors.append(
                                f"[{rel(f)}] 渲染值用了**非空默认值**兜底 → fail-open\n"
                                f"      → `{env_name}:-{mm.group(1)}`\n"
                                "      → 默认值只能是保留域名（example.com）→ "
                                "等于把「全站 404」的坑又写回集群\n"
                                "      → ✅ 修法：不给默认值（空默认 `${X:-}` 才是 fail-closed）"
                            )
                            break
                notes.append(f"[{rel(f)}] ✅ 有渲染步骤（{rel(path)} → 另一个文件）")

        if not rendered_anywhere:
            errors.append(
                f"[{rel(path)}] 没有任何自动化路径**渲染**它\n"
                f"      → 含占位符的清单必须先渲染（占位符 → 真实值）再 apply\n"
                "      → 否则占位符原样进集群 → 真实域名匹配不到 Ingress 规则 → **全站 404**"
            )

    for n in notes:
        print(f"ℹ️  {n}")
    if notes:
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处「含占位符的清单被原样 apply」：\n")
        for e in errors:
            print(f"   {e}\n")
        print(f"合计问题 {len(errors)} 项")
        return 1

    print("✅ 所有含占位符的清单都经过了渲染，没有被原样 apply")
    return 0


if __name__ == "__main__":
    sys.exit(main())
