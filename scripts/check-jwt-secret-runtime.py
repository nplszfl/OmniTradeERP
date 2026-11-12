#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「JWT 签名密钥在运行时是不是真的随机」—— 账号接管风险的守门人。

📌 **本闸门守护的缺陷**（2026-11-12 17:20 实测发现，**从未部署过所以一直没炸**）：

    k8s/deploy.sh 的执行顺序是：
        ① kubectl create secret generic erp-jwt-secret --from-literal=jwt-secret=$RANDOM
        ② for f in k8s/*.yaml; do kubectl apply -f "$f"; done   ← **这一步会把 ① 覆盖掉**
    而 `k8s/core-services.yaml` 里**也定义**了同名 Secret：
        stringData:
          jwt-secret: "PLACEHOLDER_SET_BY_deploy_sh"
    → `kubectl apply` 的三方合并：last-applied（原=随机值）≠ 新清单（=占位符）
      → **占位符胜出** → 集群里最终的 Secret 值 = **仓库里公开的那个字符串**
    → 所有服务用这个**公开的**密钥签/验 JWT → **任何拿到仓库的人都能伪造管理员 token**
      → **未认证即可接管账号**（规则 140 同族，Critical）。

📌 **为什么第 60 道闸门 `check-hardcoded-secret.py` 抓不到**（规则 100：判据只认一种写法 = 双向失明）：
    它的 C4 只检查「清单里那个 `jwt-secret:` 的**值**是不是可用密钥」
    → 占位符**不是**可用密钥 → C4 判绿 ✅
    → 但它**从来没问**「这个占位符会不会在运行时**变成真正的密钥**」。
    📌 **这是同一个缺陷的「兄弟分支」**：值合规 ≠ 运行时安全。

📌 **本闸门的判据**：

| 断言 | 内容 |
|---|---|
| **C1** | **任何 k8s 清单里都不许出现 `jwt-secret:` 的值定义** —— 清单里的值会在 apply 时**覆盖**现场生成的随机值 → 运行时用仓库里公开的值 |
| **C2** | `k8s/deploy.sh` 必须用 `kubectl create secret generic erp-jwt-secret` + `--from-literal` **现场生成**（不许把值写死在脚本里） |
| **C3** | 应用侧 `${JWT_SECRET}` **不许带默认值** —— `${JWT_SECRET:someDefault}` → 缺变量时**带着已知密钥上线** |
| **C4** | `deploy.sh` 里**创建 Secret 的步骤必须在 apply 清单循环之前** —— 顺序反了 = 随机值被清单覆盖 |

📌 **反向验证**（规则 40 / 75 / 123）：
    把 `erp-jwt-secret` 的 Secret 块**塞回** `k8s/core-services.yaml` → 本闸门必须 exit 1；
    移除 → 必须 exit 0。
"""

from __future__ import annotations

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K8S_DIR = os.path.join(REPO_ROOT, "k8s")
DEPLOY_SH = os.path.join(K8S_DIR, "deploy.sh")

SECRET_NAME = "erp-jwt-secret"
JWT_KEY = "jwt-secret"

# ── C3：应用侧不许给 ${JWT_SECRET} 写默认值 ─────────────────────────────
APP_DEFAULT_RE = re.compile(r"\$\{JWT_SECRET\s*:\s*([^}]*)\}")


def iter_k8s_manifests():
    if not os.path.isdir(K8S_DIR):
        return
    for fn in sorted(os.listdir(K8S_DIR)):
        if fn.endswith((".yaml", ".yml")):
            yield os.path.join(K8S_DIR, fn)


def iter_app_yml():
    for mod in sorted(os.listdir(REPO_ROOT)):
        if not mod.startswith("erp-") or not os.path.isdir(os.path.join(REPO_ROOT, mod)):
            continue
        res = os.path.join(REPO_ROOT, mod, "src", "main", "resources")
        if not os.path.isdir(res):
            continue
        for fn in sorted(os.listdir(res)):
            if fn.startswith("application") and fn.endswith((".yml", ".yaml")):
                yield os.path.join(res, fn), os.path.join(mod, fn)


def main() -> int:
    findings = []

    # ── C1：清单里不许有 jwt-secret 的值定义 ──────────────────────────
    for path in iter_k8s_manifests():
        rel = os.path.relpath(path, REPO_ROOT)
        text = open(path, encoding="utf-8").read()
        # 📌 **只认「jwt-secret 在左边当键」的值定义** ——
        #    `key: jwt-secret`（secretKeyRef 的 key）是**引用**，不是定义 → 不能误报（规则 99）
        def_re = re.compile(r"^\s*" + JWT_KEY + r"\s*:\s*(\S.*)$")
        for lineno, raw in enumerate(text.split("\n"), 1):
            stripped = raw.strip()
            # 注释行不算定义
            if stripped.startswith("#") or stripped.startswith("*"):
                continue
            if not def_re.match(raw):
                continue
            findings.append((
                rel, lineno,
                f"清单里出现 `{JWT_KEY}:` 的**值定义** → `kubectl apply` 会用它**覆盖** "
                f"`deploy.sh` 现场生成的随机值 → 运行时 JWT 密钥 = **仓库里公开的字符串** → "
                f"**任何人可伪造管理员 token**（规则 140，Critical）",
            ))
            break  # 一个文件报一次就够

    # ── C2 / C4：deploy.sh 必须现场生成，且要在 apply 清单之前 ─────────
    if not os.path.exists(DEPLOY_SH):
        findings.append((
            os.path.relpath(DEPLOY_SH, REPO_ROOT), 0,
            "找不到 `k8s/deploy.sh` → 没人现场生成 JWT 密钥 → 集群里只能有清单里的那个值",
        ))
    else:
        text = open(DEPLOY_SH, encoding="utf-8").read()
        create_m = re.search(
            r"kubectl\s+(?:-n\s+\S+\s+)?create\s+secret\s+generic\s+" + SECRET_NAME,
            text,
        )
        if not create_m:
            findings.append((
                "k8s/deploy.sh", 0,
                f"`deploy.sh` 里没有 `kubectl create secret generic {SECRET_NAME}` → "
                f"没有「现场生成随机密钥」这一步",
            ))
        else:
            if "--from-literal" not in text[create_m.start():create_m.start() + 400]:
                findings.append((
                    "k8s/deploy.sh", text.count("\n", 0, create_m.start()) + 1,
                    f"`create secret {SECRET_NAME}` 后面没有 `--from-literal` → "
                    f"密钥的值不是现场随机生成的",
                ))
            # C4：apply 清单的循环必须在创建 Secret **之后**
            apply_m = re.search(r"for\s+f\s+in\s+k8s/\*\.yaml", text)
            if apply_m and apply_m.start() < create_m.start():
                findings.append((
                    "k8s/deploy.sh", text.count("\n", 0, apply_m.start()) + 1,
                    f"**顺序反了**：apply 清单的循环在「创建 {SECRET_NAME}」**之前** → "
                    f"清单里的值会覆盖刚生成的随机值",
                ))

    # ── C3：应用侧不许有默认值 ───────────────────────────────────────
    for path, relname in iter_app_yml():
        text = open(path, encoding="utf-8").read()
        for lineno, raw in enumerate(text.split("\n"), 1):
            # 📌 **注释里的历史默认值不是活配置** → 不能误报（规则 99）
            #    例：`#    🔴 修复前这里是 ${JWT_SECRET:OldDefault}` ← 那是**修复记录**，不是现状
            if raw.strip().startswith("#"):
                continue
            m = APP_DEFAULT_RE.search(raw)
            if m:
                findings.append((
                    os.path.relpath(path, REPO_ROOT), lineno,
                    f"`${{JWT_SECRET:{m.group(1)[:40]}}}` **带了默认值** → "
                    f"部署层没注入 JWT_SECRET 时，服务会**带着这个已知密钥启动** → "
                    f"缺变量本该「启动失败」，实际变成「带着公开密钥上线」（规则 140）",
                ))

    print("📋 巡检「JWT 签名密钥在运行时是不是真的随机」")
    print(f"ℹ️  清单数：{sum(1 for _ in iter_k8s_manifests())}")
    if findings:
        print(f"\n❌ 发现 {len(findings)} 处「JWT 密钥可能不是随机生成」的问题：")
        for rel, lineno, msg in findings:
            print(f"   {rel}:{lineno}")
            print(f"        {msg}")
        print("\n✅ 修法：")
        print("   ① **k8s 清单里不许出现 `jwt-secret:` 的值定义** —— 清单里的值会在 apply 时覆盖")
        print("      `deploy.sh` 现场生成的随机值 → 运行时密钥 = 仓库里公开的字符串")
        print("   ② 真实值只能由 `kubectl create secret generic erp-jwt-secret --from-literal=...` 现场生成")
        print("   ③ 应用侧 `${JWT_SECRET}` **不许带默认值** → 缺变量就启动失败（fail-closed）")
        return 1

    print("\n✅ JWT 签名密钥：清单里没有值、deploy.sh 现场随机生成、应用侧无默认值")
    return 0


if __name__ == "__main__":
    sys.exit(main())
