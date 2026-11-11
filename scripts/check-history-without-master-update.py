#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「写了变更历史，却没改业务主表」的闸门 —— 规则 72 / 80 / 82 的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过的坑，2026-11-11）：

    前端点「采纳建议价」→ 弹「已采纳 {sku} 的建议价」
    后端 PricingSuggestionServiceImpl.applySuggestion()：
        ✅ suggestion.setApplied(TRUE)   → 写建议表
        ✅ priceHistoryMapper.insert(...) → 写变更历史（旧价 → 新价）
        ❌ **product.selling_price 从头到尾没人写**（全仓 `setSellingPrice` 生产代码 0 处调用）
    → 商品的**实际售价纹丝未动**，但界面说「已采纳」、历史页多了一条**根本没发生的变更**
    = **假成功**（规则 80 / 82）+ **数字来自一张没被改的表**（规则 72）。

📌 **为什么不能用「宽泛的静态扫描」**（规则 42 / 55 / 96）：
  试过「实体里金额/数量/状态类字段 → 生产代码有没有 setter」→ **17 个字段误报**
  （大量写法是 Lombok 构造器 / Builder / `LambdaUpdateWrapper.set()` / XML mapper，
  正则全都认不出来）→ **判据必须匹配「代码实际怎么写」**。

📌 **本闸门的判据**（对着真实写法来）：

| 断言 | 内容 |
|---|---|
| **C1** | 扫描**服务实现类**里 `public` + `@Override` 的**接口方法**（= 对外 API 入口） |
| **C2** | 该方法里若向 `*HistoryMapper` / `*LogMapper` / `*RecordMapper` / `*TraceMapper` **insert** 了行，且行里 `setProductId(...)` → 说明它在记录**商品维度**的变更 |
| **C3** | 那它**必须**同时更新**商品表**（`productMapper.update/insert/updateById` 等）—— 否则就是「记了账、没改东西」 |

📌 **为什么只查 `public @Override` 方法**：
  `InventoryServiceImpl.recordInventoryLog()` 是**私有 helper**，它只负责写流水，
  真正的库存更新在调用方（`increaseInventory` / `decreaseInventory`）里
  → 用它当反例会让闸门**假红**。✅ 只查**接口入口方法**（对外承诺发生在这里）。

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
  把 `applySuggestion` 里的 `productMapper.updateById(product)` 删掉 → 本闸门必须 exit 1；
  恢复 → 必须 exit 0。

用法：
    python3 scripts/check-history-without-master-update.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-history-without-master-update.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# 变更历史 / 流水 / 审计 表的 Mapper
HISTORY_MAPPER_RE = re.compile(r"(\w+)(?:History|Log|Record|Trace)Mapper")

# 对外接口方法：@Override + public
# 📌 **@Override 与 public 之间允许夹其它注解**（如 @Transactional）—— 
#    否则「我刚给这个方法加了 @Transactional」就会让闸门对**自己的目标方法静默失明**
#    （实测踩过：修完 applySuggestion 加了 @Transactional → 正则不再匹配 → 命中数从 1 变 0）。
API_METHOD_RE = re.compile(
    r"@Override(?:\s*@\w+(?:\([^)]*\))?)*\s*\n\s*public\s+[\w<>,\[\]\. ]+\s+(\w+)\s*\([^)]*\)\s*\{"
)

# 商品主表被更新的写法（product / sku / item / spu 等实体的 Mapper）
MASTER_UPDATE_RE = re.compile(
    r"\w*(?:product|sku|item|spu)\w*Mapper\s*\.\s*(?:update|insert|updateById)",
    re.IGNORECASE,
)

# 变更行里引用了商品维度
PRODUCT_REF = "setProductId"


def iter_java_files():
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/java" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                yield os.path.join(dirpath, fn)


def method_body(text: str, body_start: int) -> str:
    """取出方法体（body_start = '{' **之后**的位置；按花括号配平）。"""
    depth = 1
    i = body_start
    while i < len(text) and depth > 0:
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
        i += 1
    return text[body_start:i]


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []
    checked = 0

    for path in iter_java_files():
        try:
            text = open(path, encoding="utf-8").read()
        except Exception:
            continue
        rel = os.path.relpath(path, REPO_ROOT)
        for m in API_METHOD_RE.finditer(text):
            name = m.group(1)
            body = method_body(text, m.end())
            hm = HISTORY_MAPPER_RE.search(body)
            if not hm:
                continue
            # 必须真的 insert 了（不是只声明了 mapper 字段）
            if not re.search(
                re.escape(hm.group(0)) + r"\s*\.\s*insert\s*\(", body
            ):
                continue
            if PRODUCT_REF not in body:
                continue
            checked += 1
            if not MASTER_UPDATE_RE.search(body):
                line = text.count("\n", 0, m.start()) + 1
                findings.append((
                    rel, line, name,
                    f"方法 {name}() 向 {hm.group(0)} 写了带商品维度的变更记录，"
                    f"却**没有更新商品主表**（`productMapper.update*` 等）→ "
                    f"「记了账、没改东西」= 假成功（规则 72 / 80 / 82）",
                ))

    print("📋 巡检「写了变更历史，却没改业务主表」")
    print(f"ℹ️  可判的「对外方法 + 变更历史 + 商品维度」组合：{checked} 处")
    if verbose:
        for rel, line, name, msg in findings:
            print(f"   {rel}:{line} {msg}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「记了账、没改东西」：")
        for rel, line, name, msg in findings:
            print(f"   {rel}:{line}")
            print(f"        {msg}")
        print("\n✅ 修法：在这条路径上**真的更新业务主表**（如 `product.setSellingPrice(...)` + "
              "`productMapper.updateById(product)`），并让变更历史的旧价取**主表的真实旧值**")
        return 1

    print("\n✅ 所有「记录商品维度变更」的对外方法都同时更新了业务主表")
    return 0


if __name__ == "__main__":
    sys.exit(main())
