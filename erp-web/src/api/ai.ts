import request from './index'

/** AI 智能中心：异常检测、选品推荐、智能定价、商品描述生成、库存预测 */
export interface AnomalyOrder {
  id: number
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
  list: (params: any) => request.get('/v1/anomaly/orders', { params }),
  detect: (orderId: number) => request.post(`/v1/anomaly/detect/${orderId}`),
  confirm: (id: number, status: string) => request.post(`/v1/anomaly/${id}/confirm`, { status }),
  stats: () => request.get('/v1/anomaly/stats'),
}

export const recommendApi = {
  list: (params: any) => request.get('/v1/recommend', { params }),
  detail: (sku: string) => request.get(`/v1/recommend/${sku}`),
  refresh: () => request.post('/v1/recommend/refresh'),
}

export const pricingApi = {
  suggest: (sku: string) => request.get<any, PriceSuggest>(`/pricing/suggest/${sku}`),
  history: (sku: string, days = 30) => request.get(`/pricing/history/${sku}`, { params: { days } }),
  apply: (sku: string, price: number) => request.post(`/pricing/apply/${sku}`, { price }),
  listTasks: (params: any) => request.get('/pricing/tasks', { params }),
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
}

export const descriptionApi = {
  generate: (data: DescriptionRequest) => request.post<any, DescriptionResult>('/description/generate', data),
  templates: () => request.get('/description/templates'),
  history: (params: any) => request.get('/description/history', { params }),
}

export const inventoryPredictApi = {
  predict: (sku: string, days = 30) => request.get(`/inventory-prediction/predict/${sku}`, { params: { days } }),
  listSuggestions: (params: any) => request.get('/inventory-prediction/suggestions', { params }),
  turnover: (params: any) => request.get('/inventory-prediction/turnover', { params }),
  tasks: (params: any) => request.get('/inventory-prediction/tasks', { params }),
}

export const aiAssistantApi = {
  chat: (data: { sessionId?: string; message: string }) => request.post<any, { reply: string; sessionId: string }>('/ai/assistant/chat', data),
  history: (sessionId: string) => request.get(`/ai/assistant/history/${sessionId}`),
  knowledge: {
    list:   (params: any) => request.get('/ai/assistant/knowledge', { params }),
    add:    (data: any)   => request.post('/ai/assistant/knowledge', data),
    remove: (id: number)  => request.delete(`/ai/assistant/knowledge/${id}`),
  },
}
