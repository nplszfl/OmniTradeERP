# 🚢 部署文档

> 📌 **本文档已对照仓库现实核验（2026-11-05）**：
> 下面的服务数、文件名、端口都是从
> `docker-compose*.yml` / `k8s/*.yaml` / `*/application.yml` **实测反查**出来的，
> 不是手写记忆 —— 手写就会漂（规则 51）。
> 改完部署方式请回来同步这份文档。

## 部署方式总览

| 方式 | 适用场景 | 复杂度 |
|------|---------|--------|
| Docker Compose | 开发/测试 | ⭐ |
| Kubernetes | 生产环境 | ⭐⭐⭐ |
| 裸机部署 | 特殊需求 | ⭐ |

---

## 方式一：Docker Compose 部署（推荐）

仓库里有 8 个 compose 文件，**按需要选**：

| 文件 | 服务数 | 内容 |
|------|------|------|
| `docker-compose.lite.yml` | 3 | **只有基础设施**：MySQL + Redis + Nacos（**不含网关**，起不来业务） |
| `docker-compose.minimal.yml` | 7 | 基础设施 + Gateway + user / product / order |
| `docker-compose.full.yml` | 16 | 基础设施 + Gateway + 11 个业务服务 + frontend |
| `docker-compose.yml` | 30 | **全量**：23 个 ERP 后端服务 + frontend + nginx + MySQL + RabbitMQ + Nacos + Sentinel |
| `docker-compose.prod.yml` | 13 | 生产裁剪版 |
| `docker-compose.test.yml` / `.mini.yml` / `.audit.yml` / `.ssl.yml` | — | 各场景专用 |

### 1. 轻量部署（只要数据库）

```bash
cd OmniTradeERP
docker-compose -f docker-compose.lite.yml up -d
```

### 2. 最小化部署（跑通一条业务链路）

```bash
docker-compose -f docker-compose.minimal.yml up -d
```

### 3. 常用全量部署（开发/测试推荐）

```bash
docker-compose -f docker-compose.yml up -d
```

> ⚠️ 想要**更小的依赖面**（不起 RabbitMQ / Sentinel / nginx）就用 `docker-compose.full.yml`，
> 但它**只含 11 个业务服务**，不是全量。

### 4. 测试环境部署

```bash
./deploy-test.sh
```

---

## 方式二：Kubernetes 部署（生产环境）

### 前置要求
- Kubernetes 1.24+
- 存储类（StorageClass）
- 已配置 `KUBE_CONFIG` secret（否则 CI 的 `deploy-k8s` job 会**全部 skip 并显示 success**，
  看起来部署通了，实际一次都没跑过）

### 部署步骤

```bash
cd k8s
./deploy.sh
```

`k8s/deploy.sh` 做的事（**它带 `set -euo pipefail`，任何一步失败都会立刻停下并指名是哪份清单**）：

1. 创建 namespace `erp`
2. `mysql.yaml` → **等 ready** → `redis.yaml` → `nacos.yaml` → 等 ready
3. 依次 apply 其余全部清单：
   `k8s/gateway.yaml` / `k8s/services.yaml` / `k8s/core-services.yaml` /
   `k8s/ai-services.yaml` / `k8s/extended-services.yaml`

> 📌 **不要手写 `kubectl apply -f <某个文件>`** ——
> 漏 apply 一份清单 = 那批服务永远跑旧镜像。
> 清单数量会随服务增加而变，**脚本按 `k8s/*.yaml` 自动遍历**，手写一定会漂。

### 清单清单

| 文件 | 内容 |
|------|------|
| `k8s/namespace.yaml` | namespace 定义（由 `deploy.sh` 动态创建，这个是备选） |
| `k8s/mysql.yaml` / `k8s/redis.yaml` / `k8s/nacos.yaml` | 基础设施 |
| `k8s/gateway.yaml` | 网关（Deployment + Service） |
| `k8s/services.yaml` | 基础服务组 |
| `k8s/core-services.yaml` | 核心服务组 |
| `k8s/ai-services.yaml` | AI 服务组 |
| `k8s/extended-services.yaml` | 扩展服务组 |

> 📌 共 **23 个 ERP Deployment**（+ nacos = 24）。
> 与 **CI 构建矩阵 ↔ 部署计划** 必须三方一致，
> 由 `scripts/check-k8s-deploy-coverage.py` 巡检（不一致直接红）。

### 部署后自查

```bash
kubectl get deploy -n erp          # 期望 23 个 ERP Deployment 全 Ready
kubectl get pods -n erp
kubectl get svc -n erp
kubectl logs -f deployment/erp-gateway -n erp
```

---

## 方式三：裸机部署

### 系统要求
- CPU: 4核+
- 内存: 8GB+
- 磁盘: 100GB+

### 部署脚本

```bash
# 快速部署
./quick-start.sh

# 本地构建后部署
./local-build.sh && ./start.sh

# 智能部署（自动选择配置）
./smart-deploy.sh
```

---

## 环境变量配置

核心配置项：

```bash
# 数据库
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/omnitrade
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_password

# Nacos
SPRING_CLOUD_NACOS_SERVER_ADDR=localhost:8848

# Redis
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

# Sentinel
SENTINEL_DASHBOARD=localhost:8858

# AI服务
AI_DEEPSEEK_API_KEY=your_api_key
AI_MODEL_NAME=deepseek-chat
```

> 📌 **部署层注入的变量名必须和应用侧真正读的一致** —— 名字对不上时注入就是摆设，
> 单测和 `mvn package` 全绿，只有真起容器才暴露。
> 由 `scripts/check-env-contract.py` 巡检。

---

## 服务访问

| 服务 | 地址 |
|------|------|
| API Gateway | http://localhost:8080 |
| Nacos 控制台 | http://localhost:8848/nacos (nacos/nacos) |
| 前端 Web（compose） | http://localhost:80 |
| 前端 Web（dev server） | http://localhost:3000 |
| nginx（compose） | http://localhost:80 / https://localhost:443 |
| Sentinel 控制台 | http://localhost:8858 |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

> 📌 **端口的唯一事实来源是各模块 `application.yml` 的 `server.port`** ——
> 改端口时 deployment 层要**五处一起改**（containerPort / livenessProbe / readinessProbe /
> Service / 文档），由 `scripts/check-port-consistency.py` 巡检。
> 23 个服务当前占用的端口：8080-8100、8138、8139。

---

## 健康检查

```bash
# 网关（actuator 已在网关暴露 include: '*'）
curl http://localhost:8080/actuator/health

# 特定服务
curl http://localhost:8081/actuator/health
```

---

## 相关文档

- [快速开始](Quick-Start)
- [系统架构](Architecture)
- [AI功能介绍](AI-Features)
- [死接口清单](DEAD-ENDPOINTS.md)
