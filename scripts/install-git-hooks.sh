#!/usr/bin/env bash
# 📌 安装 git hooks（一次性）。
#
# 用法：bash scripts/install-git-hooks.sh
#
# 为什么需要它（2026-11-10 21:25 真实踩过）：
#   workflow 的**解析期**错误（缺 `runs-on` / YAML 结构错）→ GitHub **拒收整个文件**
#   → **所有 job 全部 0 秒失败** → job 里的任何闸门都跑不到 → **CI 侧结构上发现不了**
#   → 唯一能提前拦住的时机是**提交前**。
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

if [ ! -d ".githooks" ]; then
  echo "❌ 找不到 .githooks 目录"
  exit 1
fi

chmod +x .githooks/pre-commit
git config core.hooksPath .githooks

echo "✅ 已启用：core.hooksPath = .githooks"
echo "   pre-commit 钩子会在 workflow 改动时校验结构"
echo "   （可临时跳过：git commit --no-verify）"
