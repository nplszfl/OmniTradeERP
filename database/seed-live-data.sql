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
-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

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
    (14, 1001, 5, 7, 'SKU-BLU-001-BLK', 30,   28,   2,   10,  120.00, 'NORMAL'),
    -- 洛杉矶仓手机支架：可用 8 < 安全库存 20 → WARNING（速销款断货风险）
    (15, 1004, 3, NULL, 'SKU-STN-004',    12,   8,    4,   20,  12.00, 'WARNING'),
    -- 汉堡仓精华液：可用 20 < 安全库存 50 → WARNING
    (16, 1010, 4, NULL, 'SKU-BTY-010',    25,   20,   5,   50,  70.00, 'WARNING'),
    -- 洛杉矶仓真空收纳袋：可用 0 → DANGER（已售罄）
    (17, 1007, 3, NULL, 'SKU-STG-007',    0,    0,    0,   50,  18.00, 'DANGER')
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
-- 5.5 客户（活表 t_customer / t_customer_tag / t_customer_group /
--     t_customer_tag_relation / t_customer_follow_record，库 erp_customer）
-- ============================================================
-- ⚠️ 之前**完全没有客户种子数据** ——
--    seed-data.sql 种了 role/user/product/warehouse/inventory/order/finance，
--    但没有 customer。结果：客户列表页即使把「多剥一层 .data」的 bug 修好了，
--    页面还是**一张空表**（库里 0 行）。
--
-- ⚠️ uk_customer_platform 是 (platform, platform_customer_id)，
--    所以平台客户号在同一个平台内必须唯一。
--
-- ⚠️ t_customer.id 是 BIGINT 主键（没有 auto_increment），
--    这里用 9001+ 的号段，避免和运行时雪花 ID 撞号。
USE `erp_customer`;

INSERT INTO `t_customer`
    (`id`, `name`, `email`, `phone`, `platform`, `platform_customer_id`,
     `country`, `state`, `city`, `address`, `postal_code`, `level`,
     `total_orders`, `total_spent`, `avg_order_value`, `last_order_at`,
     `remark`, `status`)
VALUES
    (9001, '张伟',   'zhangwei@example.com',   '+86-138-0000-0001', 'AMAZON', 'AMZ-C10001', 'US', 'New York',    'New York',    '100 Main St',        '10001', 'VIP',    15, 12850.0000, 856.6667, '2026-10-28 10:22:00', '高频复购，注意物流时效',   'ACTIVE'),
    (9002, '李娜',   'lina@example.com',       '+86-139-0000-0002', 'EBAY',   'EBAY-E20002','UK', 'England',    'London',      '25 Oxford Rd',       'W1S 2LF', 'NORMAL',  3,   320.5000, 106.8333, '2026-10-15 09:10:00', NULL,                          'ACTIVE'),
    (9003, '王芳',   'wangfang@example.com',   '+86-137-0000-0003', 'SHOPIEE','SHOP-S30003','SG', 'Singapore',  'Singapore',   '80 Robinson Rd',     '068914', 'NORMAL',  2,    89.9000,  44.9500, '2026-09-30 14:05:00', NULL,                          'ACTIVE'),
    (9004, '陈杰',   'chenjie@example.com',    '+86-136-0000-0004', 'AMAZON', 'AMZ-C10004', 'DE', 'Bayern',     'München',     '12 Maximilianstr.',  '80539',  'VIP',    22, 24680.0000, 1121.8182, '2026-11-01 16:40:00', '大客户，走海运',           'ACTIVE'),
    (9005, '刘洋',   'liuyang@example.com',    '+86-135-0000-0005', 'TIKTOK', 'TT-T40005',   'FR', 'Ile-de-France','Paris',     '88 Rue de Rivoli',   '75001',  'NORMAL',  1,    59.0000,  59.0000, '2026-08-12 11:30:00', NULL,                          'ACTIVE'),
    (9006, '赵敏',   'zhaomin@example.com',    '+86-188-0000-0006', 'LAZADA', 'LZD-D40006',  'MY', 'Kuala Lumpur','Kuala Lumpur','12 Jalan Bukit Bintang','50100','NORMAL', 4,   412.3000, 103.0750, '2026-10-20 08:45:00', NULL,                        'ACTIVE'),
    (9007, '孙悦',   'sunyue@example.com',     '+86-189-0000-0007', 'AMAZON', 'AMZ-C10007', 'JP', 'Tokyo',      'Tokyo',       '1-1-1 Marunouchi',   '100-0005','VIP',   11,  9860.0000, 896.3636, '2026-10-30 19:15:00', '偏好次日达',              'ACTIVE'),
    (9008, '周强',   'zhouqiang@example.com',  '+86-186-0000-0008', 'EBAY',   'EBAY-E20008','CA', 'Ontario',    'Toronto',     '200 Yonge St',       'M5E 1E5','NORMAL',  2,   158.9000,  79.4500, '2026-07-19 13:00:00', NULL,                          'ACTIVE'),
    (9009, '吴静',   'wujing@example.com',     '+86-150-0000-0009', 'SHOPIEE','SHOP-S30009','TH', 'Bangkok',    'Bangkok',     '999 Silom Rd',       '10100',  'NORMAL',  6,   735.6000, 122.6000, '2026-10-05 15:25:00', '对包装很在意',            'ACTIVE'),
    (9010, '郑凯',   'zhengkai@example.com',   '+86-151-0000-0010', 'TIKTOK', 'TT-T40010',   'ES', 'Madrid',     'Madrid',      'Calle Gran Via 12',  '28013',  'NORMAL',  1,    39.9000,  39.9000, '2026-06-03 10:10:00', NULL,                          'ACTIVE'),
    (9011, '冯雪',   'fengxue@example.com',    '+86-152-0000-0011', 'AMAZON', 'AMZ-C10011', 'AU', 'New South Wales','Sydney',  '100 George St',       '2000',   'NORMAL',  3,   268.4000,  89.4667, '2026-09-22 17:50:00', NULL,                          'ACTIVE'),
    (9012, '何涛',   'hetao@example.com',      '+86-153-0000-0012', 'EBAY',   'EBAY-E20012','US', 'California', 'Los Angeles', '700 S Flower St',    '90015',  'INACTIVE',0,    0.0000,   0.0000,  NULL,                   '账户已冻结，疑似刷单',   'BLOCKED')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `email` = VALUES(`email`), `phone` = VALUES(`phone`),
    `country` = VALUES(`country`), `state` = VALUES(`state`), `city` = VALUES(`city`),
    `address` = VALUES(`address`), `postal_code` = VALUES(`postal_code`),
    `level` = VALUES(`level`), `total_orders` = VALUES(`total_orders`),
    `total_spent` = VALUES(`total_spent`), `avg_order_value` = VALUES(`avg_order_value`),
    `last_order_at` = VALUES(`last_order_at`), `remark` = VALUES(`remark`),
    `status` = VALUES(`status`);

-- 客户标签
INSERT INTO `t_customer_tag`
    (`id`, `name`, `color`, `description`, `sort_order`, `remark`, `status`)
VALUES
    (9101, '高价值',   '#F56C6C', '累计消费高、复购稳定',       1, NULL, 'ACTIVE'),
    (9102, '高频复购', '#E6A23C', '近半年下单 3 次以上',       2, NULL, 'ACTIVE'),
    (9103, '海运客户', '#409EFF', '大件商品、倾向海运',         3, NULL, 'ACTIVE'),
    (9104, '次日达',   '#67C23A', '对时效敏感、要求次日达',     4, NULL, 'ACTIVE'),
    (9105, '包装敏感', '#909399', '对包装有特殊要求',         5, NULL, 'ACTIVE'),
    (9106, '待激活',   '#F0A020', '注册未下单，需跟进',         6, NULL, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    `color` = VALUES(`color`), `description` = VALUES(`description`),
    `sort_order` = VALUES(`sort_order`), `remark` = VALUES(`remark`),
    `status` = VALUES(`status`);

-- 客户分组
INSERT INTO `t_customer_group`
    (`id`, `name`, `description`, `member_count`, `sort_order`, `status`)
VALUES
    (9201, '核心客户',   '年消费 1 万美元以上',         2, 1, 'ACTIVE'),
    (9202, '欧洲市场',   '欧盟国家客户',               3, 2, 'ACTIVE'),
    (9203, '东南亚市场', '东南亚国家客户',             3, 3, 'ACTIVE'),
    (9204, '沉睡客户',   '超过 90 天未下单',           1, 4, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    `description` = VALUES(`description`), `member_count` = VALUES(`member_count`),
    `sort_order` = VALUES(`sort_order`), `status` = VALUES(`status`);

-- 客户 ↔ 标签 关联
INSERT INTO `t_customer_tag_relation` (`id`, `customer_id`, `tag_id`, `create_time`)
VALUES
    (9301, 9001, 9101, '2026-03-01 10:00:00'),
    (9302, 9001, 9102, '2026-03-01 10:00:00'),
    (9303, 9004, 9101, '2026-03-02 10:00:00'),
    (9304, 9004, 9103, '2026-03-02 10:00:00'),
    (9305, 9007, 9101, '2026-03-03 10:00:00'),
    (9306, 9007, 9104, '2026-03-03 10:00:00'),
    (9307, 9009, 9105, '2026-03-04 10:00:00'),
    (9308, 9005, 9106, '2026-03-05 10:00:00'),
    (9309, 9010, 9106, '2026-03-05 10:00:00')
ON DUPLICATE KEY UPDATE
    `create_time` = VALUES(`create_time`);

-- 客户跟进记录（含今天到期、便于验证「今日待跟进」）
INSERT INTO `t_customer_follow_record`
    (`id`, `customer_id`, `follow_type`, `content`, `follow_time`, `next_follow_time`,
     `result`, `follow_user_id`, `follow_user_name`, `follower_name`, `remark`)
-- ⚠️ 时间用 NOW() 相对偏移（不要写死日期）：
--    写死的话过几天「今日待跟进」就永远是空的，
--    而 NOW() 偏移在**每次重新初始化时**都落在合理区间。
VALUES
    (9401, 9001, 'EMAIL', '发送最新物流时效说明，客户已确认满意', NOW() - INTERVAL 6 DAY,  NOW() + INTERVAL 12 DAY, 'SUCCESS', 1, 'admin', '客服小林', 'VIP 客户，保持每周触达'),
    (9402, 9001, 'PHONE', '回访物流破损问题，已安排补发',       NOW() - INTERVAL 1 DAY,  NOW() + INTERVAL 9 DAY,  'SUCCESS', 1, 'admin', '客服小林', NULL),
    (9403, 9004, 'VISIT', '线下拜访，确认 Q4 大单采购计划',      NOW() - INTERVAL 2 DAY,  NOW() + INTERVAL 5 DAY,  'PENDING', 1, 'admin', '销售老王', '重点跟进，预计 20000 美元'),
    (9404, 9007, 'WECHAT', '推送新品到货通知，客户要求预留',    NOW() - INTERVAL 4 DAY,  NOW() + INTERVAL 0 DAY,  'PENDING', 1, 'admin', '客服小林', '今日到期'),
    (9405, 9009, 'EMAIL', '包装方案确认，等待客户回复',        NOW() - INTERVAL 29 DAY, NOW() + INTERVAL 1 DAY,  'PENDING', 1, 'admin', '客服小林', '明日到期'),
    (9406, 9003, 'PHONE', '首次电话接通，介绍活动款',          NOW() - INTERVAL 34 DAY, NOW() + INTERVAL 0 DAY,  'PENDING', 1, 'admin', '客服小林', '今日到期'),
    (9407, 9002, 'EMAIL', '退换货流程说明已发送',              NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 9 DAY, 'SUCCESS', 1, 'admin', '客服小林', NULL),
    (9408, 9006, 'OTHER', '首次触达，客户暂无采购意向',        NOW() - INTERVAL 14 DAY, NOW() + INTERVAL 28 DAY, 'FAILED', 1, 'admin', '客服小林', '换联系方式再试')
ON DUPLICATE KEY UPDATE
    `content` = VALUES(`content`), `follow_time` = VALUES(`follow_time`),
    `next_follow_time` = VALUES(`next_follow_time`), `result` = VALUES(`result`),
    `follow_user_name` = VALUES(`follow_user_name`), `follower_name` = VALUES(`follower_name`),
    `remark` = VALUES(`remark`);

-- ============================================================
-- 6. 库存预警规则（活表 t_inventory_alert_rule，库 erp_inventory_alert）
-- ============================================================
-- ⚠️ warehouse_id 是**仓库编码**（VARCHAR，如 'WH-DE-HH'），
--    不是 erp_inventory.warehouse_id 的数字主键。
--    预警扫描（AlertScanScheduler → LiveInventoryMapper）会
--    JOIN erp_warehouse.wms_warehouse 拿 warehouse_code 来匹配规则。
--
-- ⚠️ 阈值直接对齐 erp_inventory.safety_stock（同一条库存的补货点），
--    这样「库存状态 WARNING」和「生成 LOW 预警」是同一套口径，不会互相打架。
--
-- 下面 8 条规则里，6 条**不应该**触发（库存充足），只有 3 条会触发：
--   · 1002 @ 汉堡仓   可用 18 <= 预警值 20  → LOW
--   · 1004 @ 洛杉矶仓 可用 8  <= 预警值 15  → LOW
--   · 1010 @ 汉堡仓   可用 20 <= 预警值 40  → LOW
--   · 1007 @ 洛杉矶仓 可用 0  <= 最低库存 10 → OUT
-- 其余（1001@洛杉矶 48、1001@东京 28、1005@南沙 280、上海仓通用）库存充足 → 不产生预警。
-- 这几条「不该触发的规则」是刻意留的：用来验证扫描逻辑不会无脑刷预警。
USE `erp_inventory_alert`;

INSERT INTO `t_inventory_alert_rule`
    (`id`, `tenant_id`, `product_id`, `sku`, `product_name`,
     `warehouse_id`, `warehouse_name`,
     `safe_stock`, `alert_stock`, `min_stock`, `alert_type`,
     `advance_days`, `notify_type`, `notify_to`, `enabled`, `remark`)
VALUES
    (9001, NULL, 1001, 'SKU-BLU-001-BLK', '无线蓝牙耳机 Pro', 'WH-US-LA', '美国洛杉矶仓',  20,  15,  5, 'LOW', 3, 'ALL', NULL, 1, '洛杉矶仓蓝牙耳机补货点'),
    (9002, NULL, 1001, 'SKU-BLU-001-BLK', '无线蓝牙耳机 Pro', 'WH-JP-TK', '日本东京仓',    10,   8,  3, 'LOW', 3, 'ALL', NULL, 1, '东京仓蓝牙耳机补货点'),
    (9003, NULL, 1005, 'SKU-PWB-005-BLK', '20000mAh 充电宝',   'WH-CN-GZ', '广州南沙保税仓',100,  80, 40, 'LOW', 3, 'ALL', NULL, 1, '南沙仓充电宝补货点'),
    (9004, NULL, 1002, 'SKU-WAT-002-SLV', '智能手表 X1',       'WH-DE-HH', '德国汉堡仓',    25,  20, 10, 'LOW', 3, 'ALL', NULL, 1, '汉堡仓智能手表补货点'),
    (9005, NULL, 1004, 'SKU-STN-004',     '便携手机支架',      'WH-US-LA', '美国洛杉矶仓',  20,  15,  5, 'LOW', 3, 'ALL', NULL, 1, '洛杉矶仓手机支架补货点'),
    (9006, NULL, 1010, 'SKU-BTY-010',     '精华液 30ml',       'WH-DE-HH', '德国汉堡仓',    50,  40, 20, 'LOW', 3, 'ALL', NULL, 1, '汉堡仓精华液补货点'),
    (9007, NULL, 1007, 'SKU-STG-007',     '真空收纳袋 10 件',  'WH-US-LA', '美国洛杉矶仓',  50,  40, 10, 'OUT', 3, 'ALL', NULL, 1, '洛杉矶仓真空收纳袋补货点'),
    (9008, NULL, NULL, NULL, NULL,         'WH-CN-SH', '上海中心仓',      200, 150, 50, 'LOW', 3, 'ALL', NULL, 1, '上海中心仓通用预警规则')
ON DUPLICATE KEY UPDATE
    `product_id` = VALUES(`product_id`), `sku` = VALUES(`sku`),
    `product_name` = VALUES(`product_name`), `warehouse_id` = VALUES(`warehouse_id`),
    `warehouse_name` = VALUES(`warehouse_name`), `safe_stock` = VALUES(`safe_stock`),
    `alert_stock` = VALUES(`alert_stock`), `min_stock` = VALUES(`min_stock`),
    `alert_type` = VALUES(`alert_type`), `notify_type` = VALUES(`notify_type`),
    `enabled` = VALUES(`enabled`), `remark` = VALUES(`remark`);


-- ============================================================
-- 7. 社交电商（活表 influencer / cooperation / live_stream /
--     tiktok_shop_config / douyin_shop_config，库 erp_social）
-- ============================================================
-- ⚠️ 之前**完全没有社交电商种子数据** ——
--    而且 erp_social 库本身都没挂进 docker-compose.yml，
--    → 社交电商服务在 compose 环境启动即报 "Unknown database 'erp_social'"
--    （Hibernate ddl-auto=update 只建表不建库，所以补了 DDL 也没用）
--
-- ⚠️ 前端「社交电商」页面的每一列 prop 都写错了
--    （viewers/gmv/followers/category 前端 vs viewerCount/totalGmv/
--      followerCount/categories 后端）→ 表格全是空白。
--    本轮一并修掉了。
--
-- ⚠️ platform / level / status 都是 **@Enumerated(EnumType.STRING)** 的枚举，
--    值必须落在枚举里，否则 Hibernate 读的时候直接抛
--    "No enum constant" → 整个服务查询报错。
--    直播平台只有 TIKTOK / DOUYIN / TAOBAO_LIVE（**没有** INSTAGRAM / YOUTUBE）；
--    达人平台只有 TIKTOK / DOUYIN / INSTAGRAM / YOUTUBE（**没有** TAOBAO_LIVE）。
-- ⚠️ 直播/合作的日期用 NOW() 相对偏移：
--    写死的话过几天「即将开始」永远是空的。
USE `erp_social`;

-- 达人
INSERT INTO `influencer`
    (`id`, `name`, `platform_id`, `platform`, `avatar_url`, `follower_count`,
     `following_count`, `total_views`, `total_likes`, `level`, `status`,
     `contact_email`, `contact_phone`, `cooperation_price`, `categories`,
     `description`, `created_at`, `updated_at`)
VALUES
    (7001, '美妆小美',   'TTK-90001', 'TIKTOK',    NULL, 1250000, 320,  98000000, 6200000,  'MAK',  'ACTIVE',    'xiaomei@example.com',   '+86-138-1000-7001', 35000.00, '美妆个护',           'TikTok 美妆头部达人，直播转化率高', NOW() - INTERVAL 200 DAY, NOW() - INTERVAL 3 DAY),
    (7002, '数码大叔',   'TTK-90002', 'TIKTOK',    NULL,  860000, 150,  42000000, 2100000,  'KOL',  'ACTIVE',    'dashushu@example.com',  '+86-139-1000-7002', 18000.00, '数码3C',            '3C 数码测评，客单价高',   NOW() - INTERVAL 180 DAY, NOW() - INTERVAL 2 DAY),
    (7003, '家居设计',   'IG-90003',  'INSTAGRAM', NULL,  320000, 210,  18000000,  940000,  'KOL',  'ACTIVE',    'home@example.com',     '+86-137-1000-7003', 12000.00, '家居生活',           '家居好物种草',       NOW() - INTERVAL 160 DAY, NOW() - INTERVAL 1 DAY),
    (7004, '健身教练Tom','DY-90004',  'DOUYIN',    NULL,  540000, 180,  33000000, 1800000,  'KOL',  'ACTIVE',    'tom@example.com',      '+86-136-1000-7004', 15000.00, '运动健身',           '健身器材带货',       NOW() - INTERVAL 150 DAY, NOW() - INTERVAL 5 DAY),
    (7005, '美食阿May',  'DY-90005',  'DOUYIN',    NULL,  210000, 260,   9600000,  720000,  'KOC',  'ACTIVE',    'may@example.com',      '+86-135-1000-7005',  5000.00, '食品生鲜',           '零食测评',           NOW() - INTERVAL 120 DAY, NOW() - INTERVAL 8 DAY),
    (7006, '户外老王',   'YT-90006',  'YOUTUBE',   NULL,  185000, 120,  12000000,  610000,  'KOC',  'ACTIVE',    'laowang@example.com',  '+86-134-1000-7006',  6000.00, '户外运动',           '露营装备测评',       NOW() - INTERVAL 100 DAY, NOW() - INTERVAL 12 DAY),
    (7007, '母婴小鹿',   'TTK-90007', 'TIKTOK',    NULL,   96000, 190,   4200000,  280000,  'KOC',  'ACTIVE',    'lulu@example.com',     '+86-133-1000-7007',  3000.00, '母婴用品',           '母婴好物',           NOW() - INTERVAL 80 DAY,  NOW() - INTERVAL 15 DAY),
    (7008, '科技Tech',   'TTK-90008', 'TIKTOK',    NULL,      0,   0,          0,       0,  'KOC',  'INACTIVE',  'tech@example.com',     NULL,                     0.00, '数码3C',            '已停止合作',         NOW() - INTERVAL 300 DAY, NOW() - INTERVAL 60 DAY)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `platform` = VALUES(`platform`),
    `follower_count` = VALUES(`follower_count`), `following_count` = VALUES(`following_count`),
    `total_views` = VALUES(`total_views`), `total_likes` = VALUES(`total_likes`),
    `level` = VALUES(`level`), `status` = VALUES(`status`),
    `contact_email` = VALUES(`contact_email`), `contact_phone` = VALUES(`contact_phone`),
    `cooperation_price` = VALUES(`cooperation_price`), `categories` = VALUES(`categories`),
    `description` = VALUES(`description`), `updated_at` = VALUES(`updated_at`);

-- 达人合作
INSERT INTO `cooperation`
    (`id`, `influencer_id`, `product_id`, `product_name`, `type`, `status`,
     `planned_start_date`, `planned_end_date`, `actual_start_date`, `actual_end_date`,
     `agreed_price`, `actual_cost`, `commission_rate`, `expected_views`, `actual_views`,
     `expected_sales`, `actual_sales`, `expected_gmv`, `actual_gmv`,
     `content`, `remark`, `created_at`, `updated_at`)
VALUES
    (7101, 7001, 'SKU-BTY-010',   '精华液 30ml',         'LIVE',        'COMPLETED',   NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, 35000.00, 31200.00, 15.00, 200000, 268000, 1200, 1450, 180000.00, 216400.00, '直播首发精华液新品',   '转化率最高的单场',   NOW() - INTERVAL 45 DAY, NOW() - INTERVAL 30 DAY),
    (7102, 7002, 'SKU-PWB-005-BLK','20000mAh 充电宝',    'SHORT_VIDEO', 'COMPLETED',   NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 18000.00, 17400.00, 12.00, 150000, 171000,  900, 1042, 135000.00, 156300.00, '开箱测评充电宝',     '视频播放超预期',     NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 26 DAY),
    (7103, 7003, 'SKU-STG-007',   '真空收纳袋 10 件',    'POST',        'COMPLETED',   NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, 12000.00, 11500.00, 10.00,  80000,  94000,  500,  612,  60000.00,  73440.00, '家居收纳好物图文',   '图文种草稳定',       NOW() - INTERVAL 32 DAY, NOW() - INTERVAL 20 DAY),
    (7104, 7004, 'SKU-WAT-002-SLV','智能手表 X1',        'LIVE',        'IN_PROGRESS', NOW() - INTERVAL 3 DAY,  NOW() + INTERVAL 4 DAY,  NOW() - INTERVAL 3 DAY,  NULL,              15000.00,  9200.00, 12.00, 120000,  98000,  700,  640,  90000.00,  76800.00, '智能手表直播专场',    '进行中',             NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 1 DAY),
    (7105, 7005, 'SKU-PWB-005-BLK','便携充电宝mini',     'LIVE_STREAM', 'CONFIRMED',   NOW() + INTERVAL 2 DAY,  NOW() + INTERVAL 2 DAY,  NULL, NULL,                  5000.00,     NULL, 10.00,  60000,  NULL,   400, NULL,  45000.00,     NULL, '零食专场顺带带货',   '待开播',             NOW() - INTERVAL 5 DAY,  NOW() - INTERVAL 1 DAY),
    (7106, 7006, 'SKU-STN-004',   '便携手机支架',        'COLLABORATION','PENDING',    NOW() + INTERVAL 6 DAY,  NOW() + INTERVAL 9 DAY,  NULL, NULL,                  6000.00,     NULL,  8.00,  50000,  NULL,   300, NULL,  30000.00,     NULL, '户外场景支架合作',   '待客户确认',         NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 2 DAY),
    (7107, 7007, 'SKU-BTY-010',   '精华液家庭装',        'STORY',       'DRAFT',       NOW() + INTERVAL 12 DAY, NOW() + INTERVAL 12 DAY, NULL, NULL,                  3000.00,     NULL, 12.00,  30000,  NULL,   200, NULL,  18000.00,     NULL, '限时 story 推广',     '草稿待完善',         NOW() - INTERVAL 1 DAY,  NOW() - INTERVAL 1 DAY),
    (7108, 7002, 'SKU-STG-007',   '收纳袋清仓专场',      'LIVE',        'CANCELLED',   NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY, NULL, NULL,                  12000.00,     NULL, 10.00,  70000,  NULL,   400, NULL,  42000.00,     NULL, '清仓专场（取消）',    '达人档期冲突取消',   NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 12 DAY)
ON DUPLICATE KEY UPDATE
    `product_name` = VALUES(`product_name`), `type` = VALUES(`type`),
    `status` = VALUES(`status`), `planned_start_date` = VALUES(`planned_start_date`),
    `planned_end_date` = VALUES(`planned_end_date`), `actual_start_date` = VALUES(`actual_start_date`),
    `actual_end_date` = VALUES(`actual_end_date`), `agreed_price` = VALUES(`agreed_price`),
    `actual_cost` = VALUES(`actual_cost`), `commission_rate` = VALUES(`commission_rate`),
    `expected_views` = VALUES(`expected_views`), `actual_views` = VALUES(`actual_views`),
    `expected_sales` = VALUES(`expected_sales`), `actual_sales` = VALUES(`actual_sales`),
    `expected_gmv` = VALUES(`expected_gmv`), `actual_gmv` = VALUES(`actual_gmv`),
    `content` = VALUES(`content`), `remark` = VALUES(`remark`),
    `updated_at` = VALUES(`updated_at`);

-- 直播
INSERT INTO `live_stream`
    (`id`, `stream_id`, `title`, `platform`, `influencer_id`, `status`,
     `scheduled_start_time`, `actual_start_time`, `end_time`, `duration`,
     `viewer_count`, `peak_viewers`, `like_count`, `share_count`, `comment_count`,
     `new_followers`, `total_sales`, `total_orders`, `total_gmv`,
     `cover_image_url`, `products`, `created_at`, `updated_at`)
VALUES
    (7201, 'LIVE-20261101-01', '美妆新品发布专场',   'TIKTOK',  7001, 'ENDED',     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 2 HOUR, 120, 268000, 41200, 1860000,  42000, 31000, 2400,  1680, 1450, 216400.00, NULL, 'SKU-BTY-010,SKU-BTY-011', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 3 DAY),
    (7202, 'LIVE-20261102-01', '充电宝开箱直播',     'TIKTOK',  7002, 'ENDED',     NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 90 MINUTE, 90, 171000, 27800,  940000,  26000, 19000, 1200,  1042,  890, 156300.00, NULL, 'SKU-PWB-005-BLK',        NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 2 DAY),
    (7203, 'LIVE-20261103-01', '智能手表体验直播',   'DOUYIN',  7004, 'LIVE',      NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, NULL, NULL, 98000, 21400, 620000, 15000, 11000,  680,   640,  480,  76800.00, NULL, 'SKU-WAT-002-SLV',        NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 1 HOUR),
    (7204, 'LIVE-20261105-01', '今日零食福利场',     'DOUYIN',  7005, 'SCHEDULED', NOW() + INTERVAL 1 DAY,  NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 'SKU-PWB-005-BLK', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    (7205, 'LIVE-20261108-01', '家居收纳专场直播',   'TIKTOK',  7003, 'SCHEDULED', NOW() + INTERVAL 4 DAY,  NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 'SKU-STG-007',           NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    (7206, 'LIVE-20261028-01', '露营装备选品直播',   'TAOBAO_LIVE', 7006, 'CANCELLED', NOW() - INTERVAL 8 DAY, NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 'SKU-STN-004',            NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 8 DAY)
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `platform` = VALUES(`platform`),
    `influencer_id` = VALUES(`influencer_id`), `status` = VALUES(`status`),
    `scheduled_start_time` = VALUES(`scheduled_start_time`),
    `actual_start_time` = VALUES(`actual_start_time`), `end_time` = VALUES(`end_time`),
    `duration` = VALUES(`duration`), `viewer_count` = VALUES(`viewer_count`),
    `peak_viewers` = VALUES(`peak_viewers`), `like_count` = VALUES(`like_count`),
    `share_count` = VALUES(`share_count`), `comment_count` = VALUES(`comment_count`),
    `new_followers` = VALUES(`new_followers`), `total_sales` = VALUES(`total_sales`),
    `total_orders` = VALUES(`total_orders`), `total_gmv` = VALUES(`total_gmv`),
    `products` = VALUES(`products`), `updated_at` = VALUES(`updated_at`);

-- TikTok 店铺授权
INSERT INTO `tiktok_shop_config`
    (`id`, `shop_name`, `shop_id`, `access_token`, `refresh_token`,
     `token_expire_time`, `app_key`, `app_secret`, `status`, `created_at`, `updated_at`)
VALUES
    (7301, 'OmniTrade 官方海外店', 'SHOP-TT-0001', 'tt-access-demo-0001', 'tt-refresh-demo-0001', NOW() + INTERVAL 60 DAY, 'tt-app-key-demo', 'tt-app-secret-demo', 'ACTIVE',    NOW() - INTERVAL 120 DAY, NOW() - INTERVAL 2 DAY),
    (7302, 'OmniTrade 家居专营店', 'SHOP-TT-0002', 'tt-access-demo-0002', 'tt-refresh-demo-0002', NOW() + INTERVAL 12 DAY,  'tt-app-key-demo', 'tt-app-secret-demo', 'EXPIRED',   NOW() - INTERVAL 90 DAY,  NOW() - INTERVAL 10 DAY)
ON DUPLICATE KEY UPDATE
    `shop_name` = VALUES(`shop_name`), `access_token` = VALUES(`access_token`),
    `refresh_token` = VALUES(`refresh_token`), `token_expire_time` = VALUES(`token_expire_time`),
    `app_key` = VALUES(`app_key`), `status` = VALUES(`status`), `updated_at` = VALUES(`updated_at`);

-- 抖音店铺授权
INSERT INTO `douyin_shop_config`
    (`id`, `shop_name`, `shop_id`, `access_token`, `refresh_token`,
     `token_expire_time`, `app_key`, `app_secret`, `status`, `created_at`, `updated_at`)
VALUES
    (7401, 'OmniTrade 抖音旗舰店', 'SHOP-DY-0001', 'dy-access-demo-0001', 'dy-refresh-demo-0001', NOW() + INTERVAL 45 DAY, 'dy-app-key-demo', 'dy-app-secret-demo', 'ACTIVE',  NOW() - INTERVAL 100 DAY, NOW() - INTERVAL 1 DAY),
    (7402, 'OmniTrade 数码专营店', 'SHOP-DY-0002', 'dy-access-demo-0002', 'dy-refresh-demo-0002', NOW() - INTERVAL 3 DAY,  'dy-app-key-demo', 'dy-app-secret-demo', 'ERROR',   NOW() - INTERVAL 60 DAY,  NOW() - INTERVAL 3 DAY)
ON DUPLICATE KEY UPDATE
    `shop_name` = VALUES(`shop_name`), `access_token` = VALUES(`access_token`),
    `refresh_token` = VALUES(`refresh_token`), `token_expire_time` = VALUES(`token_expire_time`),
    `app_key` = VALUES(`app_key`), `status` = VALUES(`status`), `updated_at` = VALUES(`updated_at`);


-- ============================================================
-- 8. 供应商与采购（活表 t_supplier / t_purchase_order /
--     t_purchase_order_item / t_supplier_evaluation，库 erp_supplier）
-- ============================================================
-- ⚠️ 之前**完全没有供应商种子数据** ——
--    seed-data.sql 和 seed-live-data.sql 都没种 erp_supplier 的表，
--    结果：供应商列表页 + 采购订单页 + 绩效页**全是空表**。
--
-- ⚠️ t_supplier.id / t_purchase_order.id 是 BIGINT 主键（没有 auto_increment），
--    这里用 5001+ / 5101+ / 5201+ 号段，避免和运行时雪花 ID 撞号。
--
-- ⚠️ 采购单状态：**以后端代码为准**（DDL 注释写的是 DRAFT/CONFIRMED/SHIPPED，
--    那是过时的）：
--    PENDING → APPROVED → PURCHASING → IN_TRANSIT → RECEIVED，任意可 CANCELLED
-- ⚠️ 评估表的 `conclusion` 是 VARCHAR(500)（不是枚举），
--    但取值必须和后端 SupplierEvaluationService.calculateConclusion 一致：
--    ≥4.5 EXCELLENT / ≥4.0 GOOD / ≥3.0 ACCEPTABLE / 否则 POOR
--    （没有 FAIR 这一档）
-- ⚠️ `total_score` 也由后端按加权平均算（质量30+交付25+价格20+服务15+技术10），
--    种子里写死的是当初算好的值，与后端算法一致。
USE `erp_supplier`;

-- 供应商
INSERT INTO `t_supplier`
    (`id`, `name`, `contact_person`, `phone`, `email`, `type`, `country`, `city`,
     `address`, `category`, `cooperation_start_at`, `total_purchase_amount`,
     `total_orders`, `rating`, `level`, `remark`, `status`)
VALUES
    (5001, '深圳华强电子厂',   '陈国强', '+86-755-8800-1001', 'chen@huaqiang.example.com', 'FACTORY', 'CN', '深圳',   '深圳市宝安区西乡工业园 8 号', '数码3C',   NOW() - INTERVAL 400 DAY, 486000.00, 32, 4.45, 'A', '主力电子代工，良率稳定',       'ACTIVE'),
    (5002, '义乌小商品集散中心','王丽',   '+86-579-8800-1002', 'wang@yiwu.example.com',    'TRADER',  'CN', '义乌',   '义乌市国际商贸城 3 区',         '家居日用', NOW() - INTERVAL 320 DAY, 152000.00, 21, 4.10, 'B', '小批量快反，价格有优势',       'ACTIVE'),
    (5003, '宁波家纺工厂',     '李建国', '+86-574-8800-1003', 'li@ningbo.example.com',   'FACTORY', 'CN', '宁波',   '宁波市北仑区纺织工业园 12 号',   '家纺',     NOW() - INTERVAL 280 DAY, 298000.00, 18, 4.45, 'A', '四件套起订量低',             'ACTIVE'),
    (5004, '广州美妆代工厂',   '张薇',   '+86-20-8800-1004',  'zhang@guangzhou.example.com','FACTORY','CN','广州',   '广州市白云区美妆产业园 B 座',     '美妆个护', NOW() - INTERVAL 240 DAY, 224000.00, 14, 4.50, 'A', '备案齐全，可做新品',         'ACTIVE'),
    (5005, '苏州精密五金厂',   '赵军',   '+86-512-8800-1005', 'zhao@suzhou.example.com',  'FACTORY', 'CN', '苏州',   '苏州市工业园区星湖街 328 号',      '五金工具', NOW() - INTERVAL 200 DAY,  96000.00,  9, 3.75, 'B', '起订量 500 起',             'ACTIVE'),
    (5006, '广州德迅国际物流', '孙敏',   '+86-20-8800-1006',  'sun@deyun.example.com',    'AGENT',   'CN', '广州',   '广州市天河区物流园 A 区',         '物流仓储', NOW() - INTERVAL 180 DAY,  68000.00, 12, 3.45, 'C', '海运价格低但时效一般',       'ACTIVE'),
    (5007, '越南成服装厂',     'Nguyen', '+84-24-8800-1007', 'nguyen@vietnam.example.com','FACTORY','VN','胡志明市','胡志明市第 7 郡制衣工业区',    '服装',     NOW() - INTERVAL 120 DAY,  42000.00,  6, 3.05, 'C', '关税优惠，交期不稳定',       'ACTIVE'),
    (5008, '孟加拉服装贸易商', 'Rahman', '+880-2-8800-1008', 'rahman@bangladesh.example.com','TRADER','BD','达卡','达卡 Wari 商业街 22 号',        '服装',     NOW() - INTERVAL 90 DAY,   18000.00,  3, 3.20, 'C', '量小，暂不主推',           'INACTIVE')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`), `contact_person` = VALUES(`contact_person`),
    `phone` = VALUES(`phone`), `email` = VALUES(`email`), `type` = VALUES(`type`),
    `country` = VALUES(`country`), `city` = VALUES(`city`), `address` = VALUES(`address`),
    `category` = VALUES(`category`), `total_purchase_amount` = VALUES(`total_purchase_amount`),
    `total_orders` = VALUES(`total_orders`), `rating` = VALUES(`rating`),
    `level` = VALUES(`level`), `remark` = VALUES(`remark`), `status` = VALUES(`status`);

-- 采购订单
INSERT INTO `t_purchase_order`
    (`id`, `order_no`, `supplier_id`, `supplier_name`, `status`, `total_amount`,
     `paid_amount`, `currency`, `expected_arrival_at`, `actual_arrival_at`,
     `payment_method`, `payment_status`, `purchaser_id`, `purchaser_name`, `remark`)
VALUES
    (5101, 'PO-20261001-001', 5001, '深圳华强电子厂',    'RECEIVED',   86000.00,  86000.00, 'USD', NOW() - INTERVAL 35 DAY, NOW() - INTERVAL 32 DAY, 'TT',  'PAID',    1, 'admin', '蓝牙耳机首批采购'),
    (5102, 'PO-20261005-002', 5002, '义乌小商品集散中心','RECEIVED',   32000.00,  32000.00, 'USD', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 27 DAY, 'TT',  'PAID',    1, 'admin', '手机支架补货'),
    (5103, 'PO-20261012-003', 5003, '宁波家纺工厂',      'RECEIVED',   45600.00,  45600.00, 'USD', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 21 DAY, 'LC',  'PAID',    1, 'admin', '四件套批量'),
    (5104, 'PO-20261020-004', 5004, '广州美妆代工厂',    'IN_TRANSIT', 52800.00,  26400.00, 'USD', NOW() - INTERVAL 2 DAY,  NULL,                 'TT',  'PARTIAL',1, 'admin', '精华液新品首单'),
    (5105, 'PO-20261028-005', 5005, '苏州精密五金厂',    'PURCHASING', 18900.00,      0.00, 'USD', NOW() + INTERVAL 5 DAY,  NULL,                 'TT',  'UNPAID',  1, 'admin', '五金配件待发货'),
    (5106, 'PO-20261102-006', 5001, '深圳华强电子厂',    'APPROVED',   72000.00,      0.00, 'USD', NOW() + INTERVAL 12 DAY, NULL,                 'LC',  'UNPAID',  1, 'admin', '智能手表芯片备货'),
    (5107, 'PO-20261103-007', 5002, '义乌小商品集散中心','PENDING',    24600.00,      0.00, 'USD', NOW() + INTERVAL 8 DAY,  NULL,                 'TT',  'UNPAID',  1, 'admin', '收纳袋旺季备货'),
    (5108, 'PO-20261015-008', 5006, '广州德迅国际物流',  'CANCELLED',  12400.00,      0.00, 'USD', NOW() - INTERVAL 20 DAY, NULL,                 'TT',  'UNPAID',  1, 'admin', '海运方案报价过高取消'),
    (5109, 'PO-20261018-009', 5004, '广州美妆代工厂',    'RECEIVED',   39400.00,  39400.00, 'USD', NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 19 DAY, 'TT',  'PAID',    1, 'admin', '精华液补货')
ON DUPLICATE KEY UPDATE
    `supplier_name` = VALUES(`supplier_name`), `status` = VALUES(`status`),
    `total_amount` = VALUES(`total_amount`), `paid_amount` = VALUES(`paid_amount`),
    `currency` = VALUES(`currency`), `expected_arrival_at` = VALUES(`expected_arrival_at`),
    `actual_arrival_at` = VALUES(`actual_arrival_at`), `payment_method` = VALUES(`payment_method`),
    `payment_status` = VALUES(`payment_status`), `purchaser_name` = VALUES(`purchaser_name`),
    `remark` = VALUES(`remark`);

-- 采购订单明细
INSERT INTO `t_purchase_order_item`
    (`id`, `order_id`, `sku`, `product_name`, `category`, `quantity`, `unit`,
     `unit_price`, `subtotal`, `received_quantity`, `remark`)
VALUES
    (5201, 5101, 'SKU-BLU-001-BLK', '无线蓝牙耳机 Pro',   '数码3C',   500, 'PCS', 120.0000, 60000.00, 500, '首批 500 台'),
    (5202, 5101, 'SKU-BLU-001-WHT', '无线蓝牙耳机 Pro 白色','数码3C',  200, 'PCS', 130.0000, 26000.00, 200, '白色版'),
    (5203, 5102, 'SKU-STN-004',     '便携手机支架',       '数码配件', 800, 'PCS',  40.0000, 32000.00, 800, NULL),
    (5204, 5103, 'SKU-HOM-011',     '纯棉四件套',         '家纺',     300, 'SET', 152.0000, 45600.00, 300, '标准装'),
    (5205, 5104, 'SKU-BTY-010',     '精华液 30ml',        '美妆个护',1200, 'PCS',  44.0000, 52800.00,   0, '在途，尚未收货'),
    (5206, 5105, 'SKU-HRD-003',     '精密五金配件',       '五金工具', 600, 'PCS',  31.5000, 18900.00,   0, '供应商备货中'),
    (5207, 5106, 'SKU-WAT-002-SLV', '智能手表 X1',        '数码3C',   400, 'PCS', 180.0000, 72000.00,   0, '含芯片方案'),
    (5208, 5107, 'SKU-STG-007',     '真空收纳袋 10 件',   '家居日用',1000, 'PCS',  24.6000, 24600.00,   0, '旺季备货'),
    (5209, 5108, 'SKU-LOG-001',     '海运服务费',         '物流仓储',   1, 'SET',12400.0000,12400.00,   0, '方案取消'),
    (5210, 5109, 'SKU-BTY-010',     '精华液 30ml',        '美妆个护', 700, 'PCS',  56.2857, 39400.00, 700, '补货')
ON DUPLICATE KEY UPDATE
    `product_name` = VALUES(`product_name`), `category` = VALUES(`category`),
    `quantity` = VALUES(`quantity`), `unit` = VALUES(`unit`),
    `unit_price` = VALUES(`unit_price`), `subtotal` = VALUES(`subtotal`),
    `received_quantity` = VALUES(`received_quantity`), `remark` = VALUES(`remark`);

-- 供应商绩效评估
INSERT INTO `t_supplier_evaluation`
    (`id`, `supplier_id`, `quality_score`, `delivery_score`, `price_score`,
     `service_score`, `tech_score`, `total_score`, `conclusion`, `advantages`,
     `improvements`, `remark`, `evaluator_id`, `evaluator_name`, `evaluate_at`)
VALUES
    (5301, 5001, 5, 4, 4, 5, 5, 4.55, 'EXCELLENT', '良率高、交期准、配合度高', '报价可再谈', '长期主力供应商', 1, 'admin', NOW() - INTERVAL 10 DAY),
    (5302, 5001, 4, 5, 4, 4, 5, 4.35, 'GOOD',      '产能充足',               '批次一致性待提升', 'Q3 评估',   1, 'admin', NOW() - INTERVAL 75 DAY),
    (5303, 5002, 4, 4, 5, 4, 3, 4.10, 'GOOD',      '价格有优势、款式多',     '质检流程较弱',       '小家电类',     1, 'admin', NOW() - INTERVAL 20 DAY),
    (5304, 5003, 5, 4, 4, 5, 4, 4.45, 'GOOD',      '面料品质好',             '交期偶有延误',       '家纺类',       1, 'admin', NOW() - INTERVAL 40 DAY),
    (5305, 5004, 5, 5, 3, 5, 4, 4.50, 'EXCELLENT',      '打样快、配合度高',       '单价偏高',           '美妆类',       1, 'admin', NOW() - INTERVAL 12 DAY),
    (5306, 5005, 4, 3, 4, 4, 4, 3.75, 'ACCEPTABLE',      '工艺稳定',               '起订量高、交期长',   '五金类',       1, 'admin', NOW() - INTERVAL 55 DAY),
    (5307, 5006, 3, 3, 5, 4, 2, 3.45, 'ACCEPTABLE',      '海运价格低',             '时效波动、清关慢',   '物流类',       1, 'admin', NOW() - INTERVAL 60 DAY),
    (5308, 5007, 3, 2, 5, 3, 2, 3.05, 'ACCEPTABLE',      '成本有优势',             '交期与品控不稳',     '服装类试单',   1, 'admin', NOW() - INTERVAL 30 DAY)
ON DUPLICATE KEY UPDATE
    `quality_score` = VALUES(`quality_score`), `delivery_score` = VALUES(`delivery_score`),
    `price_score` = VALUES(`price_score`), `service_score` = VALUES(`service_score`),
    `tech_score` = VALUES(`tech_score`), `total_score` = VALUES(`total_score`),
    `conclusion` = VALUES(`conclusion`), `advantages` = VALUES(`advantages`),
    `improvements` = VALUES(`improvements`), `remark` = VALUES(`remark`),
    `evaluator_name` = VALUES(`evaluator_name`), `evaluate_at` = VALUES(`evaluate_at`);

-- ============================================================
-- 完
-- ============================================================
