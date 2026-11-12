#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""账户系统鉴权闸门 —— 「谁能做什么」的守门人。

📌 **为什么需要这道闸门**（2026-11-12 真实踩过的坑）：

    erp-user-service 的 SecurityConfig 写的是 `anyRequest().permitAll()`
    → **整个账户系统一个请求都不用带 token**
    → 而 `POST /api/user/{id}/reset-password` 还接受**调用方指定**的 `defaultPassword`
    → **任何人都能把任意用户的密码改成自己知道的值**
    → 再加上 `POST /api/v1/auth/register` 是 permitAll → 注册一个账号就能改管理员密码
    = **未认证即可完成账号接管**（Critical）
    → 而且网关是 `type: LoadBalancer` + Ingress → 外部可达。

📌 **本闸门查三件事**：

| 断言 | 内容 |
|---|---|
| **C1** | 账户服务（`erp-user-service`）的 `SecurityConfig` **不许**出现 `anyRequest().permitAll()` |
| **C2** | 敏感写操作端点（重置密码 / 删人 / 建人 / 改状态 / 分配角色 / 角色增删改 / 部门增删改）**必须**有 `@PreAuthorize` |
| **C3** | `@PreAuthorize` 里用到的**角色编码必须真实存在于种子数据**的 `t_role.role_code` 里 |

📌 **C3 是实战里救过命的**（规则 76 / 82：静态追踪「前端 → 网关 → 后端 → 表」的坑）：
    我第一版写的是 `hasRole('ADMIN')`，而**种子数据里的角色编码是 `SUPER_ADMIN`**
    → 上线后**管理员会被自己的代码锁在门外**（所有管理操作 403）。
    → 静态检查发现不了（两边都「看起来对」），**只有把「代码里的角色码」和「种子里的角色码」摆在一起比**才看得见。
    📌 所以 C3 是**拿种子数据当基准**，不是拿代码里的另一处当基准（规则 128：基线要锚在独立事实源）。

📌 **为什么只覆盖 erp-user-service**：
    全仓 27 个模块里**只有 2 个**引了 `spring-boot-starter-security`
    （`erp-user-service` / `erp-tenant-service`），其余 25 个**连认证框架都没有**。
    📌 **不能给它们统一引 starter**：Spring Boot 一旦检测到 security 就在 classpath 上，
    会启用默认安全配置 → **所有接口都要求 Basic Auth** → 全线 401（比现在还糟）。
    ✅ 所以逐个服务加，**先从账户系统开始**（它管着密码哈希和账号）。
    📌 **未加固清单**（本闸门的 `PENDING` 白名单，每加一个服务就删一个）：
        见下方 PENDING 常量 —— **白名单只允许变短，不许变长**（变长 = 新开一个没防护的口子）。

用法：
    python3 scripts/check-account-authz.py           # 巡检（有问题 → exit 1）
    python3 scripts/check-account-authz.py --verbose # 打印每处判定细节
"""

from __future__ import annotations

import glob
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── 账户系统（必须已加固）────────────────────────────────────────────
ACCOUNT_SERVICE = "erp-user-service"

# 📌 **未加固清单**：还没引入 security starter 的服务。
#    **只允许变短**（每加固一个服务就删一个）；变长必须先写清楚理由。
PENDING = {
    "erp-tenant-service",   # 管理端 /api/tenant/** 目前 permitAll（租户自己的 JWT 过滤器只管 /api/v1/tenant/**）
    "erp-order-service", "erp-product-service", "erp-inventory-service",
    "erp-warehouse-service", "erp-purchase-service", "erp-supplier-service",
    "erp-customer-service", "erp-finance-service", "erp-logistics-service",
    "erp-ai-assistant-service", "erp-analytics-service",
    "erp-anomaly-detection-service", "erp-inventory-alert-service",
    "erp-inventory-prediction-service", "erp-platform-service",
    "erp-tenant-service-mgmt", "erp-social-commerce-service",
    "erp-reporting-service", "erp-product-description-service",
    "erp-product-recommendation-service", "erp-gateway",
    "erp-common", "erp-assembly",
}

# ── 敏感写操作端点：必须带 @PreAuthorize ─────────────────────────────
# 📌 判据 = **方法上的注解**，不看方法名（规则 55：判据要匹配代码实际怎么写）
SENSITIVE_PATTERNS = [
    ("UserController", r'resetPassword\s*\('),
    ("UserController", r'create\s*\(\s*@RequestBody\s+UserVO'),
    ("UserController", r'delete\s*\(\s*@PathVariable'),
    ("UserController", r'updateStatus\s*\('),
    ("UserController", r'assignRoles\s*\('),
    ("UserController", r'assignDepartments\s*\('),
    ("RoleController", r'create\s*\(\s*@RequestBody\s+RoleVO'),
    ("RoleController", r'delete\s*\(\s*@PathVariable'),
    ("RoleController", r'assignPermissions\s*\('),
    ("DepartmentController", r'create\s*\(\s*@RequestBody\s+DepartmentVO'),
    ("DepartmentController", r'delete\s*\(\s*@PathVariable'),
]

# ── 种子数据里的角色编码（独立事实源）──────────────────────────────
SEED_SQL_FILES = ["database/init.sql", "database/seed-data.sql", "database/user-service.sql"]


def read(path: str) -> str:
    try:
        with open(os.path.join(REPO_ROOT, path), encoding="utf-8") as f:
            return f.read()
    except Exception:
        return ""


def strip_comments(text: str) -> str:
    """剥掉 // 与 /* */ 注释（**认字符串字面量**）。

    📌 **必须剥**（规则 118 的教训）：修复说明的 Javadoc 里就写着
    `{@code anyRequest().permitAll()}` → 不剥注释的正则会**命中注释里的坏写法**
    → 报一个**根本不存在的**假红。

    📌 **但不能用简单的正则剥**（实测踩过）：
      `re.sub(r"/\*.*?\*/", "", text)` 会把**字符串字面量里的 `/**`** 当成块注释开头
      —— 本项目正好有 `"/api/v1/auth/**"` 和 `"/actuator/**"` 两条白名单路由
      → 剥注释时从那里一路吃到文件后面 → **真正的代码被当成注释删掉了**
      → 判据「找不到 anyRequest().authenticated()」→ **假红**。
      ✅ 所以这里用**状态机**：认字符串/字符字面量，只在代码态里处理注释。
      📌 同族坑：注释里写着「豁免要写一行 `TOKEN`」→ 豁免判据把它当真 → 自己豁免自己（规则 138）。
    """
    out = []
    i = 0
    n = len(text)
    state = "code"          # code | line | block | str | chr
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "code":
            if ch == "/" and nxt == "/":
                state = "line"
                i += 2
                continue
            if ch == "/" and nxt == "*":
                state = "block"
                i += 2
                continue
            if ch == '"':
                state = "str"
            elif ch == "'":
                state = "chr"
            out.append(ch)
            i += 1
            continue
        if state == "line":
            if ch == "\n":
                state = "code"
                out.append(ch)
            i += 1
            continue
        if state == "block":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 2
                continue
            i += 1
            continue
        # 字符串 / 字符字面量：原样保留，但处理转义
        if state in ("str", "chr"):
            if ch == "\\":
                out.append(ch)
                if i + 1 < n:
                    out.append(text[i + 1])
                i += 2
                continue
            if (state == "str" and ch == '"') or (state == "chr" and ch == "'"):
                state = "code"
            out.append(ch)
            i += 1
            continue
    return "".join(out)


def seed_role_codes() -> set[str]:
    """从种子 SQL 里取出 t_role.role_code 的全部取值。"""
    codes: set[str] = set()
    for f in SEED_SQL_FILES:
        text = read(f)
        # INSERT INTO `t_role` (...) VALUES (1, 'SUPER_ADMIN', ...)
        for m in re.finditer(r"INSERT\s+INTO\s+`?t_role`?\s*\([^)]*\)\s*VALUES\s*(.+?);",
                             text, re.S | re.I):
            block = m.group(1)
            for tup in re.findall(r"\(([^()]*)\)", block):
                parts = [p.strip().strip("'\"") for p in tup.split(",")]
                # 找到那个「像角色编码」的值：大写字母 + 下划线，且不是数字
                for p in parts:
                    if re.fullmatch(r"[A-Z][A-Z_]{1,30}", p):
                        codes.add(p)
                        break
    return codes


def java_files(pattern: str):
    for p in glob.glob(os.path.join(REPO_ROOT, pattern), recursive=True):
        if "/target/" in p:
            continue
        yield p


def main() -> int:
    verbose = "--verbose" in sys.argv
    findings: list[str] = []

    # ── C1：账户服务不许 permitAll ────────────────────────────────
    sec_path = f"{ACCOUNT_SERVICE}/src/main/java/com/crossborder/erp/user/config/SecurityConfig.java"
    sec_text = read(sec_path)
    sec_code = strip_comments(sec_text)   # 📌 剥注释后再判（规则 118）
    if not sec_text:
        findings.append(f"C1: 找不到 {sec_path} —— 账户服务的鉴权配置不见了？")
    else:
        if re.search(r"anyRequest\s*\(\s*\)\s*\.\s*permitAll\s*\(\s*\)", sec_code):
            findings.append(
                "C1: `SecurityConfig` 出现 `anyRequest().permitAll()` → "
                "**整个账户系统一个请求都不用带 token** = 未认证即可接管账号"
            )
        if not re.search(r"anyRequest\s*\(\s*\)\s*\.\s*authenticated\s*\(\s*\)", sec_code):
            findings.append(
                "C1: `SecurityConfig` 里找不到 `anyRequest().authenticated()` → "
                "非白名单接口没有默认要求认证"
            )
        if "AuthenticationEntryPoint" not in sec_code:
            findings.append(
                "C1: `SecurityConfig` 没配 AuthenticationEntryPoint → "
                "未认证请求会返回 **403 + 空 body**（前端看不到任何错误信息）"
            )
        if "UserJwtAuthenticationFilter" not in sec_code:
            findings.append(
                "C1: `SecurityConfig` 没注册 JWT 过滤器 → 认证上下文永远是空的"
            )

    # ── C2：敏感写操作必须有 @PreAuthorize ─────────────────────────
    # 📌 **判据怎么写**（规则 55 / 100：判据必须匹配「代码实际怎么写」）：
    #   方法签名**可能跨行**（`resetPassword(@PathVariable Long id,\n  @RequestParam(...) String p) {`）
    #   → 用「单行正则 + `{` 同行」的写法**会漏掉一半方法**（实测踩过：
    #     注入「删掉 resetPassword 的 @PreAuthorize」→ 闸门 exit 0 = **假绿**）。
    #   ✅ 所以改成：先定位方法声明（允许签名跨行）→ 再**向前逐行**收集它的注解。
    checked = 0
    blind_spots = []
    ctrl_dir = f"{ACCOUNT_SERVICE}/src/main/java/com/crossborder/erp/user/controller"
    for ctrl_name, method_pat in SENSITIVE_PATTERNS:
        path = os.path.join(REPO_ROOT, ctrl_dir, f"{ctrl_name}.java")
        text = strip_comments(read(os.path.relpath(path, REPO_ROOT)))
        if not text:
            continue
        decl_re = re.compile(
            r"(?:public|protected)\s+[\w<>,\[\]\.\s]*?\b" + method_pat,
            re.S)
        hits = list(decl_re.finditer(text))
        if not hits:
            # 📌 **命中 0 处** → 先怀疑「判据根本没在读代码」（规则 127），而不是「没这个问题」
            blind_spots.append(f"{ctrl_name} 的判据 `{method_pat}` **一个方法都没匹配到**")
            continue
        for m in hits:
            checked += 1
            # 向前逐行收集注解（跳过空行；遇到非注解行就停）
            before = text[:m.start()].split("\n")
            annotations = []
            for line in reversed(before):
                st = line.strip()
                if not st:
                    continue
                if st.startswith("@"):
                    annotations.append(st)
                    continue
                break
            if not any(a.startswith("@PreAuthorize") for a in annotations):
                line_no = text.count("\n", 0, m.start()) + 1
                findings.append(
                    f"C2: {ctrl_name}.java:{line_no} 敏感写操作**没有** @PreAuthorize → "
                    f"任何登录用户都能调（普通用户也能重置别人的密码 / 删人）"
                )
            if verbose:
                print(f"   {ctrl_name}: {m.group(0).strip()[:70]}  注解={annotations[:3]}")
    for b in blind_spots:
        findings.append(f"C2-盲区: {b} → 判据本身可能已经失效（规则 127）")

    # ── C4：permitAll 白名单路径必须**真的对应一个 controller 映射** ──
    # 📌 **实战救过命的**（规则 5 / 76）：
    #    白名单写成 `/api/v1/auth/**`，而服务侧真实路径是 `/auth/**`
    #    （网关 `Path=/api/auth/**` + `StripPrefix=1` → 剥掉 `/api`）
    #    → 白名单**一个都匹配不上** → **登录被拦成 401 → 全线没人能登录**。
    #    → `/api/v1/auth/**` 是 **erp-tenant-service** 的路径（那条路由 StripPrefix=0），
    #      **两个服务不是一回事** → 照抄就错。
    #    📌 判据：白名单路径 = **controller 的 @RequestMapping**，不是网关路径。
    whitelist = re.findall(r"requestMatchers\(\s*\"([^\"]+)\"", sec_code)
    ctrl_prefixes = set()
    for path in java_files(f"{ACCOUNT_SERVICE}/src/main/java/**/controller/*.java"):
        text = strip_comments(open(path, encoding="utf-8").read())
        for m in re.finditer(r"@RequestMapping\s*\(\s*\"([^\"]+)\"", text):
            ctrl_prefixes.add(m.group(1))
    for wl in whitelist:
        if not wl.startswith("/"):
            continue
        if wl.startswith("/actuator"):
            continue          # Spring Boot 自带，不在 controller 里
        base = wl.rstrip("*").rstrip("/")
        if not base:
            continue
        matched = any(cp == base or cp.startswith(base + "/") or base.startswith(cp + "/")
                       for cp in ctrl_prefixes)
        if not matched:
            findings.append(
                f"C4: 白名单 `{wl}` **匹配不上任何 controller 映射**"
                f"（本服务的类级映射只有 {sorted(ctrl_prefixes)}）→ "
                f"该路径会被当成「需要认证」→ **登录/注册直接 401 → 全线没人能登录**"
            )

    # ── C3：@PreAuthorize 里的角色码必须真实存在 ────────────────────
    seed_codes = seed_role_codes()
    used_codes: set[str] = set()
    for path in java_files(f"{ACCOUNT_SERVICE}/src/main/java/**/*.java"):
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r"@PreAuthorize\s*\(\s*\"([^\"]+)\"", text):
            expr = m.group(1)
            for role in re.findall(r"has(?:Any)?Role\s*\(\s*'([^']+)'", expr):
                used_codes.add(role)
    # 📌 **语义**：`hasAnyRole(A,B)` 只要**其中一个**编码真实存在即可（两个都写是为了兼容
    #    不同环境的历史角色名）；但 `hasRole(A)` / `hasAnyRole(A,B)` 里**一个都不存在** → 报错。
    unknown_exprs = []
    for path in java_files(f"{ACCOUNT_SERVICE}/src/main/java/**/*.java"):
        text = strip_comments(open(path, encoding="utf-8").read())
        for m in re.finditer(r"@PreAuthorize\s*\(\s*\"([^\"]+)\"", text):
            expr = m.group(1)
            roles = re.findall(r"has(?:Any)?Role\s*\(\s*'([^']+)'", expr)
            if roles and not any(r in seed_codes for r in roles):
                unknown_exprs.append((expr, roles))
    if unknown_exprs:
        for expr, roles in unknown_exprs:
            findings.append(
                f"C3: @PreAuthorize({expr}) 用到的角色编码 {roles} **一个都不在**种子数据的 "
                f"t_role.role_code 里（种子里只有 {sorted(seed_codes)}）→ "
                f"上线后**管理员会被自己的代码锁在门外**（全部 403）"
            )

    print("📋 巡检「账户系统鉴权」")
    print(f"ℹ️  种子数据里的角色编码：{sorted(seed_codes) or '（没解析到 —— 种子 SQL 结构变了？）'}")
    print(f"ℹ️  代码里用到的角色编码：{sorted(used_codes) or '（无）'}")
    print(f"ℹ️  已检查的敏感写操作：{checked} 处")
    print(f"ℹ️  尚未加固的服务（{len(PENDING)} 个，白名单只许变短）：{len(PENDING)} 个")

    if verbose:
        for f in findings:
            print(f"   ❌ {f}")

    if findings:
        print(f"\n❌ 发现 {len(findings)} 处鉴权问题：")
        for f in findings:
            print(f"   {f}")
        print("\n✅ 修法：")
        print("   ① `SecurityConfig` 用 `anyRequest().authenticated()`，只白名单登录/注册/actuator")
        print("   ② 敏感写操作加 `@PreAuthorize(\"hasAnyRole('SUPER_ADMIN','ADMIN')\")`")
        print("   ③ 角色编码必须和**种子数据**里的 `t_role.role_code` 一致")
        return 1

    print("\n✅ 账户系统：接口要认证、敏感写操作要 ADMIN、角色编码与种子数据一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
