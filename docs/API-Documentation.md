# 📡 API 文档

> 📌 **本仓库没有静态的 API 文档文件** —— API 文档由**每个服务在运行时**通过 Swagger（springdoc-openapi）提供。
> 📌 **别再往导航里加 `API-Documentation` 这种指向「不存在的文件」的链接** —— 那就是死链（详见第 67 道闸门 `check-doc-links.py` 的 C4 判据）。

## 🔗 在哪里看

服务启动后（本地 `docker compose up -d` 或 k8s 部署），直接访问该服务的 Swagger UI：

```
http://<服务端口>/swagger-ui.html      ← 可交互的 UI
http://<服务端口>/v3/api-docs          ← 机器可读的 OpenAPI JSON
```

## 🧩 哪些服务开了 Swagger

**9 个服务**在 `application.yml` 里配了 `springdoc`（`api-docs.path=/v3/api-docs`、`swagger-ui.path=/swagger-ui.html`）：

| 服务 | 端口 | Swagger UI |
|---|---|---|
| `erp-pricing-service` | 8087 | http://localhost:8087/swagger-ui.html |
| `erp-inventory-prediction-service` | 8088 | http://localhost:8088/swagger-ui.html |
| `erp-ai-assistant-service` | 8089 | http://localhost:8089/swagger-ui.html |
| `erp-product-description-service` | 8091 | http://localhost:8091/swagger-ui.html |
| `erp-social-commerce-service` | 8096 | http://localhost:8096/swagger-ui.html |
| `erp-supplier-service` | 8099 | http://localhost:8099/swagger-ui.html |
| `erp-logistics-service` | 8100 | http://localhost:8100/swagger-ui.html |
| `erp-anomaly-detection-service` | 8138 | http://localhost:8138/swagger-ui.html |
| `erp-product-recommendation-service` | 8139 | http://localhost:8139/swagger-ui.html |

> 📌 **端口的唯一事实来源 = 各模块 `application.yml` 的 `server.port`** —— 本表是**抄出来的**，改端口后**必须重数**
> （仓库有 `scripts/check-port-consistency.py` 做五方交叉校验：application.yml ↔ compose ↔ k8s ↔ Dockerfile ↔ 文档）。
> 📌 其余服务（订单 / 商品 / 库存 / 财务 / 仓库 / 客户 / 租户 / 支付 等）**当前没开 Swagger** → 它们的接口**只能看源码**。

## ⚠️ 关于「接口清单」的诚实说明

- 本仓库**没有**一份「所有接口」的完整清单文件。
- 想知道某个后端接口**前端有没有在用**，跑仓库里的巡检脚本：
  ```bash
  python3 scripts/check-dead-endpoints.py     # 后端接口 ↔ 调用方（前端 / 服务间 / 定时任务）对照
  ```
- 想知道**后端接口 ↔ 文档描述**是否一致，跑：
  ```bash
  python3 scripts/check-api-contract.py
  ```

## 🔗 相关文档

- [🏠 首页](Home.md)
- [🏗️ 架构设计](Architecture.md)
- [🚀 快速开始](Quick-Start.md)
- [❓ 常见问题](FAQ.md)
