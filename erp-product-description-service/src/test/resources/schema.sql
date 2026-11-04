-- 产品描述服务单元测试用 H2 建表脚本（MODE=MySQL）
--
-- 📌 DDL 来源：database/product-description-service.sql 的 product_description 那段
--    （只做了 H2 兼容改写：UNIQUE KEY → CONSTRAINT ... UNIQUE，DATETIME → TIMESTAMP，
--      其它列名/长度/可空性与生产保持一致）。
--
-- 📌 另外建了 **erp_product schema**（商品主数据，只读投影，跨库）：
--    前端契约的保存接口收的是 SKU，必须能解析成 product_id。
--    📌 生产库里商品主数据本来就在 erp_product 库 → **不需要改任何 DDL**，
--       compose 里所有 schema 在同一个 MySQL 实例上 → 跨库只读可行
--       （先例：erp-analytics-service 查 erp_product.t_product / erp_order.t_order）。

DROP TABLE IF EXISTS product_description;
CREATE TABLE product_description (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    language VARCHAR(10) DEFAULT 'zh',
    platform VARCHAR(50) DEFAULT 'general',
    version_number INT DEFAULT 1,
    short_title VARCHAR(100),
    template_id VARCHAR(50),
    model VARCHAR(50),
    status VARCHAR(20) DEFAULT 'GENERATED',
    tokens_used INT,
    generation_time_ms BIGINT,
    approved_by VARCHAR(100),
    approved_time TIMESTAMP,
    published_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 📌 自然唯一键：同一 (商品, 语言, 平台) 只有一行 → 保存必须走 upsert
    CONSTRAINT uk_pd_product_lang_platform UNIQUE (product_id, language, platform)
);

-- 📌 商品主数据在**跨库** erp_product 里（不在本服务的 erp_product_description_db）
--    → H2 里要显式建一个 erp_product schema 来模拟「同一个 MySQL 实例，多个库」。
--
-- 📌 **为什么必须建 t_product_sku**：
--    SKU 有**两种口径**（实测踩出来的坑）：
--      · 变体 SKU：t_product_sku.sku_code，形如 SKU-BLU-001-BLK
--        —— 前端「AI 商品描述」页展示的就是这一种
--      · 商品级 SKU：t_product.internal_sku，形如 SKU-BLU-001
--        —— 商品列表页展示的那一种
--    只建 t_product → 变体 SKU 一律解析不到 → 「明明有这个商品却存不进去」。
DROP SCHEMA IF EXISTS erp_product;
CREATE SCHEMA erp_product;

DROP TABLE IF EXISTS erp_product.t_product;
CREATE TABLE erp_product.t_product (
    id BIGINT NOT NULL PRIMARY KEY,
    internal_sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    category_id BIGINT,
    brand VARCHAR(100),
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_t_product_internal_sku UNIQUE (internal_sku)
);

DROP TABLE IF EXISTS erp_product.t_product_sku;
CREATE TABLE erp_product.t_product_sku (
    id BIGINT NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(100) NOT NULL,
    sku_name VARCHAR(200),
    specs VARCHAR(1000),
    cost_price DECIMAL(18,2),
    sale_price DECIMAL(18,2),
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sku_code UNIQUE (sku_code)
);
