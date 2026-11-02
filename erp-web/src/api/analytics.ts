import request, { PageResp, PageReq } from './index'

/** 数据分析服务（erp-analytics-service） */
export interface AnalyticsDashboard {
  totalOrders: number
  totalRevenue: number
  totalCustomers: number
  totalProducts: number
  ordersByMonth: { month: string; count: number; revenue: number }[]
  revenueByPlatform: { platform: string; revenue: number }[]
  topProducts: { sku: string; name: string; sold: number; revenue: number }[]
}

export const analyticsApi = {
  dashboard: () => request.get<any, AnalyticsDashboard>('/analytics/summary'),
  ordersTrend: (days = 30) => request.get('/analytics/orders/trend', { params: { days } }),
  revenueTrend: (days = 30) => request.get('/analytics/revenue/trend', { params: { days } }),
  topProducts: (limit = 10) => request.get('/analytics/products/top', { params: { limit } }),
  customerDistribution: () => request.get('/analytics/customers/distribution'),
}
