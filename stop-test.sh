#!/bin/bash

# 停止测试环境脚本

echo "🛑 停止测试环境..."

docker-compose -f docker-compose.test.yml down

echo "✅ 测试环境已停止！"
