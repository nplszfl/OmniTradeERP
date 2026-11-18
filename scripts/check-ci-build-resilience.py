#!/usr/bin/env python3
"""CI 镜像构建的「韧性」闸门 —— 三件事缺一不可。

📌 **为什么需要这道闸门**（2026-11-18 CI run 44254486501 实测红）：

    CI/CD Pipeline 红了 2 个镜像 job（erp-anomaly-detection / erp-customer），
    而**代码一个字都没改**。根因链条：

      ① `docker/maven-settings.xml` 把 **aliyun 镜像打进镜像**
         → CI 也在用 aliyun
      ② GitHub runner 在**美区** → aliyun 限速到 **1.0 kB/s**
         （日志实测：`commons-io-2.5.pom (13 kB at 1.0 kB/s)`）
      ③ `timeout 600 mvn` 重试 3 次全被掐 → 镜像 job 失败
      ④ 两个 workflow 都**没有并发组** → 被顶掉的 run 不自动取消
         → 多轮 CI 并存 → 并发冷下载进一步加剧限速

    ✅ 修法是三件事一起上：
      - **走官方 Maven Central**（和 npm 侧早已有的修法同构）
      - **buildx 挂 gha cache**（`--mount=type=cache` 跨 job 共享，别 23 倍冗余下载）
      - **加并发组**（`cancel-in-progress: true` → 同时只有一轮在跑）

📌 **本闸门判三件事**（对每个「用 docker/build-push-action 构建镜像」的 step）：
    1. `build-args` 里传了 `MAVEN_MIRROR_URL`，且指向**官方 Central**
    2. `cache-to` 里有 `type=gha` 且 **`mode=max`**（`mode=min` 不导 cache mount 内容）
    3. 该 workflow 顶层有 `concurrency` 且 `cancel-in-progress: true`

📌 **还要验「build-arg 真的被 Dockerfile 用上了」**（规则 32 的新形态：
    「生成器」和「校验器」用同一个方法 = 假绿）——
    只传 build-arg、但 Dockerfile 里没有 `ARG MAVEN_MIRROR_URL` / `ENV MAVEN_MIRROR_URL`
    → 镜像源根本没切换 → **闸门会假绿**。所以本闸门**同时**查 Dockerfile。

📌 **边界**：
    - 只查**镜像构建**（`docker/build-push-action`），不查别的 job
    - 只查 `docker/Dockerfile.service`（CI 镜像矩阵**唯一**用的那个）
      —— 其余 16 个 Dockerfile 是**死文件**（无人引用），改了也没用（规则 33 的镜像版）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WF_DIR = ROOT / ".github" / "workflows"

# CI 镜像矩阵唯一使用的 Dockerfile
CI_DOCKERFILE = ROOT / "docker" / "Dockerfile.service"

# 官方 Maven Central（必须指向它）
CENTRAL_MARKERS = ("repo1.maven.org", "repo.maven.apache.org")


def fail(msg: str, findings: list[str]) -> None:
    findings.append(msg)


def main() -> int:
    try:
        import yaml  # type: ignore
    except ImportError:
        print("❌ 缺少 PyYAML（pip install pyyaml）")
        return 2

    findings: list[str] = []
    checked_steps = 0

    # ── 1) Dockerfile 里必须真的接了这个 build-arg ────────────────────
    df_text = CI_DOCKERFILE.read_text(encoding="utf-8") if CI_DOCKERFILE.is_file() else ""
    if not df_text:
        fail("docker/Dockerfile.service 不存在", findings)
    else:
        # 📌 **必须是「全局」ARG（即第一个 FROM 之前的那行）且带默认值** ——
        #    stage 里的 `ARG MAVEN_MIRROR_URL` 只是**继承**全局值，本身没默认值；
        #    全局 ARG 没了 → 默认值也没了 → 本地构建的镜像源变空 → 本地也坏。
        #    （实测：本闸门第一版只匹配任意 ARG 行 → 删掉全局 ARG **没被抓到**。）
        first_from = df_text.find("\nFROM ")
        global_part = df_text[:first_from] if first_from > 0 else df_text
        m = re.search(r"^ARG\s+MAVEN_MIRROR_URL\s*=\s*(\S+)\s*$", global_part, re.M)
        if not m:
            fail(
                "docker/Dockerfile.service **没有声明带默认值的全局** `ARG MAVEN_MIRROR_URL=...`\n"
                "      → （必须在第一个 FROM 之前）默认值没了 → 本地构建的镜像源变空 → **本地也坏**\n"
                "      → 或者 CI 传的 build-arg 根本没人用 → **CI 仍会超时**",
                findings,
            )
        if not re.search(r"^ENV\s+MAVEN_MIRROR_URL=", df_text, re.M):
            fail(
                "docker/Dockerfile.service **没有设成 ENV**（`ENV MAVEN_MIRROR_URL=`）\n"
                "      → settings.xml 里写的是 `${env.MAVEN_MIRROR_URL}`，Maven 只认**环境变量**\n"
                "      → 只写 ARG 的话 Maven 拿到的是字面量 `${env.MAVEN_MIRROR_URL}` → 构建直接坏",
                findings,
            )

    # ── 2) 逐个 workflow / step 查 ───────────────────────────────────
    for wf in sorted(WF_DIR.glob("*.yml")):
        try:
            doc = yaml.safe_load(wf.read_text(encoding="utf-8")) or {}
        except Exception as exc:  # noqa: BLE001
            fail(f"{wf.name} 解析失败：{exc}", findings)
            continue

        # 并发组
        conc = doc.get("concurrency")
        if not isinstance(conc, dict) or not conc.get("group"):
            fail(
                f"{wf.name} 顶层**没有 concurrency 组**\n"
                "      → 被顶掉的 run 不自动取消 → 多轮 CI 并存 → 并发冷下载加剧镜像限速",
                findings,
            )
        elif str(conc.get("cancel-in-progress", "")).lower() != "true":
            fail(
                f"{wf.name} 的 concurrency 缺 `cancel-in-progress: true`\n"
                "      → 旧 run 不会自动取消 → 同一时间多轮 CI 抢同一份镜像源",
                findings,
            )

        jobs = doc.get("jobs")
        jobs_obj = jobs.values() if isinstance(jobs, dict) else (jobs or [])
        for job in jobs_obj:
            if not isinstance(job, dict):
                continue
            for step in job.get("steps") or []:
                if not isinstance(step, dict):
                    continue
                uses = str(step.get("uses") or "")
                if "docker/build-push-action" not in uses:
                    continue
                checked_steps += 1
                label = f"{wf.name}:{step.get('name') or job.get('name') or '?'}"
                with_ = step.get("with") or {}
                if not isinstance(with_, dict):
                    with_ = {}

                # 📌 **只管「用 Maven 构建的镜像」**（规则 129/156：判据要看「谁在用」）
                #    前端镜像用的是根 `Dockerfile`（node/npm 构建）→ **跟 Maven 镜像源无关**
                #    → 对它要求 `MAVEN_MIRROR_URL` 是**假红**（本闸门第一版就犯了这个）
                dockerfile = str(with_.get("file") or "")
                is_maven_build = "Dockerfile.service" in dockerfile
                if not is_maven_build:
                    continue

                # ① build-args 必须传 MAVEN_MIRROR_URL 指向官方 Central
                build_args = str(with_.get("build-args") or "")
                if "MAVEN_MIRROR_URL" not in build_args:
                    fail(
                        f"[{label}] build-args **没有传** `MAVEN_MIRROR_URL`\n"
                        "      → 容器用默认的 aliyun 镜像 → 美区 runner 限速 1.0 kB/s → `mvn` 必超时",
                        findings,
                    )
                elif not any(m in build_args for m in CENTRAL_MARKERS):
                    fail(
                        f"[{label}] `MAVEN_MIRROR_URL` **没有指向官方 Maven Central**\n"
                        f"      → 当前值：`{build_args.strip()[:200]}`\n"
                        "      → 必须用 repo1.maven.org（aliyun 在美区 runner 上限速到 1.0 kB/s）",
                        findings,
                    )

                # ② cache-to 必须是 gha + mode=max
                cache_to = str(with_.get("cache-to") or "")
                if "type=gha" not in cache_to:
                    fail(
                        f"[{label}] **没有** `cache-to: type=gha`\n"
                        "      → Dockerfile 的 `--mount=type=cache` 只在单次 build 内有效\n"
                        "      → 23 个镜像 job 各自冷下载一遍（冗余 23 倍）",
                        findings,
                    )
                elif "mode=max" not in cache_to:
                    fail(
                        f"[{label}] `cache-to` 缺 `mode=max`\n"
                        "      → `mode=min` 只导最终层 → **cache mount 里的 ~/.m2 不会进缓存**\n"
                        "      → 挂 gha cache 等于白挂",
                        findings,
                    )
                if "type=gha" not in str(with_.get("cache-from") or ""):
                    fail(
                        f"[{label}] **没有** `cache-from: type=gha`\n"
                        "      → 只有导出没有导入 → 缓存永远读不到",
                        findings,
                    )

    if findings:
        print(f"❌ CI 构建韧性闸门：发现 {len(findings)} 处问题\n")
        for f in findings:
            print(f"  {f}")
        print(
            "\n📌 实测（CI run 44254486501）：aliyun 镜像在**美区 runner** 上限速到 1.0 kB/s\n"
            "   → `timeout 600 mvn` 重试 3 次全被掐 → 镜像 job 失败 → **CI 红，而代码没改**\n"
            "   ✅ 三件套：官方 Central + gha cache(mode=max) + concurrency(cancel-in-progress)"
        )
        return 1

    print(
        f"✅ CI 构建韧性：{checked_steps} 个镜像构建 step（其中 Maven 镜像构建已验）—— "
        f"走官方 Maven Central + gha cache(mode=max) + workflow 有 concurrency(cancel-in-progress)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
