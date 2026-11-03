# 疑似死接口清单（自动生成，勿手改）

> 由 `scripts/check-dead-endpoints.py --markdown` 生成。
> 重新生成：`python3 scripts/check-dead-endpoints.py --markdown`

## 统计

- 后端 HTTP 接口：**461** 个
- 前端视图真正调用：**116** 处
- 服务间引用：**358** 处
- 网关路由：**30** 条
- 疑似死接口：**121** 个

## 怎么读这份清单

「疑似死接口」= **前端没有任何视图调用它，其他服务也没有引用它**。

⚠️ 它**不等于**「应该删掉」：

1. REST 接口本来就可能给外部客户端 / Postman / 第三方调用；
2. 有些是「后端做完了，UI 还没做」的半成品（最常见）；
3. 有些是给将来预留的扩展点。

所以这份清单的价值是**排期用的 backlog**，不是删除清单。

## 重点结论（人工分析，随脚本一起维护）

### 1. AI 知识库：后端和 api 层都齐了，但**没有任何视图调用**

`erp-web/src/api/ai.ts` 里 `aiAssistantApi.knowledge.list/add/remove` 三个函数
都写好了，对应的后端接口（`GET /api/ai/knowledge/search`、
`POST /api/v1/ai-assistant/knowledge`、`DELETE /api/ai/knowledge/documents/{id}`）
也都存在且网关改写正确 —— 但 `views/ai/Assistant.vue` **一个都没调**。

后果：RAG 助手的知识库**永远只能靠手工灌**，页面上也看不到一条知识。
而聊天页的开场白写着「基于 330+ 行业知识库」—— 这个承诺目前落不了地。

⚠️ 另外 `knowledge.list()` 调的是 `/search`，后端 `@RequestParam("q")` 是**必填**
→ 不传 q 会 400。api 层的语义本身就有坑。

### 2. 一批「后端做完了，UI 一个页面都没有」的功能域

| 域 | 死接口数 | 现状 |
|---|---|---|
| 客户跟进 / 分组 / 标签 | 15 | 后端 CRUD 全套，前端客户页只有列表+增删改 |
| 仓库库存作业 | 10 | 移库/冻结/解冻/报损/盘点/批次/波次/调拨 —— 前端仓库页只有仓库+库位 |
| 租户计费 / 用量 / 配额 | 17 | 前端租户页只有租户列表 |
| AI 辅助（意图/情感/统计/batch/health） | 15 | 都是「进阶能力」，UI 只有最基础的聊天 |

### 3. 「健康检查」类接口全部未接

`/health` 在 5 个服务里都有，前端一个都没调 —— 缺一个「系统健康总览」页。

### 4. 死路由

`/order/detail/:id` 这条路由没有任何地方导航过去，
而且它指向的 `views/order/index.vue` 其实是**列表页**（详情是弹窗）。

## 清单

### erp-ai-assistant-service（7 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/ai/knowledge/ask` |  |
| GET | `/api/ai/knowledge/stats` |  |
| GET | `/api/v1/ai-assistant/health` |  |
| POST | `/api/v1/ai-assistant/intent` |  |
| POST | `/api/v1/ai-assistant/knowledge` |  |
| POST | `/api/v1/ai-assistant/sentiment` |  |
| GET | `/api/v1/ai-assistant/statistics` |  |

### erp-anomaly-detection-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/anomaly/detect` |  |
| POST | `/api/v1/anomaly/detect-batch` |  |
| GET | `/api/v1/anomaly/health` |  |
| GET | `/api/v1/anomaly/{id}` |  |

### erp-customer-service（17 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/customer/follow/create` |  |
| GET | `/customer/follow/list` |  |
| PUT | `/customer/follow/{id}` |  |
| GET | `/customer/follow/{id}` |  |
| DELETE | `/customer/follow/{id}` |  |
| POST | `/customer/group/create` |  |
| GET | `/customer/group/list` |  |
| PUT | `/customer/group/{id}` |  |
| GET | `/customer/group/{id}` |  |
| DELETE | `/customer/group/{id}` |  |
| POST | `/customer/tag/create` |  |
| GET | `/customer/tag/list` |  |
| PUT | `/customer/tag/{id}` |  |
| GET | `/customer/tag/{id}` |  |
| DELETE | `/customer/tag/{id}` |  |
| GET | `/customer/vip` |  |
| GET | `/customer/{id}` |  |

### erp-inventory-alert-service（1 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/inventory/alert/slow-moving` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-inventory-prediction-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/inventory-prediction/batch-predict` |  |
| GET | `/api/v1/inventory-prediction/health` |  |
| POST | `/api/v1/inventory-prediction/optimize` |  |
| GET | `/api/v1/inventory-prediction/warn-low-stock` |  |

### erp-order-service（3 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/order/create` |  |
| GET | `/order/statistics/today` |  |
| GET | `/order/{orderId}` |  |

### erp-platform-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/order-sync/orders` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/platform/config/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/platform/sync/platforms` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/platform/sync/test` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-pricing-service（1 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/pricing/manual-adjust` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-product-description-service（9 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/product-description/ab-test` |  |
| POST | `/api/v1/product-description/batch-generate` |  |
| POST | `/api/v1/product-description/batch-import` |  |
| GET | `/api/v1/product-description/health` |  |
| GET | `/api/v1/product-description/history` |  |
| POST | `/api/v1/product-description/multi-language` |  |
| POST | `/api/v1/product-description/save` |  |
| POST | `/api/v1/product-description/seo-optimize` |  |
| GET | `/api/v1/product-description/templates` |  |

### erp-product-recommendation-service（1 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/v1/recommend/health` |  |

### erp-product-service（1 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/product/{id}` |  |

### erp-purchase-service（6 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/purchase/compare` |  |
| POST | `/purchase/forecast` |  |
| GET | `/purchase/forecasts` |  |
| POST | `/purchase/plan` |  |
| GET | `/purchase/plans` |  |
| POST | `/purchase/quotes` |  |

### erp-social-commerce-service（23 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/v1/cooperations/completed` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/cooperations/date-range` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/cooperations/upcoming` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/cooperations/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| PUT | `/api/v1/cooperations/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| DELETE | `/api/v1/cooperations/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/douyin-shop/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| PUT | `/api/v1/douyin-shop/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| DELETE | `/api/v1/douyin-shop/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/influencers/category` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/influencers/top` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/influencers/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| PUT | `/api/v1/influencers/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| DELETE | `/api/v1/influencers/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/live-streams/current-live` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/live-streams/date-range` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/live-streams/recent` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/live-streams/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| PUT | `/api/v1/live-streams/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| DELETE | `/api/v1/live-streams/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/tiktok-shop/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| PUT | `/api/v1/tiktok-shop/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| DELETE | `/api/v1/tiktok-shop/{id}` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-supplier-service（10 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/purchase/suppliers` |  |
| POST | `/supplier/evaluation/create` |  |
| GET | `/supplier/evaluation/list` |  |
| GET | `/supplier/evaluation/{id}` |  |
| DELETE | `/supplier/evaluation/{id}` |  |
| GET | `/supplier/purchase/in-transit` |  |
| GET | `/supplier/purchase/stats` |  |
| GET | `/supplier/top` |  |
| GET | `/supplier/{id}` |  |
| DELETE | `/supplier/{id}` |  |

### erp-tenant-service（15 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/tenant/billing/bills` |  |
| GET | `/api/tenant/billing/rules` |  |
| POST | `/api/tenant/billing/rules` |  |
| GET | `/api/tenant/config/features` |  |
| GET | `/api/tenant/config/plan` |  |
| GET | `/api/tenant/config/plans` |  |
| GET | `/api/tenant/dashboard/quota` |  |
| GET | `/api/tenant/dashboard/trend` |  |
| GET | `/api/tenant/usage/current` |  |
| GET | `/api/tenant/usage/period` |  |
| GET | `/api/v1/auth/me` |  |
| GET | `/api/v1/platforms/credentials` |  |
| POST | `/api/v1/platforms/credentials` |  |
| POST | `/api/v1/tenant/upgrade` |  |
| GET | `/api/v1/tenant/users` |  |

### erp-user-service（5 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/auth/logout` |  |
| POST | `/auth/refresh` |  |
| GET | `/department/list` |  |
| POST | `/user/change-password` |  |
| GET | `/user/{id}` |  |

### erp-warehouse-service（10 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/warehouse/inventory/batches` |  |
| POST | `/warehouse/inventory/checks` |  |
| POST | `/warehouse/inventory/damage` |  |
| POST | `/warehouse/inventory/freeze` |  |
| POST | `/warehouse/inventory/relocate` |  |
| POST | `/warehouse/inventory/unfreeze` |  |
| POST | `/warehouse/materials` |  |
| GET | `/warehouse/stock-transfer/page` |  |
| GET | `/warehouse/stock-transfer/{transferId}` |  |
| POST | `/warehouse/waves` |  |

