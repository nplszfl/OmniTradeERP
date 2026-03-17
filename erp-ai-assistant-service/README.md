# ERP AI Assistant Service AI智能客服系统

## 🤖 简介

**v1.5.0** - 基于LLM的智能问答助手

这是OmniTrade ERP系统的核心AI模块，通过大语言模型（LLM）和检索知识库（RAG），提供24/7智能客服能力。

## 🔥 核心功能

### 1. 智能对话
- ✅ 基于LLM的智能问答
- ✅ 多语言支持（中英文自动切换）
- ✅ 对话上下文理解
- ✅ 流式响应（SSE）
- ✅ 对话历史追踪

### 2. RAG检索增强
- ✅ 知识库检索
- ✅ 向量相似度搜索
- ✅ 相关知识注入
- ✅ 检索结果可视化

### 3. 智能能力
- ✅ 情感分析（识别客户情绪）
- ✅ 意图识别（自动理解用户意图）
- ✅ 实体提取（订单号、产品ID等）
- ✅ 智能路由（自动分配到合适的服务）

### 4. 知识库管理
- ✅ 知识库CRUD
- ✅ 批量导入
- ✅ 知识分类
- ✅ 优先级设置
- ✅ 知识激活/禁用

## 🏗️ 架构设计

```
erp-ai-assistant-service/
├── controller/
│   └── AIAssistantController.java
├── service/
│   ├── AIAssistantService.java
│   └── impl/
│       └── AIAssistantServiceImpl.java
├── dto/
│   ├── ChatRequest.java
│   └── ChatResponse.java
├── config/
│   └── (LLM配置、RAG配置）
└── resources/
    └── application.yml
```

## 🚀 快速启动

### 前置要求
- JDK 21+
- Maven 3.9+
- MySQL 8.0+
- Redis
- LLM API密钥（OpenAI或DeepSeek）

### 启动步骤

1. **初始化数据库**
```bash
mysql -u root -p < database/ai-assistant-service.sql
```

2. **配置API密钥**
编辑 `src/main/resources/application.yml`，配置LLM API密钥：
```yaml
llm:
  deepseek:
    enabled: true
    api-key: YOUR_DEEPSEEK_API_KEY
```

3. **启动服务**
```bash
cd erp-ai-assistant-service
mvn clean package -DskipTests
java -jar target/erp-ai-assistant-service-1.5.0.jar
```

## 🌐 API文档

```
http://localhost:8089/swagger-ui.html
```

## 📖 核心API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/ai-assistant/health` | GET | 健康检查 |
| `/api/v1/ai-assistant/chat` | POST | AI对话（单次） |
| `/api/v1/ai-assistant/chat/stream` | POST | AI对话（流式） |
| `/api/v1/ai-assistant/history/{userId}` | GET | 获取对话历史 |
| `/api/v1/ai-assistant/history/{userId}` | DELETE | 清空对话历史 |
| `/api/v1/ai-assistant/sentiment` | POST | 分析情感 |
| `/api/v1/ai-assistant/intent` | POST | 识别意图 |
| `/api/v1/ai-assistant/knowledge` | POST | 添加知识 |
| `/api/v1/ai-assistant/knowledge/batch` | POST | 批量添加知识 |
| `/api/v1/ai-assistant/knowledge/search` | GET | 检索知识库 |

## 💡 使用示例

### AI对话

```bash
curl -X POST http://localhost:8089/api/v1/ai-assistant/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "sessionId": "session-001",
    "message": "如何查询我的订单？",
    "language": "zh",
    "useRAG": true,
    "model": "deepseek-chat",
    "temperature": 0.7
  }'
```

### 响应示例

```json
{
  "responseIdudi": "550e8400-e29b-41d4-a716-4466554402",
  "sessionId": "session-001",
  "content": "您可以在「我的订单」页面查看所有订单信息，包括待付款、待发货、已发货等状态。如果需要帮助，请提供订单号，我会帮您查询详细状态。",
  "retrievedChunks": [
    {
      "chunkId": "1",
      "content": "您可以在「我的订单」页面查看所有订单信息...",
      "similarity": 0.85,
      "source": "订单",
      "category": "订单"
    }
  ],
  "language": "zh",
  "model": "deepseek-chat",
  "responseTime": 1250,
  "createTime": "2026-03-17T21:30:00"
}
```

### 情感分析

```bash
curl -X POST http://localhost:8089/api/v1/ai-assistant/sentiment \
  -H "Content-Type: application/json" \
  -d '我对这个服务非常满意！'
```

### 响应示例

```json
{
  "sentiment": "positive",
  "confidence": 0.95,
  "score": 0.8
}
```

## 🧪 技术实现

### RAG（检索增强生成）
1. 用户提问
2. 向量化查询文本
3. 检索知识库（向量相似度搜索）
4. 构建提示词（知识+问题）
5. 调用LLM生成回答
6. 返回结果+检索来源

### LLM集成
- **OpenAI GPT-4**：高质量问答
- **DeepSeek Chat**：性价比高
- 支持自定义模型
- 支持温度参数
- 支持最大Token限制

### 知识库
- 支持多种存储方式（Redis、Milvus、Pinecone）
- 向量维度：1536（OpenAI）
- 相似度计算：余弦相似度
- 支持知识分类和优先级

## 📊 性能指标

| 指标 | 目标值 |
|------|--------|
| 对话响应时间 | < 2s |
| 流式首字延迟 | < 500ms |
| 知识库检索 | < 200ms |
| 情感分析 | < 100ms |
| 意图识别 | < 150ms |

## 🔒 安全性

- API密钥安全存储
- 对话历史加密
- 输入验证和清理
- 防止提示词注入
- 访问频率限制

---

**版本：** v1.5.0
**作者：** 火球鼠 🔥
**状态：** 🚀 85%完成
