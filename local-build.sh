#!/bin/bash
# 使用本地 Java + Maven Wrapper 快速编译

set -e

echo "🔨 使用本地环境快速编译所有服务..."
echo "======================================"

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "❌ Java 未安装"
    exit 1
fi

echo "✅ Java 版本: $(java -version 2>&1 | head -n 1)"
echo ""

# 使用 mvnw 编译（项目自带的 Maven）
echo "📦 编译所有服务..."
./mvnw clean package -DskipTests -B

echo ""
echo "✅ 编译完成！"
echo ""
echo "📋 生成的 JAR 包："
find . -name "*.jar" -path "*/target/*" | grep -v "original" | while read jar; do
  size=$(du -h "$jar" | cut -f1)
  echo "  - $jar ($size)"
done

echo ""
echo "💡 下一步："
echo "  ./docker-deploy.sh"
