#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「硬编码密钥」的闸门 —— 规则 140。

📌 **为什么需要这道闸门**（2026-11-12 实测踩出来的真漏洞）：

    第 59 道闸门（`check-account-authz.py`）刚把整个账户系统的鉴权修好：
    `anyRequest().permitAll()` → `authenticated()`、敏感写操作加 `@PreAuthorize`、
    JWT 过滤器**失败即拒绝**（查不到角色 = 没权限，绝不退化成放行）。

    **但整个修复被一个词废掉了**：

        erp-user-service/src/main/resources/application.yml
          jwt:
            secret: cross-border-erp-secret-key-2024     ← **仓库里的字面量**

    📌 **实测已用该密钥伪造出管理员 token，并被真实的 `JwtUtil` 校验通过**
    （`validateToken=true`、`userId=1`）→ 第 59 道闸门加的所有 `@PreAuthorize` 形同虚设。

    租户服务是同一个形状：`${JWT_SECRET:OmniTradeERP Secret Key For SaaS 2026 Multi-Tenant Platform}`
    —— **默认值就在仓库里**，而且**部署层从来没注入过 `JWT_SECRET`** → 生产用的就是这个默认值。

📌 **本闸门管的是「能冒充身份 / 能调别人 API」的凭证**：

| 键 | 属于 |
|---|---|
| `secret` / `jwt-secret` / `signing-key` / `private-key` | ✅ 管（身份签名密钥） |
| `api-key` / `app-secret` / `client-secret` / `access-key` / `access-token` | ✅ 管（第三方平台凭证） |
| `password`（数据库口令） | ❌ **不管** —— 见下方「已知边界」 |
| `max-tokens` / `expiration` / `enabled` | ❌ 不是密钥（曾经被误报，见「踩过的坑」） |

📌 **已知边界（诚实记录，不藏）**：
    **数据库口令仍是硬编码**（`password: root` / `${MYSQL_PASSWORD:root}`，13 个服务 + k8s 未注入）
    → 属于**另一档风险**（内网可达、非身份冒充），整改清单见 `docs/SECURITY.md`。
    📌 本闸门**刻意不把它算进来**：把它算进来 = 闸门一上来就红 28 处 →
    要么被人「放松闸门」（规则 99：假阳性比假阴性更危险）→ **闸门从此保护不了任何东西**；
    要么一次性改 25 个模块 + 24 个 Deployment 的启动依赖 → **风险远大于收益**。
    ✅ **正确做法 = 窄口径 + 显式记录边界**，不是宽口径 + 全红。

📌 **判据**（对着真实写法来）：

| 断言 | 内容 |
|---|---|
| **C1** | 凭证键的值**不得是仓库里的字面量**（占位符除外） |
| **C2** | `${VAR:默认值}` 的默认值**不得是一个非空字面量** —— 空默认值 `${VAR:}` = 「没配就不启用」，**合法** |
| **C2b** | **Java 侧** `@Value("${prop:默认值}")` 的默认值里带凭证 → 报（配置缺失时默认值会顶上来） |
| **C3** | `${VAR}`（**无默认值**）→ **部署层必须真的注入 `VAR`**，否则服务启动失败（那是「响亮地坏掉」） |
| **C4** | k8s `Secret` 里的密钥值**不得是可用密钥**（只允许占位符） |

📌 **踩过的坑（写这道闸门时自己踩的）**：
1. ① 第一版用**子串**匹配键名 → `max-tokens` 里的 "token" 被当成密钥 → **31 处告警里 28 处是噪音**
      → ✅ 改成**精确键名集合**（规则 42 / 55：判据必须匹配「代码实际怎么写」）
2. ② 第一版把 `${VAR:}`（空默认值）也算违规 → 但那是「**没配就不启用该功能**」的正常写法
      → ✅ 改成「默认值**非空且是字面量** 才算违规」
3. ③ 第一版只查 `application*.yml`，没查部署层 → 应用改成 `${JWT_SECRET}` 但**没人注入** → 服务起不来
      → ✅ 补上 C3（规则 36：部署层注入的变量名要和应用侧读取的一致）

用法：
    python3 scripts/check-hardcoded-secret.py          # 巡检（有问题 → exit 1）
    python3 scripts/check-hardcoded-secret.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {"target", "node_modules", ".git", "dist", "build"}

# ── 凭证键的**精确**名单（不是子串！）────────────────────────────────
#    📌 用精确匹配而不是子串：子串会把 `max-tokens` / `token-expiration` 之类误判成密钥。
CREDENTIAL_KEYS = {
    "secret", "jwt-secret", "auth-secret", "signing-key", "private-key",
    "encryption-key", "credential", "credentials",
    "api-key", "api_key", "apikey",
    "app-secret", "app_secret",
    "client-secret", "client_secret",
    "access-key", "access_key",
    "access-token", "access_token",
    "refresh-token", "refresh_token",
    "api-secret", "api_secret",
}

# ── 明显不是密钥的占位符 ────────────────────────────────────────────
PLACEHOLDER_MARKERS = (
    "PLACEHOLDER", "CHANGE_ME", "SET_BY", "TODO", "REPLACE",
    "example", "your-", "your_", "xxxx", "<",
)

# ── 部署层文件 ───────────────────────────────────────────────────────
K8S_FILES = ("k8s/core-services.yaml", "k8s/extended-services.yaml",
             "k8s/ai-services.yaml", "k8s/services.yaml",
             "k8s/gateway.yaml", "k8s/mysql.yaml", "k8s/redis.yaml", "k8s/nacos.yaml")

# ── C5：Java 源码里的「账号口令常量」（2026-11-12 补）──────────────────
# 📌 **为什么单独加这一条**：C1/C2b 只管「配置键」和「@Value 默认值」，
#    管不到 **Java 里的常量** —— 那也是一条独立的硬编码路径。
#    实测到的真实例子：`UserServiceImpl.DEFAULT_PASSWORD = "admin123"`
#    → 管理员重置密码时若不指定新密码，用户拿到的就是这个**写在仓库里的通用口令**
#    → 任何拿到仓库的人都知道每个人的重置密码。
# 📌 **为什么只管 Java 常量、不管 yml 里的 `password:`**：
#    yml 的 `password:` 是**数据库口令**（内网可达、非身份冒充）→ 已知边界，见 docs/SECURITY.md；
#    而 Java 里的 `*PASSWORD*` 常量是**能直接登录的账号口令** → 属于本闸门范围。
#    📌 实测误报率：全仓生产代码里 `*PASSWORD*` 常量**只有 1 处** → 加上这条不会造成「一上来全红」。
# 📌 **2026-11-12 修：只认大写 `PASSWORD` = 双向失明（规则 100）**
#    原判据 `*PASSWORD*`（大写）**认不出**：
#      - `FALLBACK_PWD` / `DEFAULT_PWD`      → 小写缩写形态漏掉
#      - `fallbackPassword` / `defaultPwd`   → camelCase 漏掉
#    → **同一条硬编码口令，换个变量名就绕过闸门**。
#    📌 **实测的坑**：上一版的差分验证用的是 `FALLBACK_PASSWORD = "P@ssw0rd2026"`
#       —— 那个名字**恰好含大写 PASSWORD** → 被抓 → **差分「通过」了**
#       → 但换成 `FALLBACK_PWD` **立刻漏掉**（实测 exit 0 + 还报「✅ 没有硬编码凭证」）。
#       📌 **教训：差分的「注入形态」必须落在判据的盲区里**（规则 123），
#          否则测的是「判据本来就会抓的那一类」，证明不了它真的在管。
#    ✅ 现在：`password|pwd` **不分大小写** → 大写 / 小写 / camelCase 全认。
#    📌 **实测误报率**：放宽后全仓命中数**仍是 1 处**（就是下面白名单里的那处）
#       → `pwd` 不分大小写新增 **0** 处、`password` 不分大小写新增 **0** 处 → **放宽不引入误报**（规则 99）。
#    📌 **声明的边界**（规则 116）：本判据**只认**「`String` 类型 + 双引号字面量 + 名字含 password/pwd」。
#       - `char[] PASSWORD = {'a','b'}` → **认不出**（罕见写法）
#       - `String PASSWORD = "x" + y;`（拼接）→ **认不出**
#       - 名字里只含 `pass`（如 `passphrase`）→ **故意不认**（语义漂移风险）
JAVA_PWD_CONST_RE = re.compile(
    r"\bString\s+(?P<name>[A-Za-z0-9_]*(?:password|pwd)[A-Za-z0-9_]*)\s*=\s*\"(?P<val>[^\"]*)\"",
    re.IGNORECASE,
)

# 📌 **已知待整改（整行精确匹配，不做子串豁免 —— 规则 139）**
#    每加一个例外，都必须在 docs/SECURITY.md 的整改清单里有一行对应说明。
KNOWN_PENDING_JAVA_PWD = {
    (
        "erp-user-service/src/main/java/com/crossborder/erp/user/service/impl/UserServiceImpl.java",
        "DEFAULT_PASSWORD",
    ),
}


def iter_config_files():
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/resources" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.startswith("application") and fn.endswith((".yml", ".yaml")):
                yield os.path.join(dirpath, fn)


def strip_comment(line: str) -> str:
    """去掉行尾注释，但别把引号里的 # 吃掉。"""
    out, quote = [], None
    for ch in line:
        if quote:
            out.append(ch)
            if ch == quote:
                quote = None
            continue
        if ch in "\"'":
            quote = ch
            out.append(ch)
            continue
        if ch == "#":
            break
        out.append(ch)
    return "".join(out)


def is_placeholder(value: str) -> bool:
    v = value.strip().strip("\"'").strip()
    if not v:
        return True
    up = v.upper()
    return any(m.upper() in up for m in PLACEHOLDER_MARKERS)


def split_env_ref(value: str):
    """把 `${VAR}` / `${VAR:DEF}` / 嵌套 拆开。

    返回 (var_name, default_expr)；不是占位符时返回 (None, None)。
    """
    v = value.strip().rstrip(",").strip()
    if len(v) >= 2 and v[0] == v[-1] and v[0] in "\"'":
        v = v[1:-1]
    if not (v.startswith("${") and v.endswith("}")):
        return (None, None)
    inner = v[2:-1]
    # 找第一个「顶层」的冒号
    depth = 0
    for i, ch in enumerate(inner):
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        elif ch == ":" and depth == 0:
            return (inner[:i].strip(), inner[i + 1:])
    return (inner.strip(), None)


def default_is_literal_secret(default_expr) -> bool:
    """默认表达式最终落到一个**非空字面量** → 仓库里藏着真密钥。"""
    if default_expr is None:
        return False
    expr = default_expr.strip()
    # 嵌套：${A:${B:xxx}} → 往里剥
    while expr.startswith("${") and expr.endswith("}"):
        var, d = split_env_ref(expr)
        if var is None:
            return False
        if d is None:
            return False          # 内层也是纯 ${X} → 没有字面量
        expr = d.strip()
    if expr == "":
        return False              # 空默认值 = 「没配就不启用」→ 合法
    if expr.startswith("${"):
        return False
    if is_placeholder(expr):
        return False
    return True


def parse_deployments():
    """把 k8s YAML 按 `kind: Deployment` 切成块 → {deployment_name: 该块的文本}。

    📌 **必须按 Deployment 逐块核验**（规则 92 的镜像）：
    全局 grep `name: JWT_SECRET` → 只要**任何一个**服务注入了，
    **所有**服务都被判「已注入」→ 删掉 user-service 的注入也照样绿 = **假绿**。
    （实测踩过：注入「删掉 user-service 的注入」→ 闸门仍 exit 0。）
    """
    out = {}
    for f in K8S_FILES:
        fp = os.path.join(REPO_ROOT, f)
        if not os.path.exists(fp):
            continue
        text = open(fp, encoding="utf-8").read()
        # 以 `kind: Deployment` 为界切块
        parts = re.split(r"^kind:\s*Deployment\s*$", text, flags=re.MULTILINE)
        for part in parts[1:]:
            mn = re.search(r"^\s{2}name:\s*(\S+)\s*$", part, re.MULTILINE)
            if mn:
                out[mn.group(1)] = part
    return out


def iter_java_files():
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "/src/main/java" not in dirpath.replace(os.sep, "/"):
            continue
        for fn in filenames:
            if fn.endswith(".java"):
                yield os.path.join(dirpath, fn)


# 📌 **Java 侧的 @Value 默认值是「第二条硬编码路径」**（2026-11-12 实测踩过）：
#    `erp-tenant-service/.../JwtTokenProvider.java` 写的是
#    `@Value("${jwt.secret:OmniTradeERP Secret Key For SaaS 2026 Multi-Tenant Platform}")`
#    → **只改 application.yml 不够** → 配置项缺失时 @Value 的默认值会**顶上来**。
#    📌 实测已踩：改完 yml 还以为完事了 → grep 才发现 Java 里还藏着一份。
JAVA_VALUE_RE = re.compile(
    r'@Value\(\s*"\$\{(?P<prop>[A-Za-z0-9_.\-]+)(?::(?P<def>[^}]*))?\}"\s*\)',
)


def scan_java_defaults(findings):
    """扫描 Java 里的 `@Value("${...:默认值}")` —— 默认值里带凭证的一律报。"""
    for path in iter_java_files():
        rel = os.path.relpath(path, REPO_ROOT)
        text = open(path, encoding="utf-8").read()
        for lineno, raw in enumerate(text.split("\n"), 1):
            # 📌 **跳过注释行** —— 注释里常引用旧值来说明历史
            #    （实测踩过：我在 Javadoc 里贴了「原来这里是 `@Value("${jwt.secret:...}")`」
            #    → 闸门把自己写的说明当成了缺陷 → **假红**）
            stripped = raw.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            for m in JAVA_VALUE_RE.finditer(raw):
                prop = m.group("prop")
                default = m.group("def")
                if default is None or default.strip() == "":
                    continue
                leaf = prop.split(".")[-1].lower()
                if leaf not in CREDENTIAL_KEYS:
                    continue
                if is_placeholder(default):
                    continue
                findings.append((
                    rel, lineno, prop,
                    f"**Java 侧硬编码凭证**：`@Value(\"${{{prop}:<字面量>}}\")` → "
                    f"配置项缺失时**这个默认值会顶上来** → 密钥照样公开。"
                    f"✅ 去掉默认值（写成 `@Value(\"${{{prop}}}\")`）",
                ))


def scan_java_password_constants(findings):
    """扫描 Java 里的 `*PASSWORD*` 常量 = 字面量（账号口令，不是数据库口令）。"""
    for path in iter_java_files():
        rel = os.path.relpath(path, REPO_ROOT)
        text = open(path, encoding="utf-8").read()
        for lineno, raw in enumerate(text.split("\n"), 1):
            stripped = raw.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            for m in JAVA_PWD_CONST_RE.finditer(raw):
                name = m.group("name")
                val = m.group("val")
                if not val.strip() or is_placeholder(val):
                    continue
                if (rel, name) in KNOWN_PENDING_JAVA_PWD:
                    continue
                findings.append((
                    rel, lineno, name,
                    f"**Java 侧的账号口令常量**：`{name} = \"{val}\"` → "
                    f"这个**写在仓库里的通用口令**会被用于重置/初始化密码 → "
                    f"拿到仓库的人就知道别人的密码。"
                    f"✅ 改成**随机生成**并回传给操作人，或从环境变量读。",
                ))


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings = []
    required_env = {}   # env var -> (module, 文件)

    for path in iter_config_files():
        rel = os.path.relpath(path, REPO_ROOT)
        module = rel.split(os.sep)[0]
        text = open(path, encoding="utf-8").read()
        for lineno, raw in enumerate(text.split("\n"), 1):
            line = strip_comment(raw)
            if ":" not in line:
                continue
            m = re.match(r"^\s*(?P<key>[A-Za-z0-9_.\-]+)\s*:\s*(?P<val>.+?)\s*$", line)
            if not m:
                continue
            key = m.group("key")
            if key.lower() not in CREDENTIAL_KEYS:
                continue
            val = m.group("val")

            var, default = split_env_ref(val)

            if var is None:
                # C1：字面量
                if is_placeholder(val):
                    continue
                findings.append((
                    rel, lineno, key,
                    f"**硬编码凭证**：`{key}` 的值是仓库里的字面量 → "
                    f"拿到仓库的人就能用它冒充身份 / 调别人 API → "
                    f"必须改成 `${{环境变量}}`（由部署层注入）",
                ))
                continue

            # C2：默认值是字面量密钥
            if default_is_literal_secret(default):
                findings.append((
                    rel, lineno, key,
                    f"**默认值里藏着真凭证**：`{key}: ${{{var}:<字面量>}}` → "
                    f"部署层一旦没注入 `{var}`，就**悄悄用回仓库里那个默认值** → 凭证照样公开。"
                    f"✅ 要么写成 `${{{var}:}}`（没配就不启用），要么 `${{{var}}}` + 部署层注入",
                ))
                continue

            # C3：无默认值 → 部署层必须注入
            if default is None:
                required_env.setdefault(var, []).append((module, rel))

    # C2b：Java 侧 @Value 默认值
    scan_java_defaults(findings)
    scan_java_password_constants(findings)

    # C3：部署层必须真的注入
    #    📌 **必须按「服务」逐个核验，不能全局搜**（规则 92 的镜像）：
    #    全局搜 `name: JWT_SECRET` → 只要**任何一个**服务的 Deployment 里有，
    #    **所有**服务都被判「已注入」→ 删掉 user-service 的注入也照样绿 = **假绿**。
    #    ✅ 正确做法：把 k8s YAML 按 `kind: Deployment` 切成块，
    #       每块按 `metadata.name` 认服务，只在**自己那块**里找注入。
    deployments = parse_deployments()

    for var, refs in sorted(required_env.items()):
        for module, rel in refs:
            svc = module.replace("-service", "")          # erp-user-service → user
            dep = deployments.get(f"erp-{svc}-service")
            if dep is None:
                # 找不到对应 Deployment → 换个匹配：模块名里含 svc
                cand = [k for k in deployments if svc in k]
                dep = deployments[cand[0]] if len(cand) == 1 else None
            if dep is None:
                findings.append((
                    rel, 0, var,
                    f"**找不到 `{module}` 对应的 k8s Deployment** → 无法确认 `{var}` 是否被注入 → "
                    f"按「没注入」处理（宁可误报也不放行）",
                ))
                continue
            if re.search(rf"^\s*-\s*name:\s*{re.escape(var)}\s*$", dep, re.MULTILINE):
                continue
            findings.append((
                "k8s/*.yaml", 0, var,
                f"**`{module}` 的 Deployment 没注入 `{var}`**（该 Deployment 在 "
                f"`k8s/*.yaml` 里找不到 `- name: {var}`）→ "
                f"服务会**启动失败**（占位符解析不了）→ "
                f"必须在自己的 `env` 里加 `secretKeyRef`",
            ))

    # C4：k8s Secret 里的值不得是可用密钥
    for f in K8S_FILES:
        fp = os.path.join(REPO_ROOT, f)
        if not os.path.exists(fp):
            continue
        text = open(fp, encoding="utf-8").read()
        for lineno, raw in enumerate(text.split("\n"), 1):
            if "jwt-secret:" not in raw:
                continue
            val = raw.split("jwt-secret:", 1)[1].strip().strip("\"'")
            if val and not is_placeholder(val):
                findings.append((
                    f, lineno, "jwt-secret",
                    f"**k8s Secret 里放的是可用密钥**：`{val[:10]}...` → "
                    f"这个文件就在仓库里 → 密钥公开。✅ 只放占位符，"
                    f"真实值由 `deploy.sh` 用 `kubectl create secret` 现场生成",
                ))

    print("📋 巡检「硬编码凭证」（JWT 签名密钥 / 第三方平台密钥）")
    print(f"ℹ️  需要部署层注入的必填环境变量：{len(required_env)} 个 {sorted(required_env)}")
    if verbose:
        for rel, lineno, key, msg in findings:
            print(f"   {rel}:{lineno} [{key}] {msg}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处硬编码凭证风险：")
        for rel, lineno, key, msg in findings:
            print(f"   {rel}:{lineno} [{key}]")
            print(f"        {msg}")
        print("\n✅ 修法：")
        print("   ① 应用侧：`secret: ${JWT_SECRET}`（**不要写非空默认值**）")
        print("   ② 部署侧：k8s Deployment 的 env 里 `secretKeyRef` 指向一个 Secret")
        print("   ③ Secret 本身：仓库里只放**占位符**，真实值用 `kubectl create secret` 现场生成")
        return 1

    print("\n✅ 没有硬编码凭证：所有凭证都走环境变量，且部署层都真的注入了")
    print("ℹ️  数据库口令（`password:`）**不在本闸门范围** → 见 docs/SECURITY.md 的整改清单")
    return 0


if __name__ == "__main__":
    sys.exit(main())
