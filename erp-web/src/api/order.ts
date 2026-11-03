import axios from 'axios'
import { ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'

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

export interface Order {
  id: number
  platform: string
  platformOrderNo: string
  internalOrderNo: string
  buyerName: string
  buyerEmail: string
  orderAmount: number
  currencyCode: string
  status: string
  paymentStatus: string
  trackingNumber: string
  recipientName: string
  recipientCountry: string
  createTime: string
  /**
   * ⚠️ 下面这些字段后端实体（erp-order-service 的 Order）里**本来就有**，
   *    但前端接口一直漏声明 → 视图里访问 `order.logisticsCompany` 这类字段时
   *    vue-tsc 报 TS2339「Property does not exist」。
   *    这里按后端实体（entity/Order.java）补齐，缺失时统一用 undefined。
   */
  buyerCountry?: string
  buyerPhone?: string
  paymentMethod?: string
  paymentTime?: string
  shippingMethod?: string
  logisticsCompany?: string
  shippingTime?: string
  recipientState?: string
  recipientCity?: string
  recipientAddress?: string
  recipientPostalCode?: string
  recipientPhone?: string
  remark?: string
}

export interface OrderItem {
  id: number
  orderId: number
  platformProductId: string
  internalProductId: string
  platformSku: string
  internalSku: string
  productSku: string
  productName: string
  productImage: string
  unitPrice: number
  quantity: number
  totalAmount: number
  currencyCode: string
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

// 高级查询请求
export interface OrderQueryRequest {
  current?: number
  size?: number
  platform?: string
  status?: string
  paymentStatus?: string
  startTime?: string
  endTime?: string
  minAmount?: number
  maxAmount?: number
  buyerCountry?: string
  productSku?: string
  buyerName?: string
  buyerEmail?: string
  trackingNumber?: string
  internalOrderNo?: string
  platformOrderNo?: string
}

// 批量操作请求
export interface OrderBatchRequest {
  orderIds: number[]
  operation?: string
  targetStatus?: string
  trackingNumber?: string
  logisticsCompany?: string
}

// 导出响应
export interface OrderExportResponse {
  fileName: string
  data: any[]
  total: number
}

// 订单统计
export interface OrderStatistics {
  totalOrders: number
  totalAmount: number
  pendingPayment: number
  pendingShipment: number
  shipped: number
}

/** 今日订单统计（GET /order/statistics/today） */
export interface OrderTodayStats {
  /** 今日订单数 */
  orderCount: number
  /** 今日销售额（已排除 CANCELLED） */
  totalAmount: number
  /** 各平台订单数 */
  platformDistribution: Record<string, number>
  /** 各状态订单数 */
  statusDistribution: Record<string, number>
}

/** 销售趋势某一天（GET /order/statistics/trend） */
export interface OrderTrendPoint {
  /** yyyy-MM-dd */
  date: string
  /** 当天订单数 */
  count: number
  /** 当天销售额 */
  amount: number
}

export const orderApi = {
  // 分页查询订单
  getOrders: (params: {
    current: number
    size: number
    platform?: string
    status?: string
  }) => request.get<any, PageResult<Order>>('/order/list', { params }),

  // 今日订单统计（真实聚合：金额/平台分布/状态分布）
  getTodayStats: () => request.get<any, OrderTodayStats>('/order/statistics/today'),

  // 销售趋势（最近 N 天，缺失的日期会补 0）
  getTrend: (days = 7) =>
    request.get<any, OrderTrendPoint[]>('/order/statistics/trend', { params: { days } }),

  // 高级分页查询订单
  advancedQuery: (data: OrderQueryRequest) => 
    request.post<any, PageResult<Order>>('/order/advanced/list', data),

  // 获取订单详情
  getOrder: (orderId: number) => request.get<any, Order>(`/order/${orderId}`),

  // 更新订单状态
  updateStatus: (orderId: number, status: string) =>
    request.put(`/order/${orderId}/status`, null, { params: { status } }),

  // 更新物流信息
  updateShipping: (orderId: number, trackingNumber: string, logisticsCompany: string) =>
    request.put(`/order/${orderId}/shipping`, null, {
      params: { trackingNumber, logisticsCompany }
    }),

  // 批量更新订单状态
  batchUpdateStatus: (data: OrderBatchRequest) =>
    request.post<any, number>('/order/batch/update-status', data),

  // 批量标记发货
  batchMarkShipped: (data: OrderBatchRequest) =>
    request.post<any, number>('/order/batch/mark-shipped', data),

  // 批量导出订单
  batchExport: async (data: OrderBatchRequest) => {
    const response = await request.post<any, OrderExportResponse>('/order/batch/export', data)
    return response
  },

  // 导出CSV
  exportToCsv: (response: OrderExportResponse) => {
    if (!response.data || response.data.length === 0) {
      ElMessage.warning('没有可导出的数据')
      return
    }
    
    const headers = [
      '平台', '平台订单号', '内部订单号', '买家姓名', '买家邮箱', 
      '买家国家', '订单金额', '货币', '订单状态', '支付状态',
      '物流单号', '物流公司', '收货国家', '省/州', '城市', 
      '详细地址', '创建时间', '支付时间', '发货时间'
    ]
    
    const rows = response.data.map((item: any) => [
      item.platform,
      item.platformOrderNo,
      item.internalOrderNo,
      item.buyerName,
      item.buyerEmail,
      item.buyerCountry,
      item.orderAmount,
      item.currencyCode,
      item.status,
      item.paymentStatus,
      item.trackingNumber,
      item.logisticsCompany,
      item.recipientCountry,
      item.recipientState,
      item.recipientCity,
      item.recipientAddress,
      item.createTime,
      item.paymentTime,
      item.shippingTime
    ])
    
    const csvContent = [headers, ...rows]
      .map(row => row.map(cell => `"${cell || ''}"`).join(','))
      .join('\n')
    
    const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8' })
    saveAs(blob, response.fileName)
    ElMessage.success(`导出成功，共 ${response.total} 条记录`)
  },

  // 按国家统计订单
  countByCountry: (data: OrderQueryRequest) =>
    request.post<any, Record<string, number>>('/order/statistics/by-country', data),

  // 按平台统计订单金额
  sumAmountByPlatform: (data: OrderQueryRequest) =>
    request.post<any, Record<string, number>>('/order/statistics/by-platform', data),

  // 获取订单商品明细
  getOrderItems: (orderId: number) => 
    request.get<any, OrderItem[]>(`/order/${orderId}/items`)
}
