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

-- 📌 **2026-11-26 补**：`description_template`（描述模板表）
--    🔴 **为什么必须补**：2026-11-24 把「描述模板」从「内存硬编码」改成
--    「查库」之后，`DescriptionTemplateServiceImpl` 会**真的查这张表** →
--    H2 里没有这张表 → `@SpringBootTest`（ProductDescriptionPersistenceTest）
--    **启动就炸**（表不存在）→ 整模块的落库测试全部失效。
--    → 这就是「DDL 补齐了 ≠ 测试库补齐了」：单测用的是**自己那份** schema.sql。
-- 📌 列名/类型/可空性与生产 `database/product-description-service.sql` 保持一致；
--    唯一的 H2 兼容改写：`JSON` → `CLOB`（H2 的 MySQL 模式不认 MySQL 的 JSON 类型）。
DROP TABLE IF EXISTS description_template;
CREATE TABLE description_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    language VARCHAR(10) DEFAULT 'zh',
    template_content CLOB NOT NULL,
    -- 📌 生产是 `JSON` 列；H2 用 CLOB 承载（Java 侧本来就用 String 序列化）。
    variables CLOB,
    max_length INT DEFAULT 2000,
    enabled TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_description_template_id UNIQUE (template_id)
);

-- 📌 **种子数据也必须与生产一致**（生产种的是 4 条模板）。
--    🔴 为什么要种：不种的话 `listTemplates()` 返回空列表 → 前端「模板下拉框」是空的；
--      而且**平台名的大小写**正是这次修复的核心（生产种的是 `Amazon`/`eBay`/`Shopee`
--      首字母大写，请求传的是 `amazon`/`ebay`/`shopee` 全小写 → 靠显式忽略大小写匹配）。
--      单测必须复刻这个「大小写不一致」的真实场景，否则这条判据永远测不到。
INSERT INTO description_template (template_id, name, platform, language, template_content, variables, max_length, enabled) VALUES
('amazon_template', 'Amazon产品模板', 'Amazon', 'zh',
 '【产品名称】\n\n{产品特性}\n\n【产品亮点】\n{亮点列表}',
 '["产品名称", "产品特性", "亮点列表"]', 2000, 1),
('ebay_template', 'eBay产品模板', 'eBay', 'zh',
 '标题：【产品名称】\n\n描述：\n{产品特性}',
 '["产品名称", "产品特性"]', 1500, 1),
('shopee_template', 'Shopee产品模板', 'Shopee', 'zh',
 '【产品名称】\n\n产品特点：\n{产品特性}',
 '["产品名称", "产品特性"]', 2000, 1),
('general_template', '通用产品模板', 'general', 'zh',
 '产品名称：【产品名称】\n\n产品特点：\n{产品特性}',
 '["产品名称", "产品特性"]', 2500, 1);

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
    -- 📌 **2026-11-19 补**：与生产 `erp_product.t_product` 对齐（生产表实测有这两列）
    --    · product_name_en：英文场景按语言选名字（英文场景用中文名 → 中英混搭）
    --    · description：真实商品描述 → 派生 features/keywords → LLM 才有商品事实
    --    之前 H2 schema 缺这两列 → Mapper 改完后单测直接 SQL 报错（列不存在）
    product_name_en VARCHAR(500),
    description CLOB,
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
