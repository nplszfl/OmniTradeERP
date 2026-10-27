-- 报表服务单元测试用 H2 建表脚本 (MODE=MySQL)
-- 表结构与生产 MySQL 保持一致

DROP TABLE IF EXISTS t_report_sales;
CREATE TABLE t_report_sales (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(50),
    report_type VARCHAR(20) NOT NULL,
    report_date DATE NOT NULL,
    status VARCHAR(20),
    creator_id BIGINT,
    creator_name VARCHAR(100),
    create_time DATETIME,
    update_time DATETIME,
    platform VARCHAR(50),
    store_id VARCHAR(100),
    store_name VARCHAR(200),
    order_count INT DEFAULT 0,
    order_quantity INT DEFAULT 0,
    total_amount DECIMAL(18,4) DEFAULT 0,
    refund_amount DECIMAL(18,4) DEFAULT 0,
    net_amount DECIMAL(18,4) DEFAULT 0,
    avg_order_value DECIMAL(18,4) DEFAULT 0,
    new_customers INT DEFAULT 0,
    repeat_customers INT DEFAULT 0,
    currency VARCHAR(10)
);

DROP TABLE IF EXISTS t_report_inventory;
CREATE TABLE t_report_inventory (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(50),
    report_type VARCHAR(20) NOT NULL,
    report_date DATE NOT NULL,
    status VARCHAR(20),
    creator_id BIGINT,
    creator_name VARCHAR(100),
    create_time DATETIME,
    update_time DATETIME,
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    total_skus INT DEFAULT 0,
    total_quantity INT DEFAULT 0,
    low_stock_skus INT DEFAULT 0,
    out_of_stock_skus INT DEFAULT 0,
    total_value DECIMAL(18,4) DEFAULT 0,
    avg_stock_days DECIMAL(10,2) DEFAULT 0,
    inbound_quantity INT DEFAULT 0,
    outbound_quantity INT DEFAULT 0
);

DROP TABLE IF EXISTS t_report_finance;
CREATE TABLE t_report_finance (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(50),
    report_type VARCHAR(20) NOT NULL,
    report_date DATE NOT NULL,
    status VARCHAR(20),
    creator_id BIGINT,
    creator_name VARCHAR(100),
    create_time DATETIME,
    update_time DATETIME,
    warehouse_id VARCHAR(50),
    warehouse_name VARCHAR(100),
    total_revenue DECIMAL(18,4) DEFAULT 0,
    total_cost DECIMAL(18,4) DEFAULT 0,
    gross_profit DECIMAL(18,4) DEFAULT 0,
    gross_margin DECIMAL(10,4) DEFAULT 0,
    total_expense DECIMAL(18,4) DEFAULT 0,
    net_profit DECIMAL(18,4) DEFAULT 0,
    net_margin DECIMAL(10,4) DEFAULT 0,
    accounts_receivable DECIMAL(18,4) DEFAULT 0,
    accounts_payable DECIMAL(18,4) DEFAULT 0,
    cash_flow DECIMAL(18,4) DEFAULT 0,
    currency VARCHAR(10)
);
