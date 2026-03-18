# 🚀 OmniTrade ERP Kubernetes 部署指南

**为什么需要 Kubernetes？**

对于企业级跨境电商 ERP 系统，Kubernetes 提供了以下关键优势：

1. **高可用性**：自动故障恢复、多副本部署
2. **自动扩缩容**：根据流量自动调整资源
3. **服务治理**：服务发现、负载均衡、健康检查
4. **滚动更新**：零停机部署
5. **资源管理**：精确控制 CPU/内存使用
6. **生产级可靠性**：企业级容器编排标准

---

## 📋 部署前准备

### 1. Kubernetes 集群

**本地开发（推荐先试用）：**
- Docker Desktop（Mac/Windows）：启用 Kubernetes
- Minikube：轻量级本地 K8s
- Kind：在 Docker 中运行 K8s

**云平台：**
- 阿里云 ACK（推荐）
- 腾讯云 TKE
- AWS EKS
- Google GKE

### 2. 命令行工具

```bash
# 安装 kubectl
brew install kubectl

# 验证安装
kubectl version --client
```

### 3. 容器镜像仓库

将服务镜像推送到 Docker Hub 或私有镜像仓库：

```bash
# 镜像命名规范
n registry-name/erp-<service-name>:<version>

# 示例
nplszfl/erp-gateway:latest
nplszfl/erp-user-service:latest
nplszfl/erp-pricing-service:latest
```

---

## 📁 Kubernetes 配置文件结构

```
k8s/
├── namespace.yaml              # 命名空间
├── mysql.yaml                  # MySQL 配置
├── redis.yaml                  # Redis 配置
├── nacos.yaml                  # Nacos 配置
├── gateway.yaml                # 网关配置
├── core-services.yaml          # 核心服务服务配置
├── services.yaml               # 平台服务配置
├── ai-services.yaml            # AI 服务配置
├── build-images.sh             # 构建镜像脚本
└── deploy.sh                   # 一键部署脚本
```

---

## 🚀 部署步骤

### 第1步：构建并推送镜像

```bash
cd /Users/huanghuixiang/.openclaw/workspace/OmniTradeERP/k8s

# 给脚本执行权限
chmod +x build-images.sh

# 构建所有镜像
./build-images.sh
```

**build-images.sh 内容：**
```bash
#!/bin/bash
set -e

REGISTRY="nplszfl"
VERSION="latest"

SERVICES=(
  "erp-gateway"
  "erp-user-service"
  "erp-product-service"
  "erp-order-service"
  "erp-platform-service"
  "erp-inventory-service"
  "erp-warehouse-service"
  "erp-finance-service"
  "erp-pricing-service"
  "erp-inventory-prediction-service"
  "erp-ai-assistant-service"
  "serp-product-description-service"
)

for service in "${SERVICES[@]}"; do
  echo "🔨 Building $service..."
  docker build -f docker/Dockerfile.${service} -t ${REGISTRY}/${service$VERSION} .
  echo "📤 Pushing $service..."
  docker push ${REGISTRY}/${service}:${VERSION}
done

echo "✅ All images built and pushed!"
```

### 第2步：创建命名空间

```bash
kubectl apply -f namespace.yaml
```

### 第3步：部署基础设施

```bash
# 部署 MySQL
kubectl apply -f mysql.yaml

# 部署 Redis
kubectl apply -f redis.yaml

# 部署 Nacos
kubectl apply -f nacos.yaml

# 等待基础设施就绪
kubectl wait --for=condition=ready pod -l app=mysql -n erp --timeout=300s
kubectl wait --for=condition=ready pod -l app=redis -n erp --timeout=300s
kubectl wait --for=condition=ready pod -l app=nacos -n erp --timeout=300s
```

### 第4步：部署网关

```bash
kubectl apply -f gateway.yaml
```

### 第5步：部署核心服务

```bash
kubectl apply -f core-services.yaml
```

### 第6步：部署平台服务

```bash
kubectl apply -f services.yaml
```

### 第7步：部署 AI 服务

```bash
kubectl apply -f ai-services.yaml
```

---

## ⚡ 一键部署

使用提供的自动化脚本：

```bash
cd /Users/huanghuixiang/.openclaw/workspace/OmniTradeERP/k8s

# 给脚本执行权限
chmod +x deploy.sh

# 一键部署
./deploy.sh
```

---

## 📊 验证部署

### 1. 检查所有 Pod 状态

```bash
# 查看所有 Pod
kubectl get pods -n erp

# 查看详细信息
kubectl describe pods -n erp

# 查看日志
kubectl logs -f deployment/erp-gateway -n erp
kubectl logs -f deployment/erp-pricing-service -n erp
```

### 2. 检查服务状态

```bash
# 查看所有 Service
kubectl get svc -n erp

# 查看详细信息
kubectl describe svc erp-gateway-service -n erp
```

### 3. 检查 Ingress

```bash
# 查看 Ingress
kubectl get ingress -n erp

# 查看详细信息
kubectl describe ingress erp-ingress -n erp
```

### 4. 测试访问

```bash
# 端口转发（本地测试）
kubectl port-forward svc/erp-gateway-service 8080:8080 -n erp

# 测试健康检查
curl http://localhost:8080/actuator/health
```

---

## 🔧 常见操作

### 扩容服务

```bash
# 扩展到 3 个副本
kubectl scale deployment erp-pricing-service --replicas=3 -n erp

# 自动扩缩容（HPA）
kubectl autoscale deployment erp-pricing-service --cpu-percent=70 --min=2 --max=10 -n erp
```

### 更新镜像

```bash
# 滚动更新
kubectl set image deployment/erp-pricing-service pricing-service=nplszfl/erp-pricing-service:v1.5.1 -n erp

# 查看更新状态
kubectl rollout status deployment/erp-pricing-service -n erp

# 回滚
kubectl rollout undo deployment/erp-pricing-service -n erp
```

### 查看资源使用

```bash

# 查看所有 Pod 的资源使用
kubectl top pods -n erp

# 查看节点的资源使用
kubectl top nodes
```

### 进入容器

```bash
# 进入 Pod
kubectl exec -it <pod-name> -n erp -- /bin/bash

# 查看环境变量
kubectl exec <pod-name> -n erp -- env
```

---

## 🔒 配置管理

### 1. ConfigMap（配置文件）

创建 `configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: erp-config
  namespace: erp
data:
  application.yml: |
    server:
      port: 8080
    spring:
      application:
        name: erp-gateway
```

应用配置：

```bash
kubectl apply -f configmap.yaml
```

在 Deployment 中使用：

```yaml
spec:
  containers:
  - name: gateway
    volumeMounts:
    - name: config
      mountPath: /app/config
  volumes:
  - name: config
    configMap:
      name: erp-config
```

### 2. Secret（敏感信息）

创建 `secret.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: erp-secret
  namespace: erp
type: Opaque
data:
  mysql-password: cm9vdA==  # base64 编码
  llm-api-key: <base64-encoded-key>
```

应用密钥：

```bash
kubectl apply -f secret.yaml
```

在 Deployment 中使用：

```yaml
spec:
  containers:
  - name: gateway
    env:
    - name: MYSQL_PASSWORD
      valueFrom:
        secretKeyRef:
          name: erp-secret
          key: mysql-password
```

---

## 📈 监控和日志

### 1. 集成 Prometheus + Grafana

```bash
# 安装 Prometheus Operator
kubectl apply -f https://raw.githubusercontent.com/prometheus-operator/prometheus-operator/main/bundle.yaml
```

### 2. 集成 ELK Stack（日志）

```bash
# 安装 Elastic + Kibana
kubectl apply -f https://download.elastic.co/downloads/eck/eck-operator.yaml
```

---

## 🌐 暴露服务

### 方式1：LoadBalancer（云平台）

```yaml
spec:
  type: LoadBalancer
  ports:
  - port: 8080
    targetPort: 8080
```

### 方式2：NodePort（所有环境）

```yaml
spec:
  type: NodePort
  ports:
  - port: 8080
    targetPort: 8080
    nodePort: 30080
```

### 方式3：Ingress（推荐）

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: erp-ingress
  namespace: erp
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - erp.yourdomain.com
    secretName: erp-tls
  rules:
  - host: erp.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: erp-gateway-service
            port:
              number: 8080
```

---

## 🧹 清理部署

```bash
# 删除所有资源
kubectl delete -f namespace.yaml

# 或者逐个删除
kubectl delete deployment -n erp --all
kubectl delete service -n erp --all
kubectl delete configmap -n erp --all
kubectl delete secret -n erp --all
```

---

## 🎯 生产环境建议

### 1. 副本数配置

| 服务 | 副本数 | 说明 |
|------|--------|------|
| Gateway | 3+ | 入口，需要高可用 |
| 核心服务 | 2-3 | 根据负载调整 |
| AI 服务 | 2-4 | CPU 密集型，可多副本 |
| 基础设施 | 1-2 | 高可用模式 |

### 2. 资源限制

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

### 3. 健康检查

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

### 4. 自动扩缩容（HPA）

```bash
# 安装 Metrics Server
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# 创建 HPA
kubectl autoscale deployment erp-pricing-service \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n erp
```

---

## 📞 故障排查

### Pod 启动失败

```bash
# 查看 Pod 状态
kubectl describe pod <pod-name> -n erp

# 查看日志
kubectl logs <pod-name> -n erp

# 查看之前容器的日志
kubectl logs <pod-name> --previous -n erp
```

### 服务无法访问

```bash
# 检查 Service
kubectl describe svc <service-name> -n erp

# 检查 Endpoint
kubectl get endpoints <service-name> -n erp

# 测试网络连接
kubectl run -it --rm debug --image=busybox --restart=Never -- sh
wget -O- http://<service-name>:<port>/actuator/health
```

---

## 🎉 完成！

**你的 OmniTrade ERP 已经部署在 Kubernetes 上！**

访问地址：
- **Nacos 控制台：** http://nacos.yourdomain.com:8848/nacos
- **Gateway：** http://erp.yourdomain.com
- **Sentinel：** http://sentinel.yourdomain.com:8858

---

**文档生成时间：** 2026-03-18
**Kubernetes 版本：** v1.28+
**推荐集群：** 阿里云 ACK / 腾讯云 TKE
