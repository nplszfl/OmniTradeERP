# ERP Inventory Prediction Service 库存预测服务

## 🤖 简介

**v1.5.0** - AI驱动的库存预测和智能补货系统

这是OmniTrade ERP系统的核心AI模块，通过时间序列分析和机器学习，实现精准的销售预测和智能库存管理。

## 🔥 核心功能

### 1. 销售预测
- ✅ 基于历史数据的时间序列预测
- ✅ Prophet模型（Facebook开源）
- ✅ ARIMA模型
- ✅ 季节性因子分析
- ✅ 趋势分解
- ✅ 置信区间计算
- ✅ 峰值和谷值分析

### 2. 智能补货
- ✅ 自动补货建议
- ✅ 安全库存计算
- ✅ 紧急程度评估（HIGH/MEDIUM/LOW）
- ✅ 补货成本估算
- ✅ 到货时间预测

### 3. 库存预警
- ✅ 低库存预警
- ✅ 缺货风险计算
- ✅ 积压风险评估
- ✅ 风险等级划分

### 4. 库存优化
- ✅ 库存周转率计算
- ✅ 库存结构优化
- ✅ 低周转率产品识别
- ✅ 高周转率产品识别

## 🏗️ 架构设计

```
erp-inventory-prediction-service/
├── controller/
│   └── InventoryPredictionController.java
├── service/
│   ├── InventoryPredictionService.java
│   └── impl/
│       └── InventoryPredictionServiceImpl.java
├── dto/
│   ├── PredictionRequest.java
│   ├── PredictionResponse.java
│   └── ReplenishmentSuggestion.java
├── config/
│   └── (配置类）
└── resources/
    └── application.yml
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
mysql -u root -p < database/inventory-prediction-service.sql
```

2. **启动服务**
```bash
cd erp-inventory-prediction-service
mvn clean package -DskipTests
java -jar target/erp-inventory-prediction-service-1.5.0.jar
```

## 🌐 API文档

```
http://localhost:8088/swagger-ui.html
```

## 📖 核心API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/inventory-prediction/health` | GET | 健康检查 |
| `/api/v1/inventory-prediction/predict` | POST | 预测销量 |
| `/api/v1/inventory-prediction/batch-predict` | POST | 批量预测 |
| `/api/v1/inventory-prediction/replenishment/{id}` | GET | 获取补货建议 |
| `/api/v1/inventory-prediction/safety-stock/{id}` | GET | 计算安全库存 |
| `/api/v1/inventory-prediction/warn-low-stock` | GET | 预警低库存 |
| `/api/v1/inventory-prediction/turnover/{id}` | GET | 计算周转率 |
| `/api/v1/inventory-prediction/optimize` | POST | 优化库存结构 |

## 💡 使用示例

### 预测销量

```bash
curl -X POST http://localhost:8088/api/v1/inventory-prediction/predict \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "productCode": "PROD-001",
    "predictionDays": 30,
    "useSeasonalModel": true,
    "useTrendModel": true,
    "confidenceLevel": 0.9
  }'
```

### 响应示例

```json
{
  "productId": 1,
  "productCode": "PROD-001",
  "startDate": "2026-03-17",
  "endDate": "2026-04-16",
  "predictionDays": 30,
  "totalPredictedSales": 450,
  "avgDailySales": 15.0,
  "peakDailySales": 25,
  "peakDate": "2026-03-20",
  "troughDailySales": 8,
  "troughDate": "2026-03-25",
  "modelType": "Prophet + ARIMA",
  "accuracy": 0.92,
  "lowerBound": 382,
  "upperBound": 517
}
```

## 🧪 预测算法

### Prophet模型

Facebook开源的预测算法，适用于：
- ✅ 季节性数据
- ✅ 趋势变化
- ✅ 节假日效应

### ARIMA模型

经典时间序列模型，适用于：
- ✅ 短期预测
- ✅ 平稳序列
- ✅ 自相关序列

## 📊 性能指标

| 指标 | 目标值 |
|------|--------|
| 预测准确率 | > 85% |
| 预测响应时间 | < 2s |
| 批量预测（100个） | < 10s |
| 补货建议准确率 | > 80% |

---

**版本：** v1.5.0
**作者：** 火球鼠 🔥
**状态：** 🚀 90%完成
