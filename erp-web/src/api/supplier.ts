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

export interface Supplier {
  id: string
  name: string
  contactPerson: string
  phone: string
  email: string
  type: string
  country: string
  city: string
  address: string
  category: string
  cooperationStartAt: string
  totalPurchaseAmount: number
  totalOrders: number
  rating: number
  level: string
  remark: string
  status: string
  createTime: string
  updateTime: string
}

export interface PurchaseOrder {
  id: string
  orderNo: string
  supplierId: string
  supplierName: string
  status: string
  totalAmount: number
  paidAmount: number
  currency: string
  expectedArrivalAt: string
  actualArrivalAt: string
  paymentMethod: string
  paymentStatus: string
  purchaserId: string
  purchaserName: string
  remark: string
  createTime: string
  items?: PurchaseOrderItem[]
}

export interface PurchaseOrderItem {
  id: string
  orderId: string
  productName: string
  sku: string
  category?: string
  quantity: number
  unit?: string
  unitPrice: number
  /** 小计（后端字段是 subtotal，不是 totalAmount） */
  subtotal: number
  receivedQuantity?: number
  remark?: string
}

/**
 * 供应商绩效评估（对应后端实体 SupplierEvaluation / 表 t_supplier_evaluation）
 *
 * ⚠️ 字段必须逐字对齐后端实体：
 *    后端是 `totalScore` / `conclusion` / `advantages` / `improvements` /
 *    `evaluatorName` / `techScore`，
 *    **没有** `overallScore` / `comment` / `evaluateBy` / `orderId`。
 *    之前前端类型写错了 → 绩效评分卡永远显示 0.0。
 */
export interface SupplierEvaluation {
  id: string
  supplierId: string
  qualityScore: number
  deliveryScore: number
  priceScore: number
  serviceScore: number
  techScore?: number
  /** 综合得分（后端字段是 totalScore，不是 overallScore） */
  totalScore: number
  /** 评估结论 */
  conclusion?: string
  /** 优势 */
  advantages?: string
  /** 改进建议 */
  improvements?: string
  remark?: string
  evaluatorId?: string
  /** 评估人（后端字段是 evaluatorName，不是 evaluateBy） */
  evaluatorName?: string
  evaluateAt?: string
  createTime?: string
  updateTime?: string
}

export interface SupplierStats {
  totalSuppliers: number
  activeSuppliers: number
  avgRating: number
  totalPurchaseAmount: number
}

/** 采购订单统计（后端 record OrderStats，字段名全小写开头） */
export interface OrderStats {
  total: number
  pending: number
  inTransit: number
  received: number
  totalAmount: number
  totalPaid: number
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

export const supplierApi = {
  // ========== 供应商管理 ==========
  
  // 创建供应商
  createSupplier: (data: Supplier) => 
    request.post<any, number>('/supplier/create', data),

  // 获取供应商详情
  getSupplierById: (id: string) => 
    request.get<any, Supplier>(`/supplier/${id}`),

  // 分页查询供应商
  getSupplierList: (params: {
    current: number
    size: number
    keyword?: string
    type?: string
    level?: string
    country?: string
    status?: string
  }) => request.get<any, PageResult<Supplier>>('/supplier/list', { params }),

  // 获取供应商统计
  getSupplierStats: () => 
    request.get<any, SupplierStats>('/supplier/stats'),

  // 更新供应商
  updateSupplier: (id: string, data: Supplier) => 
    request.put<any, void>(`/supplier/${id}`, data),

  // 删除供应商
  deleteSupplier: (id: string) => 
    request.delete<any, void>(`/supplier/${id}`),

  // ========== 采购订单管理 ==========

  // 创建采购订单
  createPurchaseOrder: (data: PurchaseOrder) => 
    request.post<any, number>('/supplier/purchase/create', data),

  // 获取采购订单详情
  getPurchaseOrderById: (id: string) => 
    request.get<any, PurchaseOrder>(`/supplier/purchase/${id}`),

  // 分页查询采购订单
  getPurchaseOrderList: (params: {
    current: number
    size: number
    supplierId?: string
    status?: string
    paymentStatus?: string
  }) => request.get<any, PageResult<PurchaseOrder>>('/supplier/purchase/list', { params }),

  // 更新采购订单状态
  updatePurchaseOrderStatus: (id: string, status: string) => 
    request.put<any, void>(`/supplier/purchase/${id}/status`, null, { params: { status } }),

  // 删除采购订单
  deletePurchaseOrder: (id: string) => 
    request.delete<any, void>(`/supplier/purchase/${id}`),

  // 采购单明细
  getPurchaseOrderItems: (id: string) =>
    request.get<any, PurchaseOrderItem[]>(`/supplier/purchase/${id}/items`),

  // ========== 采购单状态机 ==========
  // PENDING → APPROVED → PURCHASING → RECEIVED，任意未终态可 CANCELLED
  approvePurchaseOrder: (id: string) =>
    request.post<any, void>(`/supplier/purchase/${id}/approve`),
  markPurchaseOrderPurchasing: (id: string) =>
    request.post<any, void>(`/supplier/purchase/${id}/purchasing`),
  markPurchaseOrderReceived: (id: string) =>
    request.post<any, void>(`/supplier/purchase/${id}/received`),
  cancelPurchaseOrder: (id: string, reason?: string) =>
    request.post<any, void>(`/supplier/purchase/${id}/cancel`, null, { params: { reason } }),
  payPurchaseOrder: (id: string, amount: number) =>
    request.post<any, void>(`/supplier/purchase/${id}/payment`, null, { params: { amount } }),

  // ========== 供应商绩效评估 ==========

  /**
   * 获取供应商的评估记录列表
   *
   * 📌 后端 `/supplier/evaluation/list` 支持 supplierId / conclusion 两个可选过滤，
   *    返回**裸数组**（不是分页对象）。
   */
  getEvaluationList: (params?: { supplierId?: string; conclusion?: string }) =>
    request.get<any, SupplierEvaluation[]>('/supplier/evaluation/list', { params }),

  // 获取单条评估详情
  getEvaluationDetail: (id: string) =>
    request.get<any, SupplierEvaluation>(`/supplier/evaluation/${id}`),

  // 获取某个供应商的全部评估记录
  getEvaluationsBySupplier: (supplierId: string) =>
    request.get<any, SupplierEvaluation[]>(`/supplier/evaluation/supplier/${supplierId}`),

  // 获取某个供应商的**最近一次**评估
  // ⚠️ 后端返回的是**一条评价记录**，不是聚合的评分统计对象。
  getLatestEvaluation: (supplierId: string) =>
    request.get<any, SupplierEvaluation>(`/supplier/evaluation/supplier/${supplierId}/latest`),

  // 创建供应商评估
  createEvaluation: (data: SupplierEvaluation) =>
    request.post<any, number>('/supplier/evaluation/create', data),

  // 删除评估记录
  deleteEvaluation: (id: string) =>
    request.delete<any, void>(`/supplier/evaluation/${id}`),

  // ========== 采购统计 / 在途 ==========

  // 头部供应商（按采购额排序）
  getTopSuppliers: () =>
    request.get<any, Supplier[]>('/supplier/top'),

  // 待收货（在途）采购订单
  getInTransitOrders: () =>
    request.get<any, PurchaseOrder[]>('/supplier/purchase/in-transit'),

  // 采购订单统计
  getPurchaseStats: () =>
    request.get<any, OrderStats>('/supplier/purchase/stats'),

  /** 采购单下拉用的精简供应商列表（返回 [{id, name, code}]） */
  getPurchaseSupplierOptions: () =>
    request.get<any, Array<{ id: string; name: string; code: string }>>('/purchase/suppliers'),
}