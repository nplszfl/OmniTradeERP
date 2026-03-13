import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/order'
  },
  {
    path: '/layout',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: '/order',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: '/platform',
        component: () => import('@/views/platform/index.vue'),
        meta: { title: '平台配置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
