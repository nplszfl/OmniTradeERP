-- ===================================================
-- 仓库服务 (erp-warehouse-service) 数据库初始化脚本
-- MySQL 8.0+
--
-- 覆盖 13 张 wms_* 表：
--   wms_warehouse / wms_warehouse_location / wms_inventory_batch / wms_packing_material
--   wms_wave_picking / wms_wave_picking_detail
--   wms_inbound_order / wms_inbound_detail
--   wms_outbound_order / wms_outbound_detail
--   wms_stock_transfer / wms_stock_transfer_detail
--   wms_inventory_check_result
--
-- id 生成策略：
--   IdType.AUTO       → BIGINT AUTO_INCREMENT（wms_warehouse / location / batch /
--                        packing_material / wave_picking）
--   IdType.ASSIGN_ID  → BIGINT（雪花 ID，由应用生成，不自增）
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_warehouse` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_warehouse`;

-- ---------------- 仓库 ----------------
DROP TABLE IF EXISTS wms_warehouse;
CREATE TABLE wms_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    warehouse_code VARCHAR(50) NOT NULL COMMENT '仓库编码',
    name VARCHAR(100) NOT NULL COMMENT '仓库名称',
    alias_name VARCHAR(100) COMMENT '仓库别名',
    country VARCHAR(50) COMMENT '国家',
    province VARCHAR(50) COMMENT '省/州',
    city VARCHAR(50) COMMENT '城市',
    district VARCHAR(50) COMMENT '区县',
    address VARCHAR(255) COMMENT '详细地址',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(30) COMMENT '联系电话',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态 1-启用 0-停用',
    warehouse_type VARCHAR(30) COMMENT '仓库类型',
    total_area DECIMAL(18,2) DEFAULT 0 COMMENT '总面积',
    usable_area DECIMAL(18,2) DEFAULT 0 COMMENT '可用面积',
    capacity INT DEFAULT 0 COMMENT '容量',
    time_zone VARCHAR(50) COMMENT '时区',
    currency VARCHAR(10) COMMENT '币种',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_warehouse_code (warehouse_code),
    KEY idx_warehouse_city (country, city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库';

-- ---------------- 库位 ----------------
DROP TABLE IF EXISTS wms_warehouse_location;
CREATE TABLE wms_warehouse_location (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL COMMENT '所属仓库',
    product_id BIGINT COMMENT '库位存放的商品（库位为 SKU 维度）',
    location_code VARCHAR(50) NOT NULL COMMENT '库位编码',
    location_name VARCHAR(100) COMMENT '库位名称',
    location_type VARCHAR(30) COMMENT '库位类型',
    zone VARCHAR(30) COMMENT '库区',
    row_no VARCHAR(10) COMMENT '排（row 为 SQL 保留字）',
    column_no VARCHAR(10) COMMENT '列（column 为 SQL 保留字）',
    level_no VARCHAR(10) COMMENT '层（level 为 SQL 保留字）',
    stock_quantity INT DEFAULT 0 COMMENT '库存数量',
    locked_quantity INT DEFAULT 0 COMMENT '锁定（占用）数量',
    max_capacity INT DEFAULT 0 COMMENT '最大容量',
    current_capacity INT DEFAULT 0 COMMENT '当前容量',
    status VARCHAR(20) COMMENT '状态 AVAILABLE/OCCUPIED/disabled',
    picking_priority VARCHAR(20) COMMENT '拣货优先级',
    is_pickable TINYINT(1) DEFAULT 1 COMMENT '是否可拣货',
    is_storable TINYINT(1) DEFAULT 1 COMMENT '是否可存储',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_warehouse_location (warehouse_id, location_code),
    KEY idx_location_product (warehouse_id, product_id),
    KEY idx_location_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库位';

-- ---------------- 库存批次 ----------------
DROP TABLE IF EXISTS wms_inventory_batch;
CREATE TABLE wms_inventory_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL COMMENT '批次号',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) COMMENT 'SKU',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    location_id BIGINT COMMENT '库位ID',
    quantity INT DEFAULT 0 COMMENT '总数量',
    available_quantity INT DEFAULT 0 COMMENT '可用数量',
    locked_quantity INT DEFAULT 0 COMMENT '锁定数量',
    damaged_quantity INT DEFAULT 0 COMMENT '破损数量',
    expired_quantity INT DEFAULT 0 COMMENT '过期数量',
    batch_status VARCHAR(20) COMMENT '批次状态 NORMAL/FROZEN/EXPIRED/DAMAGED',
    production_date DATETIME COMMENT '生产日期',
    expiry_date DATETIME COMMENT '过期日期',
    inbound_date DATETIME COMMENT '入库日期',
    supplier_code VARCHAR(50) COMMENT '供应商编码',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    purchase_order_no VARCHAR(50) COMMENT '采购单号',
    unit_cost DECIMAL(18,4) DEFAULT 0 COMMENT '单位成本',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_batch_no (batch_no),
    KEY idx_batch_product (product_id, warehouse_id),
    KEY idx_batch_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存批次';

-- ---------------- 包装材料 ----------------
DROP TABLE IF EXISTS wms_packing_material;
CREATE TABLE wms_packing_material (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    material_code VARCHAR(50) NOT NULL COMMENT '材料编码',
    material_name VARCHAR(100) NOT NULL COMMENT '材料名称',
    material_type VARCHAR(30) COMMENT '材料类型',
    specifications VARCHAR(100) COMMENT '规格',
    weight DOUBLE DEFAULT 0 COMMENT '重量',
    length DOUBLE DEFAULT 0 COMMENT '长',
    width DOUBLE DEFAULT 0 COMMENT '宽',
    height DOUBLE DEFAULT 0 COMMENT '高',
    stock_quantity INT DEFAULT 0 COMMENT '库存数量',
    min_stock INT DEFAULT 0 COMMENT '最低库存',
    max_stock INT DEFAULT 0 COMMENT '最高库存',
    unit VARCHAR(20) COMMENT '单位',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    supplier_code VARCHAR(50) COMMENT '供应商编码',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    status TINYINT DEFAULT 1 COMMENT '状态 1-启用 0-停用',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_material_code (material_code),
    KEY idx_material_type (material_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='包装材料';

-- ---------------- 波次拣货 ----------------
DROP TABLE IF EXISTS wms_wave_picking;
CREATE TABLE wms_wave_picking (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wave_no VARCHAR(50) NOT NULL COMMENT '波次号',
    wave_type VARCHAR(30) COMMENT '波次类型',
    wave_status VARCHAR(20) COMMENT '波次状态 CREATED/ASSIGNED/PICKING/COMPLETED/CANCELLED',
    order_count INT DEFAULT 0 COMMENT '订单数',
    sku_count INT DEFAULT 0 COMMENT 'SKU 数',
    total_quantity INT DEFAULT 0 COMMENT '总数量',
    picked_quantity INT DEFAULT 0 COMMENT '已拣数量',
    priority VARCHAR(20) COMMENT '优先级',
    assign_user VARCHAR(50) COMMENT '分配人',
    assign_time DATETIME COMMENT '分配时间',
    start_time DATETIME COMMENT '开始时间',
    complete_time DATETIME COMMENT '完成时间',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_wave_no (wave_no),
    KEY idx_wave_status (wave_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='波次拣货';

DROP TABLE IF EXISTS wms_wave_picking_detail;
CREATE TABLE wms_wave_picking_detail (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    wave_id BIGINT NOT NULL COMMENT '波次ID',
    order_id BIGINT COMMENT '订单ID',
    order_no VARCHAR(50) COMMENT '订单号',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) COMMENT 'SKU',
    product_name VARCHAR(200) COMMENT '商品名称',
    pick_quantity INT DEFAULT 0 COMMENT '待拣数量',
    picked_quantity INT DEFAULT 0 COMMENT '已拣数量',
    from_location_id BIGINT COMMENT '来源库位ID',
    from_location_code VARCHAR(50) COMMENT '来源库位编码',
    pick_status VARCHAR(20) COMMENT '拣货状态 PENDING/PICKING/PICKED',
    pick_time DATETIME COMMENT '拣货时间',
    picker VARCHAR(50) COMMENT '拣货人',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    KEY idx_wave_detail_wave (wave_id),
    KEY idx_wave_detail_status (pick_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='波次拣货明细';

-- ---------------- 入库 ----------------
DROP TABLE IF EXISTS wms_inbound_order;
CREATE TABLE wms_inbound_order (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    order_no VARCHAR(50) NOT NULL COMMENT '入库单号',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    inbound_type VARCHAR(30) COMMENT '入库类型 PURCHASE/RETURN/TRANSFER/PRODUCTION/OTHER',
    supplier_code VARCHAR(50) COMMENT '供应商编码',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    purchase_order_no VARCHAR(50) COMMENT '采购单号',
    expected_arrival_time DATETIME COMMENT '预计到货时间',
    actual_arrival_time DATETIME COMMENT '实际到货时间',
    inbound_time DATETIME COMMENT '入库时间',
    total_quantity INT DEFAULT 0 COMMENT '总数量',
    received_quantity INT DEFAULT 0 COMMENT '已收数量',
    difference_quantity INT DEFAULT 0 COMMENT '差异数量',
    status TINYINT DEFAULT 0 COMMENT '状态 0-待入库 1-已入库 -1-已取消',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_inbound_order_no (order_no),
    KEY idx_inbound_warehouse (warehouse_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单';

DROP TABLE IF EXISTS wms_inbound_detail;
CREATE TABLE wms_inbound_detail (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    inbound_order_id BIGINT NOT NULL COMMENT '入库单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) COMMENT 'SKU',
    product_name VARCHAR(200) COMMENT '商品名称',
    quantity INT DEFAULT 0 COMMENT '计划数量',
    expected_quantity INT DEFAULT 0 COMMENT '应收数量',
    received_quantity INT DEFAULT 0 COMMENT '实收数量',
    difference_quantity INT DEFAULT 0 COMMENT '差异数量',
    location_code VARCHAR(50) COMMENT '库位编码',
    batch_no VARCHAR(50) COMMENT '批次号',
    production_date DATETIME COMMENT '生产日期',
    expiry_date DATETIME COMMENT '过期日期',
    unit_cost DECIMAL(18,4) DEFAULT 0 COMMENT '单位成本',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    status VARCHAR(20) COMMENT '明细状态 PENDING/RECEIVED',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    KEY idx_inbound_detail_order (inbound_order_id),
    KEY idx_inbound_detail_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库明细';

-- ---------------- 出库 ----------------
DROP TABLE IF EXISTS wms_outbound_order;
CREATE TABLE wms_outbound_order (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    order_no VARCHAR(50) NOT NULL COMMENT '出库单号',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    outbound_type VARCHAR(30) COMMENT '出库类型 SALE/TRANSFER/RETURN/SAMPLE/DAMAGE/OTHER',
    customer_name VARCHAR(200) COMMENT '客户名称',
    shipping_address VARCHAR(255) COMMENT '收货地址',
    shipping_method VARCHAR(30) COMMENT '物流方式',
    tracking_no VARCHAR(100) COMMENT '物流单号',
    expected_ship_time DATETIME COMMENT '预计发货时间',
    actual_ship_time DATETIME COMMENT '实际发货时间',
    outbound_time DATETIME COMMENT '出库时间',
    total_quantity INT DEFAULT 0 COMMENT '总数量',
    picked_quantity INT DEFAULT 0 COMMENT '已拣数量',
    packed_quantity INT DEFAULT 0 COMMENT '已打包数量',
    status TINYINT DEFAULT 0 COMMENT '状态 0-待出库 1-已出库 -1-已取消',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_outbound_order_no (order_no),
    KEY idx_outbound_warehouse (warehouse_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单';

DROP TABLE IF EXISTS wms_outbound_detail;
CREATE TABLE wms_outbound_detail (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    outbound_order_id BIGINT NOT NULL COMMENT '出库单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) COMMENT 'SKU',
    product_name VARCHAR(200) COMMENT '商品名称',
    quantity INT DEFAULT 0 COMMENT '应出数量',
    picked_quantity INT DEFAULT 0 COMMENT '已拣数量',
    packed_quantity INT DEFAULT 0 COMMENT '已打包数量',
    location_code VARCHAR(50) COMMENT '库位编码',
    batch_no VARCHAR(50) COMMENT '批次号',
    status VARCHAR(20) COMMENT '明细状态 PENDING/PICKED/SHIPPED',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    KEY idx_outbound_detail_order (outbound_order_id),
    KEY idx_outbound_detail_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库明细';

-- ---------------- 库存调拨 ----------------
DROP TABLE IF EXISTS wms_stock_transfer;
CREATE TABLE wms_stock_transfer (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    transfer_no VARCHAR(50) NOT NULL COMMENT '调拨单号',
    from_warehouse_id BIGINT NOT NULL COMMENT '调出仓库ID',
    to_warehouse_id BIGINT NOT NULL COMMENT '调入仓库ID',
    transfer_type VARCHAR(30) COMMENT '调拨类型 TRANSFER/RETURN',
    product_id BIGINT COMMENT '商品ID（单商品调拨冗余）',
    sku_code VARCHAR(100) COMMENT 'SKU',
    quantity INT DEFAULT 0 COMMENT '调拨数量（单商品调拨冗余）',
    from_location_code VARCHAR(50) COMMENT '调出库位编码',
    to_location_code VARCHAR(50) COMMENT '调入库位编码',
    total_quantity INT DEFAULT 0 COMMENT '总数量',
    in_transit_quantity INT DEFAULT 0 COMMENT '在途数量',
    received_quantity INT DEFAULT 0 COMMENT '已收数量',
    expected_arrival_time DATETIME COMMENT '预计到达时间',
    actual_arrival_time DATETIME COMMENT '实际到达时间',
    transfer_time DATETIME COMMENT '调拨时间',
    status TINYINT DEFAULT 0 COMMENT '状态 0-待调拨 1-已调拨 2-已收货 -1-已取消',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_transfer_from (from_warehouse_id, status),
    KEY idx_transfer_to (to_warehouse_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨单';

DROP TABLE IF EXISTS wms_stock_transfer_detail;
CREATE TABLE wms_stock_transfer_detail (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    transfer_order_id BIGINT NOT NULL COMMENT '调拨单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) COMMENT 'SKU',
    product_name VARCHAR(200) COMMENT '商品名称',
    quantity INT DEFAULT 0 COMMENT '调拨数量',
    in_transit_quantity INT DEFAULT 0 COMMENT '在途数量',
    received_quantity INT DEFAULT 0 COMMENT '已收数量',
    from_location_code VARCHAR(50) COMMENT '调出库位编码',
    to_location_code VARCHAR(50) COMMENT '调入库位编码',
    batch_no VARCHAR(50) COMMENT '批次号',
    status VARCHAR(20) COMMENT '明细状态 PENDING/TRANSFERRED/RECEIVED',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    KEY idx_transfer_detail_order (transfer_order_id),
    KEY idx_transfer_detail_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存调拨明细';

-- ---------------- 库存盘点 ----------------
DROP TABLE IF EXISTS wms_inventory_check_result;
CREATE TABLE wms_inventory_check_result (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '雪花ID',
    check_id BIGINT NOT NULL COMMENT '盘点单ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    location_id BIGINT COMMENT '库位ID',
    location_code VARCHAR(50) COMMENT '库位编码',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_code VARCHAR(100) COMMENT 'SKU',
    product_name VARCHAR(200) COMMENT '商品名称',
    book_quantity INT DEFAULT 0 COMMENT '账面数量',
    actual_quantity INT DEFAULT 0 COMMENT '实盘数量',
    diff_qty INT DEFAULT 0 COMMENT '差异数量（实盘-账面）',
    status VARCHAR(20) COMMENT '状态 PENDING/CONFIRMED',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    KEY idx_check_result_check (check_id),
    KEY idx_check_result_location (warehouse_id, location_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存盘点结果';
