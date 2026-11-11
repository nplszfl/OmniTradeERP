#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「声称做了写操作，却一个请求都没发」的闸门 —— 前端「假成功 / 假删除」的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过的坑，2026-11-11）：

    erp-web/src/views/logistics/Logistics.vue
    const handleAddChannel = async () => {
      try {
        ElMessage.success('渠道添加成功')   // ← 只弹提示
        showChannelDialog.value = false
        loadChannels()                       // ← 重新拉列表（渠道并不会出现）
      } catch (error) { ... }
    }

    → 用户点「添加渠道」→ 弹「渠道添加成功」→ 弹窗关闭 → 列表刷新 → **渠道根本没进去**
    → **后端连 `POST /logistics/channel` 这个端点都不存在**
    = **假成功**（规则 80 / 82）。

📌 **v3（2026-11-12 00:50 实测盲区）—— 「消息文案」和「写动词」两处都只认一种写法**：

    ⚠️ **盲区 A：候选筛选只认「动词 + 成功/完成」这一种句式**
       判据是 `(添加|创建|…|上传).{0,6}(成功|完成)`
       → 真实代码里的说法是**另一种句式**，于是**整条消息根本不会被看一眼**：
         「已删除」「发货成功」「订单已取消」「操作成功」「直播已开始」
         「已采纳 {sku} 的建议价」「库存调整成功」… **全部漏掉**
       📌 其中「**已删除**」正是规则 80 里「假删除」的标准文案 → **闸门漏掉了自己要抓的那一类**。
       实测：全仓 104 条 success/info 提示，只有 **40 条**进了候选，**64 条没被看一眼**。

    ⚠️ **盲区 B：写动词靠手抄一张前缀表**（规则 33 / 92 的反面）
       `start` / `end` / `detect` / `refresh` / `refreshToken` / `chat` / `calculateRate`
       / `recommendChannel` / `scanInventory` / `testConnection`
       —— 这些都是**货真价实的写接口**，但前缀表里没有 → **被当成读**。
       反向也错：`completed` / `download` / `exportInventory` 是 **GET**，
       却被「complete/export/download」前缀误判成写 → **假阴性**（真缺陷被放过）。

📌 **v3 的解法：把「真相表」从代码里抽出来，不再手抄**（规则 33 / 92 / 100 / 117）：

    真值来源 = `erp-web/src/api/*.ts` 里**每个 api 方法实际用的 HTTP 动词**
    （`request.get/post/put/delete/patch`）—— 代码自己说了算，不是我抄一张表
    → 实测抽出 **418 个方法：196 写 / 222 读**。
    📌 **改一个 api 方法的动词 → 真相表自动跟着变** → 不会再漂移。

    候选筛选也不再用「动词 + 成功/完成」的固定句式，而是：
      **消息里出现任何「写语义动词」就算候选**（添加/创建/保存/更新/删除/移除/提交/发布/
       启用/停用/导入/上传/导出/下载/发货/取消/确认/采纳/应用/审批/调整/调拨/标记/
       分配/同步/刷新/启动/结束/检测/扫描/测试/连接/生成/处理/绑定…）
      → 再用真相表判「这个函数里到底有没有写动作」
      → 「已删除」「发货成功」这类**另一种句式**也被纳入了

📌 **判据**：
    C1 扫 `erp-web/src` 的 `.vue` / `.ts` / `.js`
    C2 取每条 `ElMessage.success/info(...)` 的**消息文本**
    C3 消息里含**写语义动词** → 成为候选（不再要求「动词 + 成功/完成」同现）
    C4 定位**所在函数体**（按花括号配平）
    C5 该函数体内若**没有写动作**（真相表判为写 / 裸 HTTP 写动词 / localStorage 等）→ 判红

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
  ① 删掉 `handleAddChannel` 里的 `await logisticsApi.createChannel(...)` → 必须 exit 1
  ② 注入「只读 + 成功提示」→ 必须 exit 1
  ③ 注入「localStorage 真写」→ 必须 exit 0
  ④ **删掉某处 `orderApi.updateShipping(...)`、留下「发货成功」→ 必须 exit 1**（v3 新覆盖）

用法：
    python3 scripts/check-frontend-fake-success.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-frontend-fake-success.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_SRC = os.path.join(REPO_ROOT, "erp-web", "src")
API_DIR = os.path.join(WEB_SRC, "api")

SKIP_DIRS = {"node_modules", "dist", ".git"}

# ─────────────────────────────────────────────────────────────
# 成功提示
# ─────────────────────────────────────────────────────────────
SUCCESS_RE = re.compile(
    r"(?:ElMessage|\$message|this\.\$message)\s*\.\s*(?:success|info)\s*\("
)

# 📌 **v3：不再要求「动词 + 成功/完成」同现**（那是盲区 A）
#    只要求消息里出现**写语义动词**即可成为候选。
#    词表来自**本仓库真实消息文案**（104 条 success/info 全量统计），
#    覆盖「动词+成功」「已+动词」「动词+完成」等**多种句式**（规则 100 / 117）。
WRITE_VERB_RE = re.compile(
    r"(添加|新增|创建|新建|保存|更新|修改|删除|移除|清除|提交|发布|启用|停用|禁用|"
    r"导入|上传|导出|下载|发货|收货|取消|关闭|确认|采纳|应用|审批|审核|调整|调拨|"
    r"标记|分配|同步|刷新|启动|开始|结束|检测|扫描|测试|连接|生成|处理|绑定|解绑|"
    r"设置|重置|恢复|更换|变更|替换|充值|退款|支付|结算|绑定|复制)"
)

# 📌 **v3.1：区分「成功声明」与「提示/占位」**（实测 4 处假阳性，全部出在这里）
#    `ElMessage.info('请先选择要处理的预警记录')` / `info('新建采购单 dialog（demo 占位）')`
#    → 这类是**提示**，不是「我做了某事」→ 不该进候选。
#    ✅ 判据：**只有 `success` 才要求有写动作**；
#       `info` 只有在消息里**自称「成功/完成」**时才进候选
#       （`info` + 「成功」= 它确实在宣称一次成功 → 该查）。
INFO_SUCCESS_RE = re.compile(r"(成功|完成|已\s*\S+)")
SUCCESS_ONLY_RE = re.compile(r"\.\s*success\s*\(")

# ─────────────────────────────────────────────────────────────
# 真相表：从 api 层源码抽出「方法 → 是否写」
# ─────────────────────────────────────────────────────────────
HTTP_VERB_RE = re.compile(
    r"request\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^;]*?>)?\s*\("
)
WRITE_HTTP = {"post", "put", "delete", "patch"}

# api 层对象：`export const xxxApi = { ... }`
API_OBJ_RE = re.compile(r"export\s+const\s+(\w+Api)\s*=\s*\{")
# api 层方法：两空格缩进 + `name: (args) =>` / `name: async (args) =>` / **`name: () =>`**
# 📌 **无参写法必须一起认**（实测 48 处 `stats: () => request.get(...)`）
#    → 只认「有括号的」会漏掉一半的 api 方法 → 真相表残缺 → **假阳性**（规则 100 / 117）。
API_METHOD_RE = re.compile(r"^\s{2}(\w+)\s*:\s*(?:async\s*)?\([^)]*\)\s*=>", re.M)


def build_api_write_table() -> dict:
    """返回 {apiName: {methodName: is_write}} —— 真值来源 = api 层源码本身。"""
    table: dict = {}
    if not os.path.isdir(API_DIR):
        return table
    for fn in sorted(os.listdir(API_DIR)):
        if not fn.endswith(".ts"):
            continue
        path = os.path.join(API_DIR, fn)
        try:
            text = open(path, encoding="utf-8").read()
        except Exception:
            continue
        for m in API_OBJ_RE.finditer(text):
            api = m.group(1)
            # 对象字面量按花括号配平
            i = m.end() - 1
            depth = 1
            while i < len(text) and depth > 0:
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                i += 1
            body = text[m.end():i - 1]
            methods = table.setdefault(api, {})
            for mm in API_METHOD_RE.finditer(body):
                name = mm.group(1)
                seg = body[mm.end():]
                nxt = re.search(r"^\s{2}\w+\s*:", seg, re.M)
                mbody = seg[:nxt.start()] if nxt else seg
                verbs = HTTP_VERB_RE.findall(mbody)
                if verbs:
                    methods[name] = any(v.lower() in WRITE_HTTP for v in verbs)
    return table


# ─────────────────────────────────────────────────────────────
# 「做了写操作」的信号
# ─────────────────────────────────────────────────────────────
# ① 裸 HTTP 写动词（不走 api 封装，如 `axios.post('/api/auth/login', ...)`）
BARE_WRITE_RE = re.compile(
    r"\.\s*(post|put|delete|patch)\s*\("
)
# ② `fetch(..., { method: 'POST' })`
FETCH_WRITE_RE = re.compile(
    r"method\s*:\s*['\"](POST|PUT|DELETE|PATCH)['\"]", re.IGNORECASE
)
# ③ 其它「确实做了事」的动作（不是写，但足以证明不是空壳）
OTHER_ACTION_SIGNALS = [
    re.compile(r"\blocalStorage\s*\.\s*setItem\s*\("),
    re.compile(r"\bsessionStorage\s*\.\s*setItem\s*\("),
    re.compile(r"\bsaveAs\s*\("),
    re.compile(r"\bnavigator\.clipboard\b"),
    re.compile(r"\bcreateObjectURL\s*\("),
]

# 调用形如 `xxxApi.method(`
API_CALL_RE = re.compile(r"\b(\w+Api)\s*\.\s*(\w+)\s*\(")

# 「只是读」的信号 —— 只出现这些 = 没做写操作
READ_ONLY_SIGNALS = [
    re.compile(r"\.\s*get\s*\("),
    re.compile(r"\brequest\s*\.\s*get\s*\("),
    re.compile(r"\.\s*list\s*\("),
    re.compile(r"\.\s*query\s*\("),
    re.compile(r"\.\s*search\s*\("),
    re.compile(r"\.\s*detail\s*\("),
    re.compile(r"\.\s*stats\s*\("),
]


def iter_source_files():
    for dirpath, dirnames, filenames in os.walk(WEB_SRC):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith((".vue", ".ts", ".js")):
                yield os.path.join(dirpath, fn)


ARROW_RE = re.compile(r"(?:const|let|var)\s+\w+\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{")
FUNC_DECL_RE = re.compile(r"(?:async\s+)?function\s+\w+\s*\([^)]*\)\s*\{")


def match_brace(text: str, open_idx: int) -> int:
    depth = 0
    i = open_idx
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text)


def enclosing_function(text: str, idx: int):
    """返回包含 idx 的最近函数体 (start, end) —— 找不到返回 None。"""
    best = None
    for m in ARROW_RE.finditer(text, 0, idx):
        brace = text.index("{", m.start())
        end = match_brace(text, brace)
        if brace <= idx <= end:
            if best is None or brace > best[0]:
                best = (brace, end)
    if best:
        return best
    for m in FUNC_DECL_RE.finditer(text, 0, idx):
        brace = text.index("{", m.start())
        end = match_brace(text, brace)
        if brace <= idx <= end:
            if best is None or brace > best[0]:
                best = (brace, end)
    return best


def extract_message(line: str) -> str:
    m = re.search(r"(?:success|info)\s*\(\s*(['\"`])(.+?)\1", line)
    return m.group(2) if m else ""


def has_write_action(body: str, table: dict) -> bool:
    """函数体里是否真的有写动作。真相表优先，其次裸 HTTP 动词 / 其它真动作。"""
    # ① api 层方法 → 真相表
    for m in API_CALL_RE.finditer(body):
        api, method = m.group(1), m.group(2)
        methods = table.get(api)
        if methods and method in methods:
            if methods[method]:
                return True
    # ② 裸 HTTP 写动词
    if BARE_WRITE_RE.search(body) or FETCH_WRITE_RE.search(body):
        return True
    # ③ 其它真动作
    for sig in OTHER_ACTION_SIGNALS:
        if sig.search(body):
            return True
    return False


def is_legit_non_write(body: str, table: dict, msg: str) -> bool:
    """例外白名单：**必须逐条给理由**，不能靠模糊过滤（规则 99）。

    📌 **例外 1：刷新视图**——「预测已刷新 / 推荐已刷新」这类操作的**目的就是重新拉数据**，
       函数体里 `await load()`（读）**就是**它该做的事。
       → 判据：函数体里**有 `await load()` / `await xxxApi.load()` 且**没有别的写**。

    📌 **例外 2：浏览器下载**——「下载报表：{name}」的真正动作是**把 blob 交给浏览器**，
       而 `reportApi.download()` 本身是 **GET**（读）。
       → 判据：函数体里有 `URL.createObjectURL` / `link.click()` / `saveAs`。
       📌 注意：这是**下载**，不是**改数据** → 不该用「写动词」去要求它写库。
    """
    # 例外 1：刷新视图
    if re.search(r"\bawait\s+(?:load|refreshList|fetchList)\s*\(", body) and \
       not has_write_action(body, table):
        return True
    # 例外 2：浏览器下载
    if re.search(r"(createObjectURL|link\.click\s*\(|saveAs\s*\()", body):
        return True
    # 例外 2b：**下载动作在 api 层里完成**（`reportApi.download` 内部 base64 → Blob → 触发下载）
    #   → 视图层只写 `reportApi.download(...).then(...)` → 视图层看不到任何下载痕迹
    #   → 判据：**被调的 api 方法名带下载语义**，且它本身是个真调用（非空壳）
    for m in API_CALL_RE.finditer(body):
        api, method = m.group(1), m.group(2)
        methods = table.get(api)
        if not methods or method not in methods:
            continue
        if re.search(r"(download|export)", method, re.IGNORECASE):
            return True
    return False


def main() -> int:
    verbose = "--verbose" in sys.argv
    table = build_api_write_table()
    n_api = sum(len(v) for v in table.values())
    n_write = sum(1 for v in table.values() for w in v.values() if w)

    findings = []
    checked = 0

    for path in iter_source_files():
        try:
            text = open(path, encoding="utf-8").read()
        except Exception:
            continue
        rel = os.path.relpath(path, REPO_ROOT)
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if not SUCCESS_RE.search(line):
                continue
            msg = extract_message(line)
            if not msg or not WRITE_VERB_RE.search(msg):
                continue
            # 📌 **v3.1：`info` 要自称「成功/完成」才算候选**（否则「请先选择…」这种提示会误报）
            if not SUCCESS_ONLY_RE.search(line) and not INFO_SUCCESS_RE.search(msg):
                continue
            checked += 1
            off = sum(len(x) + 1 for x in lines[:i])
            fn = enclosing_function(text, off)
            if fn is None:
                # 找不到函数体 → 无法判定，不报（避免把顶层代码误判）
                continue
            body = text[fn[0]:fn[1]]
            if has_write_action(body, table):
                continue
            if is_legit_non_write(body, table, msg):
                continue
            read_only = any(sig.search(body) for sig in READ_ONLY_SIGNALS)
            findings.append((rel, i + 1, msg[:80], read_only))

    print("📋 巡检前端「声称做了写操作，却一个请求都没发」")
    print(f"ℹ️  真相表：api 层解析出 {n_api} 个方法（{n_write} 写 / {n_api - n_write} 读）")
    print(f"ℹ️  扫到的「含写语义动词」的成功提示：{checked} 处")
    if verbose:
        for rel, ln, s, ro in findings:
            print(f"   {rel}:{ln}  {s}  {'（只做了读）' if ro else '（什么都没做）'}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「假成功 / 假删除」：")
        for rel, ln, s, ro in findings:
            print(f"   {rel}:{ln}")
            print(f"        「{s}」—— 所在函数里"
                  + ("只做了**读**操作，没有任何写动作" if ro else "没有任何真实动作"))
        print("\n✅ 修法：**真的调用后端接口**，等它成功了再弹提示；"
              "失败要弹 error 而不是 success（规则 80 / 82）")
        return 1

    print("\n✅ 所有「写操作 + 成功」的提示都有对应的真实请求")
    return 0


if __name__ == "__main__":
    sys.exit(main())
