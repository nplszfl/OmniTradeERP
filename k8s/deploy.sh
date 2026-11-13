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
  # 📌 **含占位符的清单必须跳过** —— 下面「渲染 Ingress 域名」那一步会 apply 渲染后的版本。
  #    如果这里也 apply 一次，就会**先后 apply 两个版本**：
  #    先写进集群的是带 `__INGRESS_HOST__` 的版本（非法域名），
  #    再 apply 渲染版覆盖它 → 中间有个**窗口期**，Ingress 路由是坏的；
  #    万一渲染版 apply 失败，集群里就**永久留着**那个非法域名的版本。
  #    ✅ 判据 = 清单里有没有占位符（不是硬编码文件名 → 加新清单也不会漏）。
  if grep -q '__[A-Z_]*__' "$f"; then
    # 📌 **必须用 ${base} 而不是 $base**（2026-11-13 实测踩到的 bash 陷阱）：
    #    写成 `echo "… $base（含占位符…"` → bash 会把全角括号 `（` 的**首字节**
    #    当成变量名的一部分 → 变量名变成 `base<U+FF08 的首字节>`
    #    → `set -u` 下报 `base?: unbound variable` → **整个部署脚本在第 82 行崩掉**。
    #    📌 判别依据：变量名后面紧跟**非 ASCII 字符**时，bash 不认边界。
    #       （原脚本里是 `$f"` → 紧跟引号 → 没事；这次新增了一处中文括号 → 就炸了）
    #    ✅ 解法：用 `${base}` 显式界定变量名边界。
    echo "  ⏭️  跳过 ${base}（含占位符，稍后渲染后 apply）"
    continue
  fi
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

# 📌 **渲染 Ingress 域名**（2026-11-13 新增）
#
# ⚠️ **为什么这一步必须存在**：
#    `k8s/gateway.yaml` 的 Ingress 规则里，host 现在是**占位符** `__INGRESS_HOST__`
#    （不是写死的域名 —— 写死 `erp.example.com` 那种保留域名 → 真实域名的请求
#    匹配不到规则 → **全站 404**，而且 kubectl 不报错 → 部署照样打印「✅ 部署完成！」）。
#
#    ✅ 这里的职责：**把占位符换成调用方给的真实域名**，然后 apply 渲染后的清单。
#    📌 真实域名从环境变量 `INGRESS_HOST` 来（生产环境由 CI / 运维显式注入）。
#    📌 **没给就必须报错退出** —— 绝不能「用默认值糊过去」：
#       糊过去 = 又把保留域名写进集群 = 回到原来的坑。
echo ""
echo "🌐 渲染 Ingress 域名..."
INGRESS_HOST="${INGRESS_HOST:-}"
if [ -z "$INGRESS_HOST" ]; then
  echo "❌ 未设置 INGRESS_HOST（Ingress 的对外域名）"
  echo "   例：INGRESS_HOST=erp.你的域名.com ./k8s/deploy.sh"
  echo "   📌 **故意不提供默认值** —— 默认值只能是保留域名（example.com），"
  echo "      写进集群后真实域名的请求匹配不到规则 → 全站 404（且不报错）。"
  exit 1
fi

# 域名基本校验：必须是像域名的样子（至少一个点，且不含非法字符）
if ! printf '%s' "$INGRESS_HOST" | grep -Eq '^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$'; then
  echo "❌ INGRESS_HOST 不是合法域名：$INGRESS_HOST"
  echo "   （应当形如 erp.example.org —— 至少两级、且不能用保留域名）"
  exit 1
fi

RENDERED="$(mktemp -d)/gateway.yaml"
# 📌 用 python 而不是 envsubst —— macOS 默认没装 gettext（envsubst 经常不存在）
# 📌 **渲染逻辑只有一份**（`scripts/render-ingress.py`）—— CI 那条路径也调它
#    → 避免两份实现漂移（规则 112 的同源告警）。
# 📌 不用内嵌 heredoc 是因为 heredoc 正文一旦缩进不对（这里是 shell，不涉及 YAML，
#    但 CI 那边正是栽在 YAML 块标量上）→ 抽成脚本 = 两边都不会缩进崩。
if ! python3 scripts/render-ingress.py "$INGRESS_HOST" k8s/gateway.yaml "$RENDERED"; then
  echo ""
  echo "❌ 渲染 Ingress 失败"
  exit 1
fi
echo "  → Ingress host = $INGRESS_HOST"
if ! kubectl apply -f "$RENDERED"; then
  echo ""
  echo "❌ apply 渲染后的 Ingress 失败"
  exit 1
fi

echo ""
echo "✅ 部署完成！"
echo ""
echo "🌐 访问地址：https://$INGRESS_HOST"
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
