# 数据库初始化指南

## 文件清单

| 文件 | 作用 |
|------|------|
| `init.sql`   | **主脚本**——老一代 `t_*` 表的 DDL（19 张核心业务表） |
| `seed-data.sql` | 老一代 `t_*` 表的配套种子数据（与 init.sql 一一对应） |
| `<各模块>-service.sql` | 各业务模块自己的库表 DDL（`erp_inventory` / `erp_warehouse` / `fin_*` 等） |
| **`seed-live-data.sql`** | **活表种子数据**——种各服务 Java 代码真正读写的表 |

## ⚠️ 两代表：为什么有两个种子文件

重构后各业务模块换了自己的活表，老表成了「没人读的化石表」：

| 业务 | 老表（遗留） | 活表（Java 实际读写） |
|------|--------------|---------------------|
| 库存 | `erp_inventory.t_inventory` | **`erp_inventory.erp_inventory`** |
| 仓库 | `erp_warehouse.t_warehouse` | **`erp_warehouse.wms_warehouse`** |
| 财务 | `erp_finance.t_finance_flow` | **`erp_finance.fin_record`** |

只种老表的后果是：页面全空、看板全是 0，而「看起来有数据」的其实是没人读的化石表。
`erp-analytics-service` 的库存预警就一度在查 `t_inventory`，属于同一个根因。

**活表看这里**：`grep -rn "@TableName" */src/main/java/**/entity/` 是唯一事实来源。

## ⚠️ 执行顺序：Docker 按文件名字母序，不是按 compose 挂载顺序

`/docker-entrypoint-initdb.d` 里的脚本是**按文件名字母序**执行的。
而各 `*-service.sql` 里都有 `DROP TABLE IF EXISTS`，**后跑的文件会抹掉先跑的种子数据**。

真实事故：`user-service.sql`（u）排在 `seed-data.sql`（s）之后，
它 `DROP` 了 `t_permission` / `t_role_permission` 并重建空表，
只重插了 `t_role` / `t_user` / `t_user_role` —— **整棵 RBAC 权限树被清零**，
角色授权页面空荡荡，而 `mvn test` 全绿完全看不出来。

因此：

- `seed-live-data.sql` 在 compose 里挂载为 **`zz-seed-live-data.sql`**，
  `zz-` 前缀强制它排到最后，把活表数据和权限树种回去。
- 手动执行时也必须按同样的顺序：

```bash
# 1. 建库建表（各 service.sql + init.sql）
mysql -uroot -p < init.sql
for f in database/*-service.sql; do mysql -uroot -p < "$f"; done

# 2. 老表种子数据
mysql -uroot -p < database/seed-data.sql

# 3. 活表种子数据（必须最后）
mysql -uroot -p < database/seed-live-data.sql
```

`seed-live-data.sql` 全部使用 `INSERT ... ON DUPLICATE KEY UPDATE`，重复执行安全。

## 数据库清单

| 库名 | 主要表 | 角色 |
|------|--------|------|
| `erp_user`     | user / role / permission / user_role / role_permission | 权限中心 |
| `erp_product`  | product / product_sku / product_category / platform_product_mapping | 商品中心 |
| `erp_order`    | order / order_item | 订单中心 |
| `erp_inventory`| **erp_inventory** / erp_inventory_log / erp_inventory_alert | 库存中心（活表） |
| `erp_warehouse`| **wms_warehouse** / wms_warehouse_location / wms_* 单据 | 仓库中心（活表） |
| `erp_finance`  | **fin_account** / fin_category / **fin_record** | 财务（活表） |
| `erp_platform` | platform_config / sync_log | 平台接入 |

## 种子数据规模

| 表 | 行数 |
|---|------|
| t_user             | 7   |
| t_role             | 5   |
| t_permission       | 31  |
| t_user_role        | 7   |
| t_role_permission  | 67  |
| t_product          | 10  |
| t_product_sku      | 16  |
| t_product_category | 11  |
| t_platform_product_mapping | 10 |
| t_warehouse        | 5   |
| t_warehouse_in_out | 8   |
| t_warehouse_in_out_item | 8 |
| t_inventory        | 19  |
| t_inventory_log    | 8   |
| t_order            | 30  |
| t_order_item       | 31  |
| t_platform_config  | 2   |
| t_sync_log         | 11  |
| t_finance_flow     | 15  |
| **老表合计**       | **330 行种子数据** |

### 活表种子数据（seed-live-data.sql）

| 表 | 行数 | 说明 |
|---|------|------|
| `wms_warehouse` | 5 | 5 个仓库（上海/广州/洛杉矶/汉堡/东京） |
| `wms_warehouse_location` | 7 | 库位 |
| `erp_inventory` | 14 | 库存（商品 × 仓库） |
| `erp_inventory_log` | 12 | 库存流水（出库记正数，与 `InventoryServiceImpl` 一致） |
| `erp_inventory_alert` | 1 | 库存预警（汉堡仓智能手表） |
| `fin_record` | 11 | 财务流水（before/after_balance 连续，账户余额已回写） |
| `t_permission` | 31 | **RBAC 权限树**（被 `user-service.sql` 清零后在此恢复） |
| `t_role_permission` | 67 | 角色-权限映射（同上） |
| **活表合计** | **138 行** | |

### 关键约定（改种子前必读）

- **`erp_inventory` 粒度是 (product_id, warehouse_id)**，唯一键 `uk_product_warehouse`。
  老表 `t_inventory` 是 SKU 维度，迁过来必须按商品 × 仓聚合。
- **`status` 取值**与 `InventoryServiceImpl.checkAndCreateAlert()` 一致：
  `available == 0` → `DANGER`，`available < safety` → `WARNING`，否则 `NORMAL`。
- **`erp_inventory_log.quantity` 出库也记正数**——活代码
  `decreaseInventory()` 传的就是正数，负号体现在 before/after_quantity 的差上。
- **调拨不产生 `TRANSFER` 类型流水**：`transferInventory()` 内部是
  `decreaseInventory(源仓)` + `increaseInventory(目标仓)`。
- **`fin_record.create_time` 必须显式给**：列默认值 `CURRENT_TIMESTAMP` 会让同一账户
  的流水时间戳相同，而 `reconcile()` 是 `orderByAsc(createTime)` 后累加校验余额连续性的，
  排序不确定会导致对账随机报「余额不连续」。
- **`fin_account.balance` 必须与流水的最终 `after_balance` 一致**。

## 默认账号

```
用户名: admin     密码: admin123   角色: 超级管理员
用户名: ops001    密码: admin123   角色: 运营管理员
用户名: finance001 密码: admin123  角色: 财务
用户名: wh001     密码: admin123   角色: 仓库管理
用户名: cs001     密码: admin123   角色: 客服
```

密码字段已用 BCrypt 加密（占位 hash `$2a$10$TFTY78kAORCFz.NPcDuxDOZVou3rIZhG0UiicxP3ahb.ni7Gu2Ree`），实际部署请用 erp-user-service 的真实加密逻辑重置。
