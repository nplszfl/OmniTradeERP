#!/bin/bash
set -e

# 部署配置
SERVER_IP=${1:-""}
SSH_USER=${2:-"root"}
SSH_KEY=${3:-"~/.ssh/id_rsa"}

if [ -z "$SERVER_IP" ]; then
    echo "用法: ./deploy.sh <服务器IP> [SSH用户] [SSH密钥路径]"
    echo "示例: ./deploy.sh 192.168.1.100 root ~/.ssh/id_rsa"
    exit 1
fi

echo "=== OmniTradeERP 部署脚本 ==="
echo "服务器: $SERVER_IP"
echo "用户: $SSH_USER"

# 创建临时目录
TEMP_DIR="/tmp/erp-deploy-$(date +%Y%m%d%H%M%S)"

echo ""
echo "=== 1. 打包项目 ==="
cd /Users/huanghuixiang/OmniTradeERP
mvn clean package -DskipTests -q

echo ""
echo "=== 2. 准备部署文件 ==="
mkdir -p "$TEMP_DIR"
# compose 的 build context 是仓库根目录，服务器上要在容器里重新 mvn 构建，
# 所以必须把各模块源码 + 根 pom 一起带过去（只带 docker/ 是构建不出来的）。
rsync -a \
  --exclude 'target/' \
  --exclude '.git/' \
  --exclude 'node_modules/' \
  --exclude 'erp-web/dist/' \
  --exclude '*.log' \
  ./ "$TEMP_DIR/"

# 创建Nacos命名空间（如果不存在）
echo ""
echo "=== 3. 创建Nacos命名空间 erp-dev ==="
curl -X POST "http://mse-d5a70466-nacos-ans.mse.aliyuncs.com:8848/nacos/v1/console/namespaces" \
  -d "custom=true&namespace=erp-dev&namespaceShowName=ERP开发环境" 2>/dev/null || true

echo ""
echo "=== 4. 上传文件到服务器 ==="
rsync -avz -e "ssh -i $SSH_KEY" \
  --exclude 'target/' \
  --exclude '.git/' \
  --exclude '*.class' \
  --exclude 'node_modules/' \
  "$TEMP_DIR/" "$SSH_USER@$SERVER_IP:/opt/omni-trade-erp/"

echo ""
echo "=== 5. 在服务器上构建和启动Docker容器 ==="
ssh -i "$SSH_KEY" "$SSH_USER@$SERVER_IP" << 'ENDSSH'
cd /opt/omni-trade-erp
docker-compose -f docker-compose.prod.yml down 2>/dev/null || true
docker-compose -f docker-compose.prod.yml up -d --build
docker ps
ENDSSH

echo ""
echo "=== 6. 清理本地临时文件 ==="
rm -rf "$TEMP_DIR"

echo ""
echo "=== 部署完成！==="
echo "访问地址: http://$SERVER_IP"
echo "Gateway: http://$SERVER_IP:8080"
echo "Sentinel: http://$SERVER_IP:8858"
