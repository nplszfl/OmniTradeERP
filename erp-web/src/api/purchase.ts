import request from './index'

export interface PurchaseOrder {
  id: string
  poNo: string
  supplierId: string
  supplierName: string
  totalAmount: number
  currencyCode: string
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED'
  expectedDate: string
  createdAt: string
  items: { sku: string; productName: string; quantity: number; unitPrice: number }[]
}

export const purchaseApi = {
  list:    (params: any) => request.get<any, PurchaseOrder[]>('/purchase/orders', { params }),
  detail:  (id: string)  => request.get<any, PurchaseOrder>(`/purchase/orders/${id}`),
  create:  (data: any)   => request.post<any, any>('/purchase/orders', data),
  approve: (id: string)  => request.post<any, any>(`/purchase/orders/${id}/approve`),
  cancel:  (id: string, reason: string) => request.post<any, any>(`/purchase/orders/${id}/cancel`, { reason }),
  receive: (id: string)  => request.post<any, any>(`/purchase/orders/${id}/receive`),
  suppliers: () => request.get<any, any[]>('/purchase/suppliers'),
}
