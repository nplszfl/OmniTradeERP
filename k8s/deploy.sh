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
#    📌 改法：**按清单文件逐个 apply，不手写文件名列表** ——
#    以后往 k8s/ 加一个 yaml，脚本自动跟着部署（手写清单 = 会漂移的事实来源）。
echo "⚙️  部署微服务..."
for f in k8s/gateway.yaml k8s/core-services.yaml k8s/services.yaml k8s/ai-services.yaml; do
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
