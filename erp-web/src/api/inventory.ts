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

export interface Inventory {
  id: string
  skuCode: string
  productName: string
  warehouseId: string
  warehouseName: string
  locationId: string
  locationCode: string
  availableQty: number
  lockedQty: number
  totalQty: number
  safetyStock: number
  dailySales: number
  turnoverDays: number
  lastInTime: string
  lastOutTime: string
}

export interface InventoryStats {
  totalSku: number
  normalStock: number
  lowStock: number
  outOfStock: number
}

export interface StockFlow {
  id: string
  flowTime: string
  flowType: string
  quantity: number
  orderNo: string
  remark: string
}

export interface InventoryQuery {
  current: number
  size: number
  warehouseId?: string
  skuCode?: string
  status?: string
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

export interface StockAdjustRequest {
  inventoryId: string
  type: 'increase' | 'decrease' | 'set'
  quantity: number
  reason: string
  remark?: string
}

export interface StockTransferRequest {
  fromInventoryId: string
  toWarehouseId: string
  toLocationId: string
  quantity: number
  remark?: string
}

export const inventoryApi = {
  // 获取库存统计
  getStats: () =>
    request.get<any, InventoryStats>('/inventory/stats'),

  // 分页查询库存
  getInventoryList: (params: InventoryQuery) =>
    request.get<any, PageResult<Inventory>>('/inventory/list', { params }),

  // 获取库存详情
  getInventoryDetail: (id: string) =>
    request.get<any, Inventory>(`/inventory/${id}`),

  // 获取库存流水
  getStockFlows: (inventoryId: string, page?: number, size?: number) =>
    request.get<any, PageResult<StockFlow>>(`/inventory/${inventoryId}/flows`, {
      params: { current: page || 1, size: size || 20 }
    }),

  // 调整库存
  adjustStock: (data: StockAdjustRequest) =>
    request.post<any, void>('/inventory/adjust', data),

  // 调拨库存
  transferStock: (data: StockTransferRequest) =>
    request.post<any, void>('/inventory/transfer', data),

  // 批量导入库存
  importInventory: (data: any[]) =>
    request.post<any, void>('/inventory/import', data),

  // 导出库存
  exportInventory: (params: InventoryQuery) =>
    request.get<any, Blob>('/inventory/export', {
      params,
      responseType: 'blob'
    })
}