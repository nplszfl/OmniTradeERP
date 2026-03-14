#!/bin/bash

# 跨境电商ERP测试环境部署脚本

set -e

echo "🚀 开始部署测试环境..."

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ Docker未安装，请先安装Docker"
    exit 1
fi

# 检查Docker Compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 停止并清理旧容器
echo "🧹 清理旧容器..."
docker-compose -f docker-compose.test.yml down -v

# 构建镜像
echo "🔨 构建Docker镜像..."
docker-compose -f docker-compose.test.yml build

# 启动服务
echo "▶️  启动服务..."
docker-compose -f docker-compose.test.yml up -d

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 30

# 检查服务状态
echo "📊 检查服务状态..."
docker-compose -f docker-compose.test.yml ps

# 显示服务日志
echo ""
echo "✅ 测试环境部署完成！"
echo ""
echo "📝 访问地址："
echo "  - 前端：http://localhost"
echo "  - Gateway：http://localhost:8080"
echo "  - Nacos控制台：http://localhost:8848/nacos (nacos/nacos)"
echo "  - Sentinel控制台：http://localhost:8858"
echo "  - RabbitMQ管理：http://localhost:15672 (guest/guest)"
echo ""
echo "📋 查看日志："
echo "  docker-compose -f docker-compose.test.yml logs -f"
echo ""
echo "🛑 停止服务："
echo "  docker-compose -f docker-compose.test.yml down"
echo ""
