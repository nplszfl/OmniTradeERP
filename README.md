# 跨境电商ERP系统

## 项目简介

基于Spring Cloud Alibaba微服务架构的跨境电商ERP系统，支持多平台订单管理、商品管理、库存管理、财务管理等核心功能。

## 技术栈

### 后端
- **Spring Boot 3.3.5**
- **Spring Cloud 2024.0.1**
- **Spring Cloud Alibaba 2024.0.0**
- **JDK 21** + Virtual Threads
- **MyBatis Plus 3.5.6**
- **Nacos** - 服务注册发现 + 配置中心
- **Sentinel** - 流量控制
- **Gateway** - API网关
- **OpenFeign** - 服务调用
- **MySQL 8.0+** - 主数据库
- **Redis** - 缓存
- **RabbitMQ** - 消息队列
- **Elasticsearch** - 日志分析

### 前端
- **Vue 3.4**
- **TypeScript**
- **Vite 5.0**
- **Element Plus**
- **Pinia**
- **Axios**
- **ECharts**

## 模块说明

```
cross-border-erp/
├── erp-common/              # 公共模块
│   ├── constant/           # 常量枚举
│   ├── config/             # 配置类
│   ├── exception/          # 异常处理
│   └── result/             # 统一响应
├── erp-gateway/            # 网关服务 (:8080)
├── erp-order-service/      # 订单服务 (:8081)
├── erp-platform-service/   # 平台API服务 (:8082)
├── erp-product-service/    # 商品服务 (:8083)
├── erp-user-service/       # 用户服务 (:8084)
├── erp-inventory-service/  # 库存服务 (:8085)
├── erp-warehouse-service/  # 仓库服务 (:8086)
├── erp-finance-service/    # 财务服务 (:8087)
├── erp-web/                # 前端项目 (:3000)
└── database/               # 数据库脚本
    └── init.sql           # 初始化脚本
```

## 支持平台

- ✅ 亚马逊
- ✅ eBay
- ✅ Shopee
- ✅ Lazada
- ✅ TikTok Shop
- ✅ Temu
- ✅ 速卖通
- ✅ SHEIN
- ✅ Shopify独立站
- ✅ WooCommerce独立站

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis
- RabbitMQ
- Nacos 2.4+
- Sentinel 1.8+

### 1. 初始化数据库

```bash
mysql -u root -p < database/init.sql
```

### 2. 启动中间件

```bash
# Nacos
cd nacos/bin
./startup.sh -m standalone

# Sentinel
java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=localhost:8858 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard.jar

# Redis
redis-server

# RabbitMQ
rabbitmq-server
```

### 3. 启动后端服务

```bash
# 编译打包
mvn clean package -DskipTests

# 启动网关
java -jar erp-gateway/target/erp-gateway-1.0.0.jar

# 启动订单服务
java -jar erp-order-service/target/erp-order-service-1.0.0.jar

# 启动平台服务
java -jar erp-platform-service/target/erp-platform-service-1.0.0.jar
```

### 4. 启动前端

```bash
cd erp-web
npm install
npm run dev
```

### 5. 访问系统

- 前端地址: http://localhost:3000
- Gateway: http://localhost:8080
- Nacos控制台: http://localhost:8848/nacos
- Sentinel控制台: http://localhost:8858

## 核心功能

### 订单管理
- 多平台订单同步
- 订单查询与筛选
- 订单状态管理
- 物流信息更新
- 订单统计分析

### 平台管理
- 平台配置管理
- API密钥管理
- 连接测试
- 手动/自动同步

### 商品管理
- 商品信息管理
- SKU管理
- 多平台映射
- 商品分类管理

### 库存管理
- 实时库存查询
- 库存预警
- 出入库管理
- 库存流水记录

### 仓库管理
- 多仓库管理
- 出入库单据
- 仓库调拨

### 财务管理
- 财务流水
- 收支统计
- 成本核算
- 利润分析

## API文档

各服务提供Swagger文档，访问地址：

- 订单服务: http://localhost:8081/swagger-ui.html
- 平台服务: http://localhost:8082/swagger-ui.html

## 性能指标

- 支持日订单量：10万+
- 响应时间：<200ms
- 并发支持：1000+ QPS

## 开发规范

### 分支管理

- `master` - 主分支
- `develop` - 开发分支
- `feature/*` - 功能分支
- `bugfix/*` - 修复分支

### 代码规范

- 后端遵循阿里巴巴Java开发手册
- 前端遵循Vue官方风格指南
- 统一使用UTF-8编码

## License

MIT

## 联系方式

- 开发者：黄辉翔
- 项目地址：cross-border-erp

---

🔥 用代码之火点亮跨境电商之路
