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

export interface PlatformConfig {
  id: number
  platform: string
  shopId: string
  shopName: string
  apiBaseUrl: string
  callbackUrl: string
  status: number
  createTime: string
}

export const platformApi = {
  // 获取平台配置列表
  getConfigs: () => request.get<any, PlatformConfig[]>('/platform/config/list'),

  // 保存平台配置
  saveConfig: (config: Partial<PlatformConfig>) =>
    request.post('/platform/config', config),

  // 删除平台配置
  deleteConfig: (id: number) => request.delete(`/platform/config/${id}`),

  // 测试连接
  testConnection: (id: number) => request.post(`/platform/config/${id}/test`),

  // 同步订单
  syncOrders: (platform: string, shopId: string) =>
    request.post(`/platform/sync/orders`, { platform, shopId })
}
