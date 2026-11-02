import request from './index'

/**
 * 租户管理（erp-tenant-service）
 *
 * ⚠️ 之前这个文件里大半方法在调**后端根本不存在的路径**：
 *   POST /tenant、PUT /tenant/{id}、DELETE /tenant/{id}、
 *   GET /tenant/{id}/config|dashboard|billing|usage|platforms、
 *   POST /tenant/auth/login|register|logout
 *
 * 后端真实提供的只有：
 *   GET  /api/tenant/list                       （管理端租户列表，本文件新增）
 *   GET  /api/tenant/dashboard|trend|quota      （租户看板）
 *   GET  /api/tenant/billing/**                 （账单）
 *   GET  /api/tenant/usage/**                   （用量）
 *   GET  /api/tenant/config/**                  （套餐与功能开关）
 *   /api/v1/auth/**、/api/v1/tenant/**、/api/v1/platforms/**（租户自助）
 *
 * 未实现的接口不要留在这里 —— 留着就是「看着配了、调用就 404」的假配置。
 * 登录也不走这里：前端 Login.vue 用的是 /api/auth/login（erp-user-service）。
 */
export const tenantApi = {
  /** 租户列表（裸数组，不包 Result） */
  list:        () => request.get('/tenant/list'),
  /** 租户看板：/tenant/dashboard/trend */
  dashboard:   () => request.get('/tenant/dashboard/trend'),
  /** 账单：/tenant/billing/bills */
  billing:     () => request.get('/tenant/billing/bills'),
  /** 用量：/tenant/usage/current */
  usage:       () => request.get('/tenant/usage/current'),
  /** 套餐与功能开关：/tenant/config/plan */
  config:      () => request.get('/tenant/config/plan'),
}

/**
 * 报表中心（erp-reporting-service）
 *
 * ⚠️ 之前这个文件里的 list/generate/download/templates 后端一个都没有，
 * 页面靠 catch 分支的 mock 数据撑着 —— 看着有数据，实际是前端写死的假数据。
 * 现在后端已补齐 list / generate / templates。
 */
export const reportApi = {
  /** 报表台账（后端包 Result，取 res.data） */
  list:    (params: any) => request.get('/report/list', { params }),
  /** 生成报表 */
  generate: (data: any) => request.post('/report/generate', data),
  /** 可用报表模板 */
  templates: () => request.get('/report/templates'),
  /**
   * 导出 Excel
   *
   * 后端 /report/{type}/export 返回 Result<byte[]>，
   * Jackson 把 byte[] 序列化成 base64 字符串 → 这里还原成 Blob 下载。
   */
  download: async (type: 'sales' | 'inventory' | 'finance', params: any = {}) => {
    const res: any = await request.get(`/report/${type}/export`, { params })
    const base64 = res?.data ?? res
    if (typeof base64 !== 'string') {
      throw new Error('导出接口没有返回文件内容')
    }
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    const blob = new Blob([bytes], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${type}-report.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  },
}
