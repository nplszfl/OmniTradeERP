# /customer/list 响应形状实测 & 全仓 Controller 返回类型盘点

> 调查日期：2026-11-03
> 调查方式：**真实启动服务 + curl 实测**（非静态推断）
> 范围：`erp-*/src/main/java/**/*Controller.java` 全部 486 个 HTTP 接口 + `erp-web` 前端 api/视图层
> 性质：只读调查（未修改任何 Java / TypeScript / 配置文件，未 git commit）

---

## 0. 一句话结论

**是，`CustomerList.vue` 的 `res.data` 是货真价实的运行时 bug —— 客户列表页恒为空。**

`erp-customer-service` 的 `CustomerController.pageCustomers` 返回 **裸 `IPage<Customer>`**，
实测响应 JSON 的顶层键是 `records / total / size / current / pages`，**根本没有 `data` 键**，
所以拦截器剥掉一层后 `res` 本身就是分页对象，`res.data` === `undefined`，
`res.data || {}` → `{}`，`pageData.records` → `undefined` → `customerList.value = []`。

这**不是类型问题，是运行时问题**：TS 侧 `request.get<any, PageResult<Customer>>` 声明的类型
反而是对的（因为后端确实返回 `PageResult`），错的是视图层多剥了一层 `.data`。

---

## 1. 实测：`GET /customer/list?current=1&size=5`

### 1.1 实测环境

| 项 | 值 |
|---|---|
| MySQL 容器 | `erp-ai-test`（宿主端口 **13309** → 容器 3306） |
| 凭据 | `root` / `root` |
| 建库 | `docker exec -i erp-ai-test mysql -uroot -proot < database/customer-service.sql` → `erp_customer` |
| 服务 | `erp-customer-service-1.0.0-SNAPSHOT.jar`，`--server.port=18085` |
| Nacos | `spring.cloud.nacos.discovery.enabled=false`、`config.enabled=false` |
| 打包 | `mvn -o -f erp-customer-service/pom.xml package -DskipTests` → **BUILD SUCCESS** |
| 启动 | **Started CustomerServiceApplication in 2.116 seconds**，Tomcat 18085 |
| 数据 | `t_customer` 表原本 0 行；为让证据可判读，插入 3 行种子数据（id 9001/9002/9003） |

> 种子数据是为了排除「空表所以看不出形状」的干扰。
> `t_customer` 的建表语句来自 `database/customer-service.sql`，实体映射 `@TableName("t_customer")` 一致。

### 1.2 HTTP 响应头

```
HTTP/1.1 200
Content-Type: application/json
Transfer-Encoding: chunked
```

### 1.3 响应体原文（单行，未做任何裁剪）

```json
{"records":[{"id":9001,"name":"Alice Verify","email":"alice@example.com","phone":"+1-555-0001","platform":"AMAZON","platformCustomerId":"AMZ-9001","country":"US","state":null,"city":null,"address":null,"postalCode":null,"level":"VIP","totalOrders":12,"totalSpent":3456.7800,"avgOrderValue":288.0650,"lastOrderAt":null,"remark":null,"status":"ACTIVE","createTime":"2026-11-03T07:46:02","updateTime":"2026-11-03T07:46:02"},{"id":9002,"name":"Bob Verify","email":"bob@example.com","phone":"+1-555-0002","platform":"EBAY","platformCustomerId":"EBY-9002","country":"DE","state":null,"city":null,"address":null,"postalCode":null,"level":"NORMAL","totalOrders":3,"totalSpent":210.5000,"avgOrderValue":70.1667,"lastOrderAt":null,"remark":null,"status":"ACTIVE","createTime":"2026-11-03T07:46:02","updateTime":"2026-11-03T07:46:02"},{"id":9003,"name":"Carol Verify","email":"carol@example.com","phone":"+1-555-0003","platform":"TIKTOK","platformCustomerId":"TT-9003","country":"FR","state":null,"city":null,"address":null,"postalCode":null,"level":"NEW","totalOrders":1,"totalSpent":45.0000,"avgOrderValue":45.0000,"lastOrderAt":null,"remark":null,"status":"ACTIVE","createTime":"2026-11-03T07:46:02","updateTime":"2026-11-03T07:46:02"}],"total":3,"size":5,"current":1,"pages":1}
```

### 1.4 响应体原文（格式化）

```json
{
    "records": [
        {
            "id": 9001,
            "name": "Alice Verify",
            "email": "alice@example.com",
            "phone": "+1-555-0001",
            "platform": "AMAZON",
            "platformCustomerId": "AMZ-9001",
            "country": "US",
            "state": null,
            "city": null,
            "address": null,
            "postalCode": null,
            "level": "VIP",
            "totalOrders": 12,
            "totalSpent": 3456.78,
            "avgOrderValue": 288.065,
            "lastOrderAt": null,
            "remark": null,
            "status": "ACTIVE",
            "createTime": "2026-11-03T07:46:02",
            "updateTime": "2026-11-03T07:46:02"
        },
        {
            "id": 9002,
            "name": "Bob Verify",
            "email": "bob@example.com",
            "phone": "+1-555-0002",
            "platform": "EBAY",
            "platformCustomerId": "EBY-9002",
            "country": "DE",
            "state": null,
            "city": null,
            "address": null,
            "postalCode": null,
            "level": "NORMAL",
            "totalOrders": 3,
            "totalSpent": 210.5,
            "avgOrderValue": 70.1667,
            "lastOrderAt": null,
            "remark": null,
            "status": "ACTIVE",
            "createTime": "2026-11-03T07:46:02",
            "updateTime": "2026-11-03T07:46:02"
        },
        {
            "id": 9003,
            "name": "Carol Verify",
            "email": "carol@example.com",
            "phone": "+1-555-0003",
            "platform": "TIKTOK",
            "platformCustomerId": "TT-9003",
            "country": "FR",
            "state": null,
            "city": null,
            "address": null,
            "postalCode": null,
            "level": "NEW",
            "totalOrders": 1,
            "totalSpent": 45.0,
            "avgOrderValue": 45.0,
            "lastOrderAt": null,
            "remark": null,
            "status": "ACTIVE",
            "createTime": "2026-11-03T07:46:02",
            "updateTime": "2026-11-03T07:46:02"
        }
    ],
    "total": 3,
    "size": 5,
    "current": 1,
    "pages": 1
}
```

### 1.5 顶层键判定

```text
top-level keys: ['records', 'total', 'size', 'current', 'pages']
has "data" key? False
res.data would be => None
```

**没有 `code` / `message` / `timestamp`，也没有 `data`。**
响应体就是 MyBatis-Plus `IPage` 的直接序列化结果。

### 1.6 同服务其它端点（同一 Controller，全部裸类型）

```json
GET /customer/9001
{"id":9001,"name":"Alice Verify","email":"alice@example.com",...,"createTime":"2026-11-03T07:46:02","updateTime":"2026-11-03T07:46:02"}
```

```json
GET /customer/stats
{"totalCustomers":3,"vipCount":1,"activeCount":3,"totalRevenue":3712.2800,"avgSpent":1237.43}
```

```json
GET /customer/vip
[{"id":9001,"name":"Alice Verify",...}]
```

→ `erp-customer-service` 的 **4 个 Controller、32 个接口，其中 19 个返回裸类型、0 个返回 `Result`**。

---

## 2. 结论：`CustomerList.vue` 的 `res.data` 是不是运行时 bug？

### 2.1 判定链路

| 步骤 | 事实 | 来源 |
|---|---|---|
| 1 | axios 拦截器 `response => response.data` | `erp-web/src/api/index.ts:17-18`（`customer.ts` 有同名同逻辑的独立实例，`erp-web/src/api/customer.ts:9-12`） |
| 2 | 拦截器返回后，`res` = 后端响应体本身 | 拦截器语义 |
| 3 | 后端响应体 = 裸 `IPage` JSON（无 `data` 键） | **§1.4 实测 curl 输出** |
| 4 | 因此 `res.data` === `undefined` | 3 的直接推论 |
| 5 | `res.data \|\| {}` → `{}`；`pageData.records` → `undefined` | JS 语义 |

### 2.2 实际代码

`erp-web/src/views/customer/CustomerList.vue` L304-314：

```ts
const res = await customerApi.getList({
  current: pagination.current,
  size: pagination.size,
  keyword: searchForm.keyword || undefined,
  platform: searchForm.platform || undefined,
  level: searchForm.level || undefined,
  country: searchForm.country || undefined
})
const pageData = res.data || {}          // ← res.data === undefined ⇒ pageData = {}
customerList.value = pageData.records || []  // ← undefined ⇒ 表格恒空
pagination.total = pageData.total || 0       // ← undefined ⇒ 分页器恒 0 条
```

**判定：是运行时 bug（不是仅类型问题）。**
后端返回 3 条真实记录，前端表格一条都渲染不出来；分页总数恒为 0。

### 2.3 反证：同一文件里的统计卡片是对的

`CustomerList.vue` L322-328 走的是**同一个裸类型端点** `/customer/stats`，但写法是：

```ts
const res = await customerApi.getStats()
Object.assign(stats, res)      // ← 没有 .data ⇒ 正确
```

→ 同一个文件里，**没有多剥 `.data` 的那个是对的，多剥了 `.data` 的那个是错的**。
这恰好证明问题出在「视图层假设后端包了 `Result`」，而不是出在拦截器或后端。

### 2.4 TypeScript 侧其实没错

```ts
// erp-web/src/api/customer.ts
getList: (params: {...}) => request.get<any, PageResult<Customer>>('/customer/list', { params }),
```

axios 的泛型签名是 `<T = any, R = AxiosResponse<T>>`，这里 `R = PageResult<Customer>`，
所以 `await customerApi.getList(...)` 的**静态类型就是 `PageResult<Customer>`** —— 与后端裸返回的
`IPage<Customer>` 序列化结果一致。**api 层声明是对的，视图层多写了一层 `.data` 才是 bug。**

---

## 3. 全仓 Controller 返回类型盘点

### 3.1 关键前置事实

- **全仓不存在任何 `ResponseBodyAdvice`**（`grep -rn "ResponseBodyAdvice" --include="*.java" .` → 0 命中）。
  ⇒ **没有任何自动包装机制**，后端返回什么就序列化什么。
- 唯一的包装类是 `com.crossborder.erp.common.result.Result`（`erp-common/.../result/Result.java`）。
- 全仓 **没有任何 Controller** 返回 `ResponseEntity<Result<...>>`
  （`ResponseEntity` + `Result` 的组合只出现在 `erp-common/.../GlobalExceptionHandler.java`，
  即**只有异常路径**会被包成 `Result`；成功路径一律裸返回）。
- ⇒ 因此**必须逐个 controller 人工判断**，这正是下面这张表要做的事。

### 3.2 总量统计

扫描 `erp-*/src/main/java/**/*Controller.java`（54 个 Controller 文件），共 **486** 个 HTTP 接口方法：

| 返回形态 | 数量 | 运行时 JSON 顶层是否含 `data` |
|---|---|---|
| `Result<T>` | **181** | ✅ 含（`{code,message,data,timestamp}`） |
| 裸类型（直接返回 DTO / `IPage` / `List` / `Map` / `String` …） | **140** | ❌ 不含 |
| `ResponseEntity<裸类型>`（`ResponseEntity.ok(payload)`） | **125** | ❌ 不含 |
| `void` | **40** | ❌ 空 body |
| **运行时裸类型合计** | **265** | ❌ |

> **裸类型接口 = 140 + 125 = 265 个（占全部接口的 54.5%）**

### 3.3 按服务汇总

| 服务 | 接口数 | `Result<T>` | 运行时裸类型 | void |
|---|---|---|---|---|
| erp-ai-assistant-service | 18 | 0 | 18 | 0 |
| erp-analytics-service | 21 | 0 | 21 | 0 |
| erp-anomaly-detection-service | 8 | 0 | 8 | 0 |
| erp-customer-service | 32 | 0 | 19 | 13 |
| erp-finance-service | 25 | 20 | 5 | 0 |
| erp-inventory-alert-service | 26 | 26 | 0 | 0 |
| erp-inventory-prediction-service | 16 | 0 | 16 | 0 |
| erp-inventory-service | 12 | 10 | 2 | 0 |
| erp-logistics-service | 9 | 0 | 7 | 2 |
| erp-order-service | 15 | 15 | 0 | 0 |
| erp-platform-service | 10 | 10 | 0 | 0 |
| erp-pricing-service | 21 | 0 | 21 | 0 |
| erp-product-description-service | 13 | 0 | 13 | 0 |
| erp-product-recommendation-service | 12 | 0 | 12 | 0 |
| erp-product-service | 8 | 8 | 0 | 0 |
| erp-purchase-service | 12 | 12 | 0 | 0 |
| erp-reporting-service | 14 | 14 | 0 | 0 |
| erp-social-commerce-service | 56 | 0 | 56 | 0 |
| erp-supplier-service | 37 | 0 | 21 | 16 |
| erp-tenant-service | 32 | 0 | 32 | 0 |
| erp-user-service | 26 | 5 | 12 | 9 |
| erp-warehouse-service | 63 | 61 | 2 | 0 |

**只有 10 个服务用 `Result<T>`**：
erp-finance-service、erp-inventory-alert-service、erp-inventory-service、erp-order-service、
erp-platform-service、erp-product-service、erp-purchase-service、erp-reporting-service、
erp-user-service、erp-warehouse-service

**12 个服务一次 `Result` 都没用**（100% 裸类型 / void）：
erp-ai-assistant-service、erp-analytics-service、erp-anomaly-detection-service、erp-customer-service、
erp-inventory-prediction-service、erp-logistics-service、erp-pricing-service、
erp-product-description-service、erp-product-recommendation-service、erp-social-commerce-service、
erp-supplier-service、erp-tenant-service

> 注意：**同一个服务内部也可能混用**。
> 例如 `erp-finance-service` 25 个接口里 5 个包 `Result`、5 个裸；
> `erp-user-service` 26 个里 12 个裸、其余包 `Result`；
> `erp-inventory-service`、`erp-warehouse-service` 也各只有 1-2 个裸接口。
> ⇒ **不能按服务粒度推断，必须按接口粒度判断。**

### 3.4 完整明细表（486 个接口逐个判定）


#### erp-ai-assistant-service — 18 个接口（其中裸类型 **18** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `chat` | `Post /api/v1/ai-assistant/chat` | `ChatResponse` | ❌ **裸类型** |
| `chatStream` | `Post /api/v1/ai-assistant/chat/stream` | `Flux<String>` | ❌ **裸类型** |
| `health` | `Get /api/v1/ai-assistant/health` | `String` | ❌ **裸类型** |
| `getChatHistory` | `Get /api/v1/ai-assistant/history/{userId}` | `List<AIAssistantService.ChatMessage>` | ❌ **裸类型** |
| `clearChatHistory` | `Delete /api/v1/ai-assistant/history/{userId}` | `String` | ❌ **裸类型** |
| `recognizeIntent` | `Post /api/v1/ai-assistant/intent` | `AIAssistantService.IntentRecognition` | ❌ **裸类型** |
| `addKnowledge` | `Post /api/v1/ai-assistant/knowledge` | `String` | ❌ **裸类型** |
| `batchAddKnowledge` | `Post /api/v1/ai-assistant/knowledge/batch` | `String` | ❌ **裸类型** |
| `searchKnowledge` | `Get /api/v1/ai-assistant/knowledge/search` | `List<AIAssistantService.Knowledge>` | ❌ **裸类型** |
| `analyzeSentiment` | `Post /api/v1/ai-assistant/sentiment` | `AIAssistantService.SentimentAnalysis` | ❌ **裸类型** |
| `getStatistics` | `Get /api/v1/ai-assistant/statistics` | `ServiceStatistics` | ❌ **裸类型** |
| `resetStatistics` | `Post /api/v1/ai-assistant/statistics/reset` | `String` | ❌ **裸类型** |
| `ask` | `Post /api/ai/knowledge/ask` | `ResponseEntity<RAGAnswer>` | ❌ **裸类型** |
| `addDocument` | `Post /api/ai/knowledge/documents` | `ResponseEntity<KnowledgeDocument>` | ❌ **裸类型** |
| `addBatch` | `Post /api/ai/knowledge/documents/batch` | `ResponseEntity<List<KnowledgeDocument>>` | ❌ **裸类型** |
| `delete` | `Delete /api/ai/knowledge/documents/{id}` | `ResponseEntity<Boolean>` | ❌ **裸类型** |
| `search` | `Get /api/ai/knowledge/search` | `ResponseEntity<List<RetrievalResult>>` | ❌ **裸类型** |
| `stats` | `Get /api/ai/knowledge/stats` | `ResponseEntity<KnowledgeStats>` | ❌ **裸类型** |

#### erp-analytics-service — 21 个接口（其中裸类型 **21** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `getCompetitorAnalysis` | `Get /api/v1/analytics/competitor/{productId}` | `ResponseEntity<CompetitorAnalysisDTO>` | ❌ **裸类型** |
| `getCustomerDistribution` | `Get /api/v1/analytics/customers/distribution` | `ResponseEntity<List<AnalyticsSummaryDTO.CustomerDistribution>>` | ❌ **裸类型** |
| `getDashboard` | `Get /api/v1/analytics/dashboard` | `ResponseEntity<DashboardDTO>` | ❌ **裸类型** |
| `getInventoryAlerts` | `Get /api/v1/analytics/dashboard/inventory-alerts` | `ResponseEntity<List<DashboardDTO.InventoryAlert>>` | ❌ **裸类型** |
| `getOrderStatusDistribution` | `Get /api/v1/analytics/dashboard/order-status-distribution` | `ResponseEntity<List<DashboardDTO.OrderStatusCount>>` | ❌ **裸类型** |
| `getOverviewStats` | `Get /api/v1/analytics/dashboard/overview` | `ResponseEntity<DashboardDTO.OverviewStats>` | ❌ **裸类型** |
| `getPlatformDistribution` | `Get /api/v1/analytics/dashboard/platform-distribution` | `ResponseEntity<List<DashboardDTO.PlatformSales>>` | ❌ **裸类型** |
| `getRecentOrders` | `Get /api/v1/analytics/dashboard/recent-orders` | `ResponseEntity<List<DashboardDTO.OrderSummary>>` | ❌ **裸类型** |
| `getSalesTrend` | `Get /api/v1/analytics/dashboard/sales-trend` | `ResponseEntity<List<DashboardDTO.TimeSeriesData>>` | ❌ **裸类型** |
| `getTodaySalesTrend` | `Get /api/v1/analytics/dashboard/today-trend` | `ResponseEntity<List<DashboardDTO.TimeSeriesData>>` | ❌ **裸类型** |
| `getTopProducts` | `Get /api/v1/analytics/dashboard/top-products` | `ResponseEntity<List<DashboardDTO.ProductPerformance>>` | ❌ **裸类型** |
| `batchForecast` | `Post /api/v1/analytics/forecast/batch` | `ResponseEntity<List<SalesForecastDTO>>` | ❌ **裸类型** |
| `getSalesForecast` | `Get /api/v1/analytics/forecast/{productId}` | `ResponseEntity<SalesForecastDTO>` | ❌ **裸类型** |
| `health` | `Get /api/v1/analytics/health` | `ResponseEntity<String>` | ❌ **裸类型** |
| `getOrdersTrend` | `Get /api/v1/analytics/orders/trend` | `ResponseEntity<List<AnalyticsSummaryDTO.TrendPoint>>` | ❌ **裸类型** |
| `getTopProducts` | `Get /api/v1/analytics/products/top` | `ResponseEntity<List<AnalyticsSummaryDTO.TopProduct>>` | ❌ **裸类型** |
| `getRevenueTrend` | `Get /api/v1/analytics/revenue/trend` | `ResponseEntity<List<AnalyticsSummaryDTO.TrendPoint>>` | ❌ **裸类型** |
| `getROIAnalysis` | `Get /api/v1/analytics/roi` | `ResponseEntity<ROIAnalysisDTO>` | ❌ **裸类型** |
| `getPlatformROI` | `Get /api/v1/analytics/roi/platform` | `ResponseEntity<ROIAnalysisDTO>` | ❌ **裸类型** |
| `getProductROI` | `Get /api/v1/analytics/roi/product` | `ResponseEntity<ROIAnalysisDTO>` | ❌ **裸类型** |
| `getSummary` | `Get /api/v1/analytics/summary` | `ResponseEntity<AnalyticsSummaryDTO>` | ❌ **裸类型** |

#### erp-anomaly-detection-service — 8 个接口（其中裸类型 **8** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `detect` | `Post /api/v1/anomaly/detect` | `AnomalyDetectionResult` | ❌ **裸类型** |
| `detectBatch` | `Post /api/v1/anomaly/detect-batch` | `List<AnomalyDetectionResult>` | ❌ **裸类型** |
| `detectByOrderId` | `Post /api/v1/anomaly/detect/{orderId}` | `AnomalyDetectionResult` | ❌ **裸类型** |
| `health` | `Get /api/v1/anomaly/health` | `String` | ❌ **裸类型** |
| `listOrders` | `Get /api/v1/anomaly/orders` | `List<AnomalyRecordVO>` | ❌ **裸类型** |
| `stats` | `Get /api/v1/anomaly/stats` | `AnomalyStats` | ❌ **裸类型** |
| `getOne` | `Get /api/v1/anomaly/{id}` | `AnomalyRecordVO` | ❌ **裸类型** |
| `confirm` | `Post /api/v1/anomaly/{id}/confirm` | `AnomalyRecordVO` | ❌ **裸类型** |

#### erp-customer-service — 32 个接口（其中裸类型 **19** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `createCustomer` | `Post /customer/create` | `Long` | ❌ **裸类型** |
| `pageCustomers` | `Get /customer/list` | `IPage<Customer>` | ❌ **裸类型** |
| `getStats` | `Get /customer/stats` | `CustomerService.CustomerStats` | ❌ **裸类型** |
| `getVipCustomers` | `Get /customer/vip` | `List<Customer>` | ❌ **裸类型** |
| `getCustomer` | `Get /customer/{id}` | `Customer` | ❌ **裸类型** |
| `updateCustomer` | `Put /customer/{id}` | `void` | — void |
| `deleteCustomer` | `Delete /customer/{id}` | `void` | — void |
| `create` | `Post /customer/follow/create` | `Long` | ❌ **裸类型** |
| `getByCustomerId` | `Get /customer/follow/customer/{customerId}` | `List<CustomerFollowRecord>` | ❌ **裸类型** |
| `count` | `Get /customer/follow/customer/{customerId}/count` | `long` | ❌ **裸类型** |
| `getLatest` | `Get /customer/follow/customer/{customerId}/latest` | `CustomerFollowRecord` | ❌ **裸类型** |
| `page` | `Get /customer/follow/list` | `IPage<CustomerFollowRecord>` | ❌ **裸类型** |
| `getTodayPending` | `Get /customer/follow/pending/today` | `List<CustomerFollowRecord>` | ❌ **裸类型** |
| `update` | `Put /customer/follow/{id}` | `void` | — void |
| `get` | `Get /customer/follow/{id}` | `CustomerFollowRecord` | ❌ **裸类型** |
| `delete` | `Delete /customer/follow/{id}` | `void` | — void |
| `complete` | `Post /customer/follow/{id}/complete` | `void` | — void |
| `create` | `Post /customer/group/create` | `Long` | ❌ **裸类型** |
| `list` | `Get /customer/group/list` | `List<CustomerGroup>` | ❌ **裸类型** |
| `update` | `Put /customer/group/{id}` | `void` | — void |
| `get` | `Get /customer/group/{id}` | `CustomerGroup` | ❌ **裸类型** |
| `delete` | `Delete /customer/group/{id}` | `void` | — void |
| `create` | `Post /customer/tag/create` | `Long` | ❌ **裸类型** |
| `getCustomerTags` | `Get /customer/tag/customer/{customerId}` | `List<CustomerTag>` | ❌ **裸类型** |
| `addTagToCustomer` | `Post /customer/tag/customer/{customerId}/add` | `void` | — void |
| `batchAddTags` | `Post /customer/tag/customer/{customerId}/batch-add` | `void` | — void |
| `clearCustomerTags` | `Delete /customer/tag/customer/{customerId}/clear` | `void` | — void |
| `removeTag` | `Delete /customer/tag/customer/{customerId}/tag/{tagId}` | `void` | — void |
| `list` | `Get /customer/tag/list` | `List<CustomerTag>` | ❌ **裸类型** |
| `update` | `Put /customer/tag/{id}` | `void` | — void |
| `get` | `Get /customer/tag/{id}` | `CustomerTag` | ❌ **裸类型** |
| `delete` | `Delete /customer/tag/{id}` | `void` | — void |

#### erp-finance-service — 25 个接口（其中裸类型 **5** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `createAccount` | `Post /finance/account` | `Result<FinanceAccountVO>` | ✅ Result |
| `updateAccount` | `Put /finance/account` | `Result<FinanceAccountVO>` | ✅ Result |
| `listAccounts` | `Get /finance/account/list` | `List<FinanceAccountVO>` | ❌ **裸类型** |
| `deleteAccount` | `Delete /finance/account/{id}` | `Result<Void>` | ✅ Result |
| `setDefaultAccount` | `Post /finance/account/{id}/default` | `Result<Void>` | ✅ Result |
| `createCategory` | `Post /finance/category` | `Result<FinanceCategoryVO>` | ✅ Result |
| `updateCategory` | `Put /finance/category` | `Result<FinanceCategoryVO>` | ✅ Result |
| `listCategories` | `Get /finance/category/list` | `List<FinanceCategoryVO>` | ❌ **裸类型** |
| `deleteCategory` | `Delete /finance/category/{id}` | `Result<Void>` | ✅ Result |
| `createFlow` | `Post /finance/flow` | `Result<FinanceFlowVO>` | ✅ Result |
| `updateFlow` | `Put /finance/flow` | `Result<FinanceFlowVO>` | ✅ Result |
| `batchRecordFinance` | `Post /finance/flow/batch` | `Result<BatchFinanceResult>` | ✅ Result |
| `flowCategory` | `Get /finance/flow/category` | `List<Map<String, Object>>` | ❌ **裸类型** |
| `listFlows` | `Get /finance/flow/list` | `Result<Page<FinanceFlowVO>>` | ✅ Result |
| `flowStatistics` | `Get /finance/flow/statistics` | `FinanceOverview` | ❌ **裸类型** |
| `flowTrend` | `Get /finance/flow/trend` | `List<Map<String, Object>>` | ❌ **裸类型** |
| `getFlow` | `Get /finance/flow/{id}` | `Result<FinanceFlowVO>` | ✅ Result |
| `deleteFlow` | `Delete /finance/flow/{id}` | `Result<Void>` | ✅ Result |
| `overview` | `Get /finance/overview` | `Result<FinanceOverview>` | ✅ Result |
| `reconcile` | `Get /finance/reconcile` | `Result<ReconciliationResult>` | ✅ Result |
| `accountStatistics` | `Get /finance/statistics/account` | `Result<List<AccountStatistics>>` | ✅ Result |
| `categoryStatistics` | `Get /finance/statistics/category` | `Result<List<CategoryStatistics>>` | ✅ Result |
| `dailyStatistics` | `Get /finance/statistics/daily` | `Result<FinanceDailyStatistics>` | ✅ Result |
| `monthlyStatistics` | `Get /finance/statistics/monthly` | `Result<List<FinanceMonthlyStatistics>>` | ✅ Result |
| `trend` | `Get /finance/statistics/trend` | `Result<FinanceTrend>` | ✅ Result |

#### erp-inventory-alert-service — 26 个接口（其中裸类型 **0** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `checkInventory` | `Post /inventory/alert/check` | `Result<Void>` | ✅ Result |
| `batchCheckInventory` | `Post /inventory/alert/check/batch` | `Result<Void>` | ✅ Result |
| `handleAlert` | `Post /inventory/alert/record/{id}/handle` | `Result<Void>` | ✅ Result |
| `ignoreAlert` | `Post /inventory/alert/record/{id}/ignore` | `Result<Void>` | ✅ Result |
| `queryAlerts` | `Get /inventory/alert/records` | `Result<IPage<InventoryAlert>>` | ✅ Result |
| `getPendingAlerts` | `Get /inventory/alert/records/pending` | `Result<List<InventoryAlert>>` | ✅ Result |
| `cancelReplenishment` | `Post /inventory/alert/replenishment/{id}/cancel` | `Result<Void>` | ✅ Result |
| `confirmReplenishment` | `Post /inventory/alert/replenishment/{id}/confirm` | `Result<Void>` | ✅ Result |
| `queryReplenishments` | `Get /inventory/alert/replenishments` | `Result<IPage<ReplenishmentSuggestion>>` | ✅ Result |
| `estimateTotalAmount` | `Get /inventory/alert/replenishments/estimate-amount` | `Result<java.math.BigDecimal>` | ✅ Result |
| `getPendingSuggestions` | `Get /inventory/alert/replenishments/pending` | `Result<List<ReplenishmentSuggestion>>` | ✅ Result |
| `createRule` | `Post /inventory/alert/rule` | `Result<Long>` | ✅ Result |
| `updateRule` | `Put /inventory/alert/rule/{id}` | `Result<Void>` | ✅ Result |
| `deleteRule` | `Delete /inventory/alert/rule/{id}` | `Result<Void>` | ✅ Result |
| `queryRules` | `Get /inventory/alert/rules` | `Result<IPage<InventoryAlertRule>>` | ✅ Result |
| `batchCreateRules` | `Post /inventory/alert/rules/batch` | `Result<Void>` | ✅ Result |
| `getEnabledRules` | `Get /inventory/alert/rules/enabled` | `Result<List<InventoryAlertRule>>` | ✅ Result |
| `getRulesByProduct` | `Get /inventory/alert/rules/product/{productId}` | `Result<List<InventoryAlertRule>>` | ✅ Result |
| `querySlowMoving` | `Get /inventory/alert/slow-moving` | `Result<IPage<SlowMovingInventory>>` | ✅ Result |
| `detectSlowMoving` | `Post /inventory/alert/slow-moving/detect` | `Result<SlowMovingInventory>` | ✅ Result |
| `getPendingSlowMovingAlerts` | `Get /inventory/alert/slow-moving/pending` | `Result<IPage<SlowMovingInventory>>` | ✅ Result |
| `markSlowMovingNotified` | `Post /inventory/alert/slow-moving/{id}/notified` | `Result<Void>` | ✅ Result |
| `getAlertTypeDistribution` | `Get /inventory/alert/statistics/distribution` | `Result<Map<String, Integer>>` | ✅ Result |
| `getProcessingEfficiency` | `Get /inventory/alert/statistics/efficiency` | `Result<Map<String, Object>>` | ✅ Result |
| `getRealTimeStatistics` | `Get /inventory/alert/statistics/realtime` | `Result<Map<String, Object>>` | ✅ Result |
| `getTrendData` | `Get /inventory/alert/statistics/trend` | `Result<List<Map<String, Object>>>` | ✅ Result |

#### erp-inventory-prediction-service — 16 个接口（其中裸类型 **16** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `getAccuracyTrend` | `Get /api/v1/inventory-prediction/accuracy-trend/{productId}` | `List<AccuracyEvaluation>` | ❌ **裸类型** |
| `batchEvaluateAccuracy` | `Post /api/v1/inventory-prediction/accuracy/batch` | `List<AccuracyEvaluation>` | ❌ **裸类型** |
| `evaluateAccuracy` | `Get /api/v1/inventory-prediction/accuracy/{productId}` | `AccuracyEvaluation` | ❌ **裸类型** |
| `batchPredictSales` | `Post /api/v1/inventory-prediction/batch-predict` | `List<PredictionResponse>` | ❌ **裸类型** |
| `health` | `Get /api/v1/inventory-prediction/health` | `String` | ❌ **裸类型** |
| `optimizeInventoryStructure` | `Post /api/v1/inventory-prediction/optimize` | `String` | ❌ **裸类型** |
| `predictSales` | `Post /api/v1/inventory-prediction/predict` | `PredictionResponse` | ❌ **裸类型** |
| `predictBySku` | `Get /api/v1/inventory-prediction/predict/{sku}` | `PredictionResponse` | ❌ **裸类型** |
| `getReplenishmentSuggestion` | `Get /api/v1/inventory-prediction/replenishment/{productId}` | `ReplenishmentSuggestion` | ❌ **裸类型** |
| `calculateSafetyStock` | `Get /api/v1/inventory-prediction/safety-stock/{productId}` | `int` | ❌ **裸类型** |
| `listSuggestions` | `Get /api/v1/inventory-prediction/suggestions` | `List<ReplenishmentSuggestionVO>` | ❌ **裸类型** |
| `tasks` | `Get /api/v1/inventory-prediction/tasks` | `ServiceStatistics` | ❌ **裸类型** |
| `turnover` | `Get /api/v1/inventory-prediction/turnover` | `List<TurnoverVO>` | ❌ **裸类型** |
| `calculateInventoryTurnover` | `Get /api/v1/inventory-prediction/turnover/{productId}` | `double` | ❌ **裸类型** |
| `urgentReplenishments` | `Get /api/v1/inventory-prediction/urgent` | `List<ReplenishmentSuggestion>` | ❌ **裸类型** |
| `warnLowStock` | `Get /api/v1/inventory-prediction/warn-low-stock` | `List<Long>` | ❌ **裸类型** |

#### erp-inventory-service — 12 个接口（其中裸类型 **2** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `adjust` | `Post /inventory/adjust` | `Result<Void>` | ✅ Result |
| `decrease` | `Post /inventory/decrease` | `Result<Void>` | ✅ Result |
| `export` | `Get /inventory/export` | `ResponseEntity<byte[]>` | ❌ **裸类型** |
| `importInventory` | `Post /inventory/import` | `Result<BatchOperationResult>` | ✅ Result |
| `increase` | `Post /inventory/increase` | `Result<Void>` | ✅ Result |
| `list` | `Get /inventory/list` | `Result<Page<Map<String, Object>>>` | ✅ Result |
| `statistics` | `Get /inventory/statistics` | `Result<InventoryStatistics>` | ✅ Result |
| `stats` | `Get /inventory/stats` | `Map<String, Object>` | ❌ **裸类型** |
| `transfer` | `Post /inventory/transfer` | `Result<Void>` | ✅ Result |
| `trend` | `Get /inventory/trend` | `Result<List<InventoryTrend>>` | ✅ Result |
| `detail` | `Get /inventory/{id}` | `Result<Map<String, Object>>` | ✅ Result |
| `flows` | `Get /inventory/{inventoryId}/flows` | `Result<Page<Map<String, Object>>>` | ✅ Result |

#### erp-logistics-service — 9 个接口（其中裸类型 **7** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `calculate` | `Post /logistics/calculate` | `List<ShippingRateResult>` | ❌ **裸类型** |
| `channels` | `Get /logistics/channels` | `List<LogisticsChannelVO>` | ❌ **裸类型** |
| `health` | `Get /logistics/health` | `String` | ❌ **裸类型** |
| `createRate` | `Post /logistics/rate` | `Long` | ❌ **裸类型** |
| `updateRate` | `Put /logistics/rate/{id}` | `void` | — void |
| `deleteRate` | `Delete /logistics/rate/{id}` | `void` | — void |
| `rates` | `Get /logistics/rates` | `PageVO<LogisticsRateVO>` | ❌ **裸类型** |
| `ratesCount` | `Get /logistics/rates/count` | `long` | ❌ **裸类型** |
| `recommend` | `Post /logistics/recommend` | `ShippingRateResult` | ❌ **裸类型** |

#### erp-order-service — 15 个接口（其中裸类型 **0** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `advancedQuery` | `Post /order/advanced/list` | `Result<PageResult<Order>>` | ✅ Result |
| `exportOrders` | `Post /order/batch/export` | `Result<OrderExportResponse>` | ✅ Result |
| `batchMarkShipped` | `Post /order/batch/mark-shipped` | `Result<Integer>` | ✅ Result |
| `batchUpdateStatus` | `Post /order/batch/update-status` | `Result<Integer>` | ✅ Result |
| `createOrder` | `Post /order/create` | `Result<Long>` | ✅ Result |
| `pageOrders` | `Get /order/list` | `Result<PageResult<Order>>` | ✅ Result |
| `getOrderByPlatformOrderNo` | `Get /order/platform/{platform}/{platformOrderNo}` | `Result<Order>` | ✅ Result |
| `countByCountry` | `Post /order/statistics/by-country` | `Result<Map<String, Long>>` | ✅ Result |
| `sumAmountByPlatform` | `Post /order/statistics/by-platform` | `Result<Map<String, BigDecimal>>` | ✅ Result |
| `getOrder` | `Get /order/{orderId}` | `Result<Order>` | ✅ Result |
| `getOrderItems` | `Get /order/{orderId}/items` | `Result<List<OrderItem>>` | ✅ Result |
| `updateShippingInfo` | `Put /order/{orderId}/shipping` | `Result<Void>` | ✅ Result |
| `updateOrderStatus` | `Put /order/{orderId}/status` | `Result<Void>` | ✅ Result |
| `getTodayStatistics` | `Get /order/statistics/today` | `Result<Map<String, Object>>` | ✅ Result |
| `getSalesTrend` | `Get /order/statistics/trend` | `Result<List<Map<String, Object>>>` | ✅ Result |

#### erp-platform-service — 10 个接口（其中裸类型 **0** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `syncOrders` | `Post /order-sync/orders` | `Result<Void>` | ✅ Result |
| `saveConfig` | `Post /platform/config` | `Result<PlatformConfig>` | ✅ Result |
| `getConfigList` | `Get /platform/config/list` | `Result<List<PlatformConfig>>` | ✅ Result |
| `getConfig` | `Get /platform/config/{id}` | `Result<PlatformConfig>` | ✅ Result |
| `deleteConfig` | `Delete /platform/config/{id}` | `Result<Void>` | ✅ Result |
| `updateStatus` | `Put /platform/config/{id}/status` | `Result<Void>` | ✅ Result |
| `testConnection` | `Post /platform/config/{id}/test` | `Result<Boolean>` | ✅ Result |
| `syncOrders` | `Post /platform/sync/orders` | `Result<Void>` | ✅ Result |
| `getSupportedPlatforms` | `Get /platform/sync/platforms` | `Result<List<PlatformType>>` | ✅ Result |
| `testConnection` | `Post /platform/sync/test` | `Result<Boolean>` | ✅ Result |

#### erp-pricing-service — 21 个接口（其中裸类型 **21** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `addCompetitor` | `Post /api/v1/competitor/add/{productId}` | `String` | ❌ **裸类型** |
| `getCompetitorProducts` | `Get /api/v1/competitor/list/{productId}` | `List<CompetitorProduct>` | ❌ **裸类型** |
| `batchScrape` | `Post /api/v1/competitor/scrape/batch` | `String` | ❌ **裸类型** |
| `scrapeFromPlatform` | `Post /api/v1/competitor/scrape/platform` | `String` | ❌ **裸类型** |
| `scrapeCompetitorProducts` | `Post /api/v1/competitor/scrape/{productId}` | `String` | ❌ **裸类型** |
| `getCompetitorPriceStats` | `Get /api/v1/competitor/stats/{productId}` | `CompetitorScrapeService.CompetitorPriceStats` | ❌ **裸类型** |
| `health` | `Get /api/v1/pricing/health` | `Map<String, Object>` | ❌ **裸类型** |
| `info` | `Get /api/v1/pricing/info` | `Map<String, Object>` | ❌ **裸类型** |
| `getPriceHistory` | `Get /api/v1/price-history/list/{productId}` | `List<PriceHistory>` | ❌ **裸类型** |
| `getPriceHistory` | `Get /api/v1/price-history/range/{productId}` | `List<PriceHistory>` | ❌ **裸类型** |
| `getRecentPriceChanges` | `Get /api/v1/price-history/recent/{productId}` | `List<PriceHistory>` | ❌ **裸类型** |
| `getPriceTrendAnalysis` | `Get /api/v1/price-history/trend/{productId}` | `PriceHistoryService.PriceTrendAnalysis` | ❌ **裸类型** |
| `adjustPriceByCompetitors` | `Post /api/v1/pricing/adjust-by-competitors/{productId}` | `PricingResponse` | ❌ **裸类型** |
| `apply` | `Post /api/v1/pricing/apply/{sku}` | `PricingSuggestionVO` | ❌ **裸类型** |
| `batchCalculateOptimalPrice` | `Post /api/v1/pricing/batch-calculate` | `List<PricingResponse>` | ❌ **裸类型** |
| `calculateOptimalPrice` | `Post /api/v1/pricing/calculate` | `PricingResponse` | ❌ **裸类型** |
| `history` | `Get /api/v1/pricing/history/{sku}` | `List<PricingSuggestionVO>` | ❌ **裸类型** |
| `getProductPricingInfo` | `Get /api/v1/pricing/info/{productId}` | `PricingResponse` | ❌ **裸类型** |
| `manualPriceAdjustment` | `Post /api/v1/pricing/manual-adjust` | `PricingResponse` | ❌ **裸类型** |
| `suggest` | `Get /api/v1/pricing/suggest/{sku}` | `PricingSuggestionVO` | ❌ **裸类型** |
| `listTasks` | `Get /api/v1/pricing/tasks` | `List<PricingSuggestionVO>` | ❌ **裸类型** |

#### erp-product-description-service — 13 个接口（其中裸类型 **13** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `generateABTestVersions` | `Post /api/v1/product-description/ab-test` | `List<DescriptionGenerationResponse>` | ❌ **裸类型** |
| `batchGenerateDescription` | `Post /api/v1/product-description/batch-generate` | `List<DescriptionGenerationResponse>` | ❌ **裸类型** |
| `batchImportProducts` | `Post /api/v1/product-description/batch-import` | `String` | ❌ **裸类型** |
| `generateDescription` | `Post /api/v1/product-description/generate` | `FrontendDescriptionResponse` | ❌ **裸类型** |
| `generateDescriptionInternal` | `Post /api/v1/product-description/generate/internal` | `DescriptionGenerationResponse` | ❌ **裸类型** |
| `health` | `Get /api/v1/product-description/health` | `String` | ❌ **裸类型** |
| `extractHighlights` | `Get /api/v1/product-description/highlights/{productId}` | `List<DescriptionGenerationResponse.Highlight>` | ❌ **裸类型** |
| `history` | `Get /api/v1/product-description/history` | `List<DescriptionGenerationResponse>` | ❌ **裸类型** |
| `getProductDescriptions` | `Get /api/v1/product-description/list/{productId}` | `List<DescriptionGenerationResponse>` | ❌ **裸类型** |
| `generateMultiLanguageDescription` | `Post /api/v1/product-description/multi-language` | `List<DescriptionGenerationResponse>` | ❌ **裸类型** |
| `saveDescription` | `Post /api/v1/product-description/save` | `String` | ❌ **裸类型** |
| `optimizeSEO` | `Post /api/v1/product-description/seo-optimize` | `DescriptionGenerationResponse.SEOResult` | ❌ **裸类型** |
| `templates` | `Get /api/v1/product-description/templates` | `List<Map<String, Object>>` | ❌ **裸类型** |

#### erp-product-recommendation-service — 12 个接口（其中裸类型 **12** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `submitFeedback` | `Post /api/v1/feedback` | `ResponseEntity<RecommendFeedback>` | ❌ **裸类型** |
| `listFeedback` | `Get /api/v1/feedback` | `ResponseEntity<List<RecommendFeedback>>` | ❌ **裸类型** |
| `stats` | `Get /api/v1/feedback/stats` | `ResponseEntity<FeedbackStats>` | ❌ **裸类型** |
| `tuneWeights` | `Post /api/v1/feedback/tune` | `ResponseEntity<WeightTuneResult>` | ❌ **裸类型** |
| `currentWeights` | `Get /api/v1/feedback/weights` | `ResponseEntity<WeightTuneResult.ScoringWeights>` | ❌ **裸类型** |
| `health` | `Get /api/v1/recommend/health` | `ResponseEntity<Map<String, Object>>` | ❌ **裸类型** |
| `savedResults` | `Get /api/v1/recommend/results` | `ResponseEntity<List<RecommendResult>>` | ❌ **裸类型** |
| `updateAdoption` | `Put /api/v1/recommend/results/{id}/adoption` | `ResponseEntity<RecommendResult>` | ❌ **裸类型** |
| `recommend` | `Post /api/v1/recommend/run` | `ResponseEntity<RecommendResponse>` | ❌ **裸类型** |
| `list` | `Get /api/v1/recommend` | `List<RecommendVO>` | ❌ **裸类型** |
| `refresh` | `Post /api/v1/recommend/refresh` | `Map<String, Object>` | ❌ **裸类型** |
| `detail` | `Get /api/v1/recommend/{sku}` | `RecommendVO` | ❌ **裸类型** |

#### erp-product-service — 8 个接口（其中裸类型 **0** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `saveProduct` | `Post /product` | `Result<Product>` | ✅ Result |
| `updateProduct` | `Put /product` | `Result<Product>` | ✅ Result |
| `batchOffShelf` | `Post /product/batch/off-shelf` | `Result<Integer>` | ✅ Result |
| `batchOnShelf` | `Post /product/batch/on-shelf` | `Result<Integer>` | ✅ Result |
| `listProducts` | `Get /product/list` | `Result<PageResult<Product>>` | ✅ Result |
| `getProductBySku` | `Get /product/sku/{sku}` | `Result<Product>` | ✅ Result |
| `getProduct` | `Get /product/{id}` | `Result<Product>` | ✅ Result |
| `deleteProduct` | `Delete /product/{id}` | `Result<Void>` | ✅ Result |

#### erp-purchase-service — 12 个接口（其中裸类型 **0** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `compareSuppliers` | `Get /purchase/compare` | `Result<SupplierCompareDTO>` | ✅ Result |
| `predictPurchase` | `Post /purchase/forecast` | `Result<PurchaseForecast>` | ✅ Result |
| `batchPredict` | `Post /purchase/forecast/batch` | `Result<List<PurchaseForecast>>` | ✅ Result |
| `queryForecasts` | `Get /purchase/forecasts` | `Result<IPage<PurchaseForecast>>` | ✅ Result |
| `generatePlan` | `Post /purchase/plan` | `Result<PurchasePlan>` | ✅ Result |
| `cancelPlan` | `Post /purchase/plan/{id}/cancel` | `Result<Void>` | ✅ Result |
| `confirmPlan` | `Post /purchase/plan/{id}/confirm` | `Result<Void>` | ✅ Result |
| `queryPlans` | `Get /purchase/plans` | `Result<IPage<PurchasePlan>>` | ✅ Result |
| `batchGeneratePlans` | `Post /purchase/plans/batch` | `Result<List<PurchasePlan>>` | ✅ Result |
| `getPendingPlans` | `Get /purchase/plans/pending` | `Result<List<PurchasePlan>>` | ✅ Result |
| `addQuote` | `Post /purchase/quotes` | `Result<SupplierQuote>` | ✅ Result |
| `getQuotes` | `Get /purchase/quotes/{productId}` | `Result<List<SupplierQuote>>` | ✅ Result |

#### erp-reporting-service — 14 个接口（其中裸类型 **0** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `queryFinanceReports` | `Get /report/finance` | `Result<IPage<FinanceReport>>` | ✅ Result |
| `generateDailyFinanceReport` | `Post /report/finance/daily` | `Result<FinanceReport>` | ✅ Result |
| `exportFinanceReport` | `Get /report/finance/export` | `Result<byte[]>` | ✅ Result |
| `generateReport` | `Post /report/generate` | `Result<ReportListItemDTO>` | ✅ Result |
| `queryInventoryReports` | `Get /report/inventory` | `Result<IPage<InventoryReport>>` | ✅ Result |
| `generateDailyInventoryReport` | `Post /report/inventory/daily` | `Result<InventoryReport>` | ✅ Result |
| `exportInventoryReport` | `Get /report/inventory/export` | `Result<byte[]>` | ✅ Result |
| `listReports` | `Get /report/list` | `Result<List<ReportListItemDTO>>` | ✅ Result |
| `querySalesReports` | `Get /report/sales` | `Result<IPage<SalesReport>>` | ✅ Result |
| `generateDailySalesReport` | `Post /report/sales/daily` | `Result<SalesReport>` | ✅ Result |
| `exportSalesReport` | `Get /report/sales/export` | `Result<byte[]>` | ✅ Result |
| `generateMonthlySalesReport` | `Post /report/sales/monthly` | `Result<SalesReport>` | ✅ Result |
| `generateWeeklySalesReport` | `Post /report/sales/weekly` | `Result<SalesReport>` | ✅ Result |
| `templates` | `Get /report/templates` | `Result<List<Map<String, Object>>>` | ✅ Result |

#### erp-social-commerce-service — 56 个接口（其中裸类型 **56** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `findAll` | `Get /api/v1/cooperations` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `create` | `Post /api/v1/cooperations` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `findCompleted` | `Get /api/v1/cooperations/completed` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findByDateRange` | `Get /api/v1/cooperations/date-range` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findByInfluencerId` | `Get /api/v1/cooperations/influencer/{influencerId}` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findByProductId` | `Get /api/v1/cooperations/product/{productId}` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findByStatus` | `Get /api/v1/cooperations/status/{status}` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findByType` | `Get /api/v1/cooperations/type/{type}` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findUpcoming` | `Get /api/v1/cooperations/upcoming` | `ResponseEntity<List<CooperationDTO>>` | ❌ **裸类型** |
| `findById` | `Get /api/v1/cooperations/{id}` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `update` | `Put /api/v1/cooperations/{id}` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `delete` | `Delete /api/v1/cooperations/{id}` | `ResponseEntity<Void>` | ❌ **裸类型** |
| `cancel` | `Post /api/v1/cooperations/{id}/cancel` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `complete` | `Post /api/v1/cooperations/{id}/complete` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `confirm` | `Post /api/v1/cooperations/{id}/confirm` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `start` | `Post /api/v1/cooperations/{id}/start` | `ResponseEntity<CooperationDTO>` | ❌ **裸类型** |
| `findAll` | `Get /api/v1/douyin-shop` | `ResponseEntity<List<DouyinShopConfig>>` | ❌ **裸类型** |
| `create` | `Post /api/v1/douyin-shop` | `ResponseEntity<DouyinShopConfig>` | ❌ **裸类型** |
| `findByShopId` | `Get /api/v1/douyin-shop/shop/{shopId}` | `ResponseEntity<DouyinShopConfig>` | ❌ **裸类型** |
| `syncAll` | `Post /api/v1/douyin-shop/sync` | `ResponseEntity<Map<String, Object>>` | ❌ **裸类型** |
| `findById` | `Get /api/v1/douyin-shop/{id}` | `ResponseEntity<DouyinShopConfig>` | ❌ **裸类型** |
| `update` | `Put /api/v1/douyin-shop/{id}` | `ResponseEntity<DouyinShopConfig>` | ❌ **裸类型** |
| `delete` | `Delete /api/v1/douyin-shop/{id}` | `ResponseEntity<Void>` | ❌ **裸类型** |
| `refreshToken` | `Post /api/v1/douyin-shop/{id}/refresh-token` | `ResponseEntity<DouyinShopConfig>` | ❌ **裸类型** |
| `findAll` | `Get /api/v1/influencers` | `ResponseEntity<List<InfluencerDTO>>` | ❌ **裸类型** |
| `create` | `Post /api/v1/influencers` | `ResponseEntity<InfluencerDTO>` | ❌ **裸类型** |
| `findByCategory` | `Get /api/v1/influencers/category` | `ResponseEntity<List<InfluencerDTO>>` | ❌ **裸类型** |
| `findByLevel` | `Get /api/v1/influencers/level/{level}` | `ResponseEntity<List<InfluencerDTO>>` | ❌ **裸类型** |
| `findByPlatform` | `Get /api/v1/influencers/platform/{platform}` | `ResponseEntity<List<InfluencerDTO>>` | ❌ **裸类型** |
| `findByStatus` | `Get /api/v1/influencers/status/{status}` | `ResponseEntity<List<InfluencerDTO>>` | ❌ **裸类型** |
| `findTopInfluencers` | `Get /api/v1/influencers/top` | `ResponseEntity<List<InfluencerDTO>>` | ❌ **裸类型** |
| `findById` | `Get /api/v1/influencers/{id}` | `ResponseEntity<InfluencerDTO>` | ❌ **裸类型** |
| `update` | `Put /api/v1/influencers/{id}` | `ResponseEntity<InfluencerDTO>` | ❌ **裸类型** |
| `delete` | `Delete /api/v1/influencers/{id}` | `ResponseEntity<Void>` | ❌ **裸类型** |
| `findAll` | `Get /api/v1/live-streams` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `create` | `Post /api/v1/live-streams` | `ResponseEntity<LiveStreamDTO>` | ❌ **裸类型** |
| `findCurrentLive` | `Get /api/v1/live-streams/current-live` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `findByDateRange` | `Get /api/v1/live-streams/date-range` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `findByInfluencerId` | `Get /api/v1/live-streams/influencer/{influencerId}` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `findByPlatform` | `Get /api/v1/live-streams/platform/{platform}` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `findRecent` | `Get /api/v1/live-streams/recent` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `findByStatus` | `Get /api/v1/live-streams/status/{status}` | `ResponseEntity<List<LiveStreamDTO>>` | ❌ **裸类型** |
| `findByStreamId` | `Get /api/v1/live-streams/stream/{streamId}` | `ResponseEntity<LiveStreamDTO>` | ❌ **裸类型** |
| `findById` | `Get /api/v1/live-streams/{id}` | `ResponseEntity<LiveStreamDTO>` | ❌ **裸类型** |
| `update` | `Put /api/v1/live-streams/{id}` | `ResponseEntity<LiveStreamDTO>` | ❌ **裸类型** |
| `delete` | `Delete /api/v1/live-streams/{id}` | `ResponseEntity<Void>` | ❌ **裸类型** |
| `endLive` | `Post /api/v1/live-streams/{id}/end` | `ResponseEntity<LiveStreamDTO>` | ❌ **裸类型** |
| `startLive` | `Post /api/v1/live-streams/{id}/start` | `ResponseEntity<LiveStreamDTO>` | ❌ **裸类型** |
| `findAll` | `Get /api/v1/tiktok-shop` | `ResponseEntity<List<TikTokShopConfigDTO>>` | ❌ **裸类型** |
| `create` | `Post /api/v1/tiktok-shop` | `ResponseEntity<TikTokShopConfigDTO>` | ❌ **裸类型** |
| `findByShopId` | `Get /api/v1/tiktok-shop/shop/{shopId}` | `ResponseEntity<TikTokShopConfigDTO>` | ❌ **裸类型** |
| `syncAll` | `Post /api/v1/tiktok-shop/sync` | `ResponseEntity<Map<String, Object>>` | ❌ **裸类型** |
| `findById` | `Get /api/v1/tiktok-shop/{id}` | `ResponseEntity<TikTokShopConfigDTO>` | ❌ **裸类型** |
| `update` | `Put /api/v1/tiktok-shop/{id}` | `ResponseEntity<TikTokShopConfigDTO>` | ❌ **裸类型** |
| `delete` | `Delete /api/v1/tiktok-shop/{id}` | `ResponseEntity<Void>` | ❌ **裸类型** |
| `refreshToken` | `Post /api/v1/tiktok-shop/{id}/refresh-token` | `ResponseEntity<TikTokShopConfigDTO>` | ❌ **裸类型** |

#### erp-supplier-service — 37 个接口（其中裸类型 **21** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `create` | `Post /supplier/purchase/create` | `Long` | ❌ **裸类型** |
| `getInTransit` | `Get /supplier/purchase/in-transit` | `List<PurchaseOrder>` | ❌ **裸类型** |
| `deleteItem` | `Delete /supplier/purchase/item/{itemId}` | `void` | — void |
| `page` | `Get /supplier/purchase/list` | `IPage<PurchaseOrder>` | ❌ **裸类型** |
| `getStats` | `Get /supplier/purchase/stats` | `PurchaseOrderService.OrderStats` | ❌ **裸类型** |
| `getBySupplierId` | `Get /supplier/purchase/supplier/{supplierId}` | `List<PurchaseOrder>` | ❌ **裸类型** |
| `get` | `Get /supplier/purchase/{id}` | `PurchaseOrder` | ❌ **裸类型** |
| `update` | `Put /supplier/purchase/{id}` | `void` | — void |
| `delete` | `Delete /supplier/purchase/{id}` | `void` | — void |
| `approve` | `Post /supplier/purchase/{id}/approve` | `void` | — void |
| `cancel` | `Post /supplier/purchase/{id}/cancel` | `void` | — void |
| `addItem` | `Post /supplier/purchase/{id}/item` | `void` | — void |
| `getItems` | `Get /supplier/purchase/{id}/items` | `List<PurchaseOrderItem>` | ❌ **裸类型** |
| `payment` | `Post /supplier/purchase/{id}/payment` | `void` | — void |
| `markPurchasing` | `Post /supplier/purchase/{id}/purchasing` | `void` | — void |
| `markReceived` | `Post /supplier/purchase/{id}/received` | `void` | — void |
| `updateStatus` | `Put /supplier/purchase/{id}/status` | `void` | — void |
| `list` | `Get /purchase/orders` | `List<PurchaseOrderVO>` | ❌ **裸类型** |
| `create` | `Post /purchase/orders` | `Long` | ❌ **裸类型** |
| `detail` | `Get /purchase/orders/{id}` | `PurchaseOrderVO` | ❌ **裸类型** |
| `approve` | `Post /purchase/orders/{id}/approve` | `void` | — void |
| `cancel` | `Post /purchase/orders/{id}/cancel` | `void` | — void |
| `receive` | `Post /purchase/orders/{id}/receive` | `void` | — void |
| `suppliers` | `Get /purchase/suppliers` | `List<Map<String, Object>>` | ❌ **裸类型** |
| `createSupplier` | `Post /supplier/create` | `Long` | ❌ **裸类型** |
| `pageSuppliers` | `Get /supplier/list` | `IPage<Supplier>` | ❌ **裸类型** |
| `getStats` | `Get /supplier/stats` | `SupplierService.SupplierStats` | ❌ **裸类型** |
| `getTopSuppliers` | `Get /supplier/top` | `List<Supplier>` | ❌ **裸类型** |
| `getSupplier` | `Get /supplier/{id}` | `Supplier` | ❌ **裸类型** |
| `updateSupplier` | `Put /supplier/{id}` | `void` | — void |
| `deleteSupplier` | `Delete /supplier/{id}` | `void` | — void |
| `create` | `Post /supplier/evaluation/create` | `Long` | ❌ **裸类型** |
| `list` | `Get /supplier/evaluation/list` | `List<SupplierEvaluation>` | ❌ **裸类型** |
| `getBySupplierId` | `Get /supplier/evaluation/supplier/{supplierId}` | `List<SupplierEvaluation>` | ❌ **裸类型** |
| `getLatest` | `Get /supplier/evaluation/supplier/{supplierId}/latest` | `SupplierEvaluation` | ❌ **裸类型** |
| `get` | `Get /supplier/evaluation/{id}` | `SupplierEvaluation` | ❌ **裸类型** |
| `delete` | `Delete /supplier/evaluation/{id}` | `void` | — void |

#### erp-tenant-service — 32 个接口（其中裸类型 **32** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `login` | `Post /api/v1/auth/login` | `ResponseEntity<AuthResponse>` | ❌ **裸类型** |
| `getCurrentUser` | `Get /api/v1/auth/me` | `ResponseEntity<TenantUserDTO>` | ❌ **裸类型** |
| `register` | `Post /api/v1/auth/register` | `ResponseEntity<AuthResponse>` | ❌ **裸类型** |
| `getCredentials` | `Get /api/v1/platforms/credentials` | `ResponseEntity<List<PlatformCredentialDTO>>` | ❌ **裸类型** |
| `addCredential` | `Post /api/v1/platforms/credentials` | `ResponseEntity<PlatformCredentialDTO>` | ❌ **裸类型** |
| `updateCredential` | `Put /api/v1/platforms/credentials/{id}` | `ResponseEntity<PlatformCredentialDTO>` | ❌ **裸类型** |
| `deleteCredential` | `Delete /api/v1/platforms/credentials/{id}` | `ResponseEntity<Void>` | ❌ **裸类型** |
| `getCredentialsByPlatform` | `Get /api/v1/platforms/credentials/{platform}` | `ResponseEntity<List<PlatformCredentialDTO>>` | ❌ **裸类型** |
| `getTenant` | `Get /api/v1/tenant` | `ResponseEntity<TenantDTO>` | ❌ **裸类型** |
| `upgradePlan` | `Post /api/v1/tenant/upgrade` | `ResponseEntity<TenantDTO>` | ❌ **裸类型** |
| `getUsers` | `Get /api/v1/tenant/users` | `ResponseEntity<List<TenantUserDTO>>` | ❌ **裸类型** |
| `list` | `Get /api/tenant/list` | `ResponseEntity<List<TenantDTO>>` | ❌ **裸类型** |
| `getBills` | `Get /api/tenant/billing/bills` | `ResponseEntity<List<BillDTO>>` | ❌ **裸类型** |
| `getCurrentBill` | `Get /api/tenant/billing/bills/current` | `ResponseEntity<BillDTO>` | ❌ **裸类型** |
| `getPendingBills` | `Get /api/tenant/billing/bills/pending` | `ResponseEntity<List<BillDTO>>` | ❌ **裸类型** |
| `payBill` | `Post /api/tenant/billing/bills/{billId}/pay` | `ResponseEntity<BillDTO>` | ❌ **裸类型** |
| `getBillingRules` | `Get /api/tenant/billing/rules` | `ResponseEntity<List<BillingRuleDTO>>` | ❌ **裸类型** |
| `createBillingRule` | `Post /api/tenant/billing/rules` | `ResponseEntity<BillingRuleDTO>` | ❌ **裸类型** |
| `getDashboard` | `Get /api/tenant/dashboard` | `ResponseEntity<DashboardDTO>` | ❌ **裸类型** |
| `getQuotaOverview` | `Get /api/tenant/dashboard/quota` | `ResponseEntity<Map<String, Object>>` | ❌ **裸类型** |
| `getUsageTrend` | `Get /api/tenant/dashboard/trend` | `ResponseEntity<List<DailyUsageDTO>>` | ❌ **裸类型** |
| `getFeatureFlags` | `Get /api/tenant/config/features` | `ResponseEntity<List<FeatureFlagDTO>>` | ❌ **裸类型** |
| `updateFeatureFlag` | `Put /api/tenant/config/features/{featureKey}` | `ResponseEntity<FeatureFlagDTO>` | ❌ **裸类型** |
| `isFeatureEnabled` | `Get /api/tenant/config/features/{featureKey}/enabled` | `ResponseEntity<Map<String, Boolean>>` | ❌ **裸类型** |
| `getCurrentPlan` | `Get /api/tenant/config/plan` | `ResponseEntity<TenantDTO>` | ❌ **裸类型** |
| `changePlan` | `Post /api/tenant/config/plan/change` | `ResponseEntity<TenantDTO>` | ❌ **裸类型** |
| `getAvailablePlans` | `Get /api/tenant/config/plans` | `ResponseEntity<List<Map<String, Object>>>` | ❌ **裸类型** |
| `getCurrentMonthUsage` | `Get /api/tenant/usage/current` | `ResponseEntity<UsageStatisticsDTO>` | ❌ **裸类型** |
| `getUsageByPeriod` | `Get /api/tenant/usage/period` | `ResponseEntity<UsageStatisticsDTO>` | ❌ **裸类型** |
| `recordAiCall` | `Post /api/tenant/usage/record/ai` | `ResponseEntity<Map<String, String>>` | ❌ **裸类型** |
| `recordApiCall` | `Post /api/tenant/usage/record/api` | `ResponseEntity<Map<String, String>>` | ❌ **裸类型** |
| `recordStorage` | `Post /api/tenant/usage/record/storage` | `ResponseEntity<Map<String, String>>` | ❌ **裸类型** |

#### erp-user-service — 26 个接口（其中裸类型 **12** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `login` | `Post /auth/login` | `Result<LoginResponse>` | ✅ Result |
| `logout` | `Post /auth/logout` | `Result<Void>` | ✅ Result |
| `refresh` | `Post /auth/refresh` | `Result<LoginResponse>` | ✅ Result |
| `register` | `Post /auth/register` | `Result<Void>` | ✅ Result |
| `create` | `Post /department` | `DepartmentVO` | ❌ **裸类型** |
| `update` | `Put /department` | `DepartmentVO` | ❌ **裸类型** |
| `list` | `Get /department/list` | `List<DepartmentVO>` | ❌ **裸类型** |
| `tree` | `Get /department/tree` | `List<DepartmentVO>` | ❌ **裸类型** |
| `delete` | `Delete /department/{id}` | `void` | — void |
| `tree` | `Get /permission/tree` | `List<PermissionVO>` | ❌ **裸类型** |
| `create` | `Post /role` | `RoleVO` | ❌ **裸类型** |
| `update` | `Put /role` | `RoleVO` | ❌ **裸类型** |
| `list` | `Get /role/list` | `Object` | ❌ **裸类型** |
| `detail` | `Get /role/{id}` | `RoleVO` | ❌ **裸类型** |
| `delete` | `Delete /role/{id}` | `void` | — void |
| `assignPermissions` | `Post /role/{roleId}/permissions` | `void` | — void |
| `create` | `Post /user` | `UserVO` | ❌ **裸类型** |
| `update` | `Put /user` | `UserVO` | ❌ **裸类型** |
| `changePassword` | `Post /user/change-password` | `void` | — void |
| `list` | `Get /user/list` | `Result<PageResult<UserVO>>` | ✅ Result |
| `detail` | `Get /user/{id}` | `UserVO` | ❌ **裸类型** |
| `delete` | `Delete /user/{id}` | `void` | — void |
| `resetPassword` | `Post /user/{id}/reset-password` | `void` | — void |
| `updateStatus` | `Put /user/{id}/status` | `void` | — void |
| `assignDepartments` | `Post /user/{userId}/departments` | `void` | — void |
| `assignRoles` | `Post /user/{userId}/roles` | `void` | — void |

#### erp-warehouse-service — 63 个接口（其中裸类型 **2** 个）

| 方法 | HTTP路径 | 返回类型 | 包Result? |
|---|---|---|---|
| `createInboundOrder` | `Post /warehouse/inbound/orders` | `Result<Long>` | ✅ Result |
| `listInboundOrders` | `Get /warehouse/inbound/orders/page` | `Result<Page<InboundOrder>>` | ✅ Result |
| `getInboundOrder` | `Get /warehouse/inbound/orders/{orderId}` | `Result<InboundOrder>` | ✅ Result |
| `cancelInbound` | `Post /warehouse/inbound/orders/{orderId}/cancel` | `Result<Void>` | ✅ Result |
| `confirmInbound` | `Post /warehouse/inbound/orders/{orderId}/confirm` | `Result<Void>` | ✅ Result |
| `getInboundDetails` | `Get /warehouse/inbound/orders/{orderId}/details` | `Result<List<InboundDetail>>` | ✅ Result |
| `partialInbound` | `Post /warehouse/inbound/orders/{orderId}/partial` | `Result<Void>` | ✅ Result |
| `createOutboundOrder` | `Post /warehouse/outbound/orders` | `Result<Long>` | ✅ Result |
| `listOutboundOrders` | `Get /warehouse/outbound/orders/page` | `Result<Page<OutboundOrder>>` | ✅ Result |
| `getOutboundOrder` | `Get /warehouse/outbound/orders/{orderId}` | `Result<OutboundOrder>` | ✅ Result |
| `cancelOutbound` | `Post /warehouse/outbound/orders/{orderId}/cancel` | `Result<Void>` | ✅ Result |
| `confirmOutbound` | `Post /warehouse/outbound/orders/{orderId}/confirm` | `Result<Void>` | ✅ Result |
| `getOutboundDetails` | `Get /warehouse/outbound/orders/{orderId}/details` | `Result<List<OutboundDetail>>` | ✅ Result |
| `createInventoryBatch` | `Post /warehouse/inventory/batches` | `Result<Long>` | ✅ Result |
| `getExpiringBatches` | `Get /warehouse/inventory/batches/expiring` | `Result<List<InventoryBatch>>` | ✅ Result |
| `listInventoryBatches` | `Get /warehouse/inventory/batches/page` | `Result<Page<InventoryBatch>>` | ✅ Result |
| `getInventoryBatch` | `Get /warehouse/inventory/batches/{batchId}` | `Result<InventoryBatch>` | ✅ Result |
| `freezeBatch` | `Post /warehouse/inventory/batches/{batchId}/freeze` | `Result<Void>` | ✅ Result |
| `updateBatchQuantity` | `Put /warehouse/inventory/batches/{batchId}/quantity` | `Result<Void>` | ✅ Result |
| `unfreezeBatch` | `Post /warehouse/inventory/batches/{batchId}/unfreeze` | `Result<Void>` | ✅ Result |
| `createInventoryCheck` | `Post /warehouse/inventory/checks` | `Result<Void>` | ✅ Result |
| `confirmInventoryCheck` | `Post /warehouse/inventory/checks/{checkId}/confirm` | `Result<Void>` | ✅ Result |
| `reportDamage` | `Post /warehouse/inventory/damage` | `Result<Void>` | ✅ Result |
| `freezeInventory` | `Post /warehouse/inventory/freeze` | `Result<Void>` | ✅ Result |
| `relocate` | `Post /warehouse/inventory/relocate` | `Result<Void>` | ✅ Result |
| `unfreezeInventory` | `Post /warehouse/inventory/unfreeze` | `Result<Void>` | ✅ Result |
| `createTransfer` | `Post /warehouse/stock-transfer` | `Result<Long>` | ✅ Result |
| `listTransfers` | `Get /warehouse/stock-transfer/page` | `Result<Page<StockTransfer>>` | ✅ Result |
| `getTransfer` | `Get /warehouse/stock-transfer/{transferId}` | `Result<StockTransfer>` | ✅ Result |
| `cancelTransfer` | `Post /warehouse/stock-transfer/{transferId}/cancel` | `Result<Void>` | ✅ Result |
| `executeTransfer` | `Post /warehouse/stock-transfer/{transferId}/execute` | `Result<Void>` | ✅ Result |
| `confirmTransferReceive` | `Post /warehouse/stock-transfer/{transferId}/receive` | `Result<Void>` | ✅ Result |
| `createWarehouse` | `Post /warehouse` | `Result<WarehouseVO>` | ✅ Result |
| `updateWarehouse` | `Put /warehouse` | `Result<WarehouseVO>` | ✅ Result |
| `listAllWarehouses` | `Get /warehouse/all` | `List<WarehouseVO>` | ❌ **裸类型** |
| `listWarehouses` | `Get /warehouse/list` | `Result<Page<WarehouseVO>>` | ✅ Result |
| `createLocation` | `Post /warehouse/location` | `Result<LocationVO>` | ✅ Result |
| `updateLocation` | `Put /warehouse/location` | `Result<LocationVO>` | ✅ Result |
| `getLocation` | `Get /warehouse/location/{locationId}` | `Result<LocationVO>` | ✅ Result |
| `deleteLocation` | `Delete /warehouse/location/{locationId}` | `Result<Void>` | ✅ Result |
| `getAvailableLocations` | `Get /warehouse/locations/available` | `Result<List<LocationVO>>` | ✅ Result |
| `batchCreateLocations` | `Post /warehouse/locations/batch` | `Result<Void>` | ✅ Result |
| `listLocations` | `Get /warehouse/locations/page` | `Result<Page<LocationVO>>` | ✅ Result |
| `getWarehouseDashboard` | `Get /warehouse/warehouses/{warehouseId}/dashboard` | `Result<WarehouseDashboardVO>` | ✅ Result |
| `getWarehouseStatistics` | `Get /warehouse/warehouses/{warehouseId}/statistics` | `Result<WarehouseStatisticsVO>` | ✅ Result |
| `getWarehouse` | `Get /warehouse/{warehouseId}` | `Result<WarehouseVO>` | ✅ Result |
| `deleteWarehouse` | `Delete /warehouse/{warehouseId}` | `Result<Void>` | ✅ Result |
| `getLocationsByWarehouse` | `Get /warehouse/{warehouseId}/locations` | `List<LocationVO>` | ❌ **裸类型** |
| `createPackingMaterial` | `Post /warehouse/materials` | `Result<Long>` | ✅ Result |
| `listPackingMaterials` | `Get /warehouse/materials/page` | `Result<Page<PackingMaterial>>` | ✅ Result |
| `getMaterialsNeedingReplenishment` | `Get /warehouse/materials/replenishment` | `Result<List<PackingMaterial>>` | ✅ Result |
| `updatePackingMaterial` | `Put /warehouse/materials/{materialId}` | `Result<Void>` | ✅ Result |
| `deletePackingMaterial` | `Delete /warehouse/materials/{materialId}` | `Result<Void>` | ✅ Result |
| `inboundPackingMaterial` | `Post /warehouse/materials/{materialId}/inbound` | `Result<Void>` | ✅ Result |
| `outboundPackingMaterial` | `Post /warehouse/materials/{materialId}/outbound` | `Result<Void>` | ✅ Result |
| `createWavePicking` | `Post /warehouse/waves` | `Result<Long>` | ✅ Result |
| `confirmPick` | `Put /warehouse/waves/details/{detailId}/pick` | `Result<Void>` | ✅ Result |
| `listWavePickings` | `Get /warehouse/waves/page` | `Result<Page<WavePicking>>` | ✅ Result |
| `getWavePicking` | `Get /warehouse/waves/{waveId}` | `Result<WavePicking>` | ✅ Result |
| `assignWavePicking` | `Post /warehouse/waves/{waveId}/assign` | `Result<Void>` | ✅ Result |
| `cancelWavePicking` | `Post /warehouse/waves/{waveId}/cancel` | `Result<Void>` | ✅ Result |
| `completeWavePicking` | `Post /warehouse/waves/{waveId}/complete` | `Result<Void>` | ✅ Result |
| `startWavePicking` | `Post /warehouse/waves/{waveId}/start` | `Result<Void>` | ✅ Result |

---

## 4. 前端哪些视图消费了「裸类型」接口

### 4.1 判定方法（不靠猜）

前端 `erp-web/src/api/*.ts` 里 187 个 API 函数的 URL，按网关路由表
（`erp-gateway/src/main/resources/application.yml`）逐条换算成后端实际路径，
再与 §3.4 的 controller 判定表对齐，得出每个前端函数背后的真实响应形状：

| 前端 api 函数总数 | 后端包 `Result` | 后端裸类型 | `void` |
|---|---|---|---|
| 187 | 72 | **102** | 12 |

> 网关换算规则示例：`/api/customer/**` → `StripPrefix=1` → 后端 `/customer/**`；
> `/api/social/**` → `RewritePath` → `/api/v1/**`；
> `/api/analytics/**` → `/api/v1/analytics/**`；
> `/api/tenant/**`、`/api/v1/anomaly/**`、`/api/v1/recommend/**` → `StripPrefix=0` 原样转发。

### 4.2 命中裸类型后端的前端 API 函数


**`erp-web/src/api/ai.ts`** — 23 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `chat` | `POST /ai/assistant/chat` | `/api/v1/ai-assistant/chat` | `BARE` |
| `history` | `GET /ai/assistant/history/${sessionId}` | `/api/v1/ai-assistant/history/${sessionId}` | `BARE` |
| `knowledge.list` | `GET /ai/assistant/knowledge` | `/api/ai/knowledge/search` | `ResponseEntity(bare)` |
| `knowledge.add` | `POST /ai/assistant/knowledge` | `/api/ai/knowledge/search` | `ResponseEntity(bare)` |
| `knowledge.remove` | `DELETE /ai/assistant/knowledge/${id}` | `/api/ai/knowledge/documents/${id}` | `ResponseEntity(bare)` |
| `generate` | `POST /description/generate` | `/api/v1/product-description/generate` | `BARE` |
| `history` | `GET /description/history` | `/api/v1/product-description/history` | `BARE` |
| `templates` | `GET /description/templates` | `/api/v1/product-description/templates` | `BARE` |
| `predict` | `GET /inventory-prediction/predict/${sku}` | `/api/v1/inventory-prediction/predict/${sku}` | `BARE` |
| `listSuggestions` | `GET /inventory-prediction/suggestions` | `/api/v1/inventory-prediction/suggestions` | `BARE` |
| `tasks` | `GET /inventory-prediction/tasks` | `/api/v1/inventory-prediction/tasks` | `BARE` |
| `turnover` | `GET /inventory-prediction/turnover` | `/api/v1/inventory-prediction/turnover` | `BARE` |
| `apply` | `POST /pricing/apply/${sku}` | `/api/v1/pricing/apply/${sku}` | `BARE` |
| `history` | `GET /pricing/history/${sku}` | `/api/v1/pricing/history/${sku}` | `BARE` |
| `suggest` | `GET /pricing/suggest/${sku}` | `/api/v1/pricing/suggest/${sku}` | `BARE` |
| `listTasks` | `GET /pricing/tasks` | `/api/v1/pricing/tasks` | `BARE` |
| `confirm` | `POST /v1/anomaly/${id}/confirm` | `/api/v1/anomaly/${id}/confirm` | `BARE` |
| `detect` | `POST /v1/anomaly/detect/${orderId}` | `/api/v1/anomaly/detect/${orderId}` | `BARE` |
| `list` | `GET /v1/anomaly/orders` | `/api/v1/anomaly/orders` | `BARE` |
| `stats` | `GET /v1/anomaly/stats` | `/api/v1/anomaly/stats` | `BARE` |
| `list` | `GET /v1/recommend` | `/api/v1/recommend` | `BARE` |
| `detail` | `GET /v1/recommend/${sku}` | `/api/v1/recommend/${sku}` | `BARE` |
| `refresh` | `POST /v1/recommend/refresh` | `/api/v1/recommend/refresh` | `BARE` |

**`erp-web/src/api/analytics.ts`** — 9 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `customerDistribution` | `GET /analytics/customers/distribution` | `/api/v1/analytics/customers/distribution` | `ResponseEntity(bare)` |
| `dashboardData` | `GET /analytics/dashboard` | `/api/v1/analytics/dashboard` | `ResponseEntity(bare)` |
| `inventoryAlerts` | `GET /analytics/dashboard/inventory-alerts` | `/api/v1/analytics/dashboard/inventory-alerts` | `ResponseEntity(bare)` |
| `orderStatusDistribution` | `GET /analytics/dashboard/order-status-distribution` | `/api/v1/analytics/dashboard/order-status-distribution` | `ResponseEntity(bare)` |
| `recentOrders` | `GET /analytics/dashboard/recent-orders` | `/api/v1/analytics/dashboard/recent-orders` | `ResponseEntity(bare)` |
| `ordersTrend` | `GET /analytics/orders/trend` | `/api/v1/analytics/orders/trend` | `ResponseEntity(bare)` |
| `topProducts` | `GET /analytics/products/top` | `/api/v1/analytics/products/top` | `ResponseEntity(bare)` |
| `revenueTrend` | `GET /analytics/revenue/trend` | `/api/v1/analytics/revenue/trend` | `ResponseEntity(bare)` |
| `dashboard` | `GET /analytics/summary` | `/api/v1/analytics/summary` | `ResponseEntity(bare)` |

**`erp-web/src/api/customer.ts`** — 7 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `getById` | `GET /customer/${id}` | `/customer/${id}` | `BARE` |
| `update` | `PUT /customer/${id}` | `/customer/${id}` | `BARE` |
| `delete` | `DELETE /customer/${id}` | `/customer/${id}` | `BARE` |
| `create` | `POST /customer/create` | `/customer/create` | `BARE` |
| `getList` | `GET /customer/list` | `/customer/list` | `BARE` |
| `getStats` | `GET /customer/stats` | `/customer/stats` | `BARE` |
| `getVipList` | `GET /customer/vip` | `/customer/vip` | `BARE` |

**`erp-web/src/api/finance.ts`** — 5 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `getAccounts` | `GET /finance/account/list` | `/finance/account/list` | `BARE` |
| `getCategories` | `GET /finance/category/list` | `/finance/category/list` | `BARE` |
| `getCategoryData` | `GET /finance/flow/category` | `/finance/flow/category` | `BARE` |
| `getStatistics` | `GET /finance/flow/statistics` | `/finance/flow/statistics` | `BARE` |
| `getTrendData` | `GET /finance/flow/trend` | `/finance/flow/trend` | `BARE` |

**`erp-web/src/api/inventory-alert.ts`** — 5 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `calculateRate` | `POST /logistics/calculate` | `/logistics/calculate` | `BARE` |
| `getChannels` | `GET /logistics/channels` | `/logistics/channels` | `BARE` |
| `createRate` | `POST /logistics/rate` | `/logistics/rate` | `BARE` |
| `getRates` | `GET /logistics/rates` | `/logistics/rates` | `BARE` |
| `recommendChannel` | `POST /logistics/recommend` | `/logistics/recommend` | `BARE` |

**`erp-web/src/api/inventory.ts`** — 2 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `exportInventory` | `GET /inventory/export` | `/inventory/export` | `ResponseEntity(bare)` |
| `getStats` | `GET /inventory/stats` | `/inventory/stats` | `BARE` |

**`erp-web/src/api/purchase.ts`** — 4 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `list` | `GET /purchase/orders` | `/purchase/orders` | `BARE` |
| `create` | `POST /purchase/orders` | `/purchase/orders` | `BARE` |
| `detail` | `GET /purchase/orders/${id}` | `/purchase/orders/${id}` | `BARE` |
| `suppliers` | `GET /purchase/suppliers` | `/purchase/suppliers` | `BARE` |

**`erp-web/src/api/social.ts`** — 12 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `cooperation.list` | `GET /social/cooperations` | `/api/v1/cooperations` | `ResponseEntity(bare)` |
| `cooperation.invite` | `POST /social/cooperations` | `/api/v1/cooperations` | `ResponseEntity(bare)` |
| `douyinShop.list` | `GET /social/douyin-shop` | `/api/v1/douyin-shop` | `ResponseEntity(bare)` |
| `douyinShop.sync` | `POST /social/douyin-shop/sync` | `/api/v1/douyin-shop/sync` | `ResponseEntity(bare)` |
| `influencer.list` | `GET /social/influencers` | `/api/v1/influencers` | `ResponseEntity(bare)` |
| `influencer.detail` | `GET /social/influencers/${id}` | `/api/v1/influencers/${id}` | `ResponseEntity(bare)` |
| `liveStream.list` | `GET /social/live-streams` | `/api/v1/live-streams` | `ResponseEntity(bare)` |
| `liveStream.create` | `POST /social/live-streams` | `/api/v1/live-streams` | `ResponseEntity(bare)` |
| `liveStream.end` | `POST /social/live-streams/${id}/end` | `/api/v1/live-streams/${id}/end` | `ResponseEntity(bare)` |
| `liveStream.start` | `POST /social/live-streams/${id}/start` | `/api/v1/live-streams/${id}/start` | `ResponseEntity(bare)` |
| `tiktokShop.list` | `GET /social/tiktok-shop` | `/api/v1/tiktok-shop` | `ResponseEntity(bare)` |
| `tiktokShop.sync` | `POST /social/tiktok-shop/sync` | `/api/v1/tiktok-shop/sync` | `ResponseEntity(bare)` |

**`erp-web/src/api/supplier.ts`** — 13 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `getSupplierById` | `GET /supplier/${id}` | `/supplier/${id}` | `BARE` |
| `updateSupplier` | `PUT /supplier/${id}` | `/supplier/${id}` | `BARE` |
| `deleteSupplier` | `DELETE /supplier/${id}` | `/supplier/${id}` | `BARE` |
| `createSupplier` | `POST /supplier/create` | `/supplier/create` | `BARE` |
| `createEvaluation` | `POST /supplier/evaluation/create` | `/supplier/evaluation/create` | `BARE` |
| `getEvaluationList` | `GET /supplier/evaluation/supplier/${supplierId}` | `/supplier/evaluation/supplier/${supplierId}` | `BARE` |
| `getEvaluationStats` | `GET /supplier/evaluation/supplier/${supplierId}/latest` | `/supplier/evaluation/supplier/${supplierId}/latest` | `BARE` |
| `getSupplierList` | `GET /supplier/list` | `/supplier/list` | `BARE` |
| `getPurchaseOrderById` | `GET /supplier/purchase/${id}` | `/supplier/purchase/${id}` | `BARE` |
| `deletePurchaseOrder` | `DELETE /supplier/purchase/${id}` | `/supplier/purchase/${id}` | `BARE` |
| `createPurchaseOrder` | `POST /supplier/purchase/create` | `/supplier/purchase/create` | `BARE` |
| `getPurchaseOrderList` | `GET /supplier/purchase/list` | `/supplier/purchase/list` | `BARE` |
| `getSupplierStats` | `GET /supplier/stats` | `/supplier/stats` | `BARE` |

**`erp-web/src/api/tenant.ts`** — 5 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `billing` | `GET /tenant/billing/bills` | `/api/tenant/billing/bills` | `ResponseEntity(bare)` |
| `config` | `GET /tenant/config/plan` | `/api/tenant/config/plan` | `ResponseEntity(bare)` |
| `dashboard` | `GET /tenant/dashboard/trend` | `/api/tenant/dashboard/trend` | `ResponseEntity(bare)` |
| `list` | `GET /tenant/list` | `/api/tenant/list` | `ResponseEntity(bare)` |
| `usage` | `GET /tenant/usage/current` | `/api/tenant/usage/current` | `ResponseEntity(bare)` |

**`erp-web/src/api/user.ts`** — 15 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `createDepartment` | `POST /department` | `/department` | `BARE` |
| `updateDepartment` | `PUT /department` | `/department` | `BARE` |
| `getDepartments` | `GET /department/list` | `/department/list` | `BARE` |
| `getDeptTree` | `GET /department/tree` | `/department/tree` | `BARE` |
| `getPermissionTree` | `GET /permission/tree` | `/permission/tree` | `BARE` |
| `createRole` | `POST /role` | `/role` | `BARE` |
| `updateRole` | `PUT /role` | `/role` | `BARE` |
| `getRole` | `GET /role/${id}` | `/role/${id}` | `BARE` |
| `deleteRole` | `DELETE /role/${id}` | `/role/${id}` | `BARE` |
| `getRoles` | `GET /role/list` | `/role/list` | `BARE` |
| `getRoleList` | `GET /role/list` | `/role/list` | `BARE` |
| `createUser` | `POST /user` | `/user` | `BARE` |
| `updateUser` | `PUT /user` | `/user` | `BARE` |
| `getUser` | `GET /user/${id}` | `/user/${id}` | `BARE` |
| `deleteUser` | `DELETE /user/${id}` | `/user/${id}` | `BARE` |

**`erp-web/src/api/warehouse.ts`** — 2 个函数命中裸类型后端

| 前端函数 | HTTP | 后端实际路径 | 后端返回形态 |
|---|---|---|---|
| `getLocations` | `GET /warehouse/${warehouseId}/locations` | `/warehouse/${warehouseId}/locations` | `BARE` |
| `getWarehouses` | `GET /warehouse/all` | `/warehouse/all` | `BARE` |

### 4.3 视图层 `.data` 访问点 × 后端响应形状（全部 `.data` 调用点）

对 `erp-web/src/views/**/*.vue` 中**每一个** `.data` 访问点做溯源（回溯到它所属的 `await` 调用），
再对照后端返回形态：

| 视图文件 | 行 | 前端调用 | 后端路径 | 后端返回形态 | 判定 |
|---|---|---|---|---|---|
| `customer/CustomerList.vue` | L312 | `customerApi.getList` | `/customer/list` | `BARE` | 🔴 **真实 bug** |
| `finance/index.vue` | L446 | `financeFlowApi.getFlows` | `/finance/flow/list` | `Result` | ✅ 正确 |
| `inventory-alert/InventoryAlert.vue` | L440 | `inventoryAlertApi.getRules` | `/inventory/alert/rules` | `Result` | ✅ 正确 |
| `inventory-alert/InventoryAlert.vue` | L461 | `inventoryAlertApi.getAlerts` | `/inventory/alert/records` | `Result` | ✅ 正确 |
| `inventory-alert/InventoryAlert.vue` | L481 | `inventoryAlertApi.getReplenishments` | `/inventory/alert/replenishments` | `Result` | ✅ 正确 |
| `inventory/index.vue` | L379 | `inventoryApi.getInventoryList` | `/inventory/list` | `Result` | ✅ 正确 |
| `inventory/index.vue` | L558 | `inventoryApi.getStockFlows` | `/inventory/${inventoryId}/flows` | `Result` | ✅ 正确 |
| `order/OrderList.vue` | L443 | `orderApi.advancedQuery` | `/order/advanced/list` | `Result` | ✅ 正确 |
| `order/index.vue` | L191 | `orderApi.getOrders` | `/order/list` | `Result` | ✅ 正确 |
| `product/index.vue` | L237 | `productApi.getProducts` | `/product/list` | `Result` | ✅ 正确 |
| `supplier/SupplierList.vue` | L505 | `supplierApi.getSupplierList` | `/supplier/list` | `BARE` | 🔴 **真实 bug** |
| `supplier/SupplierList.vue` | L652 | `supplierApi.getPurchaseOrderList` | `/supplier/purchase/list` | `BARE` | 🔴 **真实 bug** |
| `user/index.vue` | L423 | `userApi.getUsers` | `/user/list` | `Result` | ✅ 正确 |
| `user/index.vue` | L448 | `roleApi.getRoleList` | `/role/list` | `BARE`(分支) | ⚠️ 特例，见 §4.5（实际**不是** bug） |
| `warehouse/index.vue` | L314 | `warehouseApi.getWarehouseList` | `/warehouse/list` | `Result` | ✅ 正确 |

> ⚠️ `user/index.vue` L448 一行是**静态扫描误报**，已人工核对 `RoleController.list` 源码后排除，详见 §4.5。
>
> 因此 §4.3 全表共 15 个 `.data` 访问点，其中**真实 bug 只有 3 个**（见 §4.4），其余 11 个正确、1 个是特例。

### 4.4 真实 bug 清单（后端裸类型 + 视图层多剥了一层 `.data`）

| # | 视图文件 | 行 | 前端调用 | 后端路径 | 后端返回类型 | 运行时后果 |
|---|---|---|---|---|---|---|
| 1 | `erp-web/src/views/customer/CustomerList.vue` | L304 → L312 | `customerApi.getList` | `/customer/list` | `IPage<Customer>` | 客户表格恒空 + 分页总数恒 0 |
| 2 | `erp-web/src/views/supplier/SupplierList.vue` | L497 → L505 | `supplierApi.getSupplierList` | `/supplier/list` | `IPage<Supplier>` | 供应商表格恒空 + 分页总数恒 0 |
| 3 | `erp-web/src/views/supplier/SupplierList.vue` | L645 → L652 | `supplierApi.getPurchaseOrderList` | `/supplier/purchase/list` | `IPage<PurchaseOrder>` | 采购订单表格恒空 + 分页总数恒 0 |

**这 3 处是同一类错误的复制粘贴**（`const pageData = res.data || {}` + `pageData.records`）。

对应的三处后端实现：

```java
// erp-customer-service/.../controller/CustomerController.java
@GetMapping("/list")
public IPage<Customer> pageCustomers(...) { ... }        // 裸类型

// erp-supplier-service/.../controller/SupplierController.java
@GetMapping("/list")
public IPage<Supplier> pageSuppliers(...) { ... }        // 裸类型

// erp-supplier-service/.../controller/PurchaseOrderController.java
@GetMapping("/list")
public IPage<PurchaseOrder> page(...) { ... }           // 裸类型
```

### 4.5 一个静态扫描会误判的特例（已人工排除）

`erp-web/src/views/user/index.vue` L447-448：

```ts
const res = await roleApi.getRoleList({ current: 1, size: 100 })
const pageData = res.data || {}
```

后端 `RoleController.list` 的返回类型声明是 `Object`，**同一个路径会按入参返回两种形状**：

```java
@GetMapping("/list")
public Object list(@RequestParam(required = false) Integer current, ...) {
    if (current != null || size != null) {
        return Result.success(roleService.pageRoles(c, s, keyword, status));  // ← 包了 Result
    }
    return roleService.listRoles(status);                                          // ← 裸 List
}
```

前端 `getRoleList` 传了 `current`/`size` ⇒ 走 `Result.success(...)` 分支 ⇒ `res.data` **正确**。
而前端另一个 `getRoles()`（不传分页参数）走裸 `List` 分支，视图里写的是 `allRoles.value = res || []`（**没有** `.data`）⇒ **也正确**。
⇒ **`user/index.vue` 不是 bug**，只是「同一路径两种形状」的陷阱，纯静态扫描会误报。

### 4.6 反过来：包了 `Result` 的接口，视图层「少剥一层」同样是 bug

`erp-web/src/views/Login.vue` L83 直接用裸 `axios`（不走拦截器）：

```ts
const res = await axios.post('/api/auth/login', loginForm)
const { token, userInfo } = res.data.data   // ← axios 不剥壳，所以要两层 .data
```

`/api/auth/login` 走 `erp-user-service` 的 `AuthController`（返回 `Result<T>`）。
这里用裸 axios，所以要 `.data.data`；**如果换成走拦截器的 `request`，就只能是 `.data`**。
⇒ 全仓前端存在**两套取值约定并存**的情况，这是本次问题的根源。

---

## 5. 修复建议（本次未实施，仅供决策）

三选一，不要混用：

1. **后端统一包装（推荐）**：在 `erp-common` 加一个 `ResponseBodyAdvice`，把所有裸返回自动包成 `Result<T>`。
   优点：一处改动，全仓 265 个裸接口全部对齐前端既有约定；
   缺点：需要给 `IPage` / `List` / `String` 等设计好 `Result` 的 `data` 字段类型，且 `ResponseEntity` 分支要单独处理。
2. **前端按接口逐个改**：把 3 处 `res.data` 改成直接用 `res`，并**逐一核对**其余 100+ 个前端函数。
   工作量更大，且以后每加一个接口都要记得「这个包了没有」。
3. **中间层归一化**：在 axios 拦截器里做形状探测——
   若响应体含 `code`/`message`/`data` 则原样返回 `body.data`，否则原样返回 `body`。
   这样前端所有 `res.data` / `res` 的写法都能 work，且不动后端。

> 无论选哪种，都建议在 `erp-web/src/api/*.ts` 里补上真实的返回类型
> （现在大量 `request.get<any, X>` 里的 `X` 与后端是否包 `Result` 并无强约束，是类型系统完全失效的根因）。

---

## 附录 A. 复现步骤（逐条可跑）

```bash
cd /Users/huanghuixiang/OmniTradeERP

# 1) 建库（注意：端口是 13309，不是 3306 —— 本机 3306 是另一个原生 MySQL，
#    连过去会报 Access denied for root@xxx）
docker exec -i erp-ai-test mysql -uroot -proot < database/customer-service.sql

# 2) 打包
mvn -o -f erp-customer-service/pom.xml package -DskipTests     # → BUILD SUCCESS

# 3) 启动
nohup java -jar erp-customer-service/target/erp-customer-service-1.0.0-SNAPSHOT.jar \
  --server.port=18085 \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:13309/erp_customer?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=root --spring.datasource.password=root \
  --spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false \
  > /tmp/cust-verify.log 2>&1 &
sleep 45

# 4) 实测
curl -s "http://127.0.0.1:18085/customer/list?current=1&size=5"

# 5) 收尾
pkill -f erp-customer-service
```

## 附录 B. 关键静态检查命令

```bash
cd /Users/huanghuixiang/OmniTradeERP

# 确认没有全局 ResponseBodyAdvice（输出为空）
grep -rn "ResponseBodyAdvice" --include="*.java" .

# 唯一的结果包装类
find . -name "Result.java" -path "*main*"
# → ./erp-common/src/main/java/com/crossborder/erp/common/result/Result.java

# 哪些服务引入了 Result（= 哪些服务的 controller 会包 Result）
grep -rl "com.crossborder.erp.common.result.Result" --include="*.java" erp-*/src/main/java/ \
  | sed 's|/src/main/java.*||' | sort -u

# 前端拦截器（response => response.data）
grep -rn -A3 "interceptors.response.use" erp-web/src/api/index.ts erp-web/src/api/customer.ts
```

## 附录 C. 调查边界与清理

- **本次只写了这一个文件** `docs/CUSTOMER-RESPONSE-SHAPE.md`；
  **没有**修改任何 Java / TypeScript / 配置文件，**没有** `git commit`。
- `erp-customer-service/target/*.jar` 是 `mvn package` 的产物（`target/` 已被 `.gitignore` 忽略）。
- 测试用服务已 `pkill -f erp-customer-service` 停止；端口 18085 已释放。
- 测试库 `erp_customer` 按官方 `database/customer-service.sql` 重建；为可判读插入的 3 行种子数据
  （id 9001 / 9002 / 9003，名字带 `Verify`）已删除。
- **本次只调查了「响应形状」，没有验证「网关转发后前端拿到的形状是否一致」**——
  也就是说：直连 18085 拿到的是裸 `IPage`；经网关 `/api/customer/list` 时，
  若网关有额外的 `RewritePath` / 过滤器改写 body，形状可能不同。
  按 `erp-gateway/src/main/resources/application.yml` 的现有配置，
  客户服务路由 `customer-service` 只有 `StripPrefix=1`（只改路径、不改 body），所以经网关拿到的仍是裸 `IPage`，
  结论不变。但**这一条没有实测**，属于静态推断。
- **扫描器的已知边界**：controller 扫描基于正则 + 方法体 `return` 语句判定，
  对「同一路径按入参返回两种形状」的写法（如 `RoleController.list` 返回 `Object`）只能标为裸类型，
  这类需人工复核（本次复核了全部 4 个候选，结论见 §4.4 / §4.5）。
