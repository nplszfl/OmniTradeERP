#!/bin/bash

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
