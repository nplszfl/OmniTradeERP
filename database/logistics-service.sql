-- 物流服务数据库初始化脚本
-- 作者：火球鼠
-- 创建时间：2026-11-02

CREATE DATABASE IF NOT EXISTS erp_logistics
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE erp_logistics;

-- 物流渠道表
CREATE TABLE IF NOT EXISTS t_logistics_channel (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    channel_code VARCHAR(50) NOT NULL COMMENT '渠道编码（DHL_EXPRESS）',
    channel_name VARCHAR(100) NOT NULL COMMENT '渠道名称（DHL国际快递）',
    carrier VARCHAR(50) NOT NULL COMMENT '承运商（DHL）',
    type VARCHAR(30) DEFAULT 'EXPRESS' COMMENT '渠道类型（EXPRESS/STANDARD/ECONOMY/SPECIAL）',
    coverage VARCHAR(500) DEFAULT '[]' COMMENT '覆盖国家（JSON数组）',
    features VARCHAR(500) DEFAULT '[]' COMMENT '渠道特点（JSON数组）',
    avg_delivery_days INT DEFAULT 7 COMMENT '平均时效（天）',
    tracking_support TINYINT(1) DEFAULT 1 COMMENT '是否支持追踪',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_code (channel_code),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流渠道表';

-- 物流费率表
CREATE TABLE IF NOT EXISTS t_logistics_rate (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    channel_code VARCHAR(50) NOT NULL COMMENT '渠道编码',
    channel_name VARCHAR(100) COMMENT '渠道名称',
    carrier VARCHAR(50) COMMENT '承运商',
    shipping_method VARCHAR(50) COMMENT '配送方式',
    from_country VARCHAR(10) NOT NULL COMMENT '起运国（ISO 2位）',
    to_country VARCHAR(10) NOT NULL COMMENT '目的国（ISO 2位）',
    weight_range_min DECIMAL(10,3) DEFAULT 0.000 COMMENT '重量下限（kg）',
    weight_range_max DECIMAL(10,3) DEFAULT NULL COMMENT '重量上限（kg，NULL表示不限）',
    base_price DECIMAL(10,2) NOT NULL COMMENT '基础价',
    price_per_kg DECIMAL(10,2) DEFAULT 0.00 COMMENT '每公斤加价',
    estimated_days INT DEFAULT 7 COMMENT '预计时效（天）',
    currency VARCHAR(10) DEFAULT 'USD' COMMENT '币种',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_channel (channel_code),
    INDEX idx_route (from_country, to_country),
    INDEX idx_weight (weight_range_min, weight_range_max),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流费率表';

-- 初始化渠道数据
INSERT INTO t_logistics_channel
    (id, channel_code, channel_name, carrier, type, coverage, features, avg_delivery_days, tracking_support, enabled)
VALUES
    (1000000000000000001, 'DHL_EXPRESS',  'DHL国际快递',   'DHL',       'EXPRESS',  '["CN","US","DE","GB","FR","JP"]', '["门到门","可追踪","清关快"]', 3,  1, 1),
    (1000000000000000002, 'FEDEX_EXPRESS','FedEx国际快递','FedEx',     'EXPRESS',  '["CN","US","CA","AU","JP"]',       '["时效稳定","可追踪"]',         4,  1, 1),
    (1000000000000000003, 'UPS_EXPRESS',   'UPS国际快递',  'UPS',       'EXPRESS',  '["CN","US","GB","FR"]',             '["重货友好","可追踪"]',         4,  1, 1),
    (1000000000000000004, 'CHINA_POST',    '中国邮政小包',  'China Post','ECONOMY', '["CN"]',                          '["价格最低","时效较慢"]',       15, 0, 1),
    (1000000000000000005, 'YANWEN',        '燕文物流',      'Yanwen',    'ECONOMY', '["CN","US","BR","RU"]',            '["小包专线","价格实惠"]',       12, 1, 1),
    (1000000000000000006, 'ARAMEX',        'Aramex国际专线','Aramex',    'SPECIAL', '["CN","AE","SA","QA"]',            '["中东专线","清关能力强"]',     7,  1, 1)
ON DUPLICATE KEY UPDATE
    channel_name = VALUES(channel_name),
    carrier = VALUES(carrier),
    type = VALUES(type),
    coverage = VALUES(coverage),
    features = VALUES(features),
    avg_delivery_days = VALUES(avg_delivery_days),
    tracking_support = VALUES(tracking_support),
    enabled = VALUES(enabled);

-- 初始化费率数据（CN → US / DE / GB 等主要航线）
INSERT INTO t_logistics_rate
    (id, channel_code, channel_name, carrier, shipping_method, from_country, to_country,
     weight_range_min, weight_range_max, base_price, price_per_kg, estimated_days, currency, is_active)
VALUES
    (2000000000000000001, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'US', 0.000, 0.500, 22.00, 8.00,  3, 'USD', 1),
    (2000000000000000002, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'US', 0.500, 1.000, 26.00, 6.00,  3, 'USD', 1),
    (2000000000000000003, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'US', 1.000, 5.000, 32.00, 4.50,  4, 'USD', 1),
    (2000000000000000004, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'US', 5.000, NULL, 54.50, 3.20,  5, 'USD', 1),
    (2000000000000000005, 'FEDEX_EXPRESS', 'FedEx国际快递', 'FedEx',     'EXPRESS', 'CN', 'US', 0.000, 1.000, 24.00, 6.50,  4, 'USD', 1),
    (2000000000000000006, 'FEDEX_EXPRESS', 'FedEx国际快递', 'FedEx',     'EXPRESS', 'CN', 'US', 1.000, 5.000, 30.50, 4.80,  4, 'USD', 1),
    (2000000000000000007, 'FEDEX_EXPRESS', 'FedEx国际快递', 'FedEx',     'EXPRESS', 'CN', 'US', 5.000, NULL, 54.50, 3.40,  5, 'USD', 1),
    (2000000000000000008, 'UPS_EXPRESS',   'UPS国际快递',   'UPS',       'EXPRESS', 'CN', 'US', 0.000, 5.000, 25.00, 5.20,  4, 'USD', 1),
    (2000000000000000009, 'CHINA_POST',    '中国邮政小包',  'China Post','ECONOMY','CN', 'US', 0.000, NULL,  8.00, 3.00, 15, 'USD', 1),
    (2000000000000000010, 'YANWEN',        '燕文物流',      'Yanwen',    'ECONOMY', 'CN', 'US', 0.000, 0.500, 11.00, 4.00, 12, 'USD', 1),
    (2000000000000000011, 'YANWEN',        '燕文物流',      'Yanwen',    'ECONOMY', 'CN', 'US', 0.500, 2.000, 13.00, 3.20, 13, 'USD', 1),
    (2000000000000000012, 'YANWEN',        '燕文物流',      'Yanwen',    'ECONOMY', 'CN', 'US', 2.000, NULL, 19.40, 2.60, 14, 'USD', 1),
    (2000000000000000013, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'DE', 0.000, 1.000, 27.00, 6.50,  4, 'USD', 1),
    (2000000000000000014, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'DE', 1.000, NULL, 33.50, 4.20,  5, 'USD', 1),
    (2000000000000000015, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'GB', 0.000, 1.000, 26.00, 6.20,  4, 'USD', 1),
    (2000000000000000016, 'DHL_EXPRESS',   'DHL国际快递',   'DHL',       'EXPRESS', 'CN', 'GB', 1.000, NULL, 32.20, 4.00,  5, 'USD', 1),
    (2000000000000000017, 'ARAMEX',        'Aramex国际专线','Aramex',    'SPECIAL', 'CN', 'AE', 0.000, NULL, 30.00, 5.00,  7, 'USD', 1),
    (2000000000000000018, 'ARAMEX',        'Aramex国际专线','Aramex',    'SPECIAL', 'CN', 'SA', 0.000, NULL, 32.00, 5.20,  8, 'USD', 1)
ON DUPLICATE KEY UPDATE
    base_price = VALUES(base_price),
    price_per_kg = VALUES(price_per_kg),
    estimated_days = VALUES(estimated_days),
    is_active = VALUES(is_active);
