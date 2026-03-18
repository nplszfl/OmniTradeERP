#!/bin/bash
# 超快部署：本地 Maven + 本地 Java + Docker 运行

set -e

# 本地 Maven 路径
MAVEN_HOME="/usr/local/maven"
export PATH="$MAVEN_HOME/bin:$PATH"

echo "🚀 超快部署 OmniTrade ERP（本地环境）"
echo "======================================"

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装"
    exit 1
fi

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "❌ Java 未安装"
    exit 1
fi

# 检查 Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven 未在 $MAVEN_HOME/bin"
    exit 1
fi

echo "✅ Docker: $(docker --version)"
echo "✅ Java: $(java -version 2>&1 | head -n 1)"
echo "✅ Maven: $(mvn --version 2>&1 | head -n 1)"
echo ""

# 第1步：本地编译（超快！）
echo "📦 步骤1：本地编译所有服务..."
echo "-----------------------------------"
echo "💡 使用本地 Maven，无需下载镜像"
echo "💡 利用本地依赖缓存，速度最快"
echo ""

mvn clean package -DskipTests -B

echo ""
echo "✅ 编译完成！"
echo ""

# 第2步：构建运行时镜像
echo "🐳 步骤2：构建 Docker 镜像（只打包运行时）..."
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

echo "✅ 超快部署完成！"
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
echo "⚡ 性能优势："
echo "  - 使用本地 Maven，无需下载 Docker 镜像"
echo "  - 利用本地依赖缓存，编译速度快 3-5 倍"
echo "  - Docker 只负责运行，不重复编译"
