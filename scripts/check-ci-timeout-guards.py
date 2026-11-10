#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
⏱️ CI 超时保护巡检：卡死防护的**两层**都不能缺。

======================================================================
🔴 真实事故（2026-11-10 20:02，CI run 42811495288）
======================================================================
`Build erp-gateway image` 从 12:02:23 起 **in_progress 50 分钟**，
其余 23 个镜像 1-3 分钟全部完成 → 不是「慢」，是**卡住**。

根因（不是代码问题，是网络抖动）：
  mvn 拉 aliyun 依赖时**网络挂起**（不是 502 快速失败 → **不触发重试**）
  → mvn 默认 HTTP 超时很长 → **无限等待**
  → `docker-build` 不完成 → 下游 `deploy-k8s` / `notify` **永远等不到**
  → 整轮 CI 挂到 GitHub 的 **6 小时**上限。

⚠️ **这个坑极其隐蔽 —— 保护写在了没人用的文件里**（规则 33 的镜像版）：

| 文件 | 谁在用 | 超时保护 |
|---|---|---|
| `docker/Dockerfile.service` | **compose 全部 23 个服务 + CI 矩阵 23 个镜像** | ❌ 当时**裸奔** |
| `docker/Dockerfile.gateway` 等 17 个单模块文件 | **无人引用**（死文件） | ✅ 有 `for i in 1 2 3` 重试 |

→ **真正生效的那份没有保护**；有保护的那 17 份**没人用**。

======================================================================
📌 判据的核心：「谁在用」，不是「文件存在」
======================================================================
⚠️ 如果判据写成「`docker/Dockerfile.*` 都必须有 timeout」→ 会**假绿**：
  17 个死文件全都有 timeout → 闸门永远绿
  → 真正的 `Dockerfile.service` **可以被删掉保护而不报红**
  → 甚至**新增一个死文件反而让闸门更绿**（讽刺）。

✅ 正解：**从「引用点」反推**真正生效的 Dockerfile：
  ① compose 的 `dockerfile:` 字段
  ② workflow 的 `file:` 字段
  ③ `k8s/build-images.sh` 的 `-f` 参数
→ 对**这些**文件要求超时保护。

======================================================================
🛡️ 两层防护
======================================================================
第 1 层（**已修**，86f7a15）：Dockerfile 内的构建期超时
  `timeout 600 mvn ...` —— 挂起也会被杀（exit 124），不靠 job 超时兜底。

第 2 层（**当时完全缺失**）：job 级 `timeout-minutes`
  GitHub 默认 **6 小时** → 任何 job 卡死 → 整轮 CI 白占 runner 6 小时。
  → **13 个 job 全部没有** `timeout-minutes`。

📌 判据故意**不写死每个 job 的期望值**（那会变成第二份手写清单，规则 33）：
  → 只要求「**有** timeout-minutes，且 **<= 60 分钟**」
  → 依据：CI 里最慢的 job 是 `backend-build-test`（23m49s）→ 60 分钟给它 2.5 倍余量
  → **新增 job 会被自动要求加超时** → 判据能自动跟上，不用维护清单。
"""

import os
import re
import sys
import glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

errors = []
warns = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warns.append(msg)


# ─────────────────────────────────────────────────────────────────
# 1) 收集「真正被引用」的 Dockerfile
# ─────────────────────────────────────────────────────────────────
referenced = {}   # 路径 → [引用来源说明]


def add_ref(path, source):
    if not path:
        return
    path = path.strip().lstrip("./")
    referenced.setdefault(path, []).append(source)


# ① compose：`dockerfile:` 是**相对 `context:`** 的路径 → 必须配对解析
#    ⚠️ 正则扫 `dockerfile:` 行会把 `context: ./nginx` + `dockerfile: Dockerfile`
#    错解析成仓库根的 `Dockerfile`（规则 107：语义定位 > 形状猜测）
try:
    import yaml as _yaml
    for f in sorted(glob.glob("docker-compose*.yml") + glob.glob("docker-compose*.yaml")):
        doc = _yaml.safe_load(open(f, encoding="utf-8"))
        for svc, cfg in (doc.get("services") or {}).items():
            b = (cfg or {}).get("build")
            if not isinstance(b, dict):
                continue
            df = b.get("dockerfile")
            if not df:
                continue
            ctx = b.get("context", ".")
            path = os.path.normpath(os.path.join(ctx, df))
            add_ref(path, f"{f}#{svc}")
except Exception as e:  # noqa
    err(f"[A] compose 解析失败 → 无法核验 Dockerfile 引用：{e}")

# ② workflow：file: ./docker/Dockerfile.service
for f in sorted(glob.glob(".github/workflows/*.yml")):
    txt = open(f, encoding="utf-8").read()
    for i, line in enumerate(txt.splitlines(), 1):
        m = re.match(r"\s*file:\s*(\S+)", line)
        if m:
            add_ref(m.group(1), f"{f}:{i}")

# ③ k8s/build-images.sh：-f docker/Dockerfile.service
for f in sorted(glob.glob("k8s/*.sh") + glob.glob("scripts/*.sh")):
    txt = open(f, encoding="utf-8").read()
    for i, line in enumerate(txt.splitlines(), 1):
        m = re.search(r"(?:-f|--file)\s+(\S*Dockerfile\S*)", line)
        if m:
            add_ref(m.group(1), f"{f}:{i}")

# ─────────────────────────────────────────────────────────────────
# 2) 判据 A/B：被引用的 Dockerfile 必须存在 + 必须有构建期超时保护
# ─────────────────────────────────────────────────────────────────
# 📌 认得多种写法（规则 100）：
#   timeout 600 / timeout 600s / timeout 10m / timeout --preserve-status 600 / timeout -s 600
# 📌 **不认** `--timeout=10s`（等号形式）—— 那是 HEALTHCHECK 的，
#    不是构建保护（`HEALTHCHECK --interval=30s --timeout=10s`）。
TIMEOUT_RE = re.compile(r"(?<![\w-])timeout\s+(?:-[a-zA-Z]+\s+)*(\d+)([smh]?)")
# 网络类命令 —— 超时保护只有套在它们上面才有意义
NET_CMD_RE = re.compile(r"\b(mvn|mvnw|curl|wget|apt-get|apk|git|pip|npm|yarn|pnpm|go|cargo)\b")
# 被吞掉的写法：timeout ... || true / || echo / || :
SWALLOW_RE = re.compile(r"timeout\s+(?:-[a-zA-Z]+\s+)*\d+[smh]?\s+[^\n;]*\|\|\s*(true|echo|:)\b")


def strip_healthcheck(lines):
    """剔除 HEALTHCHECK 块 —— 里面的 wget 是**运行时**健康检查，不是构建期依赖。

    ⚠️ `nginx/Dockerfile` 里有 `HEALTHCHECK ... wget ... http://localhost/health`
    → 那是**容器起来之后**探活用的，**构建期不会执行** → 不该要求超时保护。
    """
    out, in_hc, prev_cont = [], False, False
    for line in lines:
        stripped = line.strip()
        if not in_hc and re.match(r"\s*HEALTHCHECK\b", line, re.I):
            in_hc = True
            out.append(line)
            prev_cont = stripped.endswith("\\")
            continue
        if in_hc:
            # 📌 HEALTHCHECK 常写成 `HEALTHCHECK --interval=... \` + 下一行 `CMD wget ...`
            #    → 续行属于同一条指令 → 必须整块剔除
            if prev_cont:
                prev_cont = stripped.endswith("\\")
                continue
            if re.match(r"\s*(CMD|ENTRYPOINT|RUN|COPY|ADD|FROM|EXPOSE|ENV|ARG|LABEL|USER|VOLUME|WORKDIR|ONBUILD|STOPSIGNAL|HEALTHCHECK|SHELL)\b",
                        line, re.I):
                in_hc = False
                out.append(line)
                continue
            prev_cont = stripped.endswith("\\")
            continue
        out.append(line)
        prev_cont = stripped.endswith("\\")
    return out

for path, sources in sorted(referenced.items()):
    if not os.path.exists(path):
        err(f"[A] 被引用但**不存在**：`{path}`\n"
            f"     引用点：{', '.join(sources)}\n"
            f"     → 构建时必然失败（规则 33：真相来源是「谁在用」）")
        continue

    lines = open(path, encoding="utf-8").read().splitlines()
    # 剔除 HEALTHCHECK 块（运行时探活，不是构建期依赖）
    lines = strip_healthcheck(lines)
    # 剔除注释行 + 行尾注释（避免把文档里的 timeout 误判成保护）
    code_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        # 行尾注释（Dockerfile 里 # 出现在非引号上下文中即注释）
        code = re.sub(r"(?<!['\"])#.*$", "", line)
        code_lines.append(code)
    code = "\n".join(code_lines)

    # 📌 先问「这个 Dockerfile **构建期**有没有网络依赖？」
    #    没有 → 不可能挂起 → 不该要求超时保护（否则误报，如 nginx/Dockerfile 只 COPY）
    net_lines = [ln for ln in code.splitlines() if NET_CMD_RE.search(ln)]
    if not net_lines:
        continue

    # B: 必须有构建期超时保护
    hits = []
    for ln in code.splitlines():
        for m in TIMEOUT_RE.finditer(ln):
            seg = ln[m.start():]
            if not NET_CMD_RE.search(seg):
                continue          # timeout 套的不是网络命令 → 不算保护
            num = int(m.group(1))
            unit = m.group(2)
            secs = num * {"s": 1, "m": 60, "h": 3600}.get(unit, 1)
            hits.append((secs, ln.strip()))
    if not hits:
        err(f"[B] 被引用的 Dockerfile **没有构建期超时保护**：`{path}`\n"
            f"     引用点：{', '.join(sources)}\n"
            f"     → 网络挂起时 mvn 会**无限等待**（CI 卡死 50 分钟的真实原因）\n"
            f"     修法：`timeout 600 mvn ...`（挂起也会被杀，exit 124）")
        continue

    # B2: 超时上限不能太大（>30 分钟说明保护形同虚设）
    worst = max(h[0] for h in hits)
    if worst > 1800:
        err(f"[B2] `{path}` 的超时上限 {worst}s 过大（>1800s）→ 保护形同虚设")

    # C: 超时保护不能被吞掉（规则 18：事实标志不能撒谎）
    for ln in code.splitlines():
        if SWALLOW_RE.search(ln) and NET_CMD_RE.search(ln):
            err(f"[C] `{path}` 的超时保护**被 `|| true` 之类吞掉** → "
                f"失败会被当成功（规则 18）：\n     {ln.strip()}")

# ─────────────────────────────────────────────────────────────────
# 3) 判据 D：每个 job 必须有 timeout-minutes（且 <= 60）
# ─────────────────────────────────────────────────────────────────
try:
    import yaml  # noqa
    have_yaml = True
except ImportError:
    have_yaml = False

if have_yaml:
    for f in sorted(glob.glob(".github/workflows/*.yml")):
        try:
            doc = yaml.safe_load(open(f, encoding="utf-8"))
        except Exception as e:  # noqa
            err(f"[D] `{f}` YAML 解析失败 → 无法核验 job 超时：{e}")
            continue
        for jid, job in (doc.get("jobs") or {}).items():
            tm = job.get("timeout-minutes")
            if tm is None:
                err(f"[D] job `{jid}`（{f}）**没有 `timeout-minutes`**\n"
                    f"     → GitHub 默认 **6 小时** → 卡死时整轮 CI 白占 runner 6 小时\n"
                    f"     修法：加 `timeout-minutes: 60`")
            elif isinstance(tm, (int, float)) and tm > 60:
                err(f"[D] job `{jid}`（{f}）的 `timeout-minutes: {tm}` 过大（>60）")
else:
    warn("未安装 PyYAML → 跳过判据 D（job 级 timeout-minutes）")

# ─────────────────────────────────────────────────────────────────
# 4) 死文件提示（警告，不失败）
# ─────────────────────────────────────────────────────────────────
all_dockerfiles = set(glob.glob("docker/Dockerfile*"))
dead = sorted(all_dockerfiles - set(referenced))
if dead:
    warn(f"ℹ️ 有 {len(dead)} 个 Dockerfile **无人引用**（死文件）：{', '.join(dead)}\n"
         f"   → 它们就算有超时保护也**不生效**；判据只看「谁在用」（规则 33 的镜像版）")

# ─────────────────────────────────────────────────────────────────
# 输出
# ─────────────────────────────────────────────────────────────────
print("=" * 70)
print("⏱️ CI 超时保护巡检：Dockerfile 构建期保护 + job 级上限")
print("=" * 70)
print(f"被实际引用的 Dockerfile：{len(referenced)} 个 → {', '.join(sorted(referenced)) or '（无）'}")
if warns:
    print()
    for w in warns:
        print(f"⚠️  {w}")
if errors:
    print()
    print(f"❌ 发现 {len(errors)} 处问题：\n")
    for e in errors:
        print(f"  {e}")
        print()
    print("📌 判据：")
    print("   [A] 被引用的 Dockerfile 必须存在")
    print("   [B] 被引用的 Dockerfile 必须有**构建期**超时保护（timeout 套在网络命令上）")
    print("   [C] 超时保护不能被 `|| true` 吞掉（失败必须能被感知）")
    print("   [D] 每个 job 必须有 `timeout-minutes`（<=60）")
    print()
    print("📌 **真相来源是「谁在用」** —— compose / workflow / build-images.sh 的引用点，")
    print("   不是「docker/ 目录里有几个 Dockerfile」（规则 33 的镜像版）。")
    sys.exit(1)

print()
print("✅ 超时保护：两层齐全（Dockerfile 构建期保护 + job 级 timeout-minutes）")
sys.exit(0)
