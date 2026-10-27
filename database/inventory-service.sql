-- ===================================================
-- 库存服务数据库初始化脚本 (MySQL)
-- ===================================================
-- 数据库: erp_inventory
--
-- 表清单:
--   erp_inventory              库存记录
--   erp_inventory_log          库存流水
--   erp_inventory_alert        库存预警
--   erp_inventory_lock         库存锁定记录
--   erp_inventory_check        库存盘点单
--   erp_inventory_check_detail 库存盘点明细
--
-- 枚举字段以 VARCHAR 存储枚举 name()（MyBatis-Plus 默认 EnumTypeHandler）：
--   erp_inventory.status              NORMAL / WARNING / DANGER
--   erp_inventory_log.operation_type  INBOUND / OUTBOUND / TRANSFER / LOCK / RELEASE / ADJUST
--   erp_inventory_alert.alert_type    LOW_STOCK / OUT_OF_STOCK / SLOW_MOVING
--   erp_inventory_alert.alert_status  PENDING / PROCESSED
--   erp_inventory_lock.lock_type      RESERVATION / ORDER / CHECK / OTHER
--   erp_inventory_lock.lock_status    LOCKED / RELEASED
--   erp_inventory_check.check_status  PENDING / COMPLETED
--
-- id 生成策略：IdType.AUTO → BIGINT AUTO_INCREMENT
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_inventory` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_inventory`;

-- ---------------- 库存记录 ----------------
DROP TABLE IF EXISTS erp_inventory;
CREATE TABLE erp_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '商品 ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 ID',
    location_id BIGINT COMMENT '库位 ID',
    sku_code VARCHAR(100) COMMENT 'SKU 编码',
    quantity INT NOT NULL DEFAULT 0 COMMENT '总数量',
    available_quantity INT NOT NULL DEFAULT 0 COMMENT '可用数量',
    locked_quantity INT NOT NULL DEFAULT 0 COMMENT '锁定数量',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存',
    unit_cost DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '单位成本',
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '状态 NORMAL/WARNING/DANGER',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_warehouse (product_id, warehouse_id),
    KEY idx_warehouse (warehouse_id),
    KEY idx_sku_code (sku_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存记录';

-- ---------------- 库存流水 ----------------
DROP TABLE IF EXISTS erp_inventory_log;
CREATE TABLE erp_inventory_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT COMMENT '库存记录 ID',
    product_id BIGINT NOT NULL COMMENT '商品 ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 ID',
    quantity INT NOT NULL COMMENT '操作数量',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型',
    before_quantity INT COMMENT '操作前数量',
    after_quantity INT COMMENT '操作后数量',
    order_no VARCHAR(100) COMMENT '关联订单号',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_inventory (inventory_id),
    KEY idx_product_time (product_id, create_time),
    KEY idx_operation (operation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';

-- ---------------- 库存预警 ----------------
DROP TABLE IF EXISTS erp_inventory_alert;
CREATE TABLE erp_inventory_alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT COMMENT '库存记录 ID',
    product_id BIGINT NOT NULL COMMENT '商品 ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 ID',
    alert_type VARCHAR(30) NOT NULL COMMENT '预警类型',
    current_qty INT NOT NULL DEFAULT 0 COMMENT '预警时库存数量',
    safety_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存',
    alert_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
    handler VARCHAR(100) COMMENT '处理人',
    handle_time DATETIME COMMENT '处理时间',
    handle_remark VARCHAR(500) COMMENT '处理备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_inventory_status (inventory_id, alert_status),
    KEY idx_type (alert_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警';

-- ---------------- 库存锁定记录 ----------------
DROP TABLE IF EXISTS erp_inventory_lock;
CREATE TABLE erp_inventory_lock (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT COMMENT '库存记录 ID',
    product_id BIGINT NOT NULL COMMENT '商品 ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 ID',
    lock_quantity INT NOT NULL COMMENT '锁定数量',
    used_quantity INT NOT NULL DEFAULT 0 COMMENT '已使用（已释放）数量',
    lock_type VARCHAR(20) NOT NULL COMMENT '锁定类型',
    order_no VARCHAR(100) COMMENT '关联订单号',
    remark VARCHAR(500) COMMENT '备注',
    lock_status VARCHAR(20) NOT NULL DEFAULT 'LOCKED' COMMENT '锁定状态',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_product_warehouse (product_id, warehouse_id, lock_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存锁定记录';

-- ---------------- 库存盘点单 ----------------
DROP TABLE IF EXISTS erp_inventory_check;
CREATE TABLE erp_inventory_check (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_no VARCHAR(50) NOT NULL COMMENT '盘点单号',
    warehouse_id BIGINT COMMENT '仓库 ID',
    check_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '盘点状态',
    total_profit DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '盘盈金额',
    total_loss DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '盘亏金额',
    net_profit DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '净盈亏金额',
    complete_time DATETIME COMMENT '完成时间',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_check_no (check_no),
    KEY idx_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点单';

-- ---------------- 库存盘点明细 ----------------
DROP TABLE IF EXISTS erp_inventory_check_detail;
CREATE TABLE erp_inventory_check_detail (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_id BIGINT NOT NULL COMMENT '盘点单 ID',
    inventory_id BIGINT NOT NULL COMMENT '库存记录 ID',
    check_qty INT NOT NULL COMMENT '盘点数量',
    before_qty INT COMMENT '盘点前数量',
    diff_qty INT COMMENT '差异数量',
    diff_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '差异金额',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_check (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点明细';
