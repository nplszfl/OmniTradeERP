import request from './index'

/**
 * 社交电商 API
 *
 * 📌 后端 5 个 controller（erp-social-commerce-service）：
 *    /api/v1/tiktok-shop  /api/v1/douyin-shop
 *    /api/v1/live-streams /api/v1/influencers  /api/v1/cooperations
 *    网关 `Path=/api/social/**` → RewritePath → `/api/v1/${seg}`
 *    所以前端这里写 `/social/xxx` 即可。
 *
 * 📌 响应形状：**全部是裸类型**（后端返回 ResponseEntity<List<X>> / ResponseEntity<X>，
 *    不是 Result<T>），所以不需要 unwrap()，直接 `res || []` 消费。
 *    DELETE 类接口返回 204 No Content → response.data 是空串。
 *
 * ⚠️ 字段名必须严格对齐后端 DTO（LiveStreamDTO / InfluencerDTO / CooperationDTO /
 *    TikTokShopConfigDTO / DouyinShopConfig）：
 *    直播是 `viewerCount` / `totalGmv`（不是 viewers / gmv），
 *    达人是 `followerCount` / `categories` / `status`（不是 followers / category / cooperationStatus）。
 *    之前这个页面每列 prop 都写错了 → 四张表全是空白。
 */

// ==================== 类型 ====================

export interface TikTokShopConfig {
  id: string
  shopName: string
  shopId: string
  accessToken?: string
  refreshToken?: string
  tokenExpireTime?: string
  appKey?: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface DouyinShopConfig {
  id: string
  shopName: string
  shopId: string
  accessToken?: string
  refreshToken?: string
  tokenExpireTime?: string
  appKey?: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface LiveStream {
  id: string
  streamId: string
  title: string
  platform: string
  influencerId?: string
  status: string
  scheduledStartTime?: string
  actualStartTime?: string
  endTime?: string
  duration?: number
  viewerCount?: number
  peakViewers?: number
  likeCount?: number
  shareCount?: number
  commentCount?: number
  newFollowers?: number
  totalSales?: number
  totalOrders?: number
  totalGmv?: number
  coverImageUrl?: string
  products?: string
  createdAt?: string
  updatedAt?: string
}

export interface Influencer {
  id: string
  name: string
  platformId?: string
  platform: string
  avatarUrl?: string
  followerCount?: number
  followingCount?: number
  totalViews?: number
  totalLikes?: number
  level?: string
  status?: string
  contactEmail?: string
  contactPhone?: string
  cooperationPrice?: number
  /** 后端是逗号分隔的字符串，不是数组 */
  categories?: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface Cooperation {
  id: string
  influencerId: string
  productId: string
  productName: string
  type: string
  status: string
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  agreedPrice?: number
  actualCost?: number
  commissionRate?: number
  expectedViews?: number
  actualViews?: number
  expectedSales?: number
  actualSales?: number
  expectedGmv?: number
  actualGmv?: number
  content?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

// ==================== TikTok 店铺 ====================

export const tiktokShopApi = {
  list:   () => request.get<any, TikTokShopConfig[]>('/social/tiktok-shop'),
  detail: (id: string) => request.get<any, TikTokShopConfig>(`/social/tiktok-shop/${id}`),
  byShopId: (shopId: string) => request.get<any, TikTokShopConfig>(`/social/tiktok-shop/shop/${shopId}`),
  create: (data: any) => request.post<any, TikTokShopConfig>('/social/tiktok-shop', data),
  update: (id: string, data: any) => request.put<any, TikTokShopConfig>(`/social/tiktok-shop/${id}`, data),
  remove: (id: string) => request.delete<any, void>(`/social/tiktok-shop/${id}`),
  refreshToken: (id: string) => request.post<any, TikTokShopConfig>(`/social/tiktok-shop/${id}/refresh-token`),
  sync: () => request.post<any, Record<string, any>>('/social/tiktok-shop/sync'),
}

// ==================== 抖音店铺 ====================

export const douyinShopApi = {
  list:   () => request.get<any, DouyinShopConfig[]>('/social/douyin-shop'),
  detail: (id: string) => request.get<any, DouyinShopConfig>(`/social/douyin-shop/${id}`),
  byShopId: (shopId: string) => request.get<any, DouyinShopConfig>(`/social/douyin-shop/shop/${shopId}`),
  create: (data: any) => request.post<any, DouyinShopConfig>('/social/douyin-shop', data),
  update: (id: string, data: any) => request.put<any, DouyinShopConfig>(`/social/douyin-shop/${id}`, data),
  remove: (id: string) => request.delete<any, void>(`/social/douyin-shop/${id}`),
  refreshToken: (id: string) => request.post<any, DouyinShopConfig>(`/social/douyin-shop/${id}/refresh-token`),
  sync: () => request.post<any, Record<string, any>>('/social/douyin-shop/sync'),
}

// ==================== 直播 ====================

export const liveStreamApi = {
  list:   () => request.get<any, LiveStream[]>('/social/live-streams'),
  detail: (id: string) => request.get<any, LiveStream>(`/social/live-streams/${id}`),
  byStreamId: (streamId: string) => request.get<any, LiveStream>(`/social/live-streams/stream/${streamId}`),
  byPlatform: (platform: string) => request.get<any, LiveStream[]>(`/social/live-streams/platform/${platform}`),
  byStatus: (status: string) => request.get<any, LiveStream[]>(`/social/live-streams/status/${status}`),
  byInfluencer: (influencerId: string) => request.get<any, LiveStream[]>(`/social/live-streams/influencer/${influencerId}`),
  byDateRange: (startTime: string, endTime: string) =>
    request.get<any, LiveStream[]>('/social/live-streams/date-range', { params: { startTime, endTime } }),
  currentLive: () => request.get<any, LiveStream[]>('/social/live-streams/current-live'),
  recent:   () => request.get<any, LiveStream[]>('/social/live-streams/recent'),
  create: (data: any) => request.post<any, LiveStream>('/social/live-streams', data),
  update: (id: string, data: any) => request.put<any, LiveStream>(`/social/live-streams/${id}`, data),
  start: (id: string) => request.post<any, LiveStream>(`/social/live-streams/${id}/start`),
  end:   (id: string, data?: any) => request.post<any, LiveStream>(`/social/live-streams/${id}/end`, data),
  remove: (id: string) => request.delete<any, void>(`/social/live-streams/${id}`),
}

// ==================== 达人 ====================

export const influencerApi = {
  list:   () => request.get<any, Influencer[]>('/social/influencers'),
  detail: (id: string) => request.get<any, Influencer>(`/social/influencers/${id}`),
  byPlatform: (platform: string) => request.get<any, Influencer[]>(`/social/influencers/platform/${platform}`),
  byStatus: (status: string) => request.get<any, Influencer[]>(`/social/influencers/status/${status}`),
  byLevel: (level: string) => request.get<any, Influencer[]>(`/social/influencers/level/${level}`),
  /** 头部达人：minFollowers 默认 10000 */
  top: (minFollowers?: number) => request.get<any, Influencer[]>('/social/influencers/top', { params: { minFollowers } }),
  byCategory: (category: string) => request.get<any, Influencer[]>(`/social/influencers/category`, { params: { category } }),
  create: (data: any) => request.post<any, Influencer>('/social/influencers', data),
  update: (id: string, data: any) => request.put<any, Influencer>(`/social/influencers/${id}`, data),
  remove: (id: string) => request.delete<any, void>(`/social/influencers/${id}`),
}

// ==================== 达人合作 ====================

export const cooperationApi = {
  list:   () => request.get<any, Cooperation[]>('/social/cooperations'),
  detail: (id: string) => request.get<any, Cooperation>(`/social/cooperations/${id}`),
  byInfluencer: (influencerId: string) => request.get<any, Cooperation[]>(`/social/cooperations/influencer/${influencerId}`),
  byProduct: (productId: string) => request.get<any, Cooperation[]>(`/social/cooperations/product/${productId}`),
  byType: (type: string) => request.get<any, Cooperation[]>(`/social/cooperations/type/${type}`),
  byStatus: (status: string) => request.get<any, Cooperation[]>(`/social/cooperations/status/${status}`),
  completed: () => request.get<any, Cooperation[]>('/social/cooperations/completed'),
  upcoming:  () => request.get<any, Cooperation[]>('/social/cooperations/upcoming'),
  byDateRange: (startDate: string, endDate: string) =>
    request.get<any, Cooperation[]>('/social/cooperations/date-range', { params: { startDate, endDate } }),
  create: (data: any) => request.post<any, Cooperation>('/social/cooperations', data),
  update: (id: string, data: any) => request.put<any, Cooperation>(`/social/cooperations/${id}`, data),
  confirm: (id: string) => request.post<any, Cooperation>(`/social/cooperations/${id}/confirm`),
  start:   (id: string) => request.post<any, Cooperation>(`/social/cooperations/${id}/start`),
  complete: (id: string, data?: any) => request.post<any, Cooperation>(`/social/cooperations/${id}/complete`, data),
  cancel:  (id: string) => request.post<any, Cooperation>(`/social/cooperations/${id}/cancel`),
  remove:  (id: string) => request.delete<any, void>(`/social/cooperations/${id}`),
}

/**
 * 兼容旧引用（views 里原来用的是 socialApi.*）。
 * 📌 保留是为了不破坏别处可能的引用；新代码请直接用上面拆开的 5 个 api。
 */
export const socialApi = {
  tiktokShop: tiktokShopApi,
  douyinShop: douyinShopApi,
  liveStream: liveStreamApi,
  influencer: influencerApi,
  cooperation: cooperationApi,
}
