-- ============================================================
-- 仓库服务种子数据（erp-warehouse-service）
--
-- ⚠️ 为什么单独一个文件（而不是并进 seed-live-data.sql）：
--    docker-entrypoint-initdb.d 里每个 .sql 是**一个独立的 mysql 会话、无事务**，
--    任何一条语句报错 → **从出错行起该文件剩余语句全部不执行**，
--    而容器退出码可能仍是 0 → 后面几百行种子**静默丢失**，
--    页面看着像「没数据」。拆成独立文件 = 故障隔离。
--
-- ⚠️ zz- 前缀是必须的：
--    docker-entrypoint-initdb.d 按**文件名字母序**执行，
--    而 warehouse-service.sql 里有 DROP TABLE IF EXISTS —— 种子必须排在它后面。
--
-- ⚠️ 必须自带 SET NAMES utf8mb4：
--    每个 init 文件由独立的 mysql 会话执行，容器级 locale 兜底不可靠。
--
-- 🔴 本文件补的是「WMS 作业流」这批表 —— 此前**从来没有种子数据**：
--    wms_inbound_order/detail、wms_outbound_order/detail、
--    wms_stock_transfer/detail、wms_wave_picking/detail、
--    wms_inventory_check_result、wms_inventory_batch、wms_packing_material
--    共 11 张表全空 → 入库/出库/调拨/波次拣货/盘点/批次/包装物料
--    **七个页面全是空白**（Controller 全都存在，接口全通，就是没数据）。
--    这是「DDL 建了表」≠「表里有数据」的第四、第五次发作
--    （social 库没建 → supplier 表没数据 → tenant 表没数据 → …）。
--
-- 📌 已有的种子（不要重复）：
--    wms_warehouse(5) / wms_warehouse_location(7) 在 seed-live-data.sql 里
--    —— 这里的所有 warehouse_id / location_id 都引用那 5 个仓库、7 个库位。
--
-- 📌 商品 ID 引用 erp_product.t_product 的 1001~1008：
--    1001 无线蓝牙耳机 Pro / 1002 智能手表 X1 / 1003 65W GaN 充电器
--    1004 便携手机支架 / 1005 20000mAh 充电宝 / 1006 硅胶厨具套装
--    1007 真空收纳袋 10 件 / 1008 男士速干 T 恤
--
-- 📌 状态取值必须和后端逐字一致：
--    wms_inbound_order.status    TINYINT  0-待入库 1-已入库 -1-已取消
--    wms_outbound_order.status   TINYINT  0-待出库 1-已出库 -1-已取消
--    wms_stock_transfer.status    TINYINT  0-待调拨 1-已调拨 2-已收货 -1-已取消
--    wms_inbound_detail.status   VARCHAR  PENDING/RECEIVED
--    wms_outbound_detail.status  VARCHAR  PENDING/PICKED/SHIPPED
--    wms_stock_transfer_detail.status VARCHAR PENDING/TRANSFERRED/RECEIVED
--    wms_wave_picking.wave_status VARCHAR CREATED/ASSIGNED/PICKING/COMPLETED/CANCELLED
--    wms_wave_picking_detail.pick_status VARCHAR PENDING/PICKING/PICKED
--    wms_inventory_check_result.status VARCHAR PENDING/CONFIRMED
--    wms_inventory_batch.batch_status VARCHAR NORMAL/FROZEN/EXPIRED/DAMAGED
SET NAMES utf8mb4;

USE `erp_warehouse`;

-- ============================================================
-- 1. 库存批次（入库产生的批次，其他表引用 batch_no）
-- ============================================================
-- 📌 batch_no 有唯一键，重复执行要幂等 → 用 ON DUPLICATE KEY UPDATE。
INSERT INTO `wms_inventory_batch`
    (`id`, `batch_no`, `product_id`, `sku_code`, `warehouse_id`, `location_id`,
     `quantity`, `available_quantity`, `locked_quantity`, `damaged_quantity`, `expired_quantity`,
     `batch_status`, `production_date`, `expiry_date`, `inbound_date`,
     `supplier_code`, `supplier_name`, `purchase_order_no`, `unit_cost`, `total_amount`,
     `remark`, `create_time`, `update_time`)
VALUES
    (9001, 'BATCH-2026-1001-01', 1001, 'SKU-BLU-001', 1, 1, 400, 380, 20, 0, 0,
     'NORMAL', '2026-01-15 00:00:00', '2028-01-15 00:00:00', '2026-02-01 10:00:00',
     'SUP-001', '深圳市智联电子有限公司', 'PO-2026-0001', 42.5000, 17000.0000,
     '蓝牙耳机首批入库', '2026-02-01 10:00:00', NOW()),
    (9002, 'BATCH-2026-1002-01', 1002, 'SKU-WAT-002', 1, 2, 285, 270, 15, 0, 0,
     'NORMAL', '2026-03-01 00:00:00', '2028-03-01 00:00:00', '2026-03-10 09:30:00',
     'SUP-002', '深圳市蓝海智能科技有限公司', 'PO-2026-0002', 128.0000, 36480.0000,
     '智能手表首批入库', '2026-03-10 09:30:00', NOW()),
    (9003, 'BATCH-2026-1003-01', 1003, 'SKU-USB-003', 1, 3, 1660, 1610, 50, 0, 0,
     'NORMAL', '2026-04-05 00:00:00', '2029-04-05 00:00:00', '2026-04-12 14:20:00',
     'SUP-003', '东莞速达电子厂', 'PO-2026-0003', 18.8000, 31208.0000,
     'GaN 充电器大货', '2026-04-12 14:20:00', NOW()),
    (9004, 'BATCH-2026-1005-01', 1005, 'SKU-PWB-005', 2, 4, 890, 850, 40, 0, 0,
     'NORMAL', '2026-05-08 00:00:00', '2029-05-08 00:00:00', '2026-05-15 11:00:00',
     'SUP-004', '广州华强电子元件行', 'PO-2026-0004', 26.0000, 23140.0000,
     '充电宝保税入库', '2026-05-15 11:00:00', NOW()),
    (9005, 'BATCH-2026-1001-US', 1001, 'SKU-BLU-001', 3, 5, 48, 46, 2, 0, 0,
     'NORMAL', '2026-06-10 00:00:00', '2028-06-10 00:00:00', '2026-06-20 16:45:00',
     'SUP-001', '深圳市智联电子有限公司', 'PO-2026-0005', 46.0000, 2208.0000,
     '洛杉矶仓补货', '2026-06-20 16:45:00', NOW()),
    (9006, 'BATCH-2026-1002-DE', 1002, 'SKU-WAT-002', 4, 6, 18, 16, 2, 0, 0,
     'NORMAL', '2026-07-01 00:00:00', '2028-07-01 00:00:00', '2026-07-08 08:30:00',
     'SUP-002', '深圳市蓝海智能科技有限公司', 'PO-2026-0006', 131.0000, 2358.0000,
     '汉堡仓小批量补货', '2026-07-08 08:30:00', NOW()),
    (9007, 'BATCH-2026-1001-JP', 1001, 'SKU-BLU-001', 5, 7, 28, 26, 2, 0, 0,
     'NORMAL', '2026-08-12 00:00:00', '2028-08-12 00:00:00', '2026-08-18 13:10:00',
     'SUP-001', '深圳市智联电子有限公司', 'PO-2026-0007', 47.5000, 1330.0000,
     '东京仓补货', '2026-08-18 13:10:00', NOW()),
    (9008, 'BATCH-2026-1006-EXPIRED', 1006, 'SKU-KIT-006', 1, 3, 60, 0, 0, 0, 60,
     'EXPIRED', '2025-01-01 00:00:00', '2026-01-01 00:00:00', '2025-01-15 10:00:00',
     'SUP-005', '宁波恒信厨具制造厂', 'PO-2025-0412', 32.0000, 1920.0000,
     '临期厨具套装（已过期，用于验证过期批次处理）', '2025-01-15 10:00:00', NOW()),
    (9009, 'BATCH-2026-1007-FROZEN', 1007, 'SKU-STG-007', 1, 3, 120, 0, 120, 0, 0,
     'FROZEN', '2026-09-01 00:00:00', '2030-09-01 00:00:00', '2026-09-12 09:00:00',
     'SUP-006', '义乌市新光日用品厂', 'PO-2026-0008', 12.5000, 1500.0000,
     '质量冻结批次（全部锁定）', '2026-09-12 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `product_id` = VALUES(`product_id`), `sku_code` = VALUES(`sku_code`),
    `warehouse_id` = VALUES(`warehouse_id`), `location_id` = VALUES(`location_id`),
    `quantity` = VALUES(`quantity`), `available_quantity` = VALUES(`available_quantity`),
    `locked_quantity` = VALUES(`locked_quantity`), `damaged_quantity` = VALUES(`damaged_quantity`),
    `expired_quantity` = VALUES(`expired_quantity`), `batch_status` = VALUES(`batch_status`),
    `expiry_date` = VALUES(`expiry_date`), `unit_cost` = VALUES(`unit_cost`),
    `total_amount` = VALUES(`total_amount`), `remark` = VALUES(`remark`),
    `update_time` = NOW();

-- ============================================================
-- 2. 包装材料
-- ============================================================
INSERT INTO `wms_packing_material`
    (`id`, `material_code`, `material_name`, `material_type`, `specifications`,
     `weight`, `length`, `width`, `height`, `stock_quantity`, `min_stock`, `max_stock`,
     `unit`, `unit_price`, `supplier_code`, `supplier_name`, `status`, `remark`,
     `create_time`, `update_time`)
VALUES
    (9101, 'MAT-CTN-S', '小号瓦楞纸箱', 'CARTON', '40×30×25cm', 0.42, 40, 30, 25, 3200, 500, 8000, '个', 1.2500, 'SUP-010', '东莞恒达包装制品厂', 1, '蓝牙耳机外箱', NOW(), NOW()),
    (9102, 'MAT-CTN-M', '中号瓦楞纸箱', 'CARTON', '55×40×35cm', 0.68, 55, 40, 35, 1850, 300, 5000, '个', 2.1000, 'SUP-010', '东莞恒达包装制品厂', 1, '智能手表外箱', NOW(), NOW()),
    (9103, 'MAT-CTN-L', '大号瓦楞纸箱', 'CARTON', '70×50×45cm', 1.05, 70, 50, 45, 940, 200, 3000, '个', 3.4000, 'SUP-010', '东莞恒达包装制品厂', 1, '大件商品外箱', NOW(), NOW()),
    (9104, 'MAT-BUBBLE-50', '50mm 气泡膜', 'PROTECT', '50cm×100m', 2.50, 50, 0, 0, 640, 100, 2000, '卷', 18.0000, 'SUP-011', '深圳天顺包装材料行', 1, '防震防压', NOW(), NOW()),
    (9105, 'MAT-TAPE-48', '48mm 封箱胶带', 'CONSUMABLE', '48mm×100m', 0.28, 48, 0, 0, 4200, 800, 12000, '卷', 3.6000, 'SUP-011', '深圳天顺包装材料行', 1, '日常封箱耗材', NOW(), NOW()),
    (9106, 'MAT-EXPR-BUB', '快递珍珠棉信封', 'PROTECT', '35×45cm', 0.05, 35, 45, 1, 1560, 300, 5000, '个', 0.8500, 'SUP-011', '深圳天顺包装材料行', 1, '小件防震', NOW(), NOW()),
    (9107, 'MAT-LABEL-10', '热敏标签纸 10×15cm', 'CONSUMABLE', '10×15cm', 0.01, 10, 15, 0, 8800, 2000, 20000, '卷', 12.5000, 'SUP-012', '珠海正品办公用品', 1, '面单打印', NOW(), NOW()),
    (9108, 'MAT-PALLET-P', '塑料托盘', 'PALLET', '1200×1000×150mm', 12.50, 1200, 1000, 150, 260, 50, 800, '个', 68.0000, 'SUP-013', '佛山联运塑业', 1, '仓储周转托盘', NOW(), NOW()),
    (9109, 'MAT-FOAM-20', '20mm 珍珠棉板材', 'PROTECT', '1000×500×20mm', 0.35, 1000, 500, 20, 45, 100, 500, '张', 22.0000, 'SUP-011', '深圳天顺包装材料行', 0, '库存不足，已停用', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `material_name` = VALUES(`material_name`), `material_type` = VALUES(`material_type`),
    `specifications` = VALUES(`specifications`), `stock_quantity` = VALUES(`stock_quantity`),
    `min_stock` = VALUES(`min_stock`), `max_stock` = VALUES(`max_stock`),
    `unit` = VALUES(`unit`), `unit_price` = VALUES(`unit_price`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`), `update_time` = NOW();

-- ============================================================
-- 3. 入库单 + 入库明细
-- ============================================================
-- 📌 id 是雪花 ID（ASSIGN_ID，不自增）→ 手工指定固定值，保持幂等。
--    1001+ 用于入库单，5001+ 用于明细。
INSERT INTO `wms_inbound_order`
    (`id`, `order_no`, `warehouse_id`, `inbound_type`, `supplier_code`, `supplier_name`,
     `purchase_order_no`, `expected_arrival_time`, `actual_arrival_time`, `inbound_time`,
     `total_quantity`, `received_quantity`, `difference_quantity`, `status`, `remark`,
     `create_time`, `update_time`)
VALUES
    (1001, 'IN-2026-0001', 1, 'PURCHASE', 'SUP-001', '深圳市智联电子有限公司',
     'PO-2026-0001', '2026-02-01 09:00:00', '2026-02-01 10:00:00', '2026-02-01 10:05:00',
     400, 400, 0, 1, '蓝牙耳机首批入库', '2026-01-28 09:00:00', NOW()),
    (1002, 'IN-2026-0002', 1, 'PURCHASE', 'SUP-002', '深圳市蓝海智能科技有限公司',
     'PO-2026-0002', '2026-03-10 09:00:00', '2026-03-10 09:30:00', '2026-03-10 09:35:00',
     285, 285, 0, 1, '智能手表首批入库', '2026-03-05 10:00:00', NOW()),
    (1003, 'IN-2026-0003', 2, 'PURCHASE', 'SUP-004', '广州华强电子元件行',
     'PO-2026-0004', '2026-05-15 10:00:00', '2026-05-15 11:00:00', '2026-05-15 11:10:00',
     890, 890, 0, 1, '充电宝保税入库', '2026-05-10 08:00:00', NOW()),
    (1004, 'IN-2026-0004', 3, 'PURCHASE', 'SUP-001', '深圳市智联电子有限公司',
     'PO-2026-0005', '2026-06-20 16:00:00', '2026-06-20 16:45:00', '2026-06-20 16:50:00',
     48, 48, 0, 1, '洛杉矶仓补货', '2026-06-16 11:00:00', NOW()),
    (1005, 'IN-2026-0005', 4, 'PURCHASE', 'SUP-002', '深圳市蓝海智能科技有限公司',
     'PO-2026-0006', '2026-07-08 08:00:00', '2026-07-08 08:30:00', '2026-07-08 08:35:00',
     18, 18, 0, 1, '汉堡仓小批量补货', '2026-07-04 09:00:00', NOW()),
    (1006, 'IN-2026-0006', 5, 'PURCHASE', 'SUP-001', '深圳市智联电子有限公司',
     'PO-2026-0007', '2026-08-18 13:00:00', '2026-08-18 13:10:00', '2026-08-18 13:15:00',
     28, 28, 0, 1, '东京仓补货', '2026-08-14 10:00:00', NOW()),
    (1007, 'IN-2026-0007', 1, 'PURCHASE', 'SUP-003', '东莞速达电子厂',
     'PO-2026-0008', '2026-09-25 09:00:00', NULL, NULL,
     1200, 0, 0, 0, '充电器大货（待入库）', '2026-09-20 09:00:00', NOW()),
    (1008, 'IN-2026-0008', 1, 'RETURN', 'SUP-002', '深圳市蓝海智能科技有限公司',
     NULL, '2026-10-12 14:00:00', '2026-10-12 15:00:00', '2026-10-12 15:10:00',
     12, 10, 2, 1, '客户退货入库（实收 10 短少 2）', '2026-10-10 16:00:00', NOW()),
    (1009, 'IN-2026-0009', 2, 'OTHER', NULL, '内部调拨入库',
     NULL, '2026-10-20 10:00:00', NULL, NULL,
     200, 0, 0, 0, '待入库（内部调拨）', '2026-10-18 09:00:00', NOW()),
    (1010, 'IN-2026-0010', 1, 'PURCHASE', 'SUP-005', '宁波恒信厨具制造厂',
     'PO-2025-0412', '2025-01-15 09:00:00', NULL, NULL,
     60, 0, 0, -1, '临期厨具套装（已取消）', '2025-01-10 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `warehouse_id` = VALUES(`warehouse_id`), `inbound_type` = VALUES(`inbound_type`),
    `supplier_code` = VALUES(`supplier_code`), `supplier_name` = VALUES(`supplier_name`),
    `purchase_order_no` = VALUES(`purchase_order_no`),
    `expected_arrival_time` = VALUES(`expected_arrival_time`),
    `actual_arrival_time` = VALUES(`actual_arrival_time`),
    `inbound_time` = VALUES(`inbound_time`),
    `total_quantity` = VALUES(`total_quantity`),
    `received_quantity` = VALUES(`received_quantity`),
    `difference_quantity` = VALUES(`difference_quantity`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`), `update_time` = NOW();

INSERT INTO `wms_inbound_detail`
    (`id`, `inbound_order_id`, `product_id`, `sku_code`, `product_name`,
     `quantity`, `expected_quantity`, `received_quantity`, `difference_quantity`,
     `location_code`, `batch_no`, `production_date`, `expiry_date`,
     `unit_cost`, `total_amount`, `status`, `remark`, `create_time`, `update_time`)
VALUES
    (5001, 1001, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 400, 400, 400, 0,
     'A-01-01-01', 'BATCH-2026-1001-01', '2026-01-15 00:00:00', '2028-01-15 00:00:00',
     42.5000, 17000.0000, 'RECEIVED', '全数收货', '2026-01-28 09:00:00', NOW()),
    (5002, 1002, 1002, 'SKU-WAT-002', '智能手表 X1', 285, 285, 285, 0,
     'A-01-01-02', 'BATCH-2026-1002-01', '2026-03-01 00:00:00', '2028-03-01 00:00:00',
     128.0000, 36480.0000, 'RECEIVED', '全数收货', '2026-03-05 10:00:00', NOW()),
    (5003, 1003, 1003, 'SKU-USB-003', '65W GaN 充电器', 1660, 1660, 1660, 0,
     'B-02-01-01', 'BATCH-2026-1003-01', '2026-04-05 00:00:00', '2029-04-05 00:00:00',
     18.8000, 31208.0000, 'RECEIVED', '大货到齐', '2026-04-10 08:00:00', NOW()),
    (5004, 1003, 1007, 'SKU-STG-007', '真空收纳袋 10 件', 120, 120, 120, 0,
     'B-02-01-01', 'BATCH-2026-1007-FROZEN', '2026-09-01 00:00:00', '2030-09-01 00:00:00',
     12.5000, 1500.0000, 'RECEIVED', '质量冻结批次', '2026-09-10 09:00:00', NOW()),
    (5005, 1003, 1005, 'SKU-PWB-005', '20000mAh 充电宝', 890, 890, 890, 0,
     'C-01-01-01', 'BATCH-2026-1005-01', '2026-05-08 00:00:00', '2029-05-08 00:00:00',
     26.0000, 23140.0000, 'RECEIVED', '保税仓收货', '2026-05-12 09:00:00', NOW()),
    (5006, 1004, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 48, 48, 48, 0,
     'A-01-01-01', 'BATCH-2026-1001-US', '2026-06-10 00:00:00', '2028-06-10 00:00:00',
     46.0000, 2208.0000, 'RECEIVED', '海外仓补货', '2026-06-16 11:00:00', NOW()),
    (5007, 1005, 1002, 'SKU-WAT-002', '智能手表 X1', 18, 18, 18, 0,
     'A-01-01-01', 'BATCH-2026-1002-DE', '2026-07-01 00:00:00', '2028-07-01 00:00:00',
     131.0000, 2358.0000, 'RECEIVED', '海外仓补货', '2026-07-04 09:00:00', NOW()),
    (5008, 1006, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 28, 28, 28, 0,
     'A-01-01-01', 'BATCH-2026-1001-JP', '2026-08-12 00:00:00', '2028-08-12 00:00:00',
     47.5000, 1330.0000, 'RECEIVED', '海外仓补货', '2026-08-14 10:00:00', NOW()),
    (5009, 1007, 1003, 'SKU-USB-003', '65W GaN 充电器', 1200, 1200, 0, 0,
     'B-02-01-01', NULL, NULL, NULL,
     18.8000, 22560.0000, 'PENDING', '待收货', '2026-09-20 09:00:00', NOW()),
    (5010, 1008, 1002, 'SKU-WAT-002', '智能手表 X1', 12, 12, 10, 2,
     'A-01-01-02', 'BATCH-2026-1002-01', '2026-03-01 00:00:00', '2028-03-01 00:00:00',
     128.0000, 1280.0000, 'RECEIVED', '短少 2 件', '2026-10-10 16:00:00', NOW()),
    (5011, 1009, 1004, 'SKU-STN-004', '便携手机支架', 200, 200, 0, 0,
     'C-01-01-01', NULL, NULL, NULL,
     8.5000, 1700.0000, 'PENDING', '内部调拨待入库', '2026-10-18 09:00:00', NOW()),
    (5012, 1010, 1006, 'SKU-KIT-006', '硅胶厨具套装', 60, 60, 0, 0,
     'B-02-01-01', 'BATCH-2026-1006-EXPIRED', '2025-01-01 00:00:00', '2026-01-01 00:00:00',
     32.0000, 1920.0000, 'PENDING', '订单已取消', '2025-01-10 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `product_id` = VALUES(`product_id`), `sku_code` = VALUES(`sku_code`),
    `product_name` = VALUES(`product_name`), `quantity` = VALUES(`quantity`),
    `expected_quantity` = VALUES(`expected_quantity`),
    `received_quantity` = VALUES(`received_quantity`),
    `difference_quantity` = VALUES(`difference_quantity`),
    `location_code` = VALUES(`location_code`), `batch_no` = VALUES(`batch_no`),
    `unit_cost` = VALUES(`unit_cost`), `total_amount` = VALUES(`total_amount`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`), `update_time` = NOW();

-- ============================================================
-- 4. 出库单 + 出库明细
-- ============================================================
-- 📌 2001+ 出库单，6001+ 出库明细
INSERT INTO `wms_outbound_order`
    (`id`, `order_no`, `warehouse_id`, `outbound_type`, `customer_name`, `shipping_address`,
     `shipping_method`, `tracking_no`, `expected_ship_time`, `actual_ship_time`, `outbound_time`,
     `total_quantity`, `picked_quantity`, `packed_quantity`, `status`, `remark`,
     `create_time`, `update_time`)
VALUES
    (2001, 'OUT-2026-0001', 1, 'SALE', 'Amazon US 客户订单', '美国加州洛杉矶 1000 Logistics Way',
     'FEDEX', 'FDX1002003001', '2026-02-05 09:00:00', '2026-02-05 10:30:00', '2026-02-05 10:40:00',
     20, 20, 20, 1, '亚马逊美国站订单', '2026-02-04 09:00:00', NOW()),
    (2002, 'OUT-2026-0002', 1, 'SALE', 'eBay 英国客户订单', '英国伦敦 E1 6AN',
     'DHL', 'DHL2003004001', '2026-03-12 09:00:00', '2026-03-12 11:00:00', '2026-03-12 11:10:00',
     15, 15, 15, 1, 'eBay 英国站订单', '2026-03-11 09:00:00', NOW()),
    (2003, 'OUT-2026-0003', 2, 'SALE', 'Shopee 新加坡客户订单', '新加坡樟宜机场附近',
     'J&T', 'JT3004005001', '2026-05-18 09:00:00', '2026-05-18 10:00:00', '2026-05-18 10:10:00',
     60, 60, 60, 1, 'Shopee 东南亚订单', '2026-05-17 09:00:00', NOW()),
    (2004, 'OUT-2026-0004', 3, 'SALE', 'TikTok Shop 美国客户订单', '美国德州达拉斯 1500 Commerce St',
     'UPS', 'UPS4005006001', '2026-06-22 09:00:00', '2026-06-22 12:00:00', '2026-06-22 12:10:00',
     10, 10, 10, 1, 'TikTok Shop 美国订单', '2026-06-21 09:00:00', NOW()),
    (2005, 'OUT-2026-0005', 4, 'SALE', 'Lazada 德国客户订单', '德国柏林 Alexanderplatz 1',
     'DPD', 'DPD5006007001', '2026-07-10 09:00:00', '2026-07-10 11:00:00', '2026-07-10 11:10:00',
     6, 6, 6, 1, 'Lazada 德国站订单', '2026-07-09 09:00:00', NOW()),
    (2006, 'OUT-2026-0006', 5, 'SALE', 'TikTok Shop 日本客户订单', '日本东京都港区 1-1-1',
     'Yamato', 'YM6007008001', '2026-08-20 09:00:00', '2026-08-20 13:00:00', '2026-08-20 13:10:00',
     8, 8, 8, 1, 'TikTok Shop 日本订单', '2026-08-19 09:00:00', NOW()),
    (2007, 'OUT-2026-0007', 1, 'SALE', '待处理客户订单', '待补充',
     NULL, NULL, '2026-10-25 09:00:00', NULL, NULL,
     30, 0, 0, 0, '待拣货', '2026-10-23 09:00:00', NOW()),
    (2008, 'OUT-2026-0008', 1, 'SALE', '待拣货客户订单 2', '待补充',
     NULL, NULL, '2026-10-26 09:00:00', NULL, NULL,
     18, 0, 0, 0, '待拣货', '2026-10-24 09:00:00', NOW()),
    (2009, 'OUT-2026-0009', 1, 'SALE', '待出库客户订单 3', '待补充',
     'SF', NULL, '2026-10-27 09:00:00', NULL, NULL,
     12, 12, 0, 0, '已拣货待出库', '2026-10-25 09:00:00', NOW()),
    (2010, 'OUT-2026-0010', 1, 'SAMPLE', '样品寄送客户', '深圳南山科技园',
     'SF', 'SF7008009001', '2026-09-02 09:00:00', '2026-09-02 10:00:00', '2026-09-02 10:05:00',
     2, 2, 2, 1, '寄样', '2026-08-30 09:00:00', NOW()),
    (2011, 'OUT-2026-0011', 1, 'SALE', '已取消客户订单', '待补充',
     NULL, NULL, '2026-09-15 09:00:00', NULL, NULL,
     5, 0, 0, -1, '客户取消', '2026-09-13 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `warehouse_id` = VALUES(`warehouse_id`), `outbound_type` = VALUES(`outbound_type`),
    `customer_name` = VALUES(`customer_name`), `shipping_address` = VALUES(`shipping_address`),
    `shipping_method` = VALUES(`shipping_method`), `tracking_no` = VALUES(`tracking_no`),
    `expected_ship_time` = VALUES(`expected_ship_time`),
    `actual_ship_time` = VALUES(`actual_ship_time`), `outbound_time` = VALUES(`outbound_time`),
    `total_quantity` = VALUES(`total_quantity`), `picked_quantity` = VALUES(`picked_quantity`),
    `packed_quantity` = VALUES(`packed_quantity`), `status` = VALUES(`status`),
    `remark` = VALUES(`remark`), `update_time` = NOW();

INSERT INTO `wms_outbound_detail`
    (`id`, `outbound_order_id`, `product_id`, `sku_code`, `product_name`,
     `quantity`, `picked_quantity`, `packed_quantity`, `location_code`, `batch_no`,
     `status`, `remark`, `create_time`, `update_time`)
VALUES
    (6001, 2001, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 20, 20, 20, 'A-01-01-01', 'BATCH-2026-1001-01', 'SHIPPED', '已发货', '2026-02-04 09:00:00', NOW()),
    (6002, 2002, 1002, 'SKU-WAT-002', '智能手表 X1', 15, 15, 15, 'A-01-01-02', 'BATCH-2026-1002-01', 'SHIPPED', '已发货', '2026-03-11 09:00:00', NOW()),
    (6003, 2003, 1005, 'SKU-PWB-005', '20000mAh 充电宝', 60, 60, 60, 'C-01-01-01', 'BATCH-2026-1005-01', 'SHIPPED', '已发货', '2026-05-17 09:00:00', NOW()),
    (6004, 2004, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 10, 10, 10, 'A-01-01-01', 'BATCH-2026-1001-US', 'SHIPPED', '已发货', '2026-06-21 09:00:00', NOW()),
    (6005, 2005, 1002, 'SKU-WAT-002', '智能手表 X1', 6, 6, 6, 'A-01-01-01', 'BATCH-2026-1002-DE', 'SHIPPED', '已发货', '2026-07-09 09:00:00', NOW()),
    (6006, 2006, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 8, 8, 8, 'A-01-01-01', 'BATCH-2026-1001-JP', 'SHIPPED', '已发货', '2026-08-19 09:00:00', NOW()),
    (6007, 2007, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 20, 0, 0, 'A-01-01-01', 'BATCH-2026-1001-01', 'PENDING', '待拣货', '2026-10-23 09:00:00', NOW()),
    (6008, 2007, 1003, 'SKU-USB-003', '65W GaN 充电器', 10, 0, 0, 'B-02-01-01', 'BATCH-2026-1003-01', 'PENDING', '待拣货', '2026-10-23 09:00:00', NOW()),
    (6009, 2008, 1002, 'SKU-WAT-002', '智能手表 X1', 18, 0, 0, 'A-01-01-02', 'BATCH-2026-1002-01', 'PENDING', '待拣货', '2026-10-24 09:00:00', NOW()),
    (6010, 2009, 1007, 'SKU-STG-007', '真空收纳袋 10 件', 12, 12, 0, 'B-02-01-01', 'BATCH-2026-1007-FROZEN', 'PICKED', '已拣货待出库', '2026-10-25 09:00:00', NOW()),
    (6011, 2010, 1006, 'SKU-KIT-006', '硅胶厨具套装', 2, 2, 2, 'B-02-01-01', 'BATCH-2026-1006-EXPIRED', 'SHIPPED', '寄样', '2026-08-30 09:00:00', NOW()),
    (6012, 2011, 1004, 'SKU-STN-004', '便携手机支架', 5, 0, 0, 'C-01-01-01', NULL, 'PENDING', '客户取消', '2026-09-13 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `product_id` = VALUES(`product_id`), `sku_code` = VALUES(`sku_code`),
    `product_name` = VALUES(`product_name`), `quantity` = VALUES(`quantity`),
    `picked_quantity` = VALUES(`picked_quantity`), `packed_quantity` = VALUES(`packed_quantity`),
    `location_code` = VALUES(`location_code`), `batch_no` = VALUES(`batch_no`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`), `update_time` = NOW();

-- ============================================================
-- 5. 库存调拨单 + 调拨明细
-- ============================================================
-- 📌 3001+ 调拨单，7001+ 调拨明细
--    status: 0-待调拨 1-已调拨 2-已收货 -1-已取消
INSERT INTO `wms_stock_transfer`
    (`id`, `transfer_no`, `from_warehouse_id`, `to_warehouse_id`, `transfer_type`,
     `product_id`, `sku_code`, `quantity`, `from_location_code`, `to_location_code`,
     `total_quantity`, `in_transit_quantity`, `received_quantity`,
     `expected_arrival_time`, `actual_arrival_time`, `transfer_time`, `status`, `remark`,
     `create_time`, `update_time`)
VALUES
    (3001, 'TR-2026-0001', 1, 2, 'TRANSFER', 1001, 'SKU-BLU-001', 50, 'A-01-01-01', 'C-01-01-01',
     50, 0, 0, '2026-06-05 09:00:00', '2026-06-05 10:00:00', '2026-06-05 10:05:00', 2, '上海仓调广州保税仓', '2026-06-01 09:00:00', NOW()),
    (3002, 'TR-2026-0002', 1, 3, 'TRANSFER', 1001, 'SKU-BLU-001', 30, 'A-01-01-01', 'A-01-01-01',
     30, 0, 0, '2026-06-20 09:00:00', '2026-06-20 10:00:00', '2026-06-20 10:05:00', 2, '上海仓调洛杉矶仓', '2026-06-15 09:00:00', NOW()),
    (3003, 'TR-2026-0003', 1, 4, 'TRANSFER', 1002, 'SKU-WAT-002', 12, 'A-01-01-02', 'A-01-01-01',
     12, 0, 0, '2026-07-05 09:00:00', '2026-07-05 10:00:00', '2026-07-05 10:05:00', 2, '上海仓调汉堡仓', '2026-07-01 09:00:00', NOW()),
    (3004, 'TR-2026-0004', 1, 5, 'TRANSFER', 1001, 'SKU-BLU-001', 20, 'A-01-01-01', 'A-01-01-01',
     20, 0, 0, '2026-08-15 09:00:00', '2026-08-15 10:00:00', '2026-08-15 10:05:00', 2, '上海仓调东京仓', '2026-08-10 09:00:00', NOW()),
    (3005, 'TR-2026-0005', 1, 2, 'TRANSFER', 1005, 'SKU-PWB-005', 100, 'A-01-01-01', 'C-01-01-01',
     100, 0, 0, '2026-10-22 09:00:00', NULL, NULL, 1, '已调拨（在途）', '2026-10-20 09:00:00', NOW()),
    (3006, 'TR-2026-0006', 1, 2, 'TRANSFER', 1003, 'SKU-USB-003', 200, 'B-02-01-01', 'C-01-01-01',
     200, 0, 0, '2026-10-28 09:00:00', NULL, NULL, 0, '待调拨', '2026-10-26 09:00:00', NOW()),
    (3007, 'TR-2026-0007', 3, 1, 'RETURN', 1001, 'SKU-BLU-001', 5, 'A-01-01-01', 'A-01-01-01',
     5, 0, 0, '2026-09-10 09:00:00', NULL, NULL, -1, '洛杉矶仓退运，已取消', '2026-09-08 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `from_warehouse_id` = VALUES(`from_warehouse_id`), `to_warehouse_id` = VALUES(`to_warehouse_id`),
    `transfer_type` = VALUES(`transfer_type`), `product_id` = VALUES(`product_id`),
    `sku_code` = VALUES(`sku_code`), `quantity` = VALUES(`quantity`),
    `from_location_code` = VALUES(`from_location_code`), `to_location_code` = VALUES(`to_location_code`),
    `total_quantity` = VALUES(`total_quantity`),
    `in_transit_quantity` = VALUES(`in_transit_quantity`),
    `received_quantity` = VALUES(`received_quantity`),
    `expected_arrival_time` = VALUES(`expected_arrival_time`),
    `actual_arrival_time` = VALUES(`actual_arrival_time`),
    `transfer_time` = VALUES(`transfer_time`), `status` = VALUES(`status`),
    `remark` = VALUES(`remark`), `update_time` = NOW();

INSERT INTO `wms_stock_transfer_detail`
    (`id`, `transfer_order_id`, `product_id`, `sku_code`, `product_name`,
     `quantity`, `in_transit_quantity`, `received_quantity`,
     `from_location_code`, `to_location_code`, `batch_no`, `status`, `remark`,
     `create_time`, `update_time`)
VALUES
    (7001, 3001, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 50, 0, 50, 'A-01-01-01', 'C-01-01-01', 'BATCH-2026-1001-01', 'RECEIVED', '已收货', '2026-06-01 09:00:00', NOW()),
    (7002, 3002, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 30, 0, 30, 'A-01-01-01', 'A-01-01-01', 'BATCH-2026-1001-01', 'RECEIVED', '已收货', '2026-06-15 09:00:00', NOW()),
    (7003, 3003, 1002, 'SKU-WAT-002', '智能手表 X1', 12, 0, 12, 'A-01-01-02', 'A-01-01-01', 'BATCH-2026-1002-01', 'RECEIVED', '已收货', '2026-07-01 09:00:00', NOW()),
    (7004, 3004, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 20, 0, 20, 'A-01-01-01', 'A-01-01-01', 'BATCH-2026-1001-01', 'RECEIVED', '已收货', '2026-08-10 09:00:00', NOW()),
    (7005, 3005, 1005, 'SKU-PWB-005', '20000mAh 充电宝', 100, 100, 0, 'A-01-01-01', 'C-01-01-01', 'BATCH-2026-1005-01', 'TRANSFERRED', '在途', '2026-10-20 09:00:00', NOW()),
    (7006, 3006, 1003, 'SKU-USB-003', '65W GaN 充电器', 200, 0, 0, 'B-02-01-01', 'C-01-01-01', 'BATCH-2026-1003-01', 'PENDING', '待调拨', '2026-10-26 09:00:00', NOW()),
    (7007, 3007, 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 5, 0, 0, 'A-01-01-01', 'A-01-01-01', 'BATCH-2026-1001-US', 'PENDING', '已取消', '2026-09-08 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `product_id` = VALUES(`product_id`), `sku_code` = VALUES(`sku_code`),
    `product_name` = VALUES(`product_name`), `quantity` = VALUES(`quantity`),
    `in_transit_quantity` = VALUES(`in_transit_quantity`),
    `received_quantity` = VALUES(`received_quantity`),
    `from_location_code` = VALUES(`from_location_code`), `to_location_code` = VALUES(`to_location_code`),
    `batch_no` = VALUES(`batch_no`), `status` = VALUES(`status`),
    `remark` = VALUES(`remark`), `update_time` = NOW();

-- ============================================================
-- 6. 波次拣货 + 波次明细
-- ============================================================
-- 📌 波次状态机 CREATED→ASSIGNED→PICKING→COMPLETED/CANCELLED
--    种子要覆盖各状态，页面上的状态筛选才不会是空的。
--    4001+ 波次，8001+ 明细
INSERT INTO `wms_wave_picking`
    (`id`, `wave_no`, `wave_type`, `wave_status`, `order_count`, `sku_count`,
     `total_quantity`, `picked_quantity`, `priority`, `assign_user`, `assign_time`,
     `start_time`, `complete_time`, `remark`, `create_time`, `update_time`)
VALUES
    (4001, 'WV-2026-0001', 'NORMAL', 'COMPLETED', 3, 4, 55, 55, 'HIGH', '李拣货',
     '2026-02-04 08:30:00', '2026-02-04 09:00:00', '2026-02-04 10:30:00', '亚马逊美国站集中波次',
     '2026-02-04 08:00:00', NOW()),
    (4002, 'WV-2026-0002', 'NORMAL', 'COMPLETED', 2, 2, 21, 21, 'NORMAL', '李拣货',
     '2026-03-11 08:30:00', '2026-03-11 09:00:00', '2026-03-11 09:50:00', 'eBay 英国站波次',
     '2026-03-11 08:00:00', NOW()),
    (4003, 'WV-2026-0003', 'BATCH', 'COMPLETED', 5, 3, 120, 120, 'NORMAL', '王拣货',
     '2026-05-17 08:00:00', '2026-05-17 08:30:00', '2026-05-17 11:00:00', 'Shopee 大批量波次',
     '2026-05-17 07:30:00', NOW()),
    (4004, 'WV-2026-0004', 'NORMAL', 'PICKING', 2, 2, 18, 9, 'HIGH', '王拣货',
     '2026-10-25 08:30:00', '2026-10-25 09:00:00', NULL, '进行中的波次',
     '2026-10-25 08:00:00', NOW()),
    (4005, 'WV-2026-0005', 'NORMAL', 'ASSIGNED', 1, 1, 12, 0, 'NORMAL', '赵拣货',
     '2026-10-27 08:00:00', NULL, NULL, '已分配待开始',
     '2026-10-27 07:30:00', NOW()),
    (4006, 'WV-2026-0006', 'NORMAL', 'CREATED', 3, 3, 30, 0, 'NORMAL', NULL,
     NULL, NULL, NULL, '新建待分配',
     '2026-10-28 07:30:00', NOW()),
    (4007, 'WV-2026-0007', 'NORMAL', 'CANCELLED', 1, 1, 5, 0, 'LOW', '李拣货',
     '2026-09-08 08:00:00', NULL, NULL, '货品质量问题取消',
     '2026-09-08 07:30:00', NOW())
ON DUPLICATE KEY UPDATE
    `wave_type` = VALUES(`wave_type`), `wave_status` = VALUES(`wave_status`),
    `order_count` = VALUES(`order_count`), `sku_count` = VALUES(`sku_count`),
    `total_quantity` = VALUES(`total_quantity`), `picked_quantity` = VALUES(`picked_quantity`),
    `priority` = VALUES(`priority`), `assign_user` = VALUES(`assign_user`),
    `assign_time` = VALUES(`assign_time`), `start_time` = VALUES(`start_time`),
    `complete_time` = VALUES(`complete_time`), `remark` = VALUES(`remark`),
    `update_time` = NOW();

INSERT INTO `wms_wave_picking_detail`
    (`id`, `wave_id`, `order_id`, `order_no`, `product_id`, `sku_code`, `product_name`,
     `pick_quantity`, `picked_quantity`, `from_location_id`, `from_location_code`,
     `pick_status`, `pick_time`, `picker`, `remark`, `create_time`, `update_time`)
VALUES
    (8001, 4001, 2001, 'OUT-2026-0001', 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 20, 20, 1, 'A-01-01-01', 'PICKED', '2026-02-04 09:20:00', '李拣货', '已拣完', '2026-02-04 08:00:00', NOW()),
    (8002, 4001, 2001, 'OUT-2026-0001', 1003, 'SKU-USB-003', '65W GaN 充电器', 20, 20, 3, 'B-02-01-01', 'PICKED', '2026-02-04 09:35:00', '李拣货', '已拣完', '2026-02-04 08:00:00', NOW()),
    (8003, 4001, 2002, 'OUT-2026-0002', 1002, 'SKU-WAT-002', '智能手表 X1', 15, 15, 2, 'A-01-01-02', 'PICKED', '2026-02-04 09:50:00', '李拣货', '已拣完', '2026-02-04 08:00:00', NOW()),
    (8004, 4002, 2003, 'OUT-2026-0003', 1005, 'SKU-PWB-005', '20000mAh 充电宝', 21, 21, 4, 'C-01-01-01', 'PICKED', '2026-03-11 09:20:00', '李拣货', '已拣完', '2026-03-11 08:00:00', NOW()),
    (8005, 4003, 2004, 'OUT-2026-0004', 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 60, 60, 1, 'A-01-01-01', 'PICKED', '2026-05-17 09:00:00', '王拣货', '已拣完', '2026-05-17 07:30:00', NOW()),
    (8006, 4003, 2005, 'OUT-2026-0005', 1002, 'SKU-WAT-002', '智能手表 X1', 60, 60, 2, 'A-01-01-02', 'PICKED', '2026-05-17 09:40:00', '王拣货', '已拣完', '2026-05-17 07:30:00', NOW()),
    (8007, 4004, 2007, 'OUT-2026-0007', 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 10, 10, 1, 'A-01-01-01', 'PICKED', '2026-10-25 09:30:00', '王拣货', '已拣完', '2026-10-25 08:00:00', NOW()),
    (8008, 4004, 2007, 'OUT-2026-0007', 1003, 'SKU-USB-003', '65W GaN 充电器', 8, 0, 3, 'B-02-01-01', 'PICKING', NULL, '王拣货', '拣货中', '2026-10-25 08:00:00', NOW()),
    (8009, 4005, 2009, 'OUT-2026-0009', 1007, 'SKU-STG-007', '真空收纳袋 10 件', 12, 0, 3, 'B-02-01-01', 'PENDING', NULL, NULL, '待拣货', '2026-10-27 07:30:00', NOW()),
    (8010, 4006, 2008, 'OUT-2026-0008', 1002, 'SKU-WAT-002', '智能手表 X1', 18, 0, 2, 'A-01-01-02', 'PENDING', NULL, NULL, '待拣货', '2026-10-28 07:30:00', NOW()),
    (8011, 4006, 2009, 'OUT-2026-0009', 1007, 'SKU-STG-007', '真空收纳袋 10 件', 12, 0, 3, 'B-02-01-01', 'PENDING', NULL, NULL, '待拣货', '2026-10-28 07:30:00', NOW()),
    (8012, 4007, 2011, 'OUT-2026-0011', 1004, 'SKU-STN-004', '便携手机支架', 5, 0, 4, 'C-01-01-01', 'PENDING', NULL, NULL, '波次已取消', '2026-09-08 07:30:00', NOW())
ON DUPLICATE KEY UPDATE
    `order_id` = VALUES(`order_id`), `order_no` = VALUES(`order_no`),
    `product_id` = VALUES(`product_id`), `sku_code` = VALUES(`sku_code`),
    `product_name` = VALUES(`product_name`), `pick_quantity` = VALUES(`pick_quantity`),
    `picked_quantity` = VALUES(`picked_quantity`),
    `from_location_id` = VALUES(`from_location_id`), `from_location_code` = VALUES(`from_location_code`),
    `pick_status` = VALUES(`pick_status`), `pick_time` = VALUES(`pick_time`),
    `picker` = VALUES(`picker`), `remark` = VALUES(`remark`), `update_time` = NOW();

-- ============================================================
-- 7. 库存盘点单（主表）+ 盘点结果
-- ============================================================
-- 📌 盘点单主表以前**不存在** —— createInventoryCheck 只打日志不落库，
--    所以这里只能拿「分组键」凑合。现在主表补上了，**必须同时种主表**，
--    否则 wms_inventory_check_result.check_id 指向不存在的盘点单，
--    前端点开盘点单详情会是空的。
--    check_id 7001~7004 与下面的明细行对应。
INSERT INTO `wms_inventory_check`
    (`id`, `check_no`, `warehouse_id`, `check_type`, `check_status`,
     `location_count`, `sku_count`, `total_quantity`, `difference_count`,
     `profit_quantity`, `loss_quantity`, `start_time`, `end_time`,
     `checker`, `remark`, `create_time`, `update_time`)
VALUES
    (7001, 'IC-20260720090000-1', 1, 'FULL', 'CONFIRMED',
     3, 3, 3, 2, 5, 2, '2026-07-20 09:00:00', '2026-07-20 10:30:00',
     '张库存', '7 月月度全盘', '2026-07-20 09:00:00', NOW()),
    (7002, 'IC-20260820090000-2', 2, 'CYCLE', 'CONFIRMED',
     1, 1, 1, 1, 0, 2, '2026-08-20 09:00:00', '2026-08-20 10:00:00',
     '李仓管', '8 月循环盘点', '2026-08-20 09:00:00', NOW()),
    (7003, 'IC-20260920090000-3', 3, 'SPOT', 'CONFIRMED',
     1, 1, 1, 0, 0, 0, '2026-09-20 09:00:00', '2026-09-20 09:30:00',
     '王盘点', '9 月抽盘', '2026-09-20 09:00:00', NOW()),
    (7004, 'IC-20261025090000-1', 1, 'FULL', 'CREATED',
     3, 3, 3, 0, 0, 0, '2026-10-25 09:00:00', NULL,
     '张库存', '10 月月度全盘（待复盘）', '2026-10-25 09:00:00', NOW())
ON DUPLICATE KEY UPDATE
    `check_no` = VALUES(`check_no`), `warehouse_id` = VALUES(`warehouse_id`),
    `check_type` = VALUES(`check_type`), `check_status` = VALUES(`check_status`),
    `location_count` = VALUES(`location_count`), `sku_count` = VALUES(`sku_count`),
    `total_quantity` = VALUES(`total_quantity`), `difference_count` = VALUES(`difference_count`),
    `profit_quantity` = VALUES(`profit_quantity`), `loss_quantity` = VALUES(`loss_quantity`),
    `start_time` = VALUES(`start_time`), `end_time` = VALUES(`end_time`),
    `checker` = VALUES(`checker`), `remark` = VALUES(`remark`), `update_time` = NOW();

-- status: PENDING/CONFIRMED
-- diff_qty = actual_quantity - book_quantity（差异 = 实盘 - 账面）
-- ⚠️ 正数 = 盘盈，负数 = 盘亏 —— confirmInventoryCheck 按这个符号
--    决定是加库存还是减库存，符号写反会越盘点越错。
INSERT INTO `wms_inventory_check_result`
    (`id`, `check_id`, `warehouse_id`, `location_id`, `location_code`,
     `product_id`, `sku_code`, `product_name`, `book_quantity`, `actual_quantity`,
     `diff_qty`, `status`, `remark`, `create_time`, `update_time`)
VALUES
    (11001, 7001, 1, 1, 'A-01-01-01', 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 400, 398, -2, 'CONFIRMED', '盘亏 2 件', '2026-07-20 09:00:00', NOW()),
    (11002, 7001, 1, 2, 'A-01-01-02', 1002, 'SKU-WAT-002', '智能手表 X1', 285, 285, 0, 'CONFIRMED', '账实相符', '2026-07-20 09:10:00', NOW()),
    (11003, 7001, 1, 3, 'B-02-01-01', 1003, 'SKU-USB-003', '65W GaN 充电器', 1660, 1665, 5, 'CONFIRMED', '盘盈 5 件', '2026-07-20 09:20:00', NOW()),
    (11004, 7002, 2, 4, 'C-01-01-01', 1005, 'SKU-PWB-005', '20000mAh 充电宝', 890, 888, -2, 'CONFIRMED', '盘亏 2 件', '2026-08-20 09:00:00', NOW()),
    (11005, 7003, 3, 5, 'A-01-01-01', 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 48, 48, 0, 'CONFIRMED', '账实相符', '2026-09-20 09:00:00', NOW()),
    (11006, 7004, 1, 1, 'A-01-01-01', 1001, 'SKU-BLU-001', '无线蓝牙耳机 Pro', 380, 380, 0, 'PENDING', '待复盘', '2026-10-25 09:00:00', NOW()),
    (11007, 7004, 1, 2, 'A-01-01-02', 1002, 'SKU-WAT-002', '智能手表 X1', 270, 268, -2, 'PENDING', '待复盘', '2026-10-25 09:10:00', NOW()),
    (11008, 7004, 1, 3, 'B-02-01-01', 1003, 'SKU-USB-003', '65W GaN 充电器', 1610, 1610, 0, 'PENDING', '待复盘', '2026-10-25 09:20:00', NOW())
ON DUPLICATE KEY UPDATE
    `check_id` = VALUES(`check_id`), `warehouse_id` = VALUES(`warehouse_id`),
    `location_id` = VALUES(`location_id`), `location_code` = VALUES(`location_code`),
    `product_id` = VALUES(`product_id`), `sku_code` = VALUES(`sku_code`),
    `product_name` = VALUES(`product_name`), `book_quantity` = VALUES(`book_quantity`),
    `actual_quantity` = VALUES(`actual_quantity`), `diff_qty` = VALUES(`diff_qty`),
    `status` = VALUES(`status`), `remark` = VALUES(`remark`), `update_time` = NOW();
