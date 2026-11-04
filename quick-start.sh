#!/bin/bash
# ERP系统快速启动脚本

set -e
# 📌 **`set -e`（2026-11-04 补）**：这个脚本原来没有 → 任何一条命令失败
#    （docker compose 起不来 / mvn 编译失败 / jar 不存在）都会被静默吞掉，
#    脚本照样往下跑并打印「✅ 完成」→ 以为启动/停止成功了，实际什么都没发生。

echo "🔥 启动 OmniTrade ERP 系统"
echo "================================="

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven未安装，请先安装Maven"
    echo "📖 安装步骤见 README.md 或联系火球鼠"
    exit 1
fi

echo "✅ Maven版本:"
mvn -version
echo ""

# 检查Nacos
if ! curl -s http://localhost:8848/nacos > /dev/null 2>&1; then
    echo "⚠️  Nacos未启动，启动Nacos..."
    cd /usr/local/nacos
    ./bin/startup.sh -m standalone
    sleep 5
    cd -
fi

echo "✅ 环境检查完成"
echo ""
echo "🚀 启动智能定价服务..."
echo "================================="
echo "📊 服务地址: http://localhost:8087"
echo "📖 API文档: http://localhost:8087/swagger-ui.html"
echo "🔍 健康检查: http://localhost:8087/api/v1/pricing/health"
echo "================================="
echo ""

# 编译并启动
cd erp-pricing-service
mvn clean spring-boot:run -DskipTests
