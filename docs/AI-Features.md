# 🤖 AI 功能介绍

OmniTrade ERP 内置 **4大AI服务**，打造真正的智能ERP体验。

## 1. 🏷️ 智能定价服务 (`erp-pricing-service`)

**功能：**
- 实时监控竞品价格
- 基于成本/竞品/需求自动定价
- 利润优化算法
- 多平台价格同步

**技术实现：**
- Python + FastAPI 微服务
- 竞品数据抓取（Amazon/eBay/Shopee）
- 动态定价算法
- 实时价格更新

**API接口：**
```
GET  /api/v1/pricing/recommended/{productId}  - 获取推荐价格
POST /api/v1/pricing/competitor-track         - 提交竞品数据
GET  /api/v1/pricing/strategy/{productId}    - 获取定价策略
```

---

## 2. 📈 库存预测服务 (`erp-inventory-prediction-service`)

**功能：**
- 销售趋势预测（Prophet/ARIMA）
- 智能补货建议
- 安全库存计算
- 库存预警通知

**技术实现：**
- Python + Flask 微服务
- 时间序列预测模型
- 机器学习优化算法
- 预测准确性评估

**API接口：**
```
GET  /api/v1/inventory-prediction/forecast/{productId}    - 获取预测
GET  /api/v1/inventory-prediction/replenishment/{productId} - 获取补货建议
GET  /api/v1/inventory-prediction/accuracy/{productId}     - 预测准确性评估
POST /api/v1/inventory-prediction/batch-forecast           - 批量预测
```

---

## 3. 💬 AI客服助手 (`erp-ai-assistant-service`)

**功能：**
- RAG知识库问答
- 多语言支持（中/英/泰/越/马来）
- 订单状态查询
- 商品信息推荐
- 7×24小时自动回复

**技术实现：**
- Python + Gradio 微服务
- RAG（检索增强生成）架构
- DeepSeek LLM 集成
- 向量数据库（FAISS）
- 多语言翻译

**API接口：**
```
POST /api/v1/ai-assistant/chat              - 发送消息
GET  /api/v1/ai-assistant/conversation/{id} - 获取对话历史
GET  /api/v1/ai-assistant/languages        - 支持的语言列表
```

---

## 4. 📝 产品描述生成 (`erp-product-description-service`)

**功能：**
- 多平台模板适配
- SEO关键词优化
- AI自动生成描述
- 批量生成支持

**技术实现：**
- Python + FastAPI 微服务
- 多平台模板系统（Amazon/eBay/Shopee/Lazada/TikTok）
- SEO优化算法
- 批量生成队列

**API接口：**
```
POST /api/v1/product-description/generate   - 生成描述
GET  /api/v1/product-description/templates - 获取模板列表
POST /api/v1/product-description/batch     - 批量生成
```

---

## 🚀 快速体验

Docker 一键启动 AI 服务：
```bash
# 启动全部 AI 服务
docker-compose -f docker-compose.yml up -d

# 或仅启动 AI 服务（按服务名筛选，无需额外的 compose 文件）
docker-compose up -d ai-assistant-service product-description-service \
  product-recommendation-service inventory-prediction-service
```

## 相关文档

- [系统架构](Architecture)
- [部署文档](Deployment)
- [API文档](API-Documentation)