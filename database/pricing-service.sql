-- 智能定价服务数据库初始化脚本
-- 版本: v1.5.0
-- 作者: 火球鼠
-- 创建时间: 2026-03-17

-- 创建数据库（如果不存在）
-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS erp_pricing
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE erp_pricing;

-- 产品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品ID',
    product_code VARCHAR(50) NOT NULL COMMENT '产品编码',
    product_name VARCHAR(200) NOT NULL COMMENT '产品名称',
    cost_price DECIMAL(10,2) NOT NULL COMMENT '成本价',
    selling_price DECIMAL(10,2) NOT NULL COMMENT '当前售价',
    sku VARCHAR(100) COMMENT 'SKU',
    platform_id BIGINT COMMENT '平台ID',
    platform_product_id VARCHAR(100) COMMENT '平台产品ID',
    status TINYINT DEFAULT 1 COMMENT '状态（1-上架，0-下架）',
    target_profit_margin DECIMAL(5,2) DEFAULT 20.00 COMMENT '目标利润率（%）',
    enable_competitor_analysis TINYINT DEFAULT 1 COMMENT '启用竞品分析',
    enable_seasonal_adjustment TINYINT DEFAULT 1 COMMENT '启用季节性调整',
    enable_inventory_adjustment TINYINT DEFAULT 1 COMMENT '启用库存调整',
    min_price DECIMAL(10,2) COMMENT '最低售价',
    max_price DECIMAL(10,2) COMMENT '最高售价',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_product_code (product_code),
    INDEX idx_platform_id (platform_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品表';

-- 竞品表
CREATE TABLE IF NOT EXISTS competitor_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '竞品ID',
    product_id BIGINT NOT NULL COMMENT '关联产品ID',
    competitor_name VARCHAR(200) NOT NULL COMMENT '竞品名称',
    price DECIMAL(10,2) NOT NULL COMMENT '竞品价格',
    product_url VARCHAR(500) COMMENT '竞品URL',
    platform VARCHAR(50) NOT NULL COMMENT '平台（Amazon、eBay、Shopee等）',
    rating DECIMAL(3,2) COMMENT '评分（1-5）',
    review_count BIGINT DEFAULT 0 COMMENT '评论数',
    sales_volume BIGINT DEFAULT 0 COMMENT '销量',
    scrape_time DATETIME COMMENT '抓取时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_product_id (product_id),
    INDEX idx_platform (platform),
    INDEX idx_scrape_time (scrape_time),
    -- 📌 **自然唯一键**（2026-11-04 加）：
    --    竞品数据来自**定时抓取**，同一平台同一竞品会反复抓到 →
    --    没有唯一键 → 每抓一次多一行 → 表**无限膨胀**。
    UNIQUE KEY uk_cp_product_name (product_id, competitor_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞品数据表';

-- 📌 先删掉已有的完全重复行，再加唯一键
--    （老库可能已经攒了重复行 → 直接 ALTER 会报 1062 → **init 被腰斩**）
DELETE cp1 FROM competitor_product cp1
  JOIN competitor_product cp2
    ON cp1.product_id = cp2.product_id
   AND cp1.competitor_name = cp2.competitor_name
   AND cp1.id > cp2.id;

-- 📌 给已存在的表补唯一键（`CREATE TABLE IF NOT EXISTS` 不会改已存在的表）
SET @uk_cp := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'competitor_product'
                 AND INDEX_NAME = 'uk_cp_product_name');
SET @sql := IF(@uk_cp = 0,
    'ALTER TABLE competitor_product ADD UNIQUE KEY uk_cp_product_name (product_id, competitor_name)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 价格历史表
CREATE TABLE IF NOT EXISTS price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    product_code VARCHAR(50) NOT NULL COMMENT '产品编码',
    old_price DECIMAL(10,2) NOT NULL COMMENT '原价格',
    new_price DECIMAL(10,2) NOT NULL COMMENT '新价格',
    price_change_percent DECIMAL(10,2) COMMENT '价格变动（%）',
    old_profit_margin DECIMAL(5,2) COMMENT '原利润率（%）',
    new_profit_margin DECIMAL(5,2) COMMENT '新利润率（%）',
    adjustment_reason TEXT COMMENT '调整原因',
    adjustment_type VARCHAR(20) DEFAULT 'AUTO' COMMENT '调整方式（AUTO-自动，MANUAL-手动）',
    operator VARCHAR(100) COMMENT '操作人',
    competitor_avg_price DECIMAL(10,2) COMMENT '竞品平均价格',
    seasonal_factor DECIMAL(5,4) COMMENT '季节性因子',
    inventory_factor DECIMAL(5,4) COMMENT '库存因子',
    demand_factor DECIMAL(5,4) COMMENT '需求因子',
    pricing_strategy VARCHAR(100) COMMENT '定价策略',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_create_time (create_time),
    INDEX idx_adjustment_type (adjustment_type),
    -- 📌 **自然唯一键**（2026-11-04 加）：
    --    定价是**反复跑**的 → 没有唯一键 → 每跑一次多几行 → 历史表无限膨胀。
    UNIQUE KEY uk_ph_product_prices (product_id, old_price, new_price, adjustment_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价格历史表';

-- 📌 先删掉已有的完全重复行，再加唯一键（老库可能已攒了重复行）
DELETE ph1 FROM price_history ph1
  JOIN price_history ph2
    ON ph1.product_id = ph2.product_id
   AND ph1.old_price = ph2.old_price
   AND ph1.new_price = ph2.new_price
   AND ph1.adjustment_type = ph2.adjustment_type
   AND ph1.id > ph2.id;

SET @uk_ph := (SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'price_history'
                 AND INDEX_NAME = 'uk_ph_product_prices');
SET @sql := IF(@uk_ph = 0,
    'ALTER TABLE price_history ADD UNIQUE KEY uk_ph_product_prices (product_id, old_price, new_price, adjustment_type)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 定价任务表（用于定时任务记录）
CREATE TABLE IF NOT EXISTS pricing_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型（COMPETITOR_SCRAPE、DYNAMIC_PRICING等）',
    product_id BIGINT COMMENT '产品ID（NULL表示批量任务）',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING、RUNNING、SUCCESS、FAILED）',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms BIGINT COMMENT '耗时（毫秒）',
    result_text TEXT COMMENT '结果描述',
    error_message TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_type (task_type),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定价任务表';

-- AI定价建议表
CREATE TABLE IF NOT EXISTS ai_pricing_suggestion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '建议ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    current_price DECIMAL(10,2) NOT NULL COMMENT '当前价格',
    suggested_price DECIMAL(10,2) NOT NULL COMMENT '建议价格',
    confidence_level DECIMAL(5,2) COMMENT '置信度（0-100）',
    reason TEXT COMMENT '建议原因',
    factors JSON COMMENT '影响因子（JSON格式）',
    applied TINYINT DEFAULT 0 COMMENT '是否应用',
    apply_time DATETIME COMMENT '应用时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_applied (applied),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI定价建议表';

-- 插入示例数据
INSERT INTO product (product_code, product_name, cost_price, selling_price, sku, target_profit_margin) VALUES
('PROD-001', '无线蓝牙耳机', 80.00, 120.00, 'SKU-BLU-001', 30.00),
('PROD-002', '智能手表', 150.00, 220.00, 'SKU-WAT-002', 25.00),
('PROD-003', 'USB充电器', 20.00, 35.00, 'SKU-USB-003', 40.00),
('PROD-004', '手机支架', 15.00, 28.00, 'SKU-STN-004', 45.00),
('PROD-005', '便携充电宝', 60.00, 95.00, 'SKU-PWB-005', 35.00)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name), cost_price = VALUES(cost_price),
    selling_price = VALUES(selling_price), sku = VALUES(sku),
    target_profit_margin = VALUES(target_profit_margin);

-- 插入示例竞品数据
INSERT INTO competitor_product (product_id, competitor_name, price, product_url, platform, rating, review_count, sales_volume) VALUES
(1, 'Amazon竞品1', 125.00, 'https://amazon.com/dp/B001', 'Amazon', 4.5, 1250, 3500),
(1, 'eBay竞品1', 118.00, 'https://ebay.com/itm/E001', 'eBay', 4.3, 890, 2100),
(1, 'Shopee竞品1', 115.00, 'https://shopee.com/product/S001', 'Shopee', 4.6, 1580, 4200),
(2, 'Amazon竞品2', 235.00, 'https://amazon.com/dp/B002', 'Amazon', 4.7, 2100, 5600),
(2, 'eBay竞品2', 210.00, 'https://ebay.com/itm/E002', 'eBay', 4.4, 1320, 3800)
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    price = VALUES(price), product_url = VALUES(product_url), rating = VALUES(rating),
    review_count = VALUES(review_count), sales_volume = VALUES(sales_volume);

-- 插入示例价格历史
INSERT INTO price_history (product_id, product_code, old_price, new_price, price_change_percent, old_profit_margin, new_profit_margin, adjustment_reason, adjustment_type) VALUES
(1, 'PROD-001', 118.00, 120.00, 1.69, 32.20, 33.33, '基于竞品分析上涨价格', 'AUTO'),
(1, 'PROD-001', 120.00, 125.00, 4.17, 33.33, 36.00, '市场需求旺盛，适度涨价', 'AUTO'),
(2, 'PROD-002', 218.00, 220.00, 0.92, 31.19, 31.82, '价格微调保持竞争力', 'AUTO')
-- 📌 **ON DUPLICATE KEY UPDATE → 重跑得到同一份数据**（2026-11-04 加）。
--    本表有 UNIQUE 约束，而这条 INSERT 原来没保护
--    → **重跑直接报 ERROR 1062 → 整个 init 脚本被腰斩**
--    → 排在它后面的种子文件**一个都不执行**（而容器照样起来 → 静默失败）。
ON DUPLICATE KEY UPDATE
    new_price = VALUES(new_price), price_change_percent = VALUES(price_change_percent),
    new_profit_margin = VALUES(new_profit_margin),
    adjustment_reason = VALUES(adjustment_reason), adjustment_type = VALUES(adjustment_type);

-- 查看数据
SELECT '数据库初始化完成！' AS message;
SELECT '产品数量' AS info, COUNT(*) AS value FROM product;
SELECT '竞品数量' AS info, COUNT(*) AS value FROM competitor_product;
SELECT '价格历史数量' AS info, COUNT(*) AS value FROM price_history;
