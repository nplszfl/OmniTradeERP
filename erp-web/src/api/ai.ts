import request from './index'

/** AI 智能中心：异常检测、选品推荐、智能定价、商品描述生成、库存预测 */
export interface AnomalyOrder {
  id: string
  orderNo: string
  platform: string
  amount: number
  anomalyScore: number
  anomalyType: string
  detectedAt: string
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED'
}

export interface ProductRecommend {
  sku: string
  productName: string
  score: number
  reason: string
  expectedMargin: number
  category: string
}

export interface PriceSuggest {
  sku: string
  productName: string
  currentPrice: number
  suggestedPrice: number
  competitorAvg: number
  expectedProfit: number
  strategy: string
}

export interface AnomalyStats {
  total: number
  normal: number
  anomaly: number
  rate: number
}

/** 库存预测明细（后端 PredictionResponse） */
export interface PredictionDetail {
  sku?: string
  productName?: string
  dailyPredictions?: { date?: string; predictedSales?: number }[]
  [key: string]: any
}

export interface DescriptionRequest {
  sku: string
  language: 'zh' | 'en' | 'ja' | 'es'
  tone?: string
  keywords?: string[]
}

export interface InventoryPrediction {
  sku: string
  productName: string
  currentStock: number
  predicted30DaySales: number
  recommendedReplenishment: number
  safetyStock: number
  urgency: 'HIGH' | 'MEDIUM' | 'LOW'
}

export const anomalyApi = {
  list: (params: any) => request.get<any, AnomalyOrder[]>('/v1/anomaly/orders', { params }),
  detect: (orderId: string) => request.post<any, any>(`/v1/anomaly/detect/${orderId}`),
  confirm: (id: string, status: string) => request.post<any, any>(`/v1/anomaly/${id}/confirm`, { status }),
  stats: () => request.get<any, AnomalyStats>('/v1/anomaly/stats'),
}

export const recommendApi = {
  list: (params: any) => request.get<any, ProductRecommend[]>('/v1/recommend', { params }),
  detail: (sku: string) => request.get<any, ProductRecommend>(`/v1/recommend/${sku}`),
  refresh: () => request.post<any, any>('/v1/recommend/refresh'),
}

export const pricingApi = {
  suggest: (sku: string) => request.get<any, PriceSuggest>(`/pricing/suggest/${sku}`),
  history: (sku: string, days = 30) => request.get<any, any[]>(`/pricing/history/${sku}`, { params: { days } }),
  apply: (sku: string, price: number) => request.post(`/pricing/apply/${sku}`, { price }),
  listTasks: (params: any) => request.get<any, any[]>('/pricing/tasks', { params }),
}

export interface DescriptionResult {
  title: string
  subtitle: string
  body: string
  bulletPoints: string[]
  seoKeywords: string[]
  templateId?: string
  generationTime?: number
  error?: string
  /**
   * 是否是**模拟生成**（后端没配 LLM，或真实调用失败降级）
   * 📌 true = 这段文案是本地模板拼的，不是真 AI 生成的
   */
  mock?: boolean
  /** 降级原因（mock=true 时有值） */
  mockReason?: string
}

export const descriptionApi = {
  generate: (data: DescriptionRequest) => request.post<any, DescriptionResult>('/description/generate', data),
  templates: () => request.get<any, any[]>('/description/templates'),
  history: (params: any) => request.get<any, any[]>('/description/history', { params }),
}

export const inventoryPredictApi = {
  predict: (sku: string, days = 30) => request.get<any, PredictionDetail>(`/inventory-prediction/predict/${sku}`, { params: { days } }),
  listSuggestions: (params: any) => request.get<any, any[]>('/inventory-prediction/suggestions', { params }),
  turnover: (params: any) => request.get<any, any>('/inventory-prediction/turnover', { params }),
  tasks: (params: any) => request.get<any, any[]>('/inventory-prediction/tasks', { params }),
}

/** 知识库条目（问答对，对应后端表 knowledge_base） */
export interface KnowledgeBaseItem {
  id: string
  category: string
  question: string
  answer: string
  language?: string
  tags?: string[]
  priority?: number
  active?: boolean
  createTime?: string
  updateTime?: string
}

/**
 * AI 对话响应（后端 erp-ai-assistant-service 的 `ChatResponse`）
 *
 * 📌 **字段名必须和后端 DTO 对得上** ——
 * 后端返回的是 `content`，**不是** `reply`。
 * 📌 之前这里写的是 `{ reply: string; sessionId: string }`，
 * 而后端 `ChatResponse` 里**根本没有 `reply` 字段**（只有 `content`）
 * → `r.reply` 恒为 `undefined` → **每次聊天气泡都是空的**。
 * 📌 读到的字段后端必须真的有 —— 这是「前端字段 ≠ 实体字段」那类缺陷的变种。
 */
export interface AiChatResponse {
  /** 回复正文（后端 ChatResponse.content） */
  content: string
  /** 会话 ID */
  sessionId: string
  /** 响应 ID */
  responseId?: string
  /** 模型名；模拟回复时后端会写成 'mock' */
  model?: string
  /**
   * 是否是**模拟回复**（后端没配 LLM，或真实调用失败降级）
   * 📌 true = 这段话是模板拼的，不是真 AI 生成的
   */
  mock?: boolean
  /** 降级原因（mock=true 时有值） */
  mockReason?: string
}

export const aiAssistantApi = {
  chat: (data: { sessionId?: string; message: string }) => request.post<any, AiChatResponse>('/ai/assistant/chat', data),
  history: (sessionId: string) => request.get<any, any[]>(`/ai/assistant/history/${sessionId}`),
  knowledge: {
    // 语义检索（RAG 文档侧 knowledge_document）
    // ⚠️ 后端 /api/ai/knowledge/search 的 q 是**必填**参数，不传会 400
    list:   (params: { q: string; topK?: number }) => request.get<any, any[]>('/ai/assistant/knowledge', { params }),
    add:    (data: any)   => request.post<any, any>('/ai/assistant/knowledge', data),
    remove: (id: string)  => request.delete<any, any>(`/ai/assistant/knowledge/${id}`),
  },
}

/**
 * 知识库管理（问答对，knowledge_base 表）
 * ⚠️ 与上面的 knowledge（RAG 文档）是**两张不同的表**，不要混用：
 *    knowledge        → knowledge_document（向量检索用）
 *    knowledgeBase    → knowledge_base（问答对，聊天检索用的就是这张）
 * 📌 之前这套管理接口前端压根没有页面 → 知识库永远是空的。
 */
export const knowledgeBaseApi = {
  list: (params: { page?: number; size?: number; category?: string }) =>
    request.get<any, any>('/ai/knowledge/base/list', { params }),
  categories: () => request.get<any, string[]>('/ai/knowledge/base/categories'),
  // 新增走 /ai/assistant/knowledge（网关会改写到 /api/v1/ai-assistant/knowledge）
  add: (data: { category: string; question: string; answer: string; tags?: string[]; priority?: number }) =>
    request.post<any, any>('/ai/assistant/knowledge', data),
  remove: (id: string) => request.delete<any, boolean>(`/ai/knowledge/base/${id}`),
}
