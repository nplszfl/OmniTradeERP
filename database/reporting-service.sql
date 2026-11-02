-- ===================================================
-- 报表服务 (erp-reporting-service) 数据库初始化脚本
-- MySQL 8.0+
-- ===================================================

-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_reporting` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_reporting`;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
