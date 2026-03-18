#!/bin/bash
# 智能部署：首次构建用Docker Maven，后续构建利用本地Maven缓存

set -e

echo "🚀 智能部署 OmniTrade ERP"
echo "======================================"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装"
    exit 1
fi

echo "✅ Docker: $(docker --version)"
echo ""

# 创建本地 Maven 仓库目录（如果不存在）
mkdir -p ~/.m2

# 第1步：使用 Docker Maven 编译（挂载本地仓库，利用缓存）
echo "📦 步骤1：编译所有服务（使用 Docker Maven + 本地缓存）..."
echo "-----------------------------------"
echo "💡 Maven 仓库：~/.m2（会被缓存，下次构建更快）"
echo ""

docker run --rm \
  -v "$PWD":/app \
  -v ~/.m2:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn clean package -DskipTests -B

echo ""
echo "✅ 编译完成！"
echo ""

# 第2步：构建 Docker 镜像
echo "🐳 步骤2：构建 Docker 镜像..."
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

echo "✅ 部署完成！"
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
echo ""
echo "💡 下次部署会更快！Maven 依赖已缓存在 ~/.m2"
