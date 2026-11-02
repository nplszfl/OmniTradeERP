import request, { PageResp, PageReq } from './index'

/**
 * 数据分析服务（erp-analytics-service）
 *
 * ⚠️ 路径说明：网关把 `/api/analytics/**` 重写成 `/api/v1/analytics/**`，
 *    这里 baseURL 已经是 `/api`，所以路径里只写 `/analytics/...`。
 */

/** GET /analytics/summary → AnalyticsSummaryDTO */
export interface AnalyticsSummary {
  totalOrders: number
  totalRevenue: number
  totalCustomers: number
  totalProducts: number
  revenueByPlatform: { platform: string; revenue: number; orders: number }[]
  topProducts: { sku: string; name: string; sold: number; revenue: number }[]
  customersByRegion: { region: string; customers: number; revenue: number }[]
  lastUpdated: string
}

/** 概览统计（DashboardDTO.OverviewStats） */
export interface DashboardOverview {
  /** 今日销售额 */
  todaySales: number
  /** 今日订单笔数（与 todaySales 是两回事：一个数笔数，一个数金额） */
  todayOrders: number
  yesterdaySales: number
  monthSales: number
  totalOrders: number
  pendingOrders: number
  shippedOrders: number
  completedOrders: number
  activeProducts: number
  /** 低库存记录数（商品 × 仓库 逐条判定） */
  lowStockProducts: number
}

/** 按天聚合（DashboardDTO.TimeSeriesData） */
export interface DashboardTimeSeries {
  date: string
  salesAmount: number
  orderCount: number
  avgOrderValue: number
}

/** 平台销售分布（DashboardDTO.PlatformSales） */
export interface DashboardPlatformSales {
  platform: string
  salesAmount: number
  orderCount: number
  /** 占全部平台营收的百分比 */
  percentage: number
}

/** 订单状态分布（DashboardDTO.OrderStatusCount） */
export interface DashboardOrderStatus {
  /** PENDING / CONFIRMED / PROCESSING / SHIPPED / DELIVERED / COMPLETED / CANCELLED / REFUNDED */
  status: string
  count: number
}

/** 最近订单（DashboardDTO.OrderSummary） */
export interface DashboardRecentOrder {
  orderId: string
  platform: string
  status: string
  amount: number
  orderTime: string
}

/** 库存预警（DashboardDTO.InventoryAlert） */
export interface DashboardInventoryAlert {
  productId: string
  productName: string
  currentStock: number
  safetyStock: number
  /** LOW / CRITICAL */
  alertLevel: string
}

/** GET /analytics/dashboard → DashboardDTO */
export interface DashboardData {
  overview: DashboardOverview
  salesTrend: DashboardTimeSeries[]
  platformDistribution: DashboardPlatformSales[]
  orderStatusDistribution: DashboardOrderStatus[]
  recentOrders: DashboardRecentOrder[]
  inventoryAlerts: DashboardInventoryAlert[]
  lastUpdated: string
}

/** GET /analytics/orders/trend → TrendPoint[] */
export interface TrendPoint {
  date: string
  count: number
  revenue: number
}

export const analyticsApi = {
  /** 分析汇总（数据析页用） */
  dashboard: () => request.get<any, AnalyticsSummary>('/analytics/summary'),

  /** 完整看板数据（首页 Dashboard.vue 用） */
  dashboardData: () => request.get<any, DashboardData>('/analytics/dashboard'),

  /** 订单趋势（近 N 天，支持 7/30/90 的周期切换） */
  ordersTrend: (days = 30) =>
    request.get<any, TrendPoint[]>('/analytics/orders/trend', { params: { days } }),

  /** 营收趋势 */
  revenueTrend: (days = 30) =>
    request.get<any, TrendPoint[]>('/analytics/revenue/trend', { params: { days } }),

  /** 热门商品 */
  topProducts: (limit = 10) =>
    request.get<any, { sku: string; name: string; sold: number; revenue: number }[]>(
      '/analytics/products/top',
      { params: { limit } }
    ),

  /** 客户分布 */
  customerDistribution: () =>
    request.get<any, { region: string; customers: number; revenue: number }[]>(
      '/analytics/customers/distribution'
    ),

  /** 订单状态分布 */
  orderStatusDistribution: () =>
    request.get<any, DashboardOrderStatus[]>('/analytics/dashboard/order-status-distribution'),

  /** 最近订单 */
  recentOrders: () =>
    request.get<any, DashboardRecentOrder[]>('/analytics/dashboard/recent-orders'),

  /** 库存预警 */
  inventoryAlerts: () =>
    request.get<any, DashboardInventoryAlert[]>('/analytics/dashboard/inventory-alerts'),
}
