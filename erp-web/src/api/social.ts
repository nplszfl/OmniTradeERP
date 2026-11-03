import request from './index'

export const socialApi = {
  tiktokShop: {
    list:   (params: any) => request.get<any, any[]>('/social/tiktok-shop', { params }),
    sync:   () => request.post<any, any>('/social/tiktok-shop/sync'),
  },
  liveStream: {
    list:   (params: any) => request.get<any, any[]>('/social/live-streams', { params }),
    create: (data: any)   => request.post<any, any>('/social/live-streams', data),
    start:  (id: number)  => request.post<any, any>(`/social/live-streams/${id}/start`),
    end:    (id: number)  => request.post<any, any>(`/social/live-streams/${id}/end`),
  },
  cooperation: {
    list:    (params: any) => request.get<any, any[]>('/social/cooperations', { params }),
    invite:  (data: any)   => request.post<any, any>('/social/cooperations', data),
  },
  influencer: {
    list:   (params: any) => request.get<any, any[]>('/social/influencers', { params }),
    detail: (id: number)  => request.get<any, any>(`/social/influencers/${id}`),
  },
  douyinShop: {
    list: (params: any) => request.get<any, any[]>('/social/douyin-shop', { params }),
    sync: () => request.post<any, any>('/social/douyin-shop/sync'),
  },
}
