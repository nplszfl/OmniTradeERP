#!/bin/bash
# 一次性编译所有服务（Docker Maven）

set -e

echo "🔨 使用 Docker Maven 编译所有服务..."
echo "======================================"

# 使用 Docker Maven 编译所有服务
docker run --rm \
  -v "$PWD":/app \
  -v ~/.m2:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn clean package -DskipTests -B

echo ""
echo "✅ 所有服务编译完成！"
echo ""
echo "📦 生成的 JAR 包："
find . -name "*.jar" -path "*/target/*" | while read jar; do
  echo "  - $jar"
done

echo ""
echo "💡 下一步："
echo "  ./deploy-test.sh"
