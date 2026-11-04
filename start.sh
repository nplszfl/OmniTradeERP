#!/bin/bash

set -e
# 📌 **`set -e`（2026-11-04 补）**：这个脚本原来没有 → 任何一条命令失败
#    （docker compose 起不来 / mvn 编译失败 / jar 不存在）都会被静默吞掉，
#    脚本照样往下跑并打印「✅ 完成」→ 以为启动/停止成功了，实际什么都没发生。

# 跨境电商ERP系统 - 一键启动脚本

echo "🔥 跨境电商ERP系统 - 启动脚本"
echo "=================================="

# 检查JDK版本
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
echo "JDK版本: $java_version"

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven未安装，请先安装Maven"
    exit 1
fi

echo "Maven版本: $(mvn -version | head -1)"

# 检查Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js未安装，请先安装Node.js"
    exit 1
fi

echo "Node.js版本: $(node -version)"

# 检查npm
if ! command -v npm &> /dev/null; then
    echo "❌ npm未安装，请先安装npm"
    exit 1
fi

echo "npm版本: $(npm -version)"

echo ""
echo "📦 开始编译打包..."

# 编译打包
# 📌 原来写的是 `mvn ...` + `if [ $? -ne 0 ]` ——
#    有了 `set -e` 之后，mvn 失败会**在走到 if 之前**就中止脚本，
#    「❌ 编译失败」的提示就丢了 → 必须改成 `if ! cmd` 的写法。
if ! mvn clean package -DskipTests; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

echo "🚀 启动后端服务..."

# 启动网关
echo "启动网关服务..."
java -jar erp-gateway/target/erp-gateway-1.0.0.jar > logs/gateway.log 2>&1 &
GATEWAY_PID=$!
echo "✅ 网关服务已启动 (PID: $GATEWAY_PID)"

# 启动订单服务
echo "启动订单服务..."
java -jar erp-order-service/target/erp-order-service-1.0.0-exec.jar > logs/order-service.log 2>&1 &
ORDER_PID=$!
echo "✅ 订单服务已启动 (PID: $ORDER_PID)"

# 启动平台服务
echo "启动平台服务..."
java -jar erp-platform-service/target/erp-platform-service-1.0.0.jar > logs/platform-service.log 2>&1 &
PLATFORM_PID=$!
echo "✅ 平台服务已启动 (PID: $PLATFORM_PID)"

echo ""
echo "⏳ 等待服务启动..."
sleep 10

echo ""
echo "🌐 启动前端..."

# 启动前端
cd erp-web
npm install > npm-install.log 2>&1
npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

echo "✅ 前端已启动 (PID: $FRONTEND_PID)"

echo ""
echo "=================================="
echo "🎉 所有服务已启动完成！"
echo ""
echo "📋 服务信息："
echo "  - 前端: http://localhost:3000"
echo "  - Gateway: http://localhost:8080"
echo "  - 订单服务: http://localhost:8081"
echo "  - 平台服务: http://localhost:8082"
echo ""
echo "🔧 管理后台："
echo "  - Nacos: http://localhost:8848/nacos (nacos/nacos)"
echo "  - Sentinel: http://localhost:8858"
echo ""
echo "⏹️  停止服务: ./stop.sh"
echo "=================================="

# 保存PID
echo $GATEWAY_PID > .pids
echo $ORDER_PID >> .pids
echo $PLATFORM_PID >> .pids
echo $FRONTEND_PID >> .pids
