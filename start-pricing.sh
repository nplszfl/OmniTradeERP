#!/bin/bash
# 智能定价服务启动脚本

set -e
# 📌 **`set -e`（2026-11-04 补）**：这个脚本原来没有 → 任何一条命令失败
#    （docker compose 起不来 / mvn 编译失败 / jar 不存在）都会被静默吞掉，
#    脚本照样往下跑并打印「✅ 完成」→ 以为启动/停止成功了，实际什么都没发生。

echo "🔥 启动智能定价服务 (v1.5.0)..."
echo "================================="

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ Java未安装"
    exit 1
fi

echo "✅ Java版本:"
java -version

# 检查Maven环境（如果没有，使用Docker）
if ! command -v mvn &> /dev/null; then
    echo "⚠️  Maven未安装，使用Docker方式..."
    
    # 使用Docker Maven编译
    echo "📦 正在编译项目..."
    docker run --rm -v "$(pwd)":/app -w /app \
        -e MAVEN_OPTS="-Xmx1024m" \
        maven:3.9.9-eclipse-temurin-21 \
        mvn clean package -DskipTests -pl erp-pricing-service -am
else
    echo "📦 正在编译项目..."
    mvn clean package -DskipTests -pl erp-pricing-service -am
fi

# 检查编译结果
if [ ! -f "erp-pricing-service/target/erp-pricing-service-1.0.0.jar" ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功！"
echo ""
echo "🚀 启动服务..."
echo "================================="
echo "📊 服务地址: http://localhost:8087"
echo "📖 API文档: http://localhost:8087/swagger-ui.html"
echo "🔍 健康检查: http://localhost:8087/api/v1/pricing/health"
echo "================================="
echo ""

# 启动服务
java -jar erp-pricing-service/target/erp-pricing-service-1.0.0.jar
