#!/bin/bash
# 部署脚本
#
# 📌 **为什么必须有 `set -euo pipefail`**（2026-11-04 实测）：
# 这个脚本原来**没有** set 选项 → 任何一条 kubectl 失败（清单语法错 / RBAC 拒绝 /
# 配额超限 / 集群连不上）都会被**静默吞掉**，脚本继续往下跑，最后照常打印
# **「✅ 部署完成！」** → 运维以为部署好了，实际 6 个服务的 Deployment 根本没建。
# 📌 **失败必须让脚本死掉**，并且把「失败的到底是哪一步」打出来。

set -euo pipefail

cd "$(dirname "$0")/.."

echo "🚀 开始部署跨境电商ERP系统到K8s..."

# 创建命名空间
echo "📦 创建命名空间..."
kubectl create namespace erp --dry-run=client -o yaml | kubectl apply -f -

# 部署MySQL
echo "🗄️  部署MySQL..."
kubectl apply -f k8s/mysql.yaml

# 🔴 **JWT 签名密钥**（2026-11-12 新增）
#    📌 之前 erp-user-service 的 jwt.secret 是**仓库里的字面量** → 任何人可签出管理员 token
#    📌 现在：优先用环境变量 JWT_SECRET；没有就**现场随机生成**并写入 Secret。
#    ⚠️ **每次部署都换密钥 = 所有人被强制重新登录** → 生产环境请显式传 JWT_SECRET 固定住。
if [ -z "${JWT_SECRET:-}" ]; then
  echo "⚠️  未提供 JWT_SECRET → 随机生成一个（所有已登录用户将需要重新登录）"
  JWT_SECRET="$(openssl rand -base64 32 | tr -d '\n')"
fi
echo "🔑 创建/更新 JWT 密钥 Secret..."
kubectl -n erp create secret generic erp-jwt-secret \
  --from-literal=jwt-secret="$JWT_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -

# 等待MySQL就绪
echo "⏳ 等待MySQL就绪..."
kubectl wait --for=condition=ready pod -l app=mysql -n erp --timeout=300s

# 部署Redis
echo "💾 部署Redis..."
kubectl apply -f k8s/redis.yaml

# 等待Redis就绪
echo "⏳ 等待Redis就绪..."
kubectl wait --for=condition=ready pod -l app=redis -n erp --timeout=300s

# 部署Nacos
echo "🔧 部署Nacos..."
kubectl apply -f k8s/nacos.yaml

# 等待Nacos就绪
echo "⏳ 等待Nacos就绪..."
kubectl wait --for=condition=ready pod -l app=nacos -n erp --timeout=300s

# 部署微服务
# 📌 **必须把 k8s/ 下的服务清单全部 apply** ——
#    之前这里只 apply 了 gateway.yaml 和 services.yaml，
#    **core-services.yaml（6 个服务）和 ai-services.yaml（4 个服务）从没被 apply 过**
#    → 那 10 个服务的 Deployment 在集群里**根本不存在**
#    → 加上 CI 的 `kubectl set image` 也只覆盖 3 个 → 部署形同虚设。
# 📌 改法：**自动发现 k8s/ 下的服务清单**，不手写文件名列表 ——
#    以后往 k8s/ 加一个 yaml，脚本自动跟着部署（手写清单 = 会漂移的事实来源）。
#    📌 基础设施（mysql / redis / nacos / namespace）**单独 apply**，不在这轮里：
#       它们有状态，走上面的步骤才等得到健康检查。
#       （2026-11-04 实测：手写清单漏了 extended-services.yaml → 10 个服务的
#        Deployment 根本没进集群，而脚本照样打印「✅ 部署完成！」）
echo "⚙️  部署微服务..."
for f in k8s/*.yaml; do
  base=$(basename "$f")
  case "$base" in
    mysql.yaml|redis.yaml|nacos.yaml|namespace.yaml) continue ;;
  esac
  echo "  → apply $f"
  # 📌 apply 失败必须**立刻停下**并说清楚是哪一份清单 ——
  #    否则 `set -e` 虽然会中断，但运维只看到一句含糊的退出码。
  if ! kubectl apply -f "$f"; then
    echo ""
    echo "❌ apply 失败：$f"
    echo "   （前面的步骤可能已经生效 —— 请用 kubectl get deploy -n erp 确认实际状态）"
    exit 1
  fi
done

echo ""
echo "✅ 部署完成！"
echo ""
echo "📋 查看Pod状态："
echo "kubectl get pods -n erp"
echo ""
echo "📋 查看Service："
echo "kubectl get svc -n erp"
echo ""
echo "📋 查看日志："
echo "kubectl logs -f deployment/erp-gateway -n erp"
echo ""
echo "🌐 访问Gateway："
echo "kubectl get svc erp-gateway-service -n erp"
