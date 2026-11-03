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

export interface Customer {
  id: string
  name: string
  email: string
  phone: string
  platform: string
  platformCustomerId: string
  country: string
  state: string
  city: string
  address: string
  postalCode: string
  level: string
  totalOrders: number
  totalSpent: number
  avgOrderValue: number
  lastOrderAt: string
  remark: string
  status: string
  createTime: string
  updateTime: string
}

// ========== 客户跟进记录 ==========
// 📌 后端 t_customer_follow_record 的实体字段（CustomerFollowRecord）
export interface CustomerFollowRecord {
  id: string
  customerId: string
  followType: string          // PHONE/EMAIL/WECHAT/VISIT/OTHER
  content: string
  followTime: string
  nextFollowTime: string
  result: string              // SUCCESS/PENDING/FAILED
  followUserId: string
  followUserName: string
  followerName: string
  remark: string
  createTime: string
}

// ========== 客户分组 ==========
export interface CustomerGroup {
  id: string
  name: string
  description: string
  memberCount: number
  sortOrder: number
  status: string
  createTime: string
}

// ========== 客户标签 ==========
export interface CustomerTag {
  id: string
  name: string
  color: string
  description: string
  sortOrder: number
  remark: string
  status: string
  createTime: string
}

export interface CustomerStats {
  totalCustomers: number
  vipCount: number
  activeCount: number
  totalRevenue: number
  avgSpent: number
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

export const customerApi = {
  // 创建客户
  create: (data: Customer) => 
    request.post<any, ResultShape<string>>('/customer/create', data),

  // 获取客户详情
  getById: (id: string) => 
    request.get<any, Customer>(`/customer/${id}`),

  // 分页查询客户
  getList: (params: {
    current: number
    size: number
    keyword?: string
    platform?: string
    level?: string
    country?: string
  }) => request.get<any, PageResult<Customer>>('/customer/list', { params }),

  // 获取客户统计
  getStats: () => 
    request.get<any, CustomerStats>('/customer/stats'),

  // 获取VIP客户
  getVipList: () => 
    request.get<any, Customer[]>('/customer/vip'),

  // 更新客户
  update: (id: string, data: Customer) => 
    request.put<any, void>(`/customer/${id}`, data),

  // 删除客户
  delete: (id: string) => 
    request.delete<any, void>(`/customer/${id}`)
}

// ========== 客户跟进记录 API ==========
// ⚠️ 这三个模块（跟进/分组/标签）后端**早就写完了**，
//    但前端从来没做过页面 —— 属于「后端做完 UI 没做」的半成品。
//    这里把接口接上，页面见 views/customer/CustomerList.vue 的标签页。
export const customerFollowApi = {
  // 分页查询跟进记录
  list: (params: { current: number; size: number; customerId?: string; followType?: string; result?: string }) =>
    request.get<any, PageResult<CustomerFollowRecord>>('/customer/follow/list', { params }),

  // 今日待跟进
  pendingToday: () =>
    request.get<any, CustomerFollowRecord[]>('/customer/follow/pending/today'),

  // 某个客户的全部跟进记录
  byCustomer: (customerId: string) =>
    request.get<any, CustomerFollowRecord[]>(`/customer/follow/customer/${customerId}`),

  // 新增跟进记录
  create: (data: Partial<CustomerFollowRecord>) =>
    request.post<any, ResultShape<string>>('/customer/follow/create', data),

  // 标记跟进完成
  complete: (id: string, result: string, remark?: string) =>
    request.post<any, void>(`/customer/follow/${id}/complete`, null, { params: { result, remark } }),

  // 删除跟进记录
  remove: (id: string) =>
    request.delete<any, void>(`/customer/follow/${id}`),
}

// ========== 客户分组 API ==========
export const customerGroupApi = {
  list: (keyword?: string) =>
    request.get<any, CustomerGroup[]>('/customer/group/list', { params: { keyword } }),

  create: (data: Partial<CustomerGroup>) =>
    request.post<any, ResultShape<string>>('/customer/group/create', data),

  update: (id: string, data: Partial<CustomerGroup>) =>
    request.put<any, void>(`/customer/group/${id}`, data),

  remove: (id: string) =>
    request.delete<any, void>(`/customer/group/${id}`),
}

// ========== 客户标签 API ==========
export const customerTagApi = {
  list: (keyword?: string) =>
    request.get<any, CustomerTag[]>('/customer/tag/list', { params: { keyword } }),

  create: (data: Partial<CustomerTag>) =>
    request.post<any, ResultShape<string>>('/customer/tag/create', data),

  update: (id: string, data: Partial<CustomerTag>) =>
    request.put<any, void>(`/customer/tag/${id}`, data),

  remove: (id: string) =>
    request.delete<any, void>(`/customer/tag/${id}`),

  // 客户已打的标签
  byCustomer: (customerId: string) =>
    request.get<any, CustomerTag[]>(`/customer/tag/customer/${customerId}`),

  // 给客户加标签
  addToCustomer: (customerId: string, tagId: string) =>
    request.post<any, void>(`/customer/tag/customer/${customerId}/add`, null, { params: { tagId } }),

  // 移除客户的标签
  removeFromCustomer: (customerId: string, tagId: string) =>
    request.delete<any, void>(`/customer/tag/customer/${customerId}/tag/${tagId}`),
}
