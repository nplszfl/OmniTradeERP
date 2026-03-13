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
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

export const orderApi = {
  // 分页查询订单
  getOrders: (params: {
    current: number
    size: number
    platform?: string
    status?: string
  }) => request.get<any, PageResult<Order>>('/order/order/list/list', { params }),

  // 获取订单详情
  getOrder: (orderId: number) => request.get<any, Order>(`/order/order/${orderId}`),

  // 更新订单状态
  updateStatus: (orderId: number, status: string) =>
    request.put(`/order/order/${orderId}/status`, null, { params: { status } }),

  // 更新物流信息
  updateShipping: (orderId: number, trackingNumber: string, logisticsCompany: string) =>
    request.put(`/order/order/${orderId}/shipping`, null, {
      params: { trackingNumber, logisticsCompany }
    })
}
