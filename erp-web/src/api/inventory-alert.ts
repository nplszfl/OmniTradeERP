import { ResultShape } from '@/utils/response'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.response.use(
  response => response.data,
  error => {
    ElMessage.error(error.message || '请求失败')
    return Promise.reject(error)
  }
)

// ========== 库存预警类型 ==========

export interface InventoryAlertRule {
  id: string
  tenantId: string
  productId: string
  sku: string
  productName: string
  warehouseId: string
  warehouseName: string
  safeStock: number
  alertStock: number
  minStock: number
  alertType: string
  advanceDays: number
  notifyType: string
  notifyTo: string
  emailList: string
  phoneList: string
  enabled: boolean
  remark: string
  createTime: string
  updateTime: string
}

export interface InventoryAlert {
  id: string
  productId: string
  sku: string
  productName: string
  warehouseId: string
  warehouseName: string
  currentStock: number
  alertStock: number
  alertType: string
  status: string
  handlerId: string
  handlerName: string
  handleTime: string
  handleRemark: string
  createTime: string
}

export interface ReplenishmentSuggestion {
  id: string
  productId: string
  sku: string
  productName: string
  warehouseId: string
  warehouseName: string
  currentStock: number
  suggestedQuantity: number
  estimatedPrice: number
  status: string
  confirmUserId: string
  confirmUserName: string
  confirmTime: string
  remark: string
  createTime: string
}

export interface AlertStatistics {
  totalAlerts: number
  pendingAlerts: number
  handledAlerts: number
  ignoredAlerts: number
  todayAlerts: number
  lowStockCount: number
  outOfStockCount: number
}

// ========== 物流类型 ==========

export interface LogisticsRate {
  id: string
  channelCode: string
  channelName: string
  carrier: string
  shippingMethod: string
  fromCountry: string
  toCountry: string
  weightRangeMin: number
  weightRangeMax: number
  basePrice: number
  pricePerKg: number
  estimatedDays: number
  currency: string
  isActive: boolean
  createTime: string
}

export interface LogisticsChannel {
  code: string
  name: string
  carrier: string
  type: string
  coverage: string[]
  features: string[]
  avgDeliveryDays: number
  trackingSupport: boolean
}

export interface ShippingRateQuery {
  fromCountry: string
  fromPostalCode?: string
  toCountry: string
  toPostalCode: string
  weight: number
  length?: number
  width?: number
  height?: number
}

export interface ShippingRateResult {
  channelCode: string
  channelName: string
  carrier: string
  totalPrice: number
  currency: string
  estimatedDays: number
  transitTime: string
  isRecommended: boolean
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

// ========== 库存预警API ==========

export const inventoryAlertApi = {
  // 预警规则
  createRule: (data: InventoryAlertRule) =>
    request.post<any, ResultShape<string>>('/inventory/alert/rule', data),

  getRules: (params: {
    page: number
    size: number
    warehouseId?: string
    enabled?: boolean
  }) => request.get<any, PageResult<InventoryAlertRule>>('/inventory/alert/rules', { params }),

  getEnabledRules: () =>
    request.get<any, InventoryAlertRule[]>('/inventory/alert/rules/enabled'),

  updateRule: (id: string, data: InventoryAlertRule) =>
    request.put<any, void>(`/inventory/alert/rule/${id}`, data),

  deleteRule: (id: string) =>
    request.delete<any, void>(`/inventory/alert/rule/${id}`),

  // 预警记录
  getAlerts: (params: {
    page: number
    size: number
    warehouseId?: string
    alertType?: string
    status?: string
  }) => request.get<any, PageResult<InventoryAlert>>('/inventory/alert/records', { params }),

  getPendingAlerts: () =>
    request.get<any, InventoryAlert[]>('/inventory/alert/records/pending'),

  handleAlert: (id: string, handlerId: string, handlerName: string, remark?: string) =>
    request.post<any, void>(`/inventory/alert/record/${id}/handle`, null, { params: { handlerId, handlerName, remark } }),

  ignoreAlert: (id: string, handlerId: string, handlerName: string, remark?: string) =>
    request.post<any, void>(`/inventory/alert/record/${id}/ignore`, null, { params: { handlerId, handlerName, remark } }),

  // 预警统计
  getRealTimeStatistics: (warehouseId?: string) =>
    request.get<any, AlertStatistics>('/inventory/alert/statistics/realtime', { params: { warehouseId } }),

  getTrendData: (days: number, warehouseId?: string) =>
    request.get<any, any[]>('/inventory/alert/statistics/trend', { params: { days, warehouseId } }),

  getAlertDistribution: (warehouseId?: string, startTime?: string, endTime?: string) =>
    request.get<any, Record<string, number>>('/inventory/alert/statistics/distribution', { params: { warehouseId, startTime, endTime } }),

  // 补货建议
  getReplenishments: (params: {
    page: number
    size: number
    warehouseId?: string
    status?: string
  }) => request.get<any, PageResult<ReplenishmentSuggestion>>('/inventory/alert/replenishments', { params }),

  getPendingReplenishments: () =>
    request.get<any, ReplenishmentSuggestion[]>('/inventory/alert/replenishments/pending'),

  confirmReplenishment: (id: string, userId: string, userName: string, remark?: string) =>
    request.post<any, void>(`/inventory/alert/replenishment/${id}/confirm`, null, { params: { userId, userName, remark } }),

  cancelReplenishment: (id: string, remark?: string) =>
    request.post<any, void>(`/inventory/alert/replenishment/${id}/cancel`, null, { params: { remark } }),

  // 库存检查
  // ⚠️ 旧的 checkInventory(productId, sku, ..., currentStock) 要求前端自己报库存数，
  //    实际没人调用，而且这套预警引擎长期靠它触发 → 预警记录永远是 0 条。
  //    现在改成 POST /inventory/alert/check/scan：由后端直接扫活表
  //    erp_inventory.erp_inventory 判定，前端只负责点一下。
  scanInventory: () => request.post<any, number>('/inventory/alert/check/scan')
}

// ========== 物流API ==========

export const logisticsApi = {
  // 物流渠道
  getChannels: () =>
    request.get<any, LogisticsChannel[]>('/logistics/channels'),

  // 📌 新增渠道：**后端真的存在这个端点**（POST /logistics/channel）。
  //    之前前端只弹「渠道添加成功」，后端根本没有对应接口 → 假成功（规则 80 / 82）。
  createChannel: (data: Partial<LogisticsChannel>) =>
    request.post<any, ResultShape<string>>('/logistics/channel', data),

  // 运费计算
  calculateRate: (data: ShippingRateQuery) =>
    request.post<any, ShippingRateResult[]>('/logistics/calculate', data),

  // 物流渠道推荐
  recommendChannel: (data: ShippingRateQuery) =>
    request.post<any, ShippingRateResult[]>('/logistics/recommend', data),

  // 物流费率管理
  getRates: (params: {
    page: number
    size: number
    channelCode?: string
    fromCountry?: string
    toCountry?: string
  }) => request.get<any, PageResult<LogisticsRate>>('/logistics/rates', { params }),

  createRate: (data: LogisticsRate) =>
    request.post<any, ResultShape<string>>('/logistics/rate', data),

  updateRate: (id: string, data: LogisticsRate) =>
    request.put<any, void>(`/logistics/rate/${id}`, data),

  deleteRate: (id: string) =>
    request.delete<any, void>(`/logistics/rate/${id}`)
}