# ERP Pricing Service 智能定价服务

## 🎯 简介

**v1.5.0** - 多因素规则驱动的智能定价系统（**非 LLM**）

这是OmniTrade ERP系统的核心定价模块，通过**多因素规则**（成本 + 利润率 + 季节性 + 库存）实现自动化、智能化的产品定价策略。

> 📌 **本模块没有任何 ML / LLM 依赖** —— 定价完全由**可解释的规则**算出，对外字段 `pricingStrategy` 也如实写明「非 LLM」。

## 🔥 核心功能

### 1. 智能定价算法
- ✅ 竞品数据分析 - 实时抓取Amazon、eBay、Shopee等平台的竞品价格
- ✅ 成本加成计算 - 基于成本和目标利润率计算基础价格
- ✅ 动态定价策略 - 根据季节性、库存、需求等因素自动调整
- ✅ 边界检查 - 确保价格在合理范围内

### 2. 竞品数据抓取
- ✅ 多平台支持（Amazon、eBay、Shopee、Lazada）
- ✅ 自动价格抓取（定时任务）
- ✅ 竞品分析（平均价、最低价、最高价）
- ✅ 销量和评分追踪

### 3. 价格历史追踪
- ✅ 完整的价格变更记录
- ✅ 趋势分析（指定时间范围）
- ✅ 利润率变化追踪
- ✅ 调整原因记录

### 4. 定时任务
- ✅ 竞品数据定时抓取（每小时）
- ✅ 动态定价定时调整（每30分钟）

## 🏗️ 架构设计

```
erp-pricing-service/
├── controller/          # 控制器层
│   ├── PricingController.java          # 定价API
│   ├── CompetitorController.java        # 竞品数据API
│   ├── PriceHistoryController.java      # 价格历史API
│   └── HealthController.java           # 健康检查
├── service/             # 服务层
│   ├── PricingService.java             # 定价服务接口
│   ├── CompetitorScrapeService.java    # 竞品抓取服务接口
│   ├── PriceHistoryService.java        # 价格历史服务接口
│   └── impl/
│       ├── PricingServiceImpl.java              # 定价服务实现
│       ├── CompetitorScrapeServiceImpl.java   # 竞品抓取服务实现
│       └── PriceHistoryServiceImpl.java       # 价格历史服务实现
├── entity/              # 实体层
│   ├── Product.java                # 产品实体
│   ├── CompetitorProduct.java      # 竞品实体
│   └── PriceHistory.java           # 价格历史实体
├── dto/                 # 数据传输对象
│   ├── PricingRequest.java          # 定价请求
│   └── PricingResponse.java         # 定价响应
├── config/              # 配置层
│   └── SwaggerConfig.java          # Swagger配置
├── schedule/            # 定时任务
│   └── ScheduleConfig.java         # 定时任务配置
└── resources/           # 资源文件
    └── application.yml             # 应用配置
```

## 🚀 快速启动

### 前置要求
- JDK 21+
- Maven 3.9+
- MySQL 8.0+
- Redis（可选）

### 启动步骤

1. **初始化数据库**
```bash
mysql -u root -p < database/pricing-service.sql
```

2. **修改配置**
编辑 `src/main/resources/application.yml`，配置数据库连接等信息

3. **编译和启动**
```bash
mvn clean package -DskipTests
java -jar target/erp-pricing-service-1.5.0.jar
```

或者使用快速启动脚本：
```bash
./quick-start.sh
```

## 🌐 API文档

启动服务后，访问 Swagger 文档：

```
http://localhost:8087/swagger-ui.html
```

## 📖 核心API

### 定价API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/pricing/health` | GET | 健康检查 |
| `/api/v1/pricing/calculate` | POST | 计算最优价格 |
| `/api/v1/pricing/batch-calculate` | POST | 批量计算最优价格 |
| `/api/v1/pricing/adjust-by-competitors/{id}` | POST | 根据竞品调整价格 |
| `/api/v1/pricing/manual-adjust` | POST | 手动调整价格 |
| `/api/v1/pricing/info/{id}` | GET | 获取产品定价信息 |

### 竞品数据API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/competitor/scrape/{id}` | POST | 抓取竞品数据 |
| `/api/v1/competitor/scrape/platform` | POST | 从指定平台抓取 |
| `/api/v1/competitor/scrape/batch` | POST | 批量抓取 |
| `/api/v1/competitor/list/{id}` | GET | 获取竞品数据 |
| `/api/v1/competitor/stats/{id}` | GET | 获取竞品价格统计 |

### 价格历史API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/price-history/list/{id}` | GET | 获取产品价格历史 |
| `/api/v1/price-history/range/{id}` | GET | 获取指定时间范围的历史 |
| `/api/v1/price-history/recent/{id}` | GET | 获取最近N次变更 |
| `/api/v1/price-history/trend/{id}` | GET | 获取价格趋势分析 |

## 💡 使用示例

### 计算最优价格

```bash
curl -X POST http://localhost:8087/api/v1/pricing/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "productCode": "PROD-001",
    "costPrice": 80.00,
    "currentPrice": 120.00,
    "targetProfitMargin": 30.00,
    "enableCompetitorAnalysis": true,
    "enableSeasonalAdjustment": true,
    "enableInventoryAdjustment": true
  }'
```

### 响应示例

```json
{
  "productId": 1,
  "productCode": "PROD-001",
  "originalPrice": 120.00,
  "recommendedPrice": 125.50,
  "priceChangePercent": 4.17,
  "profitMargin": 36.25,
  "competitorAvgPrice": 125.00,
  "competitorMinPrice": 115.00,
  "competitorMaxPrice": 135.00,
  "pricingStrategy": "多因素规则定价（成本 + 利润率 + 季节性 + 库存；非 LLM）",
  "adjustmentReason": "价格上涨4.17%，基于竞品分析和市场需求",
  "calculationTime": "2026-03-17T11:00:00"
}
```

## 🧪 定价算法

### 核心公式

```
最优价格 = 基础价格 × (1 + 季节性因子) × (1 + 库存因子) × (1 + 需求因子)

其中：
- 基础价格 = 成本价 × (1 + 目标利润率)
- 竞品调整后价格 = 基于市场价格区间优化
```

### 影响因子

| 因子 | 范围 | 说明 |
|------|------|------|
| 季节性因子 | -0.05 ~ +0.10 | 旺季涨价，淡季降价 |
| 库存因子 | -0.03 ~ +0.05 | 库存低涨价，库存高降价 |
| 需求因子 | -0.02 ~ +0.08 | 需求旺盛涨价 |
| 竞品因子 | -0.10 ~ +0.15 | 基于竞品价格调整 |

## 🔧 配置说明

### application.yml

```yaml
# 服务端口
server:
  port: 8087

# 定价算法参数
algorithm:
  pricing:
    base-profit-margin: 20        # 基础利润率(%)
    max-price-increase: 15        # 最多涨价(%)
    max-price-decrease: 10        # 最多降价(%)

# 定时任务间隔
pricing:
  schedule:
    competitor-scrape-interval: 60   # 竞品抓取间隔(分钟)
    dynamic-pricing-interval: 30     # 动态定价间隔(分钟)
```

## 📊 性能指标

| 指标 | 目标值 |
|------|--------|
| API响应时间 | < 200ms |
| 批量计算（100个产品） | < 5s |
| 竞品抓取准确率 | > 95% |
| 价格预测准确率 | > 85% |

## 🚧 待开发功能

- [ ] 集成真实的平台API（Amazon Selling Partner API等）
- [ ] 机器学习模型训练和部署
- [ ] A/B测试框架
- [ ] 价格影响分析（销量、利润）
- [ ] 多货币定价
- [ ] 区域化定价
- [ ] AI定价建议生成

## 🔒 安全性

- 所有价格变更记录审计日志
- 手动调整需要权限验证
- 防止SQL注入和XSS攻击
- API限流（Sentinel）

## 📞 技术支持

**作者：** 火球鼠 🔥

**技术栈：**
- Spring Boot 3.3.5
- Java 21
- MyBatis Plus
- Redis
- Swagger

---

**版本：** v1.5.0
**更新时间：** 2026-03-17
**状态：** 🚀 开发中
