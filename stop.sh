#!/bin/bash

set -e
# 📌 **`set -e`（2026-11-04 补）**：这个脚本原来没有 → 任何一条命令失败
#    （docker compose 起不来 / mvn 编译失败 / jar 不存在）都会被静默吞掉，
#    脚本照样往下跑并打印「✅ 完成」→ 以为启动/停止成功了，实际什么都没发生。

# 跨境电商ERP系统 - 停止脚本

echo "🛑 停止ERP系统..."

if [ -f .pids ]; then
    while read pid; do
        if kill -0 $pid 2>/dev/null; then
            echo "停止进程 (PID: $pid)"
            kill $pid
        fi
    done < .pids
    rm .pids
fi

# 额外清理Java进程
pkill -f "erp-gateway"
pkill -f "erp-order-service"
pkill -f "erp-platform-service"

echo "✅ 所有服务已停止"
