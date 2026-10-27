-- ===================================================
-- 采购服务 (erp-purchase-service) 数据库初始化脚本
-- MySQL 8.0+
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_purchase` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_purchase`;

DROP TABLE IF EXISTS t_purchase_forecast;
CREATE TABLE t_purchase_forecast (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(50),
    product_id BIGINT,
    sku VARCHAR(100),
    product_name VARCHAR(200),
    warehouse_id VARCHAR(50),
    predicted_quantity INT DEFAULT 0,
    current_stock INT DEFAULT 0,
    safe_stock INT DEFAULT 0,
    forecast_period VARCHAR(20),
    forecast_date DATETIME,
    status VARCHAR(20),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS t_purchase_plan;
CREATE TABLE t_purchase_plan (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(50),
    plan_no VARCHAR(50),
    plan_type VARCHAR(20),
    product_id BIGINT,
    sku VARCHAR(100),
    product_name VARCHAR(200),
    warehouse_id VARCHAR(50),
    plan_quantity INT DEFAULT 0,
    supplier_id BIGINT,
    supplier_name VARCHAR(200),
    unit_price DECIMAL(18,4) DEFAULT 0,
    total_amount DECIMAL(18,4) DEFAULT 0,
    required_date DATE,
    priority VARCHAR(20),
    status VARCHAR(20),
    creator_id BIGINT,
    creator_name VARCHAR(100),
    remark VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS t_supplier_quote;
CREATE TABLE t_supplier_quote (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(50),
    supplier_id BIGINT,
    supplier_name VARCHAR(200),
    product_id BIGINT,
    sku VARCHAR(100),
    unit_price DECIMAL(18,4) DEFAULT 0,
    moq INT DEFAULT 0,
    currency VARCHAR(10),
    lead_time_days INT DEFAULT 0,
    discount_rate DECIMAL(10,4) DEFAULT 0,
    status VARCHAR(20),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
