#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「自称 AI/ML 但其实不是」的闸门 —— 规则 18 / 56 的守门人。

📌 **为什么需要这道闸门**（本项目真实踩过的坑，2026-11-12）：

    库存预测模块 response.setModelType("Prophet + ARIMA");   ← 谎报事实
    库存预测模块 dailyPred.setConfidence(0.9);               ← 在模拟数据上编的假置信度
    定价模块     response.setPricingStrategy("AI智能定价算法 v2.0.0");  ← 该模块 0 次 LLM 调用

这三个都是**对外字段里的谎报**：调用方（前端 / 别的服务）看到「Prophet + ARIMA」「AI智能定价」
→ 会以为背后有模型在算 → 做出**它不该做的决策**（比如按「AI 建议价」直接改价）。
📌 **修好 ≠ 不会再坏**（规则 29 / 51）：没有闸门守着，下一个人重构时改回去，
**所有已有闸门依然全绿**。

📌 **判据设计**（对着「代码实际怎么写」来，不做泛扫 —— 规则 42 / 55）：

| 断言 | 内容 |
|---|---|
| **B1** | 对外「算法/模型」字段（`setModelType` / `setPricingStrategy` / `setAlgorithm` / `setModel` …）里出现 **ML 专属词**（Prophet / ARIMA / LSTM / 神经网络 / 深度学习 / 机器学习 / TensorFlow …）→ **必须**该模块真有对应依赖，否则是谎报 |
| **B2** | 对外「算法/模型」字段里出现 **「AI 智能 / AI 算法 / 大模型 / GPT」** → 该模块**必须真有 LLM 调用链路**（依赖 `erp-llm-common` 或源码里有 LLM API 调用），否则是谎报 |
| **B3** | **否定语境不算**：「非机器学习模型」「非 LLM」这类**明确否认**的表述**不算**谎报 |

📌 **真值来源**（不是手抄清单 —— 规则 33）：
- B1 的「真有 ML 依赖」 = 该模块 `pom.xml` 里声明了对应 ML 库
- B2 的「真有 LLM 链路」 = 该模块 `pom.xml` 依赖 `erp-llm-common`，**或** 源码里有 LLM 调用特征
  → **按模块目录判定**，而不是「全仓有就算」

📌 **反向验证必须注入「真实的原始缺陷」**（规则 40 / 75）：
① 在**无 LLM 依赖**的模块写 `setPricingStrategy("AI智能定价算法")` → 本闸门必须 exit 1；
② 在**无 ML 依赖**的模块写 `setModelType("Prophet + ARIMA")` → 本闸门必须 exit 1；
③ 恢复成诚实表述 → 必须 exit 0。

用法：
    python3 scripts/check-ai-claim-honesty.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-ai-claim-honesty.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# ── 对外「算法/模型」字段的 setter ────────────────────────────────
ALGO_SETTER_RE = re.compile(
    r"\bset(?:ModelType|ModelName|PricingStrategy|Strategy|Algorithm|Engine|Model)\s*\("
)

# ── ML 专属词：声称用了机器学习模型 ────────────────────────────────
ML_CLAIM_TOKENS = [
    "Prophet", "ARIMA", "LSTM", "GRU", "Transformer", "XGBoost", "LightGBM",
    "神经网络", "深度学习", "机器学习", "TensorFlow", "PyTorch", "Scikit",
    "scikit-learn", "sklearn", "CNN", "RNN",
]

# ── AI / LLM 宣称词：声称背后有模型在算 ───────────────────────────
AI_CLAIM_TOKENS = [
    "AI智能", "AI 算法", "AI算法", "大模型", "GPT", "LLM", "智能算法",
]

# ── 对应的「真值」依赖：模块 pom 里声明了这些才算真 ────────────────
ML_TRUTH_DEPS = [
    "prophet", "statsmodels", "pmdarima", "tensorflow", "torch", "pytorch",
    "scikit-learn", "xgboost", "lightgbm", "keras", "dl4j",
]

LLM_TRUTH_DEPS = ["erp-llm-common"]

# ── 源码里出现这些特征 = 该模块**确实**在调 LLM ──────────────────
LLM_CALL_MARKERS = [
    "chat/completions", "LlmService", "LLMClient", "llmClient", "LlmChatClient",
    "LlmProperties", "deepseek", "DeepSeek", "qwen", "Qwen",
]

# ── 否定语境：明确否认的不算谎报 ─────────────────────────────────
NEGATION_PREFIX = ("非", "不", "无", "没有", "非")


def iter_java_files():
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/java" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                yield os.path.join(dirpath, fn)


def module_of(path: str) -> str:
    """文件所属的 Maven 模块名（仓库根下的第一层目录）。"""
    rel = os.path.relpath(path, REPO_ROOT).replace(os.sep, "/")
    return rel.split("/")[0]


def module_pom_text(module: str) -> str:
    pom = os.path.join(REPO_ROOT, module, "pom.xml")
    if not os.path.isfile(pom):
        return ""
    try:
        return open(pom, encoding="utf-8").read()
    except Exception:
        return ""


def module_has_llm(module: str, cache: dict) -> bool:
    if module in cache:
        return cache[module]
    pom = module_pom_text(module).lower()
    ok = any(dep.lower() in pom for dep in LLM_TRUTH_DEPS)
    if not ok:
        # 源码里出现 LLM 调用特征也算（模块可能自己直接调 HTTP）
        for path in iter_java_files():
            if module_of(path) != module:
                continue
            try:
                text = open(path, encoding="utf-8").read()
            except Exception:
                continue
            if any(m in text for m in LLM_CALL_MARKERS):
                ok = True
                break
    cache[module] = ok
    return ok


def module_has_ml(module: str, cache: dict) -> bool:
    if module in cache:
        return cache[module]
    pom = module_pom_text(module).lower()
    ok = any(dep.lower() in pom for dep in ML_TRUTH_DEPS)
    cache[module] = ok
    return ok


CONST_DECL_RE = re.compile(
    r"\b(?:private|public|protected|static|final|\s)*String\s+([A-Z][A-Z0-9_]*)\s*=\s*\"((?:[^\"\\]|\\.)*)\""
)


def parse_string_constants(text: str) -> dict:
    """解析本文件里的 `static final String NAME = "值";` → {NAME: 值}。

    📌 **为什么要解析常量**（规则 90 / 100 的新形态）：
    真实的「谎报」几乎都写在**常量**里（`MOCK_MODEL_NAME = "mock"`、
    `STRATEGY_RULE_BASED = "…"`，`setModelType(MODEL_TYPE_HEURISTIC)`），
    只认**字面量**的闸门会「一个都判不出来」→ **真空绿**。
    """
    out = {}
    for m in CONST_DECL_RE.finditer(text):
        out[m.group(1)] = m.group(2)
    return out


def extract_args(text: str, start: int):
    """从 text[start]（必须是 '('）开始提取**全部实参**（按顶层逗号切分，可跨行）。

    📌 每个实参保留原样：字符串字面量（含引号）、标识符、调用表达式。
    后面再决定「能不能当事实看」。
    """
    i = start
    if i < 0 or i >= len(text) or text[i] != "(":
        return []
    depth = 0
    args = []
    cur = ""
    in_str = False
    quote = ""
    j = i
    while j < len(text):
        ch = text[j]
        if in_str:
            cur += ch
            if ch == "\\":
                if j + 1 < len(text):
                    cur += text[j + 1]
                    j += 1
            elif ch == quote:
                in_str = False
        else:
            if ch in "\"'":
                in_str = True
                quote = ch
                cur += ch
            elif ch == "(":
                depth += 1
                if depth > 1:
                    cur += ch
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
                cur += ch
            elif ch == "," and depth == 1:
                args.append(cur.strip())
                cur = ""
            else:
                cur += ch
        j += 1
    if cur.strip():
        args.append(cur.strip())
    return args


IDENT_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def resolve_arg(arg: str, constants: dict):
    """实参 → 可判定的字符串事实；判不出来返回 None。"""
    if not arg:
        return None
    # 1) 纯字符串字面量
    if len(arg) >= 2 and arg[0] == '"' and arg[-1] == '"':
        return arg[1:-1]
    # 2) 常量引用（真实代码里「谎报」几乎都写在常量里 —— 规则 100 的新形态）
    if IDENT_RE.match(arg) and arg in constants:
        return constants[arg]
    # 3) 常量 + 拼接：取常量部分（够用即可）
    if IDENT_RE.match(arg) is None and '"' not in arg:
        for name, val in constants.items():
            if name in arg:
                return val
    return None


def is_negated(text: str, token: str) -> bool:
    """token 前面紧邻的否定词（可隔一个空格）→ 视为「明确否认」，不算宣称。"""
    idx = text.find(token)
    while idx != -1:
        prefix = text[max(0, idx - 3):idx].strip()
        if prefix and prefix[-1] in NEGATION_PREFIX:
            return True
        idx = text.find(token, idx + 1)
    return False


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []
    llm_cache: dict = {}
    ml_cache: dict = {}

    checked = 0
    for path in iter_java_files():
        try:
            text = open(path, encoding="utf-8").read()
        except Exception:
            continue
        module = module_of(path)
        rel = os.path.relpath(path, REPO_ROOT)
        constants = parse_string_constants(text)
        lines = text.splitlines()
        for m in ALGO_SETTER_RE.finditer(text):
            # 该 setter 的实参（可能跨行）
            args = extract_args(text, m.end() - 1)
            # 行号
            idx = text.count("\n", 0, m.start()) + 1
            for literal in (resolve_arg(a, constants) for a in args):
                if not literal or len(literal) < 2:
                    continue
                checked += 1
                # B1：ML 专属词
                for token in ML_CLAIM_TOKENS:
                    if token in literal and not is_negated(literal, token):
                        if not module_has_ml(module, ml_cache):
                            findings.append((
                                rel, idx, "B1",
                                f"对外字段宣称用了「{token}」模型，但模块 {module} **没有任何 ML 依赖**"
                                f"（pom.xml 无 {', '.join(ML_TRUTH_DEPS[:4])} 等）→ 谎报事实",
                            ))
                        break
                # B2：AI / LLM 宣称词
                for token in AI_CLAIM_TOKENS:
                    if token in literal and not is_negated(literal, token):
                        if not module_has_llm(module, llm_cache):
                            findings.append((
                                rel, idx, "B2",
                                f"对外字段宣称「{token}」，但模块 {module} **没有 LLM 调用链路**"
                                f"（既不依赖 erp-llm-common，源码里也没有 LLM 调用）→ 谎报事实",
                            ))
                        break

    print(f"📋 巡检「自称 AI/ML 但其实不是」（对外算法/模型字段）")
    print(f"ℹ️  可判的算法字段赋值：{checked} 处")
    if verbose:
        for rel, idx, code, msg in findings:
            print(f"   [{code}] {rel}:{idx} {msg}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处谎报：")
        for rel, idx, code, msg in findings:
            print(f"   [{code}] {rel}:{idx}")
            print(f"        {msg}")
        print("\n✅ 修法：改成如实描述（如「多因素规则定价（非 LLM）」「启发式（…非机器学习模型）」）")
        return 1

    print("\n✅ 所有对外算法/模型字段的宣称都与实际能力一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
