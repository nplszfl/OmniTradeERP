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

export interface Warehouse {
  id: number
  warehouseCode: string
  warehouseName: string
  warehouseType: string
  country: string
  province: string
  city: string
  address: string
  contactPerson: string
  contactPhone: string
  email: string
  status: number
  isDefault: number
  createTime: string
  updateTime: string
}

export interface WarehouseLocation {
  id: number
  warehouseId: number
  locationCode: string
  locationType: string
  zone: string
  row: string
  column: string
  level: string
  status: number
}

export const warehouseApi = {
  // 获取启用状态的仓库列表（后端返回裸数组，直接给下拉框用）
  getWarehouses: (params?: { status?: number }) =>
    request.get<any, Warehouse[]>('/warehouse/all', { params }),

  // 分页查询仓库
  getWarehouseList: (params: { current: number; size: number; keyword?: string }) =>
    request.get<any, { current: number; size: number; total: number; records: Warehouse[] }>('/warehouse/list', { params }),

  // 获取仓库详情
  getWarehouse: (id: number) =>
    request.get<any, Warehouse>(`/warehouse/${id}`),

  // 创建仓库
  createWarehouse: (data: Partial<Warehouse>) =>
    request.post<any, Warehouse>('/warehouse', data),

  // 更新仓库
  updateWarehouse: (data: Partial<Warehouse>) =>
    request.put<any, Warehouse>('/warehouse', data),

  // 删除仓库
  deleteWarehouse: (id: number) =>
    request.delete<any, void>(`/warehouse/${id}`),

  // 获取库位列表
  getLocations: (warehouseId: number) =>
    request.get<any, WarehouseLocation[]>(`/warehouse/${warehouseId}/locations`),

  // 创建库位
  createLocation: (data: Partial<WarehouseLocation>) =>
    request.post<any, WarehouseLocation>('/warehouse/location', data),

  // 更新库位
  updateLocation: (data: Partial<WarehouseLocation>) =>
    request.put<any, WarehouseLocation>('/warehouse/location', data),

  // 删除库位
  deleteLocation: (id: number) =>
    request.delete<any, void>(`/warehouse/location/${id}`)
}