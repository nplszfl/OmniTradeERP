#!/bin/bash

set -e
# 📌 **`set -e`（2026-11-04 补）**：这个脚本原来没有 → 任何一条命令失败
#    （docker compose 起不来 / mvn 编译失败 / jar 不存在）都会被静默吞掉，
#    脚本照样往下跑并打印「✅ 完成」→ 以为启动/停止成功了，实际什么都没发生。

# 停止测试环境脚本

echo "🛑 停止测试环境..."

docker-compose -f docker-compose.test.yml down

echo "✅ 测试环境已停止！"
