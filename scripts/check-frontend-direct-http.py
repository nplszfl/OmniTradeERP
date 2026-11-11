#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前端「绕过 api/ 层的直接网络调用」巡检（第 53 道闸门）。

背景（2026-11-11 13:45 实测）：
  本项目前端的约定是**所有业务请求都走 `src/api/*.ts`**，那些模块统一用
  `axios.create({ baseURL: '/api' })` → 所以 api 层里写的路径**不带** `/api` 前缀：

      request.get('/ai/knowledge/stats')   →  实际打到  /api/ai/knowledge/stats

  但**只有 `src/api/` 下的模块**才有那个 baseURL。若在 `.vue` / 其它 `.ts` 里直接用
  **裸 axios**（`import axios from 'axios'`）发请求，就**没有 baseURL**，
  路径必须**自己带上 `/api`**：

      axios.post('/api/auth/login', form)  ✅ 正确（裸 axios → 路径要自带 /api）
      axios.post('/auth/login', form)      ❌ 错 → 实际打到 /auth/login → 网关没有这条路由 → 404

📌 **为什么值得单独一道闸门**：
  - 这类错误**编译期不报错、类型检查也不报错**（路径就是字符串），
    `vue-tsc` / ESLint / Prettier **全都发现不了**；
  - 前端只有 2 处这样的调用（Login.vue / Register.vue，**都写对了**），
    所以现在**没有坏掉的功能** → 但**没有任何机制阻止第 3 处写错**；
  - 写错的后果是**登录/注册这类关键入口 404**，而页面上只会看到「登录失败」（规则 12/82 的观感）。

## 检查项

1. `src/api/` 之外的直接网络调用（`axios.get/post/put/delete/patch`、`fetch(...)`）
   → 路径**必须**自带 `/api` 前缀
2. `src/api/` 之外**不许**出现 `axios.create({ baseURL: ... })`
   （baseURL 是 api 层的约定；放到别处会让人误以为全局都有前缀）
3. `src/api/` 之外的请求路径若是**变量**（不是字面量）→ 无法静态判断 → 报 ℹ️（不算失败）

📌 **已知边界（声明出来，规则 116）：
  - 只认**字面量路径**；`axios.get(url)` 这种动态路径只能报 ℹ️；
  - `axios` 的**拦截器**里若改写了 baseURL / 路径，本脚本看不见（当前仓库没有这种写法）；
  - `src/api/` 内部的路径**不查**（它们靠 baseURL='/api'，路径本来就不带前缀）。

用法：python3 scripts/check-frontend-direct-http.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_SRC = os.path.join(ROOT, "erp-web", "src")
API_DIR = os.path.join(WEB_SRC, "api")

SKIP_DIRS = {"node_modules", "dist", ".git", "coverage"}

# 直接网络调用：axios.get( / axios.post( / fetch(
CALL_RE = re.compile(
    r"\b(?:axios\s*\.\s*(?:get|post|put|delete|patch|request)|fetch)\s*\(\s*"
    r"(?P<q>['\"])(?P<path>[^'\"]*)(?P=q)",
    re.IGNORECASE,
)
# 路径里的变量 / 模板拼接（无法静态判断）
DYNAMIC_RE = re.compile(r"\$\{|\+\s*[A-Za-z_$]|`")

CREATE_RE = re.compile(r"axios\s*\.\s*create\s*\(")

SKIP_FILE_RE = re.compile(r"(\.spec\.|\.test\.|\.d\.ts$)")


def iter_files():
    for dirpath, dirnames, filenames in os.walk(WEB_SRC):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if not fn.endswith((".vue", ".ts", ".js", ".tsx", ".jsx")):
                continue
            if SKIP_FILE_RE.search(fn):
                continue
            yield os.path.join(dirpath, fn)


def is_api_layer(path):
    return os.path.abspath(path).startswith(os.path.abspath(API_DIR) + os.sep)


def main():
    errors = []
    infos = []
    checked_calls = 0

    if not os.path.isdir(WEB_SRC):
        print(f"🔴 找不到前端源码目录 {WEB_SRC} → 本闸门无法工作")
        return 1

    for path in iter_files():
        rel = os.path.relpath(path, ROOT)
        if is_api_layer(path):
            continue          # api 层靠 baseURL='/api'，路径本来就不带前缀
        try:
            with open(path, encoding="utf-8", errors="ignore") as f:
                src = f.read()
        except OSError as e:
            errors.append(f"[{rel}] 读文件失败：{e}")
            continue

        # 2) api 层之外不许建带 baseURL 的实例
        for m in CREATE_RE.finditer(src):
            line = src[:m.start()].count("\n") + 1
            errors.append(
                f"[{rel}:{line}] 在 `src/api/` 之外用了 `axios.create(...)`\n"
                f"      → baseURL 是 **api 层的约定**；放到别处会让人误以为全局都带 `/api` 前缀"
            )

        # 1) 直接网络调用的路径必须自带 /api
        for m in CALL_RE.finditer(src):
            checked_calls += 1
            p = m.group("path")
            line = src[:m.start()].count("\n") + 1
            if not p.startswith("/"):
                # 相对路径 / 绝对 URL：不属于本闸门要管的形态（多半是跨域绝对地址）
                infos.append(f"[{rel}:{line}] 路径 {p!r} 不是以 / 开头（绝对地址或相对路径，跳过）")
                continue
            if DYNAMIC_RE.search(p):
                infos.append(
                    f"[{rel}:{line}] 路径是**动态拼接**的 {p!r} → 无法静态判断（ℹ️ 不算失败）"
                )
                continue
            if p.startswith("/api/"):
                continue      # ✅ 裸 axios 的正确写法
            errors.append(
                f"[{rel}:{line}] 直接网络调用**没带 `/api` 前缀**：{p!r}\n"
                f"      → `src/api/` 之外的调用是**裸 axios**（没有 baseURL）→ 实际打到 {p}\n"
                f"      → 网关路由是 `/api/**` → **404**，而页面上只会显示「登录失败」这类误导信息\n"
                f"      ✅ 修法：写成 `{p if p.startswith('/') else '/' + p}` 的 **/api** 形式，"
                f"或改走 `src/api/` 层"
            )

    print(f"📋 巡检「绕过 api/ 层的直接网络调用」（前端源码 {WEB_SRC}）\n")
    print(f"ℹ️  静态可判的直接调用：{checked_calls} 处\n")

    if infos:
        for i in infos:
            print(f"ℹ️  {i}")
        print()

    if errors:
        print(f"🔴 发现 {len(errors)} 处「直接网络调用路径可能打不通」问题：\n")
        for e in errors:
            print(f"   {e}\n")
        print(f"合计问题 {len(errors)} 项")
        print()
        print("📌 **前端只有 2 处这样的调用**（Login.vue / Register.vue）—— 少写一个 `/api` 就是 404")
        return 1

    print(f"✅ 所有直接网络调用都自带 `/api` 前缀（或路径为动态拼接，已标注）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
