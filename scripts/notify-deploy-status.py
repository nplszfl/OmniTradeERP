#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI 部署状态通知 —— 发到飞书群机器人（webhook）。

📌 **为什么需要它**（规则 101 的续集）：

规则 101 修好了「**没部署却报『部署成功』**」，但通知仍然只是
`echo "✅ 部署成功！"` → **只出现在 GitHub Actions 日志里**
→ **只有主动打开网页的人看得到** → 你不打开网页，就**根本不知道这次 CI 出了什么事**。

📌 **三条硬规则**（都来自实战教训）：

1. **事实标志不许撒谎**（规则 18 / 19）：
   - 部署了 → `✅ 部署成功`
   - 没部署（secret 缺失 → step 全跳过）→ `⏭️ **本次未部署**`（**不许**说成「部署成功」）
   - CI 挂了 → `❌ CI 失败`
   → 判据是 `needs.deploy-k8s.outputs.deployed`，**不是** job 结论。

2. **发送失败不许把 CI 拖红**：
   webhook 挂了只是「通知没送到」，**部署本身可能已经成功**
   → 绝不能因为发不出去就让整个 workflow 失败 → 本脚本**永远 exit 0**，
     但会把失败**如实打印出来**（不能静默）。

3. **不许把凭据写进仓库**（规则：私有东西留在私有处）：
   webhook URL **只从环境变量读**（`FEISHU_WEBHOOK_URL`）→ CI 里配成 secret
   → **仓库里只有变量名，没有 URL**。

📌 **用法**：

    # 本地冒烟（不真发）
    python3 scripts/notify-deploy-status.py --dry-run --status deployed

    # 真发（CI 里）
    FEISHU_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxx \
      python3 scripts/notify-deploy-status.py --status not-deployed --commit abc123 --run-url https://...

📌 **返回码**：**恒为 0**（见硬规则 2）。发送失败时会在 stdout 打印 `❌` 并带上原因。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

WEBHOOK_ENV = "FEISHU_WEBHOOK_URL"

# 事实标志 → 文案。**必须**由调用方按事实传入，脚本自己不猜（规则 18）。
STATUS_TEXT = {
    "deployed": ("✅", "部署成功", "green"),
    "not-deployed": ("⏭️", "本次未部署", "grey"),
    "failed": ("❌", "CI 失败", "red"),
}


def build_payload(status, commit, run_url, message=""):
    emoji, title, color = STATUS_TEXT.get(status, ("❓", "未知状态", "grey"))
    lines = [
        f"**{emoji} {title}**",
        "",
        f"- 仓库：`nplszfl/OmniTradeERP`",
    ]
    if commit:
        lines.append(f"- 提交：`{commit}`")
    if run_url:
        lines.append(f"- CI 日志：{run_url}")
    if message:
        lines.append(f"- 说明：{message}")
    if status == "not-deployed":
        lines.append("")
        lines.append("（这不是「部署成功」—— 集群里一个 Deployment 都没变。）")
    return {
        "msg_type": "text",
        "content": {"text": "\n".join(lines)},
    }


def send(webhook_url, payload, timeout=10):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        webhook_url,
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode("utf-8", errors="replace")
        return resp.status, body


def main() -> int:
    try:
        ap = argparse.ArgumentParser(description="CI 部署状态 → 飞书群机器人")
        ap.add_argument("--status", required=True, choices=list(STATUS_TEXT.keys()),
                        help="事实标志：deployed / not-deployed / failed")
        ap.add_argument("--commit", default=os.environ.get("GITHUB_SHA", "")[:7])
        ap.add_argument("--run-url", default="")
        ap.add_argument("--message", default="")
        ap.add_argument("--dry-run", action="store_true", help="只打印 payload，不真发")
        args = ap.parse_args()
    except SystemExit as e:
        # 📌 **参数写错也不能拖红 CI**（硬规则 2）
        #    `argparse` 参数错误默认 exit 2 → 整个 job 失败 → CI 变红
        #    → 而「通知脚本用错」根本不是「部署失败」→ 必须 exit 0。
        print(f"⚠️ 通知脚本参数错误（exit {e.code}）→ 跳过通知，CI 不受影响")
        return 0
    except Exception as e:  # noqa: BLE001
        print(f"⚠️ 通知脚本初始化失败：{type(e).__name__}: {e} → 跳过通知，CI 不受影响")
        return 0

    try:
        payload = build_payload(args.status, args.commit, args.run_url, args.message)
    except Exception as e:  # noqa: BLE001
        print(f"⚠️ 构造通知内容失败：{type(e).__name__}: {e} → 跳过通知，CI 不受影响")
        return 0

    if args.dry_run:
        print("=== dry-run payload ===")
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    url = os.environ.get(WEBHOOK_ENV, "").strip()
    if not url:
        # 📌 通知发不出去**不是** CI 的错 → 打印清楚，然后 exit 0（硬规则 2）
        print(f"⚠️ 未配置 {WEBHOOK_ENV} → 跳过通知（状态已记录在 CI 日志里）")
        return 0

    try:
        status, body = send(url, payload)
    except urllib.error.HTTPError as e:
        print(f"❌ 通知发送失败：HTTP {e.code} —— {e.read().decode('utf-8', errors='replace')[:300]}")
        return 0
    except Exception as e:  # noqa: BLE001 —— 通知失败不许拖垮 CI
        print(f"❌ 通知发送失败：{type(e).__name__}: {e}")
        return 0

    # 飞书机器人返回 HTTP 200 但 body 里可能带 code != 0 的业务错误
    try:
        j = json.loads(body)
        if isinstance(j, dict) and j.get("code") not in (0, None):
            print(f"❌ 通知被飞书拒绝：code={j.get('code')} msg={j.get('msg')}")
            return 0
    except Exception:
        pass  # 非 JSON 也算送达

    print(f"✅ 通知已发送（HTTP {status}）")
    return 0


if __name__ == "__main__":
    # 📌 **最外层也要包一层** —— 硬规则 2：「发送失败不许把 CI 拖红」。
    #    任何未预料的异常（import 问题、编码问题……）都不能让 CI 变红。
    try:
        sys.exit(main())
    except SystemExit:
        raise
    except Exception as e:  # noqa: BLE001
        print(f"⚠️ 通知脚本异常：{type(e).__name__}: {e} → 跳过通知，CI 不受影响")
        sys.exit(0)
