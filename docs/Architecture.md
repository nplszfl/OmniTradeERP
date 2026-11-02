# 🏗️ 系统架构

## 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 (Vue 3)                            │
│                    erp-web / erp-admin                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (Spring Cloud)                  │
│                        erp-gateway                              │
│                   路由 / 鉴权 / 限流 / 监控                      │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  用户服务    │      │  订单服务    │      │  商品服务    │
│erp-user-svc │      │erp-order-svc│      │erp-product-svc│
└──────────────┘      └──────────────┘      └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  库存服务    │      │  财务服务    │      │  仓库服务    │
│erp-inventory│      │erp-finance-svc│     │erp-warehouse │
└──────────────┘      └──────────────┘      └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  平台对接    │      │  租户服务    │      │  客户管理    │
│erp-platform-│      │erp-tenant-svc│      │erp-customer │
│  api/service│      └──────────────┘      └──────────────┘
└──────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AI 服务层 (4大AI服务)                         │
├──────────────┬──────────────┬──────────────┬──────────────┐
│智能定价服务   │库存预测服务   │AI客服助手    │产品描述生成  │
│erp-pricing  │erp-inventory │erp-ai-assist │erp-product-  │
│             │-prediction  │-service     │description   │
└──────────────┴──────────────┴──────────────┴──────────────┘
```

## 核心模块

| 服务 | 端口 | 说明 |
|------|------|------|
| erp-gateway | 8080 | API网关 |
| erp-user-service | 8084 | 用户/权限 |
| erp-order-service | 8081 | 订单管理 |
| erp-product-service | 8083 | 商品管理 |
| erp-inventory-service | 8094 | 库存管理 |
| erp-finance-service | 8090 | 财务管理 |
| erp-warehouse-service | 8092 | 仓库管理 |
| erp-platform-service | 8082 | 平台对接 |
| erp-pricing-service | 8087 | 智能定价(AI) |
| erp-inventory-prediction-service | 8088 | 库存预测(AI) |
| erp-ai-assistant-service | 8089 | AI客服(AI) |
| erp-product-description-service | 8091 | 产品描述(AI) |

## 技术栈

- **后端框架**: Spring Boot 3.3.5 / Spring Cloud Alibaba
- **微服务基础设施**: Nacos (注册/配置) / Sentinel (限流熔断) / Seata (分布式事务)
- **前端框架**: Vue 3 + Vite + Element Plus
- **数据库**: MySQL 8.0
- **消息队列**: RocketMQ
- **搜索引擎**: Elasticsearch
- **缓存**: Redis
- **容器化**: Docker / Kubernetes

## 数据流

```
用户请求 → Gateway → 业务服务 → MySQL/Redis/ES
                          ↓
                    RocketMQ (异步处理)
                          ↓
                    AI 服务层 (智能决策)
```

## 相关文档

- [快速开始](Quick-Start)
- [AI功能介绍](AI-Features)
- [部署文档](Deployment)