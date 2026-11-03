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

export interface Product {
  id: string
  internalSku: string
  productName: string
  brand: string
  category: string
  mainImage: string
  images: string[]
  description: string
  weight: number
  dimensions: string
  costPrice: number
  salePrice: number
  currencyCode: string
  status: number
  createTime: string
  updateTime: string
}

export interface ProductQuery {
  current: number
  size: number
  keyword?: string
  category?: string
  brand?: string
  status?: number
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  records: T[]
}

export const productApi = {
  // 分页查询商品
  getProducts: (params: ProductQuery) =>
    request.get<any, PageResult<Product>>('/product/list', { params }),

  // 获取商品详情
  getProduct: (id: string) =>
    request.get<any, Product>(`/product/${id}`),

  // 创建商品
  createProduct: (data: Partial<Product>) =>
    request.post<any, Product>('/product', data),

  // 更新商品
  updateProduct: (data: Partial<Product>) =>
    request.put<any, Product>('/product', data),

  // 删除商品
  deleteProduct: (id: string) =>
    request.delete<any, void>(`/product/${id}`),

  // 批量上架
  batchOnShelf: (ids: number[]) =>
    request.post<any, void>('/product/batch/on-shelf', { ids }),

  // 批量下架
  batchOffShelf: (ids: number[]) =>
    request.post<any, void>('/product/batch/off-shelf', { ids })
}