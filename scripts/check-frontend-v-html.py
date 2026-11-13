#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 68 道闸门：前端「未净化的 v-html」巡检 —— 存储型 XSS 的守门人。

📌 **为什么需要这道闸门**（2026-11-13 23:40 实测踩出来的真缺陷）：

`erp-web/src/views/ai/Description.vue` 曾经这么写：

    <p class="body" v-html="result.body.replace(/\\n/g, '<br/>')" />

→ `result.body` 是 **LLM 的原始输出**（后端 `ProductDescriptionServiceImpl`
   的 `optimizeDescription()` 只做了 `trim()`，一路透传到前端）
→ LLM 输出里出现 `<img src=x onerror=alert(1)>` 这类内容，
   浏览器就会**当 HTML 执行** → **存储型 XSS**。

📌 **危害比一般 XSS 更大（三条）**：

    ① **存储型** —— 这段内容会先落库（`descriptionApi.save()` → `t_product_description`）
       → 一次生成，**全站每个打开该描述页的人都被打**（不是只打生成者）。
    ② **提示词注入面** —— LLM 的输入含**用户可控**的 SKU / 关键词 / 商品名
       → 攻击者可以用「请在描述里写 `<script>…`」这类输入影响输出。
    ③ **同源** → 偷到的 cookie / token 能直接调 `/api/user/**` 等接口 → 账号被接管。

📌 **为什么这类缺陷能一路活到今天**：

    - `vue-tsc`（类型检查）**不报** —— `v-html` 的类型是 `string`，合法；
    - ESLint / Prettier **不报** —— 语法完全正确；
    - 前端单测**没有覆盖**这个页面；
    → 也就是说：**编译期、类型期、格式期全都看不见它**。
    📌 与 `check-id-precision.py`（雪花 ID 精度）同族：**只有运行时才发作的缺陷**，
    静态工具全都漏。

📌 **本闸门的判据（窄口径，避免「一上来全红」）**：

| 断言 | 内容 |
|---|---|
| **C1** | `.vue` / `.ts` 里的 `v-html` **必须**绑定到**净化函数**（本项目约定：`renderPlainText(...)`）。裸绑定 → 报错。 |
| **C2** | `v-html` 的值若是**字面量 HTML 字符串** → 报错（静态 HTML 同样会绕过净化，且改文案时容易漏） |
| **C3** | 净化函数**必须在册**（`erp-web/src/utils/sanitize.ts` 存在且导出 `renderPlainText`）→ 否则「净化函数」这个约定是空的，闸门会误判成「已净化」 |

📌 **为什么不复用「全部禁用 v-html」的粗暴判据**：

    本项目**确实**有一处合法需求要渲染 HTML —— 商品描述要保留换行排版。
    一刀切禁用 → 逼下一个人去「放松闸门」（规则 99：闸门太吵就会被拆掉）。
    ✅ 所以判据是「**必须经过净化**」而不是「不许用」。

📌 **为什么不引 DOMPurify**（这是个被反复问到的决策，理由写在这里）：

    ① **不引新依赖** —— `package.json` 只有 10 个运行时依赖；
       引一个包 = lockfile 变动 = 供应链面变大 = CI 要重新校验 146 个包的 integrity。
       本项目自己踩过「lockfile 全指向国内镜像」的坑（见 `.github/workflows/code-quality.yml`）。
    ② **DOMPurify 也不是银弹** —— 它要求**每次都记得调** `DOMPurify.sanitize()`；
       漏调一次 = 防护归零。而**「不许裸用 v-html」是结构性约束**，
       不依赖「人每次都记得」（规则 93：能靠结构保证的，别靠人记）。

📌 **已知边界（诚实记录，不藏）**：

    - 只判 **`v-html` 指令**；`innerHTML` / `document.write` / `outerHTML` 不在范围
      （本仓库实测 0 处 —— 但闸门**不保证**将来也没有）。
    - 只判**静态可判**的绑定表达式；若 `v-html` 绑的是变量
      （`v-html="html"`）→ 变量里是不是净化过的，本闸门**看不见** → 报 ℹ️ 不算失败。
    - **不判断数据来源**（是否来自 LLM / 用户）→ 只看「有没有走净化函数」。
    - 不扫 `node_modules` / `dist`。

用法：
    python3 scripts/check-frontend-v-html.py
退出码：0 = 通过；1 = 发现问题。
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_SRC = os.path.join(ROOT, "erp-web", "src")

SKIP_DIRS = {"node_modules", "dist", ".git", "coverage", "target"}

# ── 净化函数白名单（本项目的约定）────────────────────────────────────
# 📌 语义 = 「这个函数保证返回的字符串里除了它自己加的标签，没有别的标签会生效」
SANITIZER_NAMES = ("renderPlainText",)

# ── 净化函数的**权威定义文件**（规则 33：真相来源 = 仓库本身）──────────
SANITIZER_FILE = os.path.join(WEB_SRC, "utils", "sanitize.ts")

# `v-html="..."` —— Vue 指令
V_HTML_RE = re.compile(r"""v-html\s*=\s*(?P<q>["'])(?P<expr>.*?)(?P=q)""", re.DOTALL)

# ── 注释剥离 ────────────────────────────────────────────────────────
# 📌 **实测踩到的坑（2026-11-13 23:50）**：`sanitize.ts` 的**文档注释里**
#    引用了修复前的旧写法 `v-html="result.body.replace(...)"`（当反例展示）
#    → 闸门把**注释里的反例**当成真实代码 → **假阳性**。
#    📌 规则 99：闸门一旦有假阳性，下一个人就会去「放松闸门」→ 必须先把噪声去掉。
#    → 判据：把 HTML 注释、JS 块注释、行注释整段剥掉再匹配。
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT_RE = re.compile(r"^\s*//.*$", re.MULTILINE)


def strip_comments(src: str) -> str:
    """剥掉注释，**保留行数**（用等长空白替换）→ 行号才不会错位。

    📌 用空白替换而不是直接删：删掉会让后续 `count('\\n')` 的行号全部前移 →
       报错指向错误的行 → 排查时白查半天（规则 93：报错要能指对人）。
    """
    def blank(m):
        return re.sub(r"[^\n]", " ", m.group(0))

    src = HTML_COMMENT_RE.sub(blank, src)
    src = BLOCK_COMMENT_RE.sub(blank, src)
    src = LINE_COMMENT_RE.sub(blank, src)
    return src


def iter_files():
    for dirpath, dirnames, filenames in os.walk(WEB_SRC):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if not fn.endswith((".vue", ".ts", ".js", ".tsx", ".jsx")):
                continue
            yield os.path.join(dirpath, fn)


def sanitizer_registered() -> bool:
    """净化函数是否**真的在册**（C3）。

    📌 没有这一步，「必须走净化函数」就只是一句空话 ——
       闸门会把 `v-html="renderPlainText(x)"` 判成「已净化」，
       而 `renderPlainText` **根本不存在** → 页面直接白屏 / 编译失败。
    """
    if not os.path.isfile(SANITIZER_FILE):
        return False
    try:
        with open(SANITIZER_FILE, encoding="utf-8", errors="ignore") as f:
            src = f.read()
    except OSError:
        return False
    # 📌 **等价写法必须一起认**（规则 100 / 117）：
    #    同一个导出至少有 3 种等价写法，只认一种 = **双向失明**：
    #      ① export function renderPlainText(...) {}      ← 原判据只认这种
    #      ② export const renderPlainText = (...) => {}   ← 箭头函数（Vue 项目最常见）
    #      ③ export { renderPlainText }                   ← 底部集中导出
    #    📌 **实测踩到的假阳性**（2026-11-14 03:20 差分验证）：
    #       写法 ③ → 判成「不在册」→ 闸门把**合法的净化写法**判红 →
    #       下一个人只能去「放松闸门」（规则 99：假阳性比假阴性更危险）。
    #    ✅ 解法：三种都认；且 ③ 要**排除** `export { renderPlainText as default }`
    #       （default 导出用 `import X from` 导入，名字对不上 → 不算在册）。
    for name in SANITIZER_NAMES:
        n = re.escape(name)
        if re.search(r"export\s+(?:async\s+)?(?:function|const|let|var)\s+" + n + r"\b", src):
            return True
        # `export { a, renderPlainText, b }` / `export { renderPlainText as default }`
        m = re.search(r"export\s*\{([^}]*)\}", src, re.DOTALL)
        if m:
            for part in m.group(1).split(","):
                part = part.strip()
                if re.fullmatch(r"(?:\w+\s+as\s+)?" + n + r"(?:\s+as\s+(?!default\b)\w+)?", part):
                    return True
    return False


def expr_uses_sanitizer(expr: str) -> bool:
    """表达式里是否调用了净化函数。"""
    return any(re.search(r"\b" + re.escape(name) + r"\s*\(", expr)
               for name in SANITIZER_NAMES)


def looks_like_literal_html(expr: str) -> bool:
    """表达式是不是**字面量 HTML**（静态字符串里带标签）。"""
    stripped = expr.strip()
    if not (stripped.startswith("'") or stripped.startswith('"')):
        return False
    return bool(re.search(r"<\s*/?\s*[a-zA-Z][\w-]*", stripped))


def main() -> int:
    if not os.path.isdir(WEB_SRC):
        print(f"🔴 找不到前端源码目录 {WEB_SRC} → 本闸门无法工作（不是「通过」）")
        return 1

    errors = []
    infos = []
    total = 0

    # ── C3：净化函数必须在册 ──────────────────────────────────────────
    if not sanitizer_registered():
        errors.append(
            "[sanitize.ts] 净化函数**不在册** → 「v-html 必须走净化函数」这个约定是空的\n"
            f"      → 期望文件：{os.path.relpath(SANITIZER_FILE, ROOT)}\n"
            f"      → 期望导出：{', '.join(SANITIZER_NAMES)}\n"
            "      → 不修这条，下面所有 `v-html=\"renderPlainText(x)\"` 都会被误判成「已净化」"
        )

    for path in iter_files():
        rel = os.path.relpath(path, ROOT)
        try:
            with open(path, encoding="utf-8", errors="ignore") as f:
                src = f.read()
        except OSError as e:
            errors.append(f"[{rel}] 读文件失败：{e}")
            continue

        # 📌 **先剥注释** —— 否则文档注释里的「反例写法」会被当成真实代码（假阳性）
        code = strip_comments(src)

        for m in V_HTML_RE.finditer(code):
            total += 1
            expr = m.group("expr").strip()
            line = code[:m.start()].count("\n") + 1

            # C2：字面量 HTML
            if looks_like_literal_html(expr):
                errors.append(
                    f"[{rel}:{line}] `v-html` 绑的是**字面量 HTML**：{expr[:100]}\n"
                    "      → 静态 HTML 同样绕过净化，而且改文案时最容易漏掉\n"
                    "      → ✅ 修法：写进模板（`{{ }}` 插值，Vue 自动转义），或走净化函数"
                )
                continue

            # C1：必须走净化函数
            if expr_uses_sanitizer(expr):
                continue

            # 变量绑定 → 本闸门看不见数据是否净化过 → ℹ️ 不算失败
            # 📌 **实测踩到的坑（2026-11-13 23:55，差分验证抓出来的）**：
            #    判据原本写成「整个表达式匹配 `[A-Za-z_$][\w$]*(\.[\w$]+)*`」
            #    → `v-html="result.body"`（**属性访问**）也匹配上了
            #    → 被判成「变量绑定，看不见」→ **ℹ️ 不算失败** → **假绿**！
            #    而 `v-html="result.body"` **恰恰就是原始缺陷的形态**
            #    （修复前是 `result.body.replace(...)`，同一条路径）。
            #    📌 规则 55：判据只认一种写法 = 双向失明。
            #    ✅ 修法：只有**纯标识符**（没有任何 `.`）才算「看不见的变量绑定」；
            #       一旦带属性访问（`.`）→ 说明它读的是**某个对象的字段**
            #       → 字段值大概率是外部数据（LLM/用户/DB）→ 必须判红。
            if re.fullmatch(r"[A-Za-z_$][\w$]*", expr):
                infos.append(
                    f"[{rel}:{line}] `v-html` 绑的是变量 `{expr}` → "
                    "本闸门看不见它是否净化过（ℹ️ 不算失败，但请确认赋值处走了净化函数）"
                )
                continue

            if re.fullmatch(r"[A-Za-z_$][\w$]*(\.[\w$]+)*", expr):
                errors.append(
                    f"[{rel}:{line}] `v-html` 绑的是**对象的属性** `{expr}` → 未净化\n"
                    "      → 属性访问说明读的是某个对象的字段 → 字段值通常来自外部数据\n"
                    "        （LLM 输出 / 用户输入 / 数据库）→ 标签会被浏览器执行 → **存储型 XSS**\n"
                    f"      → ✅ 修法：改成 `v-html=\"{SANITIZER_NAMES[0]}({expr})\"`"
                )
                continue

            errors.append(
                f"[{rel}:{line}] `v-html` **未经净化**：{expr[:120]}\n"
                "      → 这是**存储型 XSS** 的入口：\n"
                "        · 渲染的内容若来自 LLM / 用户输入 / 数据库 → 标签会被浏览器执行\n"
                "        · 若内容会落库 → 一次注入，全站每个打开该页的人都被打\n"
                f"      → ✅ 修法：改成 `v-html=\"{SANITIZER_NAMES[0]}(...)\"`"
                "（只放行 `<br>`，其余标签转义成可见文本）"
            )

    print("📋 巡检「前端未净化的 v-html」（存储型 XSS 入口）")
    print(f"ℹ️  扫描目录：{os.path.relpath(WEB_SRC, ROOT)}")
    print(f"ℹ️  净化函数在册：{'✅ 是' if sanitizer_registered() else '❌ 否'}"
          f"（{', '.join(SANITIZER_NAMES)}）")
    print(f"ℹ️  共发现 {total} 处 `v-html` 绑定\n")

    if infos:
        for i in infos:
            print(f"ℹ️  {i}")
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处「未净化的 v-html」：\n")
        for e in errors:
            print(f"   {e}\n")
        print(f"合计问题 {len(errors)} 项")
        print()
        print("📌 **v-html 是 Vue 里唯一会绕过自动转义的渲染指令**")
        print("   → 模板里 `{{ }}` 插值是**安全**的（Vue 自动转义），不需要动")
        return 1

    print("✅ 所有 `v-html` 都经过了净化函数（或已是净化函数的定义文件本身）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
