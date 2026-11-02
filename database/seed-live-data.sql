-- ============================================================
-- 活表种子数据（Live Tables Seed）
-- ============================================================
-- 为什么需要这个文件？
--
-- 背景：init.sql + seed-data.sql 是「老一代」schema（t_* 前缀），
--   而各业务模块（erp-inventory-service / erp-warehouse-service /
--   erp-finance-service ...）在重构后都换成了自己的活表：
--       库存  → erp_inventory.erp_inventory     (不是 t_inventory)
--       仓库  → erp_warehouse.wms_warehouse      (不是 t_warehouse)
--       流水  → erp_finance.fin_record           (不是 t_finance_flow)
--
--   结果：老表 seed 得满满当当，活表却是 0 行 →
--   页面全空、看板全是 0，而「看起来有数据」的其实是没人读的化石表。
--   （erp-analytics-service 一度就在查 t_inventory，属于同一个根因。）
--
-- 本文件只种「Java 代码真正读写的活表」，老表的老种子数据保留在
--   seed-data.sql 里不动（init.sql 依然会建它们，属于历史兼容层）。
--
-- ⚠️ 执行顺序：文件名必须排在所有 *-service.sql 之后！
--   Docker 的 /docker-entrypoint-initdb.d 是**按文件名字母序**执行的，
--   而 *-service.sql 里都有 DROP TABLE IF EXISTS —— 若本文件先跑，
--   会被后面的 service.sql 直接 DROP 掉，种子数据全丢。
--   （真实事故：user-service.sql 字母序在 seed-data.sql 之后，
--     把 t_permission / t_role_permission 的 31 + 67 行种子数据洗成 0，
--     整个 RBAC 权限树清零。这里用 zz- 前缀强制排到最后。）
--
-- 幂等：全部使用 INSERT ... ON DUPLICATE KEY UPDATE 或先 DELETE，
--   重复执行不会产生重复数据，也不会因为主键冲突报错中断整个 init。
-- ============================================================

-- ============================================================
-- 1. 仓库（活表 wms_warehouse）
-- ============================================================
USE `erp_warehouse`;

-- 与老表 t_warehouse 的 5 个仓库一一对应（id 保持一致，便于交叉引用）
INSERT INTO `wms_warehouse`
    (`id`, `warehouse_code`, `name`, `alias_name`, `country`, `province`, `city`,
     `district`, `address`, `contact`, `phone`, `email`, `status`,
     `warehouse_type`, `total_area`, `usable_area`, `capacity`, `time_zone`, `currency`, `remark`)
VALUES
    (1, 'WH-CN-SH', '上海中心仓',     '上海仓',   'CN', '上海',   '上海',   '青浦区',   '上海市青浦区华新路 123 号',  '王仓管', '13800138003', 'sh@crossborder.com',   1, 'OWN',      5000.00, 4200.00, 5000,  'Asia/Shanghai', 'CNY', '国内主仓'),
    (2, 'WH-CN-GZ', '广州南沙保税仓', '南沙仓',   'CN', '广东',   '广州',   '南沙区',   '广州市南沙区龙穴岛 88 号',   '李仓管', '13800138013', 'gz@crossborder.com',   1, 'BONDED',   3000.00, 2500.00, 3000,  'Asia/Shanghai', 'CNY', '保税仓'),
    (3, 'WH-US-LA', '美国洛杉矶仓',   '洛杉矶仓', 'US', 'California', 'Los Angeles', 'LA', '1000 Logistics Way, LA, CA', 'Mike', '+1-213-555-0100', 'la@crossborder.com', 1, 'OVERSEAS', 8000.00, 6500.00, 8000,  'America/Los_Angeles', 'USD', '美国海外仓'),
    (4, 'WH-DE-HH', '德国汉堡仓',     '汉堡仓',   'DE', 'Hamburg', 'Hamburg', 'HH', 'Hafenstraße 8, 20457 Hamburg', 'Hans', '+49-40-555-0100', 'hh@crossborder.com', 1, 'OVERSEAS', 6000.00, 5000.00, 6000,  'Europe/Berlin', 'EUR', '欧洲海外仓'),
    (5, 'WH-JP-TK', '日本东京仓',     '东京仓',   'JP', 'Tokyo',   'Tokyo',   'Tokyo', '3-1-1 Shinagawa, Tokyo',  'Yamada', '+81-3-555-0100', 'tk@crossborder.com', 1, 'OVERSEAS', 4000.00, 3200.00, 4000,  'Asia/Tokyo', 'JPY', '日本海外仓')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `alias_name` = VALUES(`alias_name`),
    `country` = VALUES(`country`), `city` = VALUES(`city`),
    `address` = VALUES(`address`), `contact` = VALUES(`contact`),
    `phone` = VALUES(`phone`), `status` = VALUES(`status`),
    `warehouse_type` = VALUES(`warehouse_type`), `capacity` = VALUES(`capacity`);

-- 库位（活表 wms_warehouse_location）：每个主仓 3 个库位
INSERT INTO `wms_warehouse_location`
    (`id`, `warehouse_id`, `product_id`, `location_code`, `location_name`, `location_type`,
     `zone`, `row_no`, `column_no`, `level_no`, `stock_quantity`, `locked_quantity`,
     `max_capacity`, `current_capacity`, `status`, `picking_priority`,
     `is_pickable`, `is_storable`, `remark`)
VALUES
    (1, 1, 1001, 'A-01-01-01', 'A区01排01列01层', 'STANDARD', 'A', '01', '01', '01', 400, 20, 1000, 420, 'OCCUPIED', 'NORMAL', 1, 1, '蓝牙耳机黑色主库位'),
    (2, 1, 1002, 'A-01-01-02', 'A区01排01列02层', 'STANDARD', 'A', '01', '01', '02', 285, 15, 800,  300, 'OCCUPIED', 'NORMAL', 1, 1, '智能手表主库位'),
    (3, 1, 1003, 'B-02-01-01', 'B区02排01列01层', 'STANDARD', 'B', '02', '01', '01', 1660, 50, 2000, 1710,'OCCUPIED', 'NORMAL', 1, 1, '充电器主库位'),
    (4, 2, 1005, 'C-01-01-01', 'C区01排01列01层', 'STANDARD', 'C', '01', '01', '01', 890, 40, 1000, 930, 'OCCUPIED', 'NORMAL', 1, 1, '充电宝保税库位'),
    (5, 3, 1001, 'A-01-01-01', 'A区01排01列01层', 'STANDARD', 'A', '01', '01', '01', 48,  2,  200,  50,  'OCCUPIED', 'NORMAL', 1, 1, '洛杉矶仓蓝牙耳机'),
    (6, 4, 1002, 'A-01-01-01', 'A区01排01列01层', 'STANDARD', 'A', '01', '01', '01', 18,  2,  100,  20,  'OCCUPIED', 'NORMAL', 1, 1, '汉堡仓智能手表'),
    (7, 5, 1001, 'A-01-01-01', 'A区01排01列01层', 'STANDARD', 'A', '01', '01', '01', 28,  2,  100,  30,  'OCCUPIED', 'NORMAL', 1, 1, '东京仓蓝牙耳机')
ON DUPLICATE KEY UPDATE
    `product_id` = VALUES(`product_id`), `stock_quantity` = VALUES(`stock_quantity`),
    `locked_quantity` = VALUES(`locked_quantity`), `current_capacity` = VALUES(`current_capacity`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`);

-- ============================================================
-- 2. 库存（活表 erp_inventory）
-- ============================================================
-- ⚠️ 粒度是 (product_id, warehouse_id)，不是 SKU 维度：
--    uk_product_warehouse 是唯一键，一个商品在一个仓库只有一行。
--    sku_code 列保留首个 SKU 编码作为标识（服务层也是这么写的：
--    InventoryServiceImpl 建库存时 setSkuCode("SKU-" + productId)）。
--
--    老表 t_inventory 是 SKU 维度且同 SKU 分布在多仓，这里按
--    「同商品同仓合并」的方式落库，与活表唯一键保持一致。
--
--    status 取值与 InventoryServiceImpl.checkAndCreateAlert 的判定一致：
--      available == 0                → DANGER
--      available < safetyStock       → WARNING
--      其余                          → NORMAL
-- ============================================================
USE `erp_inventory`;

INSERT INTO `erp_inventory`
    (`id`, `product_id`, `warehouse_id`, `location_id`, `sku_code`,
     `quantity`, `available_quantity`, `locked_quantity`, `safety_stock`,
     `unit_cost`, `status`)
VALUES
    -- 上海中心仓（warehouse 1）
    (1,  1001, 1, 1, 'SKU-BLU-001-BLK', 700,  670,  30,  180, 120.00, 'NORMAL'),
    (2,  1002, 1, 2, 'SKU-WAT-002-BLK', 300,  285,  15,  100, 280.00, 'NORMAL'),
    (3,  1003, 1, 3, 'SKU-USB-003-US',  1710, 1660, 50,  350,  35.00, 'NORMAL'),
    (4,  1004, 1, NULL, 'SKU-STN-004',    1950, 1920, 30, 300,  12.00, 'NORMAL'),
    (5,  1005, 1, 4, 'SKU-PWB-005-BLK', 930,  890,  40,  180,  80.00, 'NORMAL'),
    (6,  1006, 1, NULL, 'SKU-KIT-006-RED', 480,  460,  20,  100,  45.00, 'NORMAL'),
    (7,  1007, 1, NULL, 'SKU-STG-007',    1450, 1420, 30, 200,  18.00, 'NORMAL'),
    (8,  1008, 1, NULL, 'SKU-MEN-008-L',  580,  560,  20,  100,  30.00, 'NORMAL'),
    (9,  1009, 1, NULL, 'SKU-WOM-009-M',  780,  760,  20,  160,  48.00, 'NORMAL'),
    (10, 1010, 1, NULL, 'SKU-BTY-010',    780,  760,  20,  150,  70.00, 'NORMAL'),
    -- 广州南沙保税仓（warehouse 2）
    (11, 1005, 2, 4, 'SKU-PWB-005-BLK', 300,  280,  20,  100,  80.00, 'NORMAL'),
    -- 美国洛杉矶仓（warehouse 3）
    (12, 1001, 3, 5, 'SKU-BLU-001-BLK', 50,   48,   2,   20,  120.00, 'NORMAL'),
    -- 德国汉堡仓（warehouse 4）—— 可用 18 < 安全库存 25 → WARNING
    (13, 1002, 4, 6, 'SKU-WAT-002-SLV', 20,   18,   2,   25,  280.00, 'WARNING'),
    -- 日本东京仓（warehouse 5）
    (14, 1001, 5, 7, 'SKU-BLU-001-BLK', 30,   28,   2,   10,  120.00, 'NORMAL')
ON DUPLICATE KEY UPDATE
    `location_id` = VALUES(`location_id`), `sku_code` = VALUES(`sku_code`),
    `quantity` = VALUES(`quantity`), `available_quantity` = VALUES(`available_quantity`),
    `locked_quantity` = VALUES(`locked_quantity`), `safety_stock` = VALUES(`safety_stock`),
    `unit_cost` = VALUES(`unit_cost`), `status` = VALUES(`status`);

-- 库存流水（活表 erp_inventory_log）
--
-- ⚠️ quantity 的符号必须跟活代码一致：**出库也记正数**。
--    InventoryServiceImpl.decreaseInventory() 调 recordInventoryLog(..., quantity, ...)
--    传的是正的出库数量（负号体现在 before/after_quantity 的差上），
--    不是负数。所以这里出库一律写正数。
--
-- ⚠️ 调拨不产生 TRANSFER 类型的流水：transferInventory() 内部是
--    decreaseInventory(源仓) + increaseInventory(目标仓)，
--    所以调拨在流水里就是一对 OUTBOUND / INBOUND，没有独立的 TRANSFER 记录。
INSERT INTO `erp_inventory_log`
    (`id`, `inventory_id`, `product_id`, `warehouse_id`, `quantity`,
     `operation_type`, `before_quantity`, `after_quantity`, `order_no`, `remark`)
VALUES
    (1,  1, 1001, 1, 500,  'INBOUND',  0,   500,  'IN-2026-0001',  '采购入库'),
    (2,  1, 1001, 1, 80,   'OUTBOUND', 500, 420, 'OUT-2026-0001', '出库到亚马逊仓'),
    (3,  2, 1002, 1, 200,  'INBOUND',  0,   200,  'IN-2026-0002',  '采购入库'),
    (4,  2, 1002, 1, 30,   'OUTBOUND', 200, 170, 'OUT-2026-0002', '出库到洛杉矶仓'),
    (5,  3, 1003, 1, 1000, 'INBOUND',  0,   1000, 'IN-2026-0003', '采购入库'),
    (6,  3, 1003, 1, 250,  'OUTBOUND', 1000, 750,'OUT-2026-0003', '平台调拨出库'),
    (7,  5, 1005, 1, 300,  'INBOUND',  0,   300,  'IN-2026-0004',  '保税仓入库'),
    (8,  12, 1001, 3, 50,  'INBOUND',  0,   50,   'IN-2026-0005',  '洛杉矶仓入库'),
    (9,  13, 1002, 4, 20,  'INBOUND',  0,   20,   'IN-2026-0006',  '汉堡仓入库'),
    (10, 14, 1001, 5, 30,  'INBOUND',  0,   30,   'IN-2026-0007',  '东京仓入库'),
    (11, 11, 1005, 2, 300, 'INBOUND',  0,   300,  'IN-2026-0008',  '广州保税仓调入'),
    (12, 11, 1005, 2, 20,  'OUTBOUND', 300, 280, 'TR-2026-0001',  '调拨出库至海外仓')
ON DUPLICATE KEY UPDATE
    `inventory_id` = VALUES(`inventory_id`), `quantity` = VALUES(`quantity`),
    `operation_type` = VALUES(`operation_type`), `remark` = VALUES(`remark`);

-- ============================================================
-- 3. 财务流水（活表 fin_record）
-- ============================================================
-- ⚠️ fin_record.account_id 必填，且 before/after_balance 必须自洽：
--    FinanceServiceImpl.reconcile() 是从首条流水的 beforeBalance 起算累加的，
--    所以这里的 before/after 必须严格连续，否则对账会算错。
--    账户 1 = 现金账户（ACC202601010001，余额 0.00 → 记完流水后更新）
--    账户 2 = 银行账户（ACC202601010002，余额 0.00 → 记完流水后更新）
--
--    流水类型：INCOME / EXPENSE / REFUND（FinanceType 枚举）
--    分类映射：SALES→1(销售收入) / PURCHASE→3(采购支出) /
--              LOGISTICS→4(物流费用) / ADVERTISING→5(广告推广)
--              PLATFORM_FEE→3(采购支出，平台抽佣归入采购成本)
-- ============================================================
USE `erp_finance`;

-- 账户 1 现金账户：3 笔收入 + 2 笔支出
-- ⚠️ create_time 必须显式给，不能用列默认值 CURRENT_TIMESTAMP：
--    FinanceServiceImpl.reconcile() 是 orderByAsc(createTime) 后逐条累加校验
--    余额连续性的。如果同一账户的流水时间戳完全相同，排序就是不确定的，
--    对账会随机报「余额不连续」。这里按业务发生顺序显式排好时间。
INSERT INTO `fin_record`
    (`id`, `record_no`, `account_id`, `type`, `amount`, `category_id`, `order_no`,
     `before_balance`, `after_balance`, `status`, `remark`, `create_time`)
VALUES
    (1, 'FR202611010001', 1, 'INCOME',  299.00,  1, 'IO-202603-0001', 0.00,    299.00,  'COMPLETED', '订单 IO-202603-0001', '2026-03-02 10:00:00'),
    (2, 'FR202611010002', 1, 'INCOME',  599.00,  1, 'IO-202603-0002', 299.00,  898.00,  'COMPLETED', '订单 IO-202603-0002', '2026-03-05 10:00:00'),
    (3, 'FR202611010003', 1, 'EXPENSE', 5000.00, 3, NULL,            898.00,  -4101.00, 'COMPLETED', '蓝牙耳机采购',     '2026-03-10 10:00:00'),
    (4, 'FR202611010004', 1, 'INCOME',  599.00,  1, 'IO-202604-0001', -4101.00, -3502.00, 'COMPLETED', '订单 IO-202604-0001', '2026-04-08 10:00:00'),
    (5, 'FR202611010005', 1, 'EXPENSE', 2000.00, 4, NULL,            -3502.00, -5502.00, 'COMPLETED', 'DHL 国际运费',    '2026-04-20 10:00:00')
ON DUPLICATE KEY UPDATE
    `account_id` = VALUES(`account_id`), `type` = VALUES(`type`),
    `amount` = VALUES(`amount`), `category_id` = VALUES(`category_id`),
    `order_no` = VALUES(`order_no`), `before_balance` = VALUES(`before_balance`),
    `after_balance` = VALUES(`after_balance`), `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    -- ⚠️ create_time 也要一起更新：流水行的业务时间决定 reconcile() 的累加顺序。
    --    只更新金额不改时间的话，重跑种子后所有流水的时间戳仍是首次插入时的
    --    同一秒，排序退化成不确定 → 对账随机报「余额不连续」。
    `create_time` = VALUES(`create_time`);

-- 账户 2 银行账户：3 笔收入 + 3 笔支出
INSERT INTO `fin_record`
    (`id`, `record_no`, `account_id`, `type`, `amount`, `category_id`, `order_no`,
     `before_balance`, `after_balance`, `status`, `remark`, `create_time`)
VALUES
    (6,  'FR202611010006', 2, 'INCOME',  199.00,  1, 'IO-202604-0002', 0.00,    199.00,  'COMPLETED', '订单 IO-202604-0002', '2026-04-12 10:00:00'),
    (7,  'FR202611010007', 2, 'EXPENSE', 1500.00, 3, NULL,            199.00,  -1301.00, 'COMPLETED', '亚马逊平台抽佣',   '2026-04-18 10:00:00'),
    (8,  'FR202611010008', 2, 'INCOME',  299.00,  1, 'IO-202605-0003', -1301.00, -1002.00, 'COMPLETED', '订单 IO-202605-0003', '2026-05-06 10:00:00'),
    (9,  'FR202611010009', 2, 'EXPENSE', 3200.00, 5, NULL,            -1002.00, -4202.00, 'COMPLETED', 'Amazon Ads 投放',   '2026-05-15 10:00:00'),
    (10, 'FR202611010010', 2, 'INCOME',  599.00,  1, 'IO-202606-0001', -4202.00, -3603.00, 'COMPLETED', '订单 IO-202606-0001', '2026-06-10 10:00:00'),
    (11, 'FR202611010011', 2, 'INCOME',  599.00,  1, 'IO-202607-0002', -3603.00, -3004.00, 'COMPLETED', '订单 IO-202607-0002', '2026-07-08 10:00:00')
ON DUPLICATE KEY UPDATE
    `account_id` = VALUES(`account_id`), `type` = VALUES(`type`),
    `amount` = VALUES(`amount`), `category_id` = VALUES(`category_id`),
    `order_no` = VALUES(`order_no`), `before_balance` = VALUES(`before_balance`),
    `after_balance` = VALUES(`after_balance`), `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    -- ⚠️ create_time 也要一起更新：流水行的业务时间决定 reconcile() 的累加顺序。
    --    只更新金额不改时间的话，重跑种子后所有流水的时间戳仍是首次插入时的
    --    同一秒，排序退化成不确定 → 对账随机报「余额不连续」。
    `create_time` = VALUES(`create_time`);

-- 账户余额回写（必须与上面流水的最终 after_balance 一致）
UPDATE `fin_account` SET `balance` = -5502.00 WHERE `id` = 1;
UPDATE `fin_account` SET `balance` = -3004.00 WHERE `id` = 2;

-- ============================================================
-- 4. 权限树（活表 t_permission / t_role_permission）
-- ============================================================
-- ⚠️ 为什么这里要重复种一遍？
--    user-service.sql 里有 DROP TABLE IF EXISTS t_permission / t_role_permission
--    并重建空表，而它自己的字母序排在 seed-data.sql **之后**
--    （u > s），所以 seed-data.sql 种的 31 条权限 + 67 条角色权限
--    被它洗成了 0 行 —— 整个 RBAC 权限树清零，角色授权页面空荡荡。
--
--    本文件排在最后（zz- 前缀），可以把权限重新种回去。
--    表结构与 user-service.sql 完全一致（含 component/icon 两列）。
-- ============================================================
USE `erp_user`;

INSERT INTO `t_permission`
    (`id`, `parent_id`, `permission_code`, `permission_name`, `permission_type`,
     `url`, `component`, `icon`, `sort_order`, `status`)
VALUES
    (1,    0,   'DASHBOARD',         '数据看板',     'menu', '/dashboard',           NULL, NULL, 1,   1),
    (10,   0,   'ORDER',             '订单管理',     'menu', '/order',               NULL, NULL, 10,  1),
    (11,   10,  'ORDER_LIST',        '订单列表',     'menu', '/order',               NULL, NULL, 1,   1),
    (12,   10,  'ORDER_DETAIL',      '订单详情',     'menu', '/order/detail',        NULL, NULL, 2,   1),
    (20,   0,   'PRODUCT',           '商品管理',     'menu', '/product',             NULL, NULL, 20,  1),
    (21,   20,  'PRODUCT_LIST',      '商品列表',     'menu', '/product',             NULL, NULL, 1,   1),
    (22,   20,  'PRODUCT_CATEGORY',  '商品分类',     'menu', '/product/category',    NULL, NULL, 2,   1),
    (23,   20,  'PRODUCT_MAPPING',   '平台映射',     'menu', '/product/mapping',     NULL, NULL, 3,   1),
    (30,   0,   'INVENTORY',         '库存管理',     'menu', '/inventory',           NULL, NULL, 30,  1),
    (31,   30,  'INVENTORY_LIST',    '库存列表',     'menu', '/inventory',           NULL, NULL, 1,   1),
    (32,   30,  'INVENTORY_ALERT',   '库存预警',     'menu', '/inventory-alert',     NULL, NULL, 2,   1),
    (33,   30,  'WAREHOUSE',         '仓库管理',     'menu', '/warehouse',           NULL, NULL, 3,   1),
    (34,   30,  'INVENTORY_INOUT',   '出入库记录',   'menu', '/inventory/inout',     NULL, NULL, 4,   1),
    (40,   0,   'LOGISTICS',         '物流管理',     'menu', '/logistics',           NULL, NULL, 40,  1),
    (50,   0,   'CUSTOMER',          '客户管理',     'menu', '/customer',            NULL, NULL, 50,  1),
    (60,   0,   'SUPPLIER',          '供应商管理',   'menu', '/supplier',            NULL, NULL, 60,  1),
    (70,   0,   'FINANCE',           '财务管理',     'menu', '/finance',             NULL, NULL, 70,  1),
    (80,   0,   'PLATFORM',          '平台配置',     'menu', '/platform',            NULL, NULL, 80,  1),
    (90,   0,   'USER_MGMT',         '用户管理',     'menu', '/user',                NULL, NULL, 90,  1),
    (100,  0,   'AI_CENTER',         'AI 智能中心',  'menu', '/ai',                  NULL, NULL, 100, 1),
    (101,  100, 'AI_ASSISTANT',      'AI 客服助手',  'menu', '/ai/assistant',        NULL, NULL, 1,   1),
    (102,  100, 'AI_PRICING',        '智能定价',     'menu', '/ai/pricing',          NULL, NULL, 2,   1),
    (103,  100, 'AI_RECOMMEND',      '选品推荐',     'menu', '/ai/recommend',        NULL, NULL, 3,   1),
    (104,  100, 'AI_ANOMALY',        '异常检测',     'menu', '/ai/anomaly',          NULL, NULL, 4,   1),
    (105,  100, 'AI_DESCRIPTION',    '商品描述生成', 'menu', '/ai/description',      NULL, NULL, 5,   1),
    (106,  100, 'AI_INVENTORY_PRED', '库存预测',     'menu', '/ai/inventory-predict', NULL, NULL, 6,   1),
    (110,  0,   'PURCHASE',          '采购管理',     'menu', '/purchase',            NULL, NULL, 110, 1),
    (120,  0,   'ANALYTICS',         '数据分析',     'menu', '/analytics',           NULL, NULL, 120, 1),
    (130,  0,   'REPORT',            '报表中心',     'menu', '/report',              NULL, NULL, 130, 1),
    (140,  0,   'SOCIAL',            '社交电商',     'menu', '/social',              NULL, NULL, 140, 1),
    (150,  0,   'TENANT',            '租户管理',     'menu', '/tenant',              NULL, NULL, 150, 1)
ON DUPLICATE KEY UPDATE
    `parent_id` = VALUES(`parent_id`), `permission_name` = VALUES(`permission_name`),
    `permission_type` = VALUES(`permission_type`), `url` = VALUES(`url`),
    `sort_order` = VALUES(`sort_order`), `status` = VALUES(`status`);

-- 角色-权限映射：超级管理员拥有全部，业务经理拥有运营类
INSERT INTO `t_role_permission` (`id`, `role_id`, `permission_id`) VALUES
    -- super admin
    (1, 1, 1),(2, 1, 10),(3, 1, 20),(4, 1, 30),(5, 1, 40),(6, 1, 50),(7, 1, 60),(8, 1, 70),(9, 1, 80),(10, 1, 90),
    (11, 1, 100),(12, 1, 110),(13, 1, 120),(14, 1, 130),(15, 1, 140),(16, 1, 150),
    (20, 1, 11),(21, 1, 12),(22, 1, 21),(23, 1, 22),(24, 1, 23),(25, 1, 31),(26, 1, 32),(27, 1, 33),(28, 1, 34),
    (30, 1, 101),(31, 1, 102),(32, 1, 103),(33, 1, 104),(34, 1, 105),(35, 1, 106),
    -- ops admin
    (40, 2, 1),(41, 2, 10),(42, 2, 20),(43, 2, 30),(44, 2, 40),(45, 2, 50),(46, 2, 60),
    (50, 2, 11),(51, 2, 12),(52, 2, 21),(53, 2, 22),(54, 2, 23),(55, 2, 31),(56, 2, 32),(57, 2, 33),(58, 2, 34),
    (60, 2, 100),(61, 2, 101),(62, 2, 102),(63, 2, 103),
    -- finance
    (70, 3, 1),(71, 3, 70),(72, 3, 120),(73, 3, 130),
    -- warehouse
    (80, 4, 1),(81, 4, 30),(82, 4, 31),(83, 4, 32),(84, 4, 33),(85, 4, 34),
    -- customer service
    (90, 5, 1),(91, 5, 10),(92, 5, 50),(93, 5, 11),(94, 5, 12),(95, 5, 101)
ON DUPLICATE KEY UPDATE
    `role_id` = VALUES(`role_id`), `permission_id` = VALUES(`permission_id`);

-- ============================================================
-- 5. 库存预警（活表 erp_inventory_alert）
-- ============================================================
-- 与上面 WARNING 那条库存（product 1002 / 汉堡仓）对应
-- ⚠️ 注意：这张表在 erp_inventory 库，上一节切到了 erp_user，
--    这里必须切回来，否则会报 "Table 'erp_user.erp_inventory_alert' doesn't exist"。
USE `erp_inventory`;

INSERT INTO `erp_inventory_alert`
    (`id`, `inventory_id`, `product_id`, `warehouse_id`, `alert_type`,
     `current_qty`, `safety_stock`, `alert_status`, `handle_remark`)
VALUES
    (1, 13, 1002, 4, 'LOW_STOCK', 18, 25, 'PENDING', '汉堡仓智能手表库存低于安全库存')
ON DUPLICATE KEY UPDATE
    `inventory_id` = VALUES(`inventory_id`), `alert_type` = VALUES(`alert_type`),
    `current_qty` = VALUES(`current_qty`), `safety_stock` = VALUES(`safety_stock`),
    `alert_status` = VALUES(`alert_status`), `handle_remark` = VALUES(`handle_remark`);

-- ============================================================
-- 完
-- ============================================================
