-- 库存预警服务数据库初始化脚本
-- PostgreSQL
--
-- ⚠️ 这份脚本之前不存在 —— 而 erp-inventory-alert-service 的 5 张表
--    （t_inventory_alert / t_inventory_alert_rule / t_inventory_alert_statistics /
--      t_replenishment_suggestion / t_slow_moving_inventory）
--    在整个仓库里找不到任何 CREATE TABLE。
--
--    为什么 ddl-auto: update 救不了：
--    这个模块用的是 **MyBatis-Plus**（@TableName），不是 JPA。
--    Hibernate 的 ddl-auto 只处理 @Entity 类，
--    而本模块一个 @Entity 都没有 → 那句 ddl-auto 实际上是空转，
--    永远建不出这 5 张表。
--
--    后果：任何一次 Mapper 查询都会抛
--    "relation \"t_inventory_alert\" does not exist"。
--
-- 字段严格按实体定义对齐（全部是 VARCHAR，没有用 MySQL 枚举）：

-- ---------------- 库存预警表 ----------------
CREATE TABLE IF NOT EXISTS t_inventory_alert (
    id BIGINT PRIMARY KEY,
    rule_id BIGINT,
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(200),
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    current_stock INT DEFAULT 0,
    alert_stock INT DEFAULT 0,
    safe_stock INT DEFAULT 0,
    alert_type VARCHAR(20) COMMENT 'LOW/OUT/SAFE',
    message VARCHAR(500),
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/DETECTED/HANDLED/IGNORED/CANCELLED',
    handler_id BIGINT,
    handler_name VARCHAR(100),
    handle_remark VARCHAR(500),
    notified BOOLEAN DEFAULT FALSE,
    notify_type VARCHAR(20) DEFAULT 'ALL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    handled_time TIMESTAMP
);

-- ---------------- 库存预警规则表 ----------------
CREATE TABLE IF NOT EXISTS t_inventory_alert_rule (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    product_id BIGINT,
    sku VARCHAR(100),
    product_name VARCHAR(200),
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    safe_stock INT DEFAULT 0,
    alert_stock INT DEFAULT 0,
    min_stock INT DEFAULT 0,
    alert_type VARCHAR(20),
    advance_days INT DEFAULT 0,
    notify_type VARCHAR(20) DEFAULT 'ALL',
    notify_to VARCHAR(200),
    email_list VARCHAR(500),
    phone_list VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    remark VARCHAR(500),
    creator_id BIGINT,
    creator_name VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------- 库存预警统计表 ----------------
CREATE TABLE IF NOT EXISTS t_inventory_alert_statistics (
    id BIGINT PRIMARY KEY,
    statistics_date DATE,
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    total_alert_count INT DEFAULT 0,
    pending_count INT DEFAULT 0,
    handled_count INT DEFAULT 0,
    ignored_count INT DEFAULT 0,
    low_stock_count INT DEFAULT 0,
    out_of_stock_count INT DEFAULT 0,
    safe_stock_warning_count INT DEFAULT 0,
    handle_rate DOUBLE PRECISION DEFAULT 0,
    avg_handle_duration DOUBLE PRECISION DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------- 补货建议表 ----------------
CREATE TABLE IF NOT EXISTS t_replenishment_suggestion (
    id BIGINT PRIMARY KEY,
    alert_id BIGINT,
    rule_id BIGINT,
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(200),
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    current_stock INT DEFAULT 0,
    safe_stock INT DEFAULT 0,
    suggested_quantity INT DEFAULT 0,
    suggested_date TIMESTAMP,
    expected_arrival_date TIMESTAMP,
    purchase_price DECIMAL(18,2) DEFAULT 0,
    currency VARCHAR(10) DEFAULT 'USD',
    estimated_amount DECIMAL(18,2) DEFAULT 0,
    supplier_id BIGINT,
    supplier_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/PURCHASED/CANCELLED',
    urgency_level VARCHAR(20),
    remark VARCHAR(500),
    creator_id BIGINT,
    creator_name VARCHAR(100),
    confirmer_id BIGINT,
    confirmer_name VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------- 滞销库存表 ----------------
CREATE TABLE IF NOT EXISTS t_slow_moving_inventory (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(50),
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(200),
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    current_stock INT DEFAULT 0,
    sales_last_30_days INT DEFAULT 0,
    sales_last_60_days INT DEFAULT 0,
    sales_last_90_days INT DEFAULT 0,
    turnover_rate DOUBLE PRECISION DEFAULT 0,
    last_sale_date DATE,
    stagnant_days INT DEFAULT 0,
    alert_level VARCHAR(20) COMMENT 'LOW/MEDIUM/HIGH',
    status VARCHAR(20) DEFAULT 'PENDING',
    notified BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------- 索引 ----------------
CREATE INDEX IF NOT EXISTS idx_alert_product ON t_inventory_alert (product_id);
CREATE INDEX IF NOT EXISTS idx_alert_status ON t_inventory_alert (status);
CREATE INDEX IF NOT EXISTS idx_alert_sku ON t_inventory_alert (sku);
CREATE INDEX IF NOT EXISTS idx_alert_rule_product ON t_inventory_alert_rule (product_id);
CREATE INDEX IF NOT EXISTS idx_alert_rule_sku ON t_inventory_alert_rule (sku);
CREATE INDEX IF NOT EXISTS idx_alert_stat_date ON t_inventory_alert_statistics (statistics_date);
CREATE INDEX IF NOT EXISTS idx_replenishment_product ON t_replenishment_suggestion (product_id);
CREATE INDEX IF NOT EXISTS idx_replenishment_status ON t_replenishment_suggestion (status);
CREATE INDEX IF NOT EXISTS idx_slow_product ON t_slow_moving_inventory (product_id);
CREATE INDEX IF NOT EXISTS idx_slow_sku ON t_slow_moving_inventory (sku);
