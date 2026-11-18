#!/usr/bin/env python3
"""LLM 配置契约闸门 —— 「应用侧读的 LLM_*」↔「部署层注入的 LLM_*」（compose + k8s）。

📌 **这个闸门管的是什么**（2026-11-18 实测出来的缺口）：

ERP 的 AI 能力（产品描述生成 / AI 客服）在本机跑得好好的 —— 真调 LLM、
`mock:false`、内容是真的。**但容器里 AI 是关的**：

    应用的 application.yml 读 `${LLM_API_KEY:...}` / `${LLM_PROVIDER:deepseek}` / ...
    而 docker-compose*.yml 里 **一个 LLM_* 变量都没注入**
    → 容器里的应用全部退回默认值 → `llm.enabled=false` → **AI 静默关闭**

📌 为什么别的一道闸门抓不到：
    - `check-env-contract.py` 只管**基础设施类**变量（MYSQL/NACOS/REDIS…）
      —— LLM 是**可选的外部能力**，不属于基础设施，**故意不在它的范围内**
    - `check-hardcoded-secret.py` 只扫「密钥有没有被写死在仓库里」
      —— 这里的问题是「密钥**没被传进去**」，不是「密钥被写死了」

📌 **本闸门判三件事**（每个 compose 文件 × 每个 AI 服务）：
    1. **缺失**：应用读了这个 LLM_* 变量，部署层却没注入 → ERROR
    2. **名字对不上**：注入的变量名不在应用读取列表里 → ERROR（注入是摆设）
    3. **默认值漂移**：注入的默认值 ≠ 应用侧该变量的默认值 → ERROR

📌 **为什么第 3 条最要紧**：为了「不注入 = 用应用默认值」，compose 只能写成
    `LLM_PROVIDER: ${LLM_PROVIDER:-deepseek}` —— 于是**默认值被抄了第二遍**
    （应用 yml 一份、compose 一份）。抄第二遍 = 两份会漂移：
    改了应用默认值、忘了改 compose → **容器里的行为和本机不一样**，
    而且**没有任何闸门会发现**（本闸门就是来发现这个的）。

📌 **边界（声明清楚，避免误以为覆盖了全部）**：
    - 查 **docker-compose*.yml** 和 **k8s/*.yaml**（k8s 走 Secret + 字面量两套形式，
      判据见下面的 `k8s_env`）
    - 只查 **AI 服务**（erp-ai-assistant-service / erp-product-description-service）
    - 查的是**变量名与默认值**，**不查密钥本身有没有值**（那是 fail-closed 的事）
    - **不查 k8s Secret 是否真的被创建**（那是 deploy.sh 的职责，且需要真集群）

📌 判据必须匹配「代码实际怎么写」：
    应用侧是 `${VAR:default}`（**单冒号**），compose 侧是 `${VAR:-default}`（**双冒号**）
    —— 两者语法不同，这里**分别解析**，别用同一个正则硬套（规则 55/227）。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover
    yaml = None  # type: ignore

ROOT = Path(__file__).resolve().parents[1]

# ── 需要检查的 AI 服务（应用模块名 → compose 里的服务名）──────────────────
AI_SERVICES = {
    "erp-ai-assistant-service": "ai-assistant-service",
    "erp-product-description-service": "product-description-service",
    # 📌 **2026-11-18 新增**：定价服务也接了 LLM（建议原因）→ 部署层必须一样注入
    "erp-pricing-service": "pricing-service",
}
# ── k8s 清单里的 Deployment 名 = 应用模块名 ──────────────────────────────
AI_MODULES = set(AI_SERVICES.keys())

# ── 部署侧：`${VAR:-default}` / `${VAR-default}` / `${VAR}` ───────────────────
DEPLOY_PLACEHOLDER = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::?-([^}]*))?\}")

LLM_PREFIX = "LLM_"


def iter_placeholders(expr: str):
    """用**花括号配平**扫描出 expr 里所有 `${VAR:default}`（返回 (变量名, 默认值)）。

    📌 为什么不能用 `re.finditer(r"\$\{(\w+):(.*?)\}")`：
       非贪婪的 `.*?` 会**吃掉内层的右花括号** →
       `${LLM_API_KEY:${DEEPSEEK_API_KEY:${OPENAI_API_KEY:}}}` 被切成
       默认值=`${DEEPSEEK_API_KEY:${OPENAI_API_KEY:`（**截断**）→ 解析结果静默错误。
       → 判据必须匹配「配置实际怎么写」（规则 55）。
    """
    i = 0
    while i < len(expr):
        if not expr.startswith("${", i):
            i += 1
            continue
        end = _match_brace(expr, i)
        if end == -1:
            return
        inner = expr[i + 2:end]
        name, sep, default = inner.partition(":")
        if sep and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
            yield name, default
        i = end + 1


def _match_brace(expr: str, start: int) -> int:
    """从 `start`（指向 `${` 的 `$`）开始找**配平**的右花括号下标。

    📌 为什么不能只用正则（规则 92 的形态）：`${A:${B:}}` 里，
    非贪婪的 `\$\{(\w+):(.*?)\}` 会**吃掉内层的右花括号** →
    切出来的「默认值」是**截断的** → 解析结果静默错误（本轮实测：
    `${LLM_API_KEY:${DEEPSEEK_API_KEY:}}` 被解析成 `${DEEPSEEK_API_KEY:`）。
    → 判据必须匹配「代码实际怎么写」（规则 55）。
    """
    depth = 0
    i = start
    while i < len(expr):
        if expr.startswith("${", i):
            depth += 1
            i += 2
            continue
        if expr[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def resolve_nested(expr: str, depth: int = 0) -> str:
    """把 `${A:${B:fallback}}` 递归解析成最终默认值（花括号配平）。"""
    if depth > 5 or "${" not in expr:
        return expr

    out: list[str] = []
    i = 0
    while i < len(expr):
        if expr.startswith("${", i):
            end = _match_brace(expr, i)
            if end == -1:
                out.append(expr[i:])
                break
            inner = expr[i + 2:end]
            # 变量名 = 第一个冒号之前；其余是默认值（可能还是占位符）
            name, _, default = inner.partition(":")
            resolved = resolve_nested(default, depth + 1) if "${" in default else default
            out.append(resolved)
            i = end + 1
            continue
        out.append(expr[i])
        i += 1
    return "".join(out)


def app_llm_defaults(module: str) -> dict[str, str]:
    """从模块的 application.yml 里取 `llm:` 段读到的 LLM_* 变量及其默认值。"""
    yml = ROOT / module / "src" / "main" / "resources" / "application.yml"
    if not yml.is_file():
        return {}
    text = yml.read_text(encoding="utf-8")

    # 只取 `llm:` 这一段（缩进 2 空格的顶级键）
    lines = text.splitlines()
    block: list[str] = []
    in_llm = False
    for line in lines:
        if re.match(r"^llm:\s*$", line):
            in_llm = True
            continue
        if in_llm:
            if line.strip() and not line.startswith("  "):
                break
            block.append(line)
    if not block:
        return {}

    out: dict[str, str] = {}
    for line in block:
        if ":" not in line or line.strip().startswith("#"):
            continue
        value = line.split(":", 1)[1].strip()
        for var, default in iter_placeholders(value):
            if not var.startswith(LLM_PREFIX):
                continue
            out[var] = resolve_nested(default)
    return out


def compose_service_env(path: Path, service: str) -> dict[str, str] | None:
    """取 compose 文件里某个服务的 environment 映射（原始字符串值）。"""
    try:
        import yaml  # type: ignore
    except ImportError:
        print("❌ 缺少 PyYAML（pip install pyyaml）")
        raise SystemExit(2)

    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except Exception as exc:  # noqa: BLE001
        print(f"⚠️  {path.name} 解析失败：{exc}")
        return None

    services = data.get("services")
    if not isinstance(services, dict):
        return None
    svc = services.get(service)
    if not isinstance(svc, dict):
        return None
    env = svc.get("environment")
    if not isinstance(env, dict):
        return {}
    return {str(k): str(v) for k, v in env.items()}


def deploy_default(raw: str) -> tuple[str | None, str]:
    """返回 (变量名, 默认值)；raw 不是占位符时返回 (None, raw)。"""
    m = DEPLOY_PLACEHOLDER.fullmatch(raw.strip())
    if not m:
        return None, raw.strip()
    var, default = m.group(1), m.group(2)
    return var, ("" if default is None else default.strip())



def k8s_ai_envs() -> list[tuple[str, str, dict[str, str]]]:
    """扫 k8s/*.yaml，返回 [(文件, 部署名, env 映射)]（只含 AI 服务）。

    📌 k8s 的 env 有**两种形式**，必须都认（规则 100）：
        - `value: "xxx"`      → 字面量（默认值走这条）
        - `valueFrom: secretKeyRef` → 密钥（**默认值不存在**，不算漂移）
    """
    try:
        import yaml  # type: ignore
    except ImportError:
        print("❌ 缺少 PyYAML（pip install pyyaml）")
        raise SystemExit(2)

    out: list[tuple[str, str, dict[str, str]]] = []
    for path in sorted(ROOT.glob("k8s/*.yaml")):
        try:
            docs = list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
        except Exception as exc:  # noqa: BLE001
            print(f"⚠️  {path.name} 解析失败：{exc}")
            continue
        for doc in docs:
            if not isinstance(doc, dict) or doc.get("kind") not in ("Deployment", "StatefulSet"):
                continue
            name = ((doc.get("metadata") or {}).get("name") or "")
            if name not in AI_MODULES:
                continue
            tmpl = ((doc.get("spec") or {}).get("template") or {}).get("spec") or {}
            for c in tmpl.get("containers") or []:
                if not isinstance(c, dict):
                    continue
                env: dict[str, str] = {}
                for e in c.get("env") or []:
                    if not isinstance(e, dict):
                        continue
                    ename = str(e.get("name") or "")
                    if not ename.startswith(LLM_PREFIX):
                        continue
                    if "value" in e:
                        env[ename] = str(e["value"])
                    elif "valueFrom" in e:
                        # Secret 引用 → **没有默认值**（不参与漂移比对）
                        env[ename] = "\x00SECRET"
                out.append((path.name, name, env))
    return out


def main() -> int:
    findings: list[str] = []
    checked = 0

    compose_files = sorted(ROOT.glob("docker-compose*.yml"))
    if not compose_files:
        print("❌ 找不到任何 docker-compose*.yml")
        return 1

    for path in compose_files:
        for module, service in AI_SERVICES.items():
            app_defaults = app_llm_defaults(module)
            if not app_defaults:
                # 该模块没有 llm 段 → 不在检查范围
                continue
            env = compose_service_env(path, service)
            if env is None:
                continue  # 这个 compose 没部署该服务
            checked += 1

            injected = {k: v for k, v in env.items() if k.startswith(LLM_PREFIX)}

            for var, app_default in sorted(app_defaults.items()):
                if var not in injected:
                    findings.append(
                        f"[{path.name}:{service}] 应用读了 `{var}`，部署层**没有注入**\n"
                        f"      → 容器里退回默认值 → AI 静默关闭（`llm.enabled=false`）\n"
                        f"      → 修法：在该服务的 environment 里加 `{var}: ${{{var}:-<应用默认值>}}`"
                    )
                    continue

                _, dep_default = deploy_default(injected[var])
                if dep_default != app_default:
                    findings.append(
                        f"[{path.name}:{service}] `{var}` 的默认值**两边不一致**\n"
                        f"      → 应用侧（application.yml）：`{app_default}`\n"
                        f"      → 部署侧（compose）      ：`{dep_default}`\n"
                        f"      → 改了一边忘了另一边 → **容器行为和本机不一样**，且没人会发现"
                    )

            for var in sorted(injected):
                if var not in app_defaults:
                    findings.append(
                        f"[{path.name}:{service}] 注入了 `{var}`，但应用**根本不读**它\n"
                        f"      → 注入是摆设（规则 7/36）"
                    )

    # ── k8s 侧 ────────────────────────────────────────────────────────
    k8s_checked = 0
    for fname, dep_name, env in k8s_ai_envs():
        module = dep_name
        app_defaults = app_llm_defaults(module)
        if not app_defaults:
            continue
        k8s_checked += 1
        for var, app_default in sorted(app_defaults.items()):
            if var not in env:
                findings.append(
                    f"[{fname}:{dep_name}] 应用读了 `{var}`，k8s 清单**没有注入**\n"
                    f"      → Pod 里退回默认值 → AI 静默关闭（`llm.enabled=false`）"
                )
                continue
            val = env[var]
            if val == "\x00SECRET":
                continue  # 密钥走 Secret → 没有默认值可比
            if val != app_default:
                findings.append(
                    f"[{fname}:{dep_name}] `{var}` 的默认值**两边不一致**\n"
                    f"      → 应用侧（application.yml）：`{app_default}`\n"
                    f"      → 部署侧（k8s）          ：`{val}`\n"
                    f"      → 改了一边忘了另一边 → **Pod 行为和本机不一样**，且没人会发现"
                )
        for var in sorted(env):
            if var not in app_defaults:
                findings.append(
                    f"[{fname}:{dep_name}] 注入了 `{var}`，但应用**根本不读**它 → 注入是摆设（规则 7/36）"
                )

    # ── k8s Secret 的「名字/键」在 manifest 与 deploy.sh 之间必须一致 ──
    #    📌 **两个文件各写一遍** = 会漂移（规则 32 的镜像版）：
    #       manifest 说 `secretKeyRef: {name: X, key: Y}`，
    #       deploy.sh 说 `create secret generic X --from-literal=Y=...`
    #       → 改了一边 → Secret **建出来了**，但 Pod 里**取不到那个键**
    #       → `LLM_API_KEY` 不落地 → **AI 静默关闭**（而一切看起来正常）
    manifest_refs = set()
    # 📌 **Secret 有两个来源，两个都要认**（规则 100：等价形态必须一起认）：
    #    ① 清单里**自己声明**的 `kind: Secret`（如 `k8s/mysql.yaml` 的 `mysql-secret`）
    #    ② `k8s/deploy.sh` 里的 `kubectl create secret generic`（如 `erp-jwt-secret` / `erp-llm-secret`）
    declared_secrets: set[tuple[str, str]] = set()
    for path in sorted(ROOT.glob("k8s/*.yaml")):
        try:
            docs = list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
        except Exception as exc:  # noqa: BLE001
            # ⚠️ **不许静默吞掉**（规则 43/88）：本闸门自己崩了 = 静默失效，
            #    本地根本看不出来（规则 88）→ 直接判失败。
            findings.append(f"[k8s/{path.name}] 解析失败，**无法核对 Secret 引用**：{exc}")
            continue
        for doc in docs:
            if not isinstance(doc, dict):
                continue
            if doc.get("kind") == "Secret":
                sname = str((doc.get("metadata") or {}).get("name") or "")
                keys = set((doc.get("stringData") or {}).keys()) | set((doc.get("data") or {}).keys())
                for k in keys:
                    declared_secrets.add((sname, str(k)))
                continue
            tmpl = ((doc.get("spec") or {}).get("template") or {}).get("spec") or {}
            for c in tmpl.get("containers") or []:
                if not isinstance(c, dict):
                    continue
                for e in c.get("env") or []:
                    if not isinstance(e, dict):
                        continue
                    vf = e.get("valueFrom") or {}
                    ref = vf.get("secretKeyRef") if isinstance(vf, dict) else None
                    if isinstance(ref, dict) and ref.get("name"):
                        manifest_refs.add((str(ref["name"]), str(ref.get("key") or "")))

    deploy_sh = ROOT / "k8s" / "deploy.sh"
    if yaml is None:
        findings.append("缺少 PyYAML → **无法解析 k8s 清单** → 本闸门处于失效状态（不许静默跳过）")
    elif deploy_sh.is_file() and manifest_refs:
        sh = deploy_sh.read_text(encoding="utf-8")
        created: set[tuple[str, str]] = set()
        for m in re.finditer(
            r"create\s+secret\s+generic\s+([A-Za-z0-9._-]+)[^|]*?--from-literal=([A-Za-z0-9._-]+)=",
            sh,
            re.S,
        ):
            created.add((m.group(1), m.group(2)))
        available = created | declared_secrets
        for name, key in sorted(manifest_refs):
            if (name, key) in available:
                continue
            # 是不是别的 Secret 名/键拼错？
            near_key = sorted({k for n, k in available if n == name})
            hint = ""
            if near_key:
                hint = f"\n      → 已有的 `{name}` 用的是键 {near_key}，manifest 要的是 `{key}`"
            findings.append(
                f"[k8s Secret 引用核对] manifest 引用了 `{name}/{key}`，"
                f"但**没有任何地方创建这个「名+键」组合**\n"
                f"      → 来源①：k8s/*.yaml 里没有 `kind: Secret` 声明它\n"
                f"      → 来源②：k8s/deploy.sh 里没有 `create secret generic {name} --from-literal={key}=`\n"
                f"      → Pod **取不到那个键** → 对应变量不落地 → **能力静默关闭**（而一切看起来正常）{hint}"
            )

    if findings:
        print(f"❌ LLM 配置契约闸门：发现 {len(findings)} 处问题\n")
        for f in findings:
            print(f"  {f}")
        print(
            "\n📌 AI 能力要**在容器里真的能用**，部署层必须把应用读的 LLM_* 注入进去：\n"
            "   · 应用侧读 `${LLM_ENABLED}` / `${LLM_API_KEY}` / `${LLM_PROVIDER}` /\n"
            "     `${LLM_BASE_URL}` / `${LLM_MODEL}` → 部署层必须同名注入（规则 36/387）\n"
            "   · 默认值**必须两边一致**，否则容器和本机行为不同（且没人会发现）\n"
            "   · 密钥**不写默认值**（空即可）→ 不配就是 AI 关闭，不影响其他功能"
        )
        return 1

    print(
        f"✅ LLM 配置契约：{len(compose_files)} 个 compose × {checked} 个 AI 服务 + "
        f"k8s × {k8s_checked} 个 AI 部署 —— "
        f"应用读的 LLM_* 全部被同名注入，且默认值两边一致"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
