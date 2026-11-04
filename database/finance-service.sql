-- ===================================================
-- 财务服务 (erp-finance-service) 数据库初始化脚本
-- MySQL 8.0+
--
-- 覆盖 4 张 fin_* 表：
--   fin_account     财务账户
--   fin_record      财务流水记录
--   fin_category    财务分类
--   fin_statistics  财务统计快照
--
-- 枚举字段以 VARCHAR 存储枚举 name()（MyBatis-Plus 默认 EnumTypeHandler）：
--   fin_record.type        INCOME / EXPENSE / REFUND
--   fin_record.status      PENDING / COMPLETED / VOIDED
--   fin_account.status     ACTIVE / INACTIVE / DELETED
--   fin_category.type      INCOME / EXPENSE
--
-- id 生成策略：IdType.AUTO → BIGINT AUTO_INCREMENT
-- ===================================================

-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_finance` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_finance`;

-- ---------------- 财务账户 ----------------
DROP TABLE IF EXISTS fin_account;
CREATE TABLE fin_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号',
    name VARCHAR(100) NOT NULL COMMENT '账户名称',
    code VARCHAR(50) COMMENT '账户编码',
    account_type VARCHAR(50) COMMENT '账户类型 BANK/ALIPAY/WECHAT/PAYPAL',
    currency VARCHAR(10) DEFAULT 'USD' COMMENT '币种',
    balance DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '当前余额',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/INACTIVE/DELETED',
    bank_name VARCHAR(100) COMMENT '银行名称',
    account_number VARCHAR(100) COMMENT '账号',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认账户 1是 0否',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    UNIQUE KEY uk_account_name (name),
    KEY idx_account_status (status),
    KEY idx_account_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务账户';

-- ---------------- 财务分类 ----------------
DROP TABLE IF EXISTS fin_category;
CREATE TABLE fin_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    type VARCHAR(20) NOT NULL COMMENT '分类类型 INCOME/EXPENSE',
    parent_id BIGINT DEFAULT NULL COMMENT '父分类 ID',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    KEY idx_category_type (type),
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务分类';

-- ---------------- 财务流水记录 ----------------
DROP TABLE IF EXISTS fin_record;
CREATE TABLE fin_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_no VARCHAR(50) NOT NULL COMMENT '流水号',
    account_id BIGINT NOT NULL COMMENT '账户 ID',
    type VARCHAR(20) NOT NULL COMMENT '流水类型 INCOME/EXPENSE/REFUND',
    amount DECIMAL(18,2) NOT NULL COMMENT '金额',
    category_id BIGINT DEFAULT NULL COMMENT '分类 ID',
    order_no VARCHAR(100) COMMENT '关联订单号',
    before_balance DECIMAL(18,2) DEFAULT NULL COMMENT '记账前余额',
    after_balance DECIMAL(18,2) DEFAULT NULL COMMENT '记账后余额',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/COMPLETED/VOIDED',
    void_time DATETIME DEFAULT NULL COMMENT '作废时间',
    void_reason VARCHAR(500) COMMENT '作废原因',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    UNIQUE KEY uk_record_no (record_no),
    KEY idx_record_account (account_id),
    KEY idx_record_type (type),
    KEY idx_record_category (category_id),
    KEY idx_record_status (status),
    KEY idx_record_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务流水记录';

-- ---------------- 财务统计快照 ----------------
DROP TABLE IF EXISTS fin_statistics;
CREATE TABLE fin_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    dimension VARCHAR(20) NOT NULL DEFAULT 'DAILY' COMMENT '统计维度 DAILY/MONTHLY',
    account_id BIGINT DEFAULT NULL COMMENT '账户 ID',
    total_income DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '收入合计',
    total_expense DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '支出合计',
    profit DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '利润',
    record_count INT NOT NULL DEFAULT 0 COMMENT '流水笔数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    KEY idx_stats_date_dimension (stat_date, dimension),
    KEY idx_stats_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务统计快照';

-- ---------------- 初始化数据 ----------------
INSERT INTO fin_account (account_no, name, code, account_type, currency, balance, status, remark)
VALUES
    ('ACC202601010001', '现金账户', 'CASH001', 'CASH', 'USD', 0.00, 'ACTIVE', '默认现金账户'),
    ('ACC202601010002', '银行账户', 'BANK001', 'BANK', 'USD', 0.00, 'ACTIVE', '默认银行账户')
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    code = VALUES(code), account_type = VALUES(account_type), currency = VALUES(currency),
    status = VALUES(status), remark = VALUES(remark);
-- 📌 **balance 不更新** —— 那是运行时累积的余额，重跑不该把它清零。

INSERT INTO fin_category (name, type, parent_id, sort_order, enabled)
VALUES
    ('销售收入', 'INCOME', NULL, 1, 1),
    ('其他收入', 'INCOME', NULL, 2, 1),
    ('采购支出', 'EXPENSE', NULL, 1, 1),
    ('物流费用', 'EXPENSE', NULL, 2, 1),
    ('广告推广', 'EXPENSE', NULL, 3, 1)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    type = VALUES(type), parent_id = VALUES(parent_id),
    sort_order = VALUES(sort_order), enabled = VALUES(enabled);
