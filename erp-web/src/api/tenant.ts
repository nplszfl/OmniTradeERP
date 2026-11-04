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
/** `Result<T>` 的最小形状（报表生成接口要读它的 message） */
export interface ResultLike {
  code?: number
  message?: string
  data?: any
}

export const tenantApi = {
  /** 租户列表（裸数组，不包 Result） */
  list:        () => request.get<any, any[]>('/tenant/list'),
  /**
   * 租户看板：/tenant/dashboard/trend
   *
   * 📌 后端这些接口都接受可选的 `tenantId`：
   *    管理端要能看**指定租户**的数据，就得把它传下去，
   *    否则后端按**当前登录租户**解析 → 看到的是别人的数据。
   */
  dashboard:   (tenantId?: string) =>
    request.get<any, any>('/tenant/dashboard/trend', { params: tenantId ? { tenantId } : {} }),
  /** 账单：/tenant/billing/bills */
  billing:     (tenantId?: string) =>
    request.get<any, any>('/tenant/billing/bills', { params: tenantId ? { tenantId } : {} }),
  /** 用量：/tenant/usage/current */
  usage:       (tenantId?: string) =>
    request.get<any, any>('/tenant/usage/current', { params: tenantId ? { tenantId } : {} }),
  /** 套餐与功能开关：/tenant/config/plan */
  config:      (tenantId?: string) =>
    request.get<any, any>('/tenant/config/plan', { params: tenantId ? { tenantId } : {} }),
  /**
   * 可用套餐列表（真实价格 / 单量上限 / AI 配额）
   *
   * 📌 之前前端**把套餐价格和限额写死**在数组里
   *    （而且 STARTER 的单量写的是 1000，后端实际是 3000）→ **数字在骗人**（规则 56）。
   *    → 现在从后端取，后端改价格前端自动跟着变。
   */
  plans:       () => request.get<any, any[]>('/tenant/config/plans'),
  /**
   * 变更租户套餐
   *
   * 📌 必须带 tenantId —— 后端 changePlan 原本**只按当前登录租户**改
   *    （而其它接口都带 tenantId）→ 管理端改别人的套餐 = **改错对象还提示成功**。
   */
  changePlan:  (targetPlan: string, tenantId?: string) =>
    request.post('/tenant/config/plan/change',
                 { targetPlan, changeType: 'UPGRADE' },
                 { params: tenantId ? { tenantId } : {} }),
  /**
   * 删除租户（软删，后端置 status=DELETED）
   *
   * 📌 后端 erp-tenant-service 的 TenantListController 有
   *    `DELETE /{id}`（网关 /api/tenant/** → StripPrefix=0 → 原样转发）。
   *    ⚠️ 之前前端**没有这个方法**，页面的删除按钮只改本地数组 → 数据没删。
   */
  remove:      (id: string) => request.delete(`/tenant/${encodeURIComponent(id)}`),
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
  list:    (params: any) => request.get<any, any[]>('/report/list', { params }),
  /** 生成报表 */
  generate: (data: any) => request.post<any, ResultLike>('/report/generate', data),
  /** 可用报表模板 */
  templates: () => request.get<any, any[]>('/report/templates'),
  /**
   * 删除一条报表台账记录
   *
   * 📌 后端 `DELETE /report/{type}/{id}` —— **type 必须带上**：
   *    报表分属 sales / inventory / finance 三张表，id 各自自增
   *    → 只给 id 不知道该删哪张表。
   *
   * 📌 **id 必须是 string**：后端 erp-common 的 CommonJacksonAutoConfiguration
   *    把 Long 序列化成字符串（雪花 id 19 位 → JS Number 会截断 → 删错行）。
   */
  remove:     (type: string, id: string) =>
    request.delete(`/report/${encodeURIComponent(type)}/${id}`),
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
