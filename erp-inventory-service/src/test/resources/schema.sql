-- 库存服务单元测试用 H2 建表脚本 (MODE=MySQL)

DROP TABLE IF EXISTS erp_inventory;
CREATE TABLE erp_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    location_id BIGINT,
    sku_code VARCHAR(100),
    quantity INT NOT NULL DEFAULT 0,
    available_quantity INT NOT NULL DEFAULT 0,
    locked_quantity INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    unit_cost DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    create_time DATETIME,
    update_time DATETIME
);

DROP TABLE IF EXISTS erp_inventory_log;
CREATE TABLE erp_inventory_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    before_quantity INT,
    after_quantity INT,
    order_no VARCHAR(100),
    remark VARCHAR(500),
    create_time DATETIME
);

DROP TABLE IF EXISTS erp_inventory_alert;
CREATE TABLE erp_inventory_alert (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    alert_type VARCHAR(30) NOT NULL,
    current_qty INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    alert_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handler VARCHAR(100),
    handle_time DATETIME,
    handle_remark VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME
);

DROP TABLE IF EXISTS erp_inventory_lock;
CREATE TABLE erp_inventory_lock (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    lock_quantity INT NOT NULL,
    used_quantity INT NOT NULL DEFAULT 0,
    lock_type VARCHAR(20) NOT NULL,
    order_no VARCHAR(100),
    remark VARCHAR(500),
    lock_status VARCHAR(20) NOT NULL DEFAULT 'LOCKED',
    create_time DATETIME,
    update_time DATETIME
);

DROP TABLE IF EXISTS erp_inventory_check;
CREATE TABLE erp_inventory_check (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_no VARCHAR(50) NOT NULL,
    warehouse_id BIGINT,
    check_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_profit DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    total_loss DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    net_profit DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    complete_time DATETIME,
    remark VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME
);

DROP TABLE IF EXISTS erp_inventory_check_detail;
CREATE TABLE erp_inventory_check_detail (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    check_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    check_qty INT NOT NULL,
    before_qty INT,
    diff_qty INT,
    diff_amount DECIMAL(18,2) DEFAULT 0.00,
    remark VARCHAR(500),
    create_time DATETIME
);
