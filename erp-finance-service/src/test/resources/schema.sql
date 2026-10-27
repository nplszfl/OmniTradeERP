-- 财务服务单元测试用 H2 建表脚本 (MODE=MySQL)

DROP TABLE IF EXISTS fin_account;
CREATE TABLE fin_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_no VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    account_type VARCHAR(50),
    currency VARCHAR(10) DEFAULT 'USD',
    balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    bank_name VARCHAR(100),
    account_number VARCHAR(100),
    is_default TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS fin_category;
CREATE TABLE fin_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    parent_id BIGINT,
    sort_order INT DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS fin_record;
CREATE TABLE fin_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_no VARCHAR(50) NOT NULL,
    account_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    category_id BIGINT,
    order_no VARCHAR(100),
    before_balance DECIMAL(18,2),
    after_balance DECIMAL(18,2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    void_time DATETIME,
    void_reason VARCHAR(500),
    remark VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0
);

DROP TABLE IF EXISTS fin_statistics;
CREATE TABLE fin_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL,
    dimension VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    account_id BIGINT,
    total_income DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    total_expense DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    profit DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    record_count INT NOT NULL DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0
);
