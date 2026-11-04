# 疑似死接口清单（自动生成，勿手改）

> 由 `scripts/check-dead-endpoints.py --markdown` 生成。
> 重新生成：`python3 scripts/check-dead-endpoints.py --markdown`

## 统计

- 后端 HTTP 接口：**472** 个
- 前端视图真正调用：**205** 处
- 服务间引用：**2** 处
- 网关路由：**36** 条
- 疑似死接口：**259** 个

## 怎么读这份清单

「疑似死接口」= **前端没有任何视图调用它，其他服务也没有引用它**。

⚠️ 它**不等于**「应该删掉」：

1. REST 接口本来就可能给外部客户端 / Postman / 第三方调用；
2. 有些是「后端做完了，UI 还没做」的半成品（最常见）；
3. 有些是给将来预留的扩展点。

所以这份清单的价值是**排期用的 backlog**，不是删除清单。

## 重点结论（**每条结论都由脚本当场核验**，结论过期会自动报警）

> 📌 这些结论是**手写的**，但**不能信** —— 手写结论会静默过期
> （2026-11-04 实测：上一版写着「知识库没有任何视图调用」，
>   结果上一轮已经把知识库管理页做完了 → 文档在骗人）。
> 所以每条结论后面用 `<!-- dead: <路径> -->` 声明它断言为死的接口，
> **脚本会当场核验**：这些接口若已经有调用方 → 报告变红并提示结论已过期。

### 1. 知识库：UI 已做完，剩的是 RAG 侧的 ask / stats

上一版这里写着「知识库没有任何视图调用」—— **那已经过期了**：
上一轮已经把知识库管理页做进 `views/ai/Assistant.vue` 的「知识库」tab
（`knowledgeBaseApi.list/categories/add/remove` 都在调）。

现在真正还没接的是**知识库问答**接口：
<!-- dead: POST /api/ai/knowledge/ask -->

（`GET /api/ai/knowledge/stats` 2026-11-04 已接上 ——
  聊天页开场白的「基于 N 条行业知识库」改成动态取真实条数，
  原来写死「330+」而真实条数远少于此 → 界面在承诺不存在的东西。）

<!-- 下面是**声明**，由 `scripts/check-seed-row-claims.py` 当场核验 ——
     种子改了而这里没改 → 闸门报红（规则 51：手写结论会静默过期）。 -->
<!-- rows: knowledge_base=31 -->
<!-- rows: knowledge_document=5 -->

⚠️ **同日的第二个真缺陷**：这个 `stats` 接口原先返回的是
`knowledge_document` 表的条数，而**聊天检索**和**知识库列表**用的都是
`knowledge_base` 表 → 开场白说 5、页签列 31 → **界面自相矛盾**。
2026-11-04 已修：`stats` 改数 `knowledge_base`，
向量文档侧的统计另开 `GET /api/ai/knowledge/documents/stats`（两个口径用 URL 区分）。
→ 新增闸门 `scripts/check-stat-source-consistency.py` 防止复发。

⚠️ **api 层有个命名陷阱**（不是死接口，但容易踩）：
`aiAssistantApi.knowledge.list/add/remove` 和 `knowledgeBaseApi.list/add/remove`
**指向同一批 URL**，但注释声称它们是**两张不同的表**
（`knowledge_document` vs `knowledge_base`）。
实际后端 `addKnowledge()` 写的是 `knowledge_base` —— 注释与实现对不上。
→ 视图里能用的只有 `knowledgeBaseApi` 那一套。

### 2. 一批「后端做完了，UI 一个页面都没有」的功能域

| 域 | 现状 |
|---|---|
| 客户跟进 / 分组 / 标签 | 后端 CRUD 全套，前端客户页只有列表+增删改 |
| 仓库库存作业 | 移库/冻结/解冻/报损/盘点/批次/波次/调拨 —— 前端仓库页只有仓库+库位 |
| 租户计费 / 用量 / 配额 | 前端租户页只有租户列表 |
| AI 辅助（意图/情感/统计/batch/health） | 都是「进阶能力」，UI 只有最基础的聊天 |

### 3. 「健康检查」类接口全部未接

`/health` 在 8 个服务里都有（共 9 条声明，`/api/v1/pricing/health` 被两个 controller 各写了一遍），
前端一个都没调 —— 缺一个「系统健康总览」页。
<!-- dead: GET /api/v1/ai-assistant/health -->
<!-- dead: GET /api/v1/anomaly/health -->
<!-- dead: GET /api/v1/inventory-prediction/health -->
<!-- dead: GET /api/v1/product-description/health -->
<!-- dead: GET /api/v1/recommend/health -->
<!-- dead: GET /api/v1/analytics/health -->
<!-- dead: GET /api/v1/pricing/health -->
<!-- dead: GET /logistics/health -->

### 4. 死路由

`/order/detail/:id` 这条路由没有任何地方导航过去，
而且它指向的 `views/order/index.vue` 其实是**列表页**（详情是弹窗）。

## 清单

### erp-ai-assistant-service（16 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/ai/knowledge/ask` |  |
| POST | `/api/ai/knowledge/documents` |  |
| POST | `/api/ai/knowledge/documents/batch` |  |
| GET | `/api/ai/knowledge/documents/stats` |  |
| DELETE | `/api/ai/knowledge/documents/{id}` |  |
| GET | `/api/ai/knowledge/search` |  |
| POST | `/api/v1/ai-assistant/chat/stream` |  |
| GET | `/api/v1/ai-assistant/health` |  |
| GET | `/api/v1/ai-assistant/history/{userId}` |  |
| DELETE | `/api/v1/ai-assistant/history/{userId}` |  |
| POST | `/api/v1/ai-assistant/intent` |  |
| POST | `/api/v1/ai-assistant/knowledge/batch` |  |
| GET | `/api/v1/ai-assistant/knowledge/search` |  |
| POST | `/api/v1/ai-assistant/sentiment` |  |
| GET | `/api/v1/ai-assistant/statistics` |  |
| POST | `/api/v1/ai-assistant/statistics/reset` |  |

### erp-analytics-service（16 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/v1/analytics/competitor/{productId}` |  |
| GET | `/api/v1/analytics/customers/distribution` |  |
| GET | `/api/v1/analytics/dashboard/inventory-alerts` |  |
| GET | `/api/v1/analytics/dashboard/order-status-distribution` |  |
| GET | `/api/v1/analytics/dashboard/platform-distribution` |  |
| GET | `/api/v1/analytics/dashboard/sales-trend` |  |
| GET | `/api/v1/analytics/dashboard/today-trend` |  |
| GET | `/api/v1/analytics/dashboard/top-products` |  |
| POST | `/api/v1/analytics/forecast/batch` |  |
| GET | `/api/v1/analytics/forecast/{productId}` |  |
| GET | `/api/v1/analytics/health` |  |
| GET | `/api/v1/analytics/products/top` |  |
| GET | `/api/v1/analytics/revenue/trend` |  |
| GET | `/api/v1/analytics/roi` |  |
| GET | `/api/v1/analytics/roi/platform` |  |
| GET | `/api/v1/analytics/roi/product` |  |

### erp-anomaly-detection-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/anomaly/detect` |  |
| POST | `/api/v1/anomaly/detect-batch` |  |
| GET | `/api/v1/anomaly/health` |  |
| GET | `/api/v1/anomaly/{id}` |  |

### erp-customer-service（8 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/customer/follow/customer/{customerId}/count` |  |
| PUT | `/customer/follow/{id}` |  |
| GET | `/customer/follow/{id}` |  |
| GET | `/customer/group/{id}` |  |
| POST | `/customer/tag/customer/{customerId}/add` |  |
| POST | `/customer/tag/customer/{customerId}/batch-add` |  |
| DELETE | `/customer/tag/customer/{customerId}/clear` |  |
| GET | `/customer/tag/{id}` |  |

### erp-finance-service（14 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/finance/account/{id}/default` |  |
| POST | `/finance/category` |  |
| PUT | `/finance/category` |  |
| DELETE | `/finance/category/{id}` |  |
| POST | `/finance/flow/batch` |  |
| GET | `/finance/flow/{id}` |  |
| DELETE | `/finance/flow/{id}` |  |
| GET | `/finance/overview` |  |
| GET | `/finance/reconcile` |  |
| GET | `/finance/statistics/account` |  |
| GET | `/finance/statistics/category` |  |
| GET | `/finance/statistics/daily` |  |
| GET | `/finance/statistics/monthly` |  |
| GET | `/finance/statistics/trend` |  |

### erp-inventory-alert-service（15 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/inventory/alert/check` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/inventory/alert/check/batch` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/records/pending` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/replenishments/estimate-amount` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/replenishments/pending` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/inventory/alert/rules/batch` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/rules/enabled` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/rules/product/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/slow-moving` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/inventory/alert/slow-moving/detect` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/slow-moving/pending` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/inventory/alert/slow-moving/{id}/notified` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/statistics/distribution` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/statistics/efficiency` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/inventory/alert/statistics/trend` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-inventory-prediction-service（14 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/v1/inventory-prediction/accuracy-trend/{productId}` |  |
| POST | `/api/v1/inventory-prediction/accuracy/batch` |  |
| GET | `/api/v1/inventory-prediction/accuracy/{productId}` |  |
| POST | `/api/v1/inventory-prediction/batch-predict` |  |
| GET | `/api/v1/inventory-prediction/health` |  |
| POST | `/api/v1/inventory-prediction/optimize` |  |
| POST | `/api/v1/inventory-prediction/predict` |  |
| GET | `/api/v1/inventory-prediction/replenishment/{productId}` |  |
| GET | `/api/v1/inventory-prediction/safety-stock/{productId}` |  |
| GET | `/api/v1/inventory-prediction/tasks` |  |
| GET | `/api/v1/inventory-prediction/turnover` |  |
| GET | `/api/v1/inventory-prediction/turnover/{productId}` |  |
| GET | `/api/v1/inventory-prediction/urgent` |  |
| GET | `/api/v1/inventory-prediction/warn-low-stock` |  |

### erp-inventory-service（6 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/inventory/decrease` |  |
| POST | `/inventory/import` |  |
| POST | `/inventory/increase` |  |
| GET | `/inventory/statistics` |  |
| GET | `/inventory/trend` |  |
| GET | `/inventory/{id}` |  |

### erp-logistics-service（3 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/logistics/health` |  |
| GET | `/logistics/rates/count` |  |
| POST | `/logistics/recommend` |  |

### erp-order-service（8 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/order/batch/export` |  |
| POST | `/order/create` |  |
| GET | `/order/platform/{platform}/{platformOrderNo}` |  |
| POST | `/order/statistics/by-country` |  |
| POST | `/order/statistics/by-platform` |  |
| GET | `/order/statistics/today` |  |
| GET | `/order/statistics/trend` |  |
| GET | `/order/{orderId}` |  |

### erp-platform-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/order-sync/orders` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/platform/config/{id}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/platform/sync/platforms` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/platform/sync/test` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-pricing-service（19 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/competitor/add/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/competitor/list/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/competitor/scrape/batch` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/competitor/scrape/platform` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/competitor/scrape/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/competitor/stats/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/price-history/list/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/price-history/range/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/price-history/recent/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/price-history/trend/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/pricing/adjust-by-competitors/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/pricing/batch-calculate` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/pricing/calculate` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/pricing/health` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/pricing/health` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/pricing/history/{sku}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/pricing/info` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/pricing/info/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| POST | `/api/v1/pricing/manual-adjust` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-product-description-service（12 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/product-description/ab-test` |  |
| POST | `/api/v1/product-description/batch-generate` |  |
| POST | `/api/v1/product-description/batch-import` |  |
| POST | `/api/v1/product-description/generate/internal` |  |
| GET | `/api/v1/product-description/health` |  |
| GET | `/api/v1/product-description/highlights/{productId}` |  |
| GET | `/api/v1/product-description/history` |  |
| GET | `/api/v1/product-description/list/{productId}` |  |
| POST | `/api/v1/product-description/multi-language` |  |
| POST | `/api/v1/product-description/save/internal` |  |
| POST | `/api/v1/product-description/seo-optimize` |  |
| GET | `/api/v1/product-description/templates` |  |

### erp-product-recommendation-service（7 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/v1/feedback/stats` |  |
| POST | `/api/v1/feedback/tune` |  |
| GET | `/api/v1/feedback/weights` |  |
| GET | `/api/v1/recommend/health` |  |
| GET | `/api/v1/recommend/results` |  |
| POST | `/api/v1/recommend/run` |  |
| GET | `/api/v1/recommend/{sku}` |  |

### erp-product-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/product/batch/off-shelf` |  |
| POST | `/product/batch/on-shelf` |  |
| GET | `/product/sku/{sku}` |  |
| GET | `/product/{id}` |  |

### erp-purchase-service（10 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/purchase/compare` |  |
| POST | `/purchase/forecast` |  |
| POST | `/purchase/forecast/batch` |  |
| GET | `/purchase/forecasts` |  |
| POST | `/purchase/plan` |  |
| GET | `/purchase/plans` |  |
| POST | `/purchase/plans/batch` |  |
| GET | `/purchase/plans/pending` |  |
| POST | `/purchase/quotes` |  |
| GET | `/purchase/quotes/{productId}` |  |

### erp-reporting-service（11 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/report/finance` |  |
| POST | `/report/finance/daily` |  |
| GET | `/report/finance/export` |  |
| GET | `/report/inventory` |  |
| POST | `/report/inventory/daily` |  |
| GET | `/report/sales` |  |
| POST | `/report/sales/daily` |  |
| GET | `/report/sales/export` |  |
| POST | `/report/sales/monthly` |  |
| POST | `/report/sales/weekly` |  |
| GET | `/report/templates` |  |

### erp-social-commerce-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/v1/cooperations/product/{productId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/douyin-shop/shop/{shopId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/live-streams/stream/{streamId}` | 该模块有 `@Scheduled`，可能内部触发 |
| GET | `/api/v1/tiktok-shop/shop/{shopId}` | 该模块有 `@Scheduled`，可能内部触发 |

### erp-supplier-service（6 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/purchase/orders/{id}/receive` |  |
| DELETE | `/supplier/purchase/item/{itemId}` |  |
| PUT | `/supplier/purchase/{id}` |  |
| DELETE | `/supplier/purchase/{id}` |  |
| POST | `/supplier/purchase/{id}/item` |  |
| POST | `/supplier/purchase/{id}/payment` |  |

### erp-tenant-service（26 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/tenant/billing/bills` |  |
| GET | `/api/tenant/billing/bills/current` |  |
| GET | `/api/tenant/billing/bills/pending` |  |
| POST | `/api/tenant/billing/bills/{billId}/pay` |  |
| GET | `/api/tenant/billing/rules` |  |
| POST | `/api/tenant/billing/rules` |  |
| GET | `/api/tenant/config/features` |  |
| PUT | `/api/tenant/config/features/{featureKey}` |  |
| GET | `/api/tenant/config/features/{featureKey}/enabled` |  |
| GET | `/api/tenant/config/plan` |  |
| GET | `/api/tenant/dashboard/quota` |  |
| GET | `/api/tenant/dashboard/trend` |  |
| GET | `/api/tenant/usage/current` |  |
| GET | `/api/tenant/usage/period` |  |
| POST | `/api/tenant/usage/record/ai` |  |
| POST | `/api/tenant/usage/record/api` |  |
| POST | `/api/tenant/usage/record/storage` |  |
| PUT | `/api/tenant/{id}` |  |
| GET | `/api/v1/auth/me` |  |
| GET | `/api/v1/platforms/credentials` |  |
| POST | `/api/v1/platforms/credentials` |  |
| PUT | `/api/v1/platforms/credentials/{id}` |  |
| DELETE | `/api/v1/platforms/credentials/{id}` |  |
| GET | `/api/v1/platforms/credentials/{platform}` |  |
| POST | `/api/v1/tenant/upgrade` |  |
| GET | `/api/v1/tenant/users` |  |

### erp-user-service（7 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/auth/logout` |  |
| POST | `/auth/refresh` |  |
| GET | `/department/list` |  |
| POST | `/user/change-password` |  |
| GET | `/user/{id}` |  |
| POST | `/user/{id}/reset-password` |  |
| POST | `/user/{userId}/departments` |  |

### erp-warehouse-service（45 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/warehouse/inbound/orders` |  |
| GET | `/warehouse/inbound/orders/page` |  |
| GET | `/warehouse/inbound/orders/{orderId}/details` |  |
| POST | `/warehouse/inbound/orders/{orderId}/partial` |  |
| POST | `/warehouse/inventory/batches` |  |
| GET | `/warehouse/inventory/batches/expiring` |  |
| GET | `/warehouse/inventory/batches/page` |  |
| GET | `/warehouse/inventory/batches/{batchId}` |  |
| POST | `/warehouse/inventory/batches/{batchId}/freeze` |  |
| PUT | `/warehouse/inventory/batches/{batchId}/quantity` |  |
| POST | `/warehouse/inventory/batches/{batchId}/unfreeze` |  |
| POST | `/warehouse/inventory/checks` |  |
| GET | `/warehouse/inventory/checks` |  |
| GET | `/warehouse/inventory/checks/{checkId}` |  |
| GET | `/warehouse/inventory/checks/{checkId}/results` |  |
| POST | `/warehouse/inventory/damage` |  |
| POST | `/warehouse/inventory/freeze` |  |
| POST | `/warehouse/inventory/relocate` |  |
| POST | `/warehouse/inventory/unfreeze` |  |
| GET | `/warehouse/location/{locationId}` |  |
| GET | `/warehouse/locations/available` |  |
| POST | `/warehouse/locations/batch` |  |
| GET | `/warehouse/locations/page` |  |
| POST | `/warehouse/materials` |  |
| GET | `/warehouse/materials/page` |  |
| GET | `/warehouse/materials/replenishment` |  |
| PUT | `/warehouse/materials/{materialId}` |  |
| DELETE | `/warehouse/materials/{materialId}` |  |
| POST | `/warehouse/materials/{materialId}/inbound` |  |
| POST | `/warehouse/materials/{materialId}/outbound` |  |
| POST | `/warehouse/outbound/orders` |  |
| GET | `/warehouse/outbound/orders/page` |  |
| GET | `/warehouse/outbound/orders/{orderId}/details` |  |
| GET | `/warehouse/stock-transfer/page` |  |
| GET | `/warehouse/stock-transfer/{transferId}` |  |
| POST | `/warehouse/stock-transfer/{transferId}/execute` |  |
| POST | `/warehouse/stock-transfer/{transferId}/receive` |  |
| GET | `/warehouse/warehouses/{warehouseId}/dashboard` |  |
| GET | `/warehouse/warehouses/{warehouseId}/statistics` |  |
| POST | `/warehouse/waves` |  |
| PUT | `/warehouse/waves/details/{detailId}/pick` |  |
| GET | `/warehouse/waves/page` |  |
| GET | `/warehouse/waves/{waveId}` |  |
| POST | `/warehouse/waves/{waveId}/assign` |  |
| GET | `/warehouse/{warehouseId}` |  |

