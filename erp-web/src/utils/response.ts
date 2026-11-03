/**
 * 后端响应形状工具
 * ---------------------------------------------------------------------------
 * 为什么需要这个文件：
 *
 * 后端 486 个接口的返回形状是**混用**的：
 *   - 181 个返回 `Result<T>`（形如 `{code, message, data, timestamp}`）
 *   - 265 个返回**裸类型**（`IPage` / `List` / `Map` / 实体 / `ResponseEntity`）
 *
 * 且全仓**没有任何 `ResponseBodyAdvice`**（0 处），所以裸类型接口不会被自动包装。
 * 前端 axios 拦截器是 `response => response.data`，即 `res` 就是 HTTP 响应体本身。
 *
 * 于是「列表页取 `res.data.records`」这种写法：
 *   - 打到返回 `Result` 的接口  → 正常
 *   - 打到返回裸 `IPage` 的接口 → `res.data` 是 undefined → 表格渲染 0 行、分页总数恒为 0
 *
 * 真实踩过的坑：`/customer/list`、`/supplier/list`、`/supplier/purchase/list`
 * 三个接口后端都是裸 `IPage`，前端写了 `res.data` → 客户/供应商列表页永远是空的。
 *
 * 这里**不写死某一层**，而是做形状探测：
 *   - 像 `Result`（有数字型 code + data 字段）→ 剥一层取 `data`
 *   - 其余（裸类型）→ 原样返回
 * 这样同一份视图代码对两种后端约定都成立，后端将来统一包装也不会再炸前端。
 *
 * 📌 注意：不要在 axios 拦截器里做这个归一化 —— `error.response.data.message`
 *    和 `res.message`（报表页读 Result 的 message）依赖原始结构。
 */

/** `Result<T>` 的响应体形状（只声明用得到的字段） */
export interface ResultShape<T = unknown> {
  code?: number
  message?: string
  data?: T
  timestamp?: number
}

/** 分页结果（后端 `IPage` / `Page` / `PageResult` 三种类都是这个形状） */
export interface PageResult<T = any> {
  records: T[]
  total: number
  size?: number
  current?: number
  pages?: number
}

/**
 * 判断响应体是不是 `Result<T>` 包装。
 *
 * ⚠️ 判定条件不能只看「有没有 data 字段」：业务接口返回的 `Map<String,Object>`
 *    里完全可能带一个叫 `data` 的业务键（如 `/inventory/stats`）→ 会误判。
 * 所以额外要求 `code` 存在且是数字（`Result.code` 是 Integer）。
 */
export function isResultShape(res: any): res is ResultShape {
  return (
    !!res &&
    typeof res === 'object' &&
    !Array.isArray(res) &&
    typeof (res as any).code === 'number' &&
    'data' in (res as any)
  )
}

/**
 * 剥掉 `Result<T>` 外壳，返回真正的业务载荷。
 * - 裸类型响应 → 原样返回
 * - `Result<T>` 响应 → 返回 `T`（`data` 为 null 时返回 null）
 */
export function unwrap<T = any>(res: any): T {
  if (isResultShape(res)) {
    return (res as ResultShape<T>).data as T
  }
  return res as T
}

/**
 * 取出分页数据，兼容「裸 IPage」和「Result<IPage>」两种后端约定。
 *
 * 永远返回 `{records, total}`，records 保证是数组、total 保证是数字 ——
 * 视图层不用再写 `|| []` / `|| 0` 兜底，也就不会出现「空数组被当成正常结果」的情况。
 */
export function unwrapPage<T = any>(res: any): PageResult<T> {
  const page = unwrap<PageResult<T>>(res)
  return {
    records: Array.isArray(page?.records) ? page.records : [],
    total: Number(page?.total) || 0,
    size: page?.size,
    current: page?.current,
    pages: page?.pages
  }
}
