#!/bin/bash
# 快速部署：本地编译 + Docker 运行

set -e

echo "🚀 快速部署 OmniTrade ERP（本地编译版）"
echo "======================================"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装"
    exit 1
fi

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "❌ Java 未安装"
    exit exit 1
fi

echo "✅ Docker: $(docker --version)"
echo "✅ Java: $(java -version 2>&1 | head -n 1)"
echo ""

# 第1步：本地编译
echo "📦 步骤1：本地编译所有服务..."
echo "-----------------------------------"
./mvnw clean package -DskipTests -B
echo ""

# 第2步：构建运行时镜像
echo "🐳 步骤2：构建 Docker 镜像（只打包运行时环境，跳过编译）..."
echo "-----------------------------------"
docker-compose -f docker-compose.test.yml build
echo ""

# 第3步：启动服务
echo "▶️  步骤3：启动所有服务..."
echo "-----------------------------------"
docker-compose -f docker-compose.test.yml up -d
echo ""

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 30

# 检查服务状态
echo "📊 服务状态："
echo "-----------------------------------"
docker-compose -f docker-compose.test.yml ps
echo ""

echo "✅ 快速部署完成！"
echo ""
echo "🌐 访问地址："
echo "  - 前端：http://localhost"
echo "  - Gateway：http://localhost:8080"
echo "  - Nacos控制台：http://localhost:8848/nacos (nacos/nacos)"
echo "  - Sentinel：http://localhost:8858"
echo "  - RabbitMQ管理：http://localhost:15672 (guest/guest)"
echo ""
echo "📋 常用命令："
echo "  - 查看日志：docker-compose -f docker-compose.test.yml logs -f"
echo "  - 查看状态：docker-compose -f docker-compose.test.yml ps"
echo "  - 停止服务：docker-compose -f docker-compose.test.yml down"
