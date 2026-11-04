#!/bin/bash
# ERP系统部署准备脚本
# 用途：部署前检查和准备所有必要的配置和依赖

set -e
# 📌 **`set -e`（2026-11-04 补）**：这个脚本原来没有 → 任何一条命令失败
#    （docker compose 起不来 / mvn 编译失败 / jar 不存在）都会被静默吞掉，
#    脚本照样往下跑并打印「✅ 完成」→ 以为启动/停止成功了，实际什么都没发生。

echo "🚀 OmniTrade ERP 部署准备"
echo "======================================"
echo ""

# 1. 检查Java环境
echo "1️⃣ 检查Java环境..."
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | head -n 1)
    echo "✅ Java已安装: $java_version"
else
    echo "❌ Java未安装，请先安装JDK 21"
    exit 1
fi
echo ""

# 2. 检查Docker环境
echo "2️⃣ 检查Docker环境..."
if command -v docker &> /dev/null; then
    docker_version=$(docker --version)
    echo "✅ Docker已安装: $docker_version"
    
    # 检查Docker Compose
    if command -v docker-compose &> /dev/null; then
        compose_version=$(docker-compose --version)
        echo "✅ Docker Compose已安装: $compose_version"
    else
        echo "⚠️  Docker Compose未安装，将使用docker compose"
    fi
else
    echo "❌ Docker未安装，请先安装Docker"
    exit 1
fi
echo ""

# 3. 检查Maven环境
echo "3️⃣ 检查Maven环境..."
if command -v mvn &> /dev/null; then
    maven_version=$(mvn -version | head -n 1)
    echo "✅ Maven已安装: $maven_version"
else
    echo "⚠️  Maven未安装，将使用Docker Maven"
fi
echo ""

# 4. 检查MySQL连接
echo "4️⃣ 检查MySQL连接..."
if command -v mysql &> /dev/null; then
    # 尝试连接（假设本地MySQL）
    if mysql -u root -e "SELECT 1;" &> /dev/null 2>&1; then
        echo "✅ MySQL连接正常"
        
        # 列出已有数据库
        echo "📋 已有数据库:"
        mysql -u root -e "SHOW DATABASES;" 2>/dev/null | grep -v "Database\|information_schema\|mysql\|performance_schema"
    else
        echo "⚠️  MySQL需要密码或连接失败"
        echo "💡 提示：请在部署前确保MySQL可访问"
    fi
else
    echo "⚠️  MySQL命令未找到"
    echo "💡 提示：将在部署时使用Docker MySQL"
fi
echo ""

# 5. 检查Nacos（如果本地部署）
echo "5️⃣ 检查Nacos..."
if curl -s http://localhost:8848/nacos > /dev/null 2>&1; then
    echo "✅ Nacos服务已运行"
else
    echo "⚠️  Nacos服务未运行"
    echo "💡 提示：启动Nacos或使用docker-compose启动"
fi
echo ""

# 6. 检查Redis（如果需要）
echo "6️⃣ 检查Redis..."
if command -v redis-cli &> /dev/null; then
    if redis-cli ping > /dev/null 2>&1; then
        echo "✅ Redis服务已运行"
    else
        echo "⚠️  Redis服务未运行"
    fi
else
    echo "⚠️  Redis命令未找到"
    echo "💡 提示：将使用Docker Redis"
fi
echo ""

# 7. 检查项目结构
echo "7️⃣ 检查项目结构..."
if [ -d "erp-pricing-service" ]; then
    echo "✅ 智能定价服务目录存在"
    if [ -f "erp-pricingci-service/src/main/java/com/crossborder/pricing/PricingServiceApplication.java" ]; then
        echo "✅ 智能定价服务主类存在"
    else
        echo "⚠️  智能定价服务主类缺失"
    fi
else
    echo "❌ 智能定价服务目录不存在"
fi

if [ -d "erp-inventory-prediction-service" ]; then
    echo "✅ 库存预测服务目录存在"
else
    echo "❌ 库存预测服务目录不存在"
fi

if [ -d "erp-ai-assistant-service" ]; then
    echo "✅ AI智能客服服务目录存在"
else
    echo "❌ AI智能客服服务目录不存在"
fi

if [ -d "erp-product-description-service" ]; then
    echo "✅ 产品描述生成服务目录存在"
else
    echo "❌ 产品描述生成服务目录不存在"
fi
echo ""

# 8. 检查配置文件
echo "8️⃣ 检查配置文件..."
config_files=(
    "docker-compose.yml"
    "docker-compose.test.yml"
    "erp-pricing-service/src/main/resources/application.yml"
    "erp-inventory-prediction-service/src/main/resources/application.yml"
    "erp-ai-assistant-service/src/main/resources/application.yml"
    "erp-product-description-service/src/main/resources/application.yml"
)

for file in "${config_files[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ $file"
    else
        echo "⚠️  $file 缺失"
    fi
done
echo ""

# 9. 检查数据库脚本
echo "9️⃣ 检查数据库脚本..."
db_scripts=(
    "database/pricing-service.sql"
    "database/inventory-prediction-service.sql"
    "database/ai-assistant-service.sql"
    "database/product-description-service.sql"
)

for script in "${db_scripts[@]}"; do
    if [ -f "$script" ]; then
        echo "✅ $script"
    else
        echo "⚠️  $script 缺失"
    fi
done
echo ""

# 10. 部署环境检查完成
echo "======================================"
echo "✅ 部署准备检查完成！"
echo ""
echo "📋 下一步："
echo "1. 执行数据库脚本（如需要）"
echo "2. 编译所有服务"
echo "3. 启动Nacos（如果使用本地Nacos）"
echo "4. 使用docker-compose启动所有服务"
echo ""
echo "🚀 准备开始部署！"
