#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「选完丢掉」的闸门 —— 规则 86 形态 ② 的守门人。

📌 **为什么需要这道闸门**：

规则 86 列了「假成功」的三种形态，形态 ② 是**「选完丢掉」**：
`ElMessage.success('已选择 X')` 但**不存** → **页面里没有任何地方记着它**
→ 用户以为选好了，**选择当场蒸发**。

📌 **本项目真实发作过**（`erp-web/src/views/logistics/Logistics.vue` 的 `selectChannel`）：
原来只弹了个「已选择 X」，**没有任何地方记着它** → 用户以为选好了渠道，**其实什么都没发生**。
修法：真的记住选择（`selectedRate`）→ 表格里标「已选」、上方摘要显示渠道/运费/时效
→ **「已选择」这句话才是真的**。

⚠️ **为什么这条不能靠 `check-fake-success.py` 兜住**：
那道闸门的判据是「函数体里**没有**后端调用 **且** **没有**调用本文件其它函数」
→ 而「选完丢掉」的函数**很可能有**后端调用（选了渠道 → 调接口）→ **完全逃逸**。
→ 两条判据**管的是不同的东西**，缺一不可（规则 86 的原话）。

📌 **判据**：

| 断言 | 内容 |
|---|---|
| **A** | 出现「**已选择** / **已选中** / **选择成功**」类**成功提示**的函数体里，必须有**对某个状态变量的赋值**（`.value =` / `xxx =` / `xxx.push(` / `xxx.splice(` / `Object.assign`） |

📌 **为什么只看「选择类」提示，不看所有 `ElMessage.success`**：
「已提交」「已删除」这类提示**不需要**在函数体里赋值（状态可能由接口响应驱动，
也可能由别的函数赋值）→ 泛扫会假红（规则 99：假阳性比假阴性更危险）。
→ 只有「**选择**」这个动作，语义上**必然**要求「把选中的东西记下来」。

📌 **反向验证**（规则 40 / 75，注入真实的原始形态）：
① 把 `selectedRate.value = rate` 删掉 → A 报红；
② 加一段只有提示、没有赋值的「选择」函数 → A 报红。

用法：
    python3 scripts/check-selection-persistence.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-selection-persistence.py --verbose # 打印明细
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_SRC = os.path.join(REPO_ROOT, "erp-web", "src")
SKIP_DIRS = {"node_modules", "dist", ".git", ".idea"}

# 「选择类」成功提示的措辞
SELECT_MSG_RE = re.compile(
    r"ElMessage\s*\.\s*success\s*\(\s*[`'\"]\s*(?:已选择|已选中|选择成功|已勾选|已选定)"
)

# 赋值形态：.value = / xxx = / push / splice / Object.assign
ASSIGN_RE = re.compile(
    r"\.value\s*=|"
    r"\b[A-Za-z_$][\w$]*\s*=(?!=)|"
    r"\.\s*push\s*\(|\.\s*splice\s*\(|\.\s*unshift\s*\(|"
    r"Object\.assign\s*\("
)

# 函数定义（箭头函数 / function 声明 / 方法）
FUNC_RE = re.compile(
    r"(?:async\s+)?function\s+(\w+)\s*\([^)]*\)\s*\{"
    r"|(?:const|let|var)\s+(\w+)\s*(?::[^=]+)?=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{"
    r"|(?:const|let|var)\s+(\w+)\s*(?::[^=]+)?=\s*(?:async\s*)?\w+\s*=>\s*\{"
)


def block_end(text, start_brace):
    depth = 0
    for i in range(start_brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return None


def find_functions(text):
    """返回 [(name, body_start, body_end)]。"""
    out = []
    for m in FUNC_RE.finditer(text):
        brace = text.find("{", m.end() - 1)
        if brace < 0:
            continue
        end = block_end(text, brace)
        if end is None:
            continue
        out.append((m.group(1) or m.group(2) or m.group(3) or "?", brace, end))
    return out


def strip_comments(text):
    """去掉块注释和行注释，避免注释里的字样被当成代码（规则 97 的教训）。"""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return text


def main() -> int:
    verbose = "--verbose" in sys.argv
    if not os.path.isdir(WEB_SRC):
        print("❌ 找不到 erp-web/src —— 前端目录结构变了？本闸门会变成瞎的")
        return 1

    findings = []
    checked_files = 0
    checked_funcs = 0

    for dirpath, dirnames, filenames in os.walk(WEB_SRC):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if not fn.endswith((".vue", ".ts", ".js")):
                continue
            path = os.path.join(dirpath, fn)
            raw = open(path, encoding="utf-8", errors="replace").read()
            code = strip_comments(raw)
            if not SELECT_MSG_RE.search(code):
                continue
            checked_files += 1
            rel = os.path.relpath(path, REPO_ROOT)

            for name, bstart, bend in find_functions(code):
                body = code[bstart:bend]
                if not SELECT_MSG_RE.search(body):
                    continue
                checked_funcs += 1
                if ASSIGN_RE.search(body):
                    continue
                line = raw[:bstart].count("\n") + 1
                findings.append(
                    f"[A] `{rel}:{line}` 的 `{name}()` 弹了「已选择/已选中」类成功提示，"
                    f"但**函数体里没有任何赋值** → **页面里没有任何地方记着这个选择**"
                    f" → 用户以为选好了，**选择当场蒸发**（规则 86 形态 ②「选完丢掉」）"
                )

    if findings:
        print("❌ 「选完丢掉」闸门：发现 %d 处问题\n" % len(findings))
        for f in findings:
            print("  " + f)
        print(
            "\n📌 规则 86 形态 ②：弹了「已选择 X」但**不存** → 选择当场蒸发。\n"
            "   → 选择类动作**必须**把结果记进某个状态变量（并在页面上可见）。\n"
            "   ⚠️ `check-fake-success.py` 抓不到这个形态：它只看「有没有后端调用」，\n"
            "      而「选完丢掉」的函数**很可能有**后端调用 → 完全逃逸。"
        )
        return 1

    if verbose:
        print("检查了 %d 个含「选择类」提示的文件、%d 个相关函数" % (checked_files, checked_funcs))
    print("✅ 「选完丢掉」：%d 个文件 / %d 个函数，均有把选择结果记下来的赋值（A）"
          % (checked_files, checked_funcs))
    return 0


if __name__ == "__main__":
    sys.exit(main())
