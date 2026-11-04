# 疑似死接口清单（自动生成，勿手改）

> 由 `scripts/check-dead-endpoints.py --markdown` 生成。
> 重新生成：`python3 scripts/check-dead-endpoints.py --markdown`

## 统计

- 后端 HTTP 接口：**469** 个
- 前端视图真正调用：**193** 处
- 服务间引用：**367** 处
- 网关路由：**34** 条
- 疑似死接口：**75** 个

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

现在真正还没接的是**知识库问答**的两个接口：
<!-- dead: POST /api/ai/knowledge/ask -->
<!-- dead: GET /api/ai/knowledge/stats -->

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

`/health` 在多个服务里都有，前端一个都没调 —— 缺一个「系统健康总览」页。
<!-- dead: GET /api/v1/ai-assistant/health -->
<!-- dead: GET /api/v1/anomaly/health -->
<!-- dead: GET /api/v1/inventory-prediction/health -->
<!-- dead: GET /api/v1/product-description/health -->
<!-- dead: GET /api/v1/recommend/health -->

### 4. 死路由

`/order/detail/:id` 这条路由没有任何地方导航过去，
而且它指向的 `views/order/index.vue` 其实是**列表页**（详情是弹窗）。

## 清单

### erp-ai-assistant-service（6 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/ai/knowledge/ask` |  |
| GET | `/api/ai/knowledge/stats` |  |
| GET | `/api/v1/ai-assistant/health` |  |
| POST | `/api/v1/ai-assistant/intent` |  |
| POST | `/api/v1/ai-assistant/sentiment` |  |
| GET | `/api/v1/ai-assistant/statistics` |  |

### erp-anomaly-detection-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/api/v1/anomaly/detect` |  |
| POST | `/api/v1/anomaly/detect-batch` |  |
| GET | `/api/v1/anomaly/health` |  |
| GET | `/api/v1/anomaly/{id}` |  |

### erp-customer-service（4 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| PUT | `/customer/follow/{id}` |  |
| GET | `/customer/follow/{id}` |  |
| GET | `/customer/group/{id}` |  |
| GET | `/customer/tag/{id}` |  |

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

### erp-tenant-service（15 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| GET | `/api/tenant/billing/rules` |  |
| POST | `/api/tenant/billing/rules` |  |
| GET | `/api/tenant/config/features` |  |
| GET | `/api/tenant/config/plan` |  |
| GET | `/api/tenant/config/plans` |  |
| GET | `/api/tenant/dashboard/quota` |  |
| GET | `/api/tenant/dashboard/trend` |  |
| GET | `/api/tenant/usage/current` |  |
| GET | `/api/tenant/usage/period` |  |
| PUT | `/api/tenant/{id}` |  |
| DELETE | `/api/tenant/{id}` |  |
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

### erp-warehouse-service（11 个）

| 方法 | 路径 | 备注 |
|---|---|---|
| POST | `/warehouse/inventory/batches` |  |
| POST | `/warehouse/inventory/checks` |  |
| GET | `/warehouse/inventory/checks` |  |
| POST | `/warehouse/inventory/damage` |  |
| POST | `/warehouse/inventory/freeze` |  |
| POST | `/warehouse/inventory/relocate` |  |
| POST | `/warehouse/inventory/unfreeze` |  |
| POST | `/warehouse/materials` |  |
| GET | `/warehouse/stock-transfer/page` |  |
| GET | `/warehouse/stock-transfer/{transferId}` |  |
| POST | `/warehouse/waves` |  |

