-- ============================================
-- 社交电商服务 (erp-social-commerce-service)
-- 库名: erp_social
-- ============================================
-- ⚠️ 这个库之前在整个仓库里**既没有 CREATE DATABASE，也没有建表语句**，
-- 但 erp-social-commerce-service 的 application.yml 明确指向它：
--     jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/erp_social
-- 而服务用的是 JPA + ddl-auto: update —— Hibernate 只建表、不建库，
-- 所以服务启动时直接报 "Unknown database 'erp_social'"。
--
-- 📌 表名严格对齐实体的 @Table(name=...)：
--    influencer / cooperation / live_stream / tiktok_order /
--    tiktok_product / tiktok_shop_config / douyin_shop_config
-- 📌 列名按 Spring Boot 默认的 CamelCase→snake_case 策略转换
--    （Boot 3 = ImplicitNamingStrategyJpaCompliantImpl + StandardImpl）
-- 📌 枚举一律 @Enumerated(EnumType.STRING) → VARCHAR，不是数字

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_social`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `erp_social`;

-- ------------------------------------------------------------
-- 达人
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `influencer` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(255) NOT NULL COMMENT '达人名称',
    `platform_id`       VARCHAR(255)          DEFAULT NULL COMMENT '平台内唯一ID',
    `platform`          VARCHAR(50)  NOT NULL COMMENT 'TIKTOK/DOUYIN/INSTAGRAM/YOUTUBE',
    `avatar_url`        VARCHAR(500)          DEFAULT NULL,
    `follower_count`    INT                   DEFAULT NULL,
    `following_count`   INT                   DEFAULT NULL,
    `total_views`       BIGINT                DEFAULT NULL,
    `total_likes`       BIGINT                DEFAULT NULL,
    `level`             VARCHAR(50)           DEFAULT NULL COMMENT 'KOC/KOL/MAK',
    `status`            VARCHAR(50)           DEFAULT NULL COMMENT 'ACTIVE/INACTIVE/BLACKLIST',
    `contact_email`     VARCHAR(255)          DEFAULT NULL,
    `contact_phone`     VARCHAR(50)           DEFAULT NULL,
    `cooperation_price` DECIMAL(10,2)         DEFAULT NULL,
    `categories`        VARCHAR(1000)         DEFAULT NULL,
    `description`       VARCHAR(2000)         DEFAULT NULL,
    `created_at`        DATETIME              DEFAULT NULL,
    `updated_at`        DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_influencer_platform_id` (`platform_id`),
    KEY `idx_influencer_platform` (`platform`),
    KEY `idx_influencer_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交电商达人表';

-- ------------------------------------------------------------
-- 达人合作
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cooperation` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `influencer_id`     BIGINT        NOT NULL COMMENT '关联达人',
    `product_id`        VARCHAR(255)  NOT NULL,
    `product_name`      VARCHAR(255)  NOT NULL,
    `type`              VARCHAR(50)   NOT NULL COMMENT 'LIVE/SHORT_VIDEO/POST/STORY/COLLABORATION/LIVE_STREAM',
    `status`            VARCHAR(50)   NOT NULL COMMENT 'DRAFT/PENDING/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED',
    `planned_start_date` DATETIME     DEFAULT NULL,
    `planned_end_date`  DATETIME      DEFAULT NULL,
    `actual_start_date` DATETIME      DEFAULT NULL,
    `actual_end_date`   DATETIME      DEFAULT NULL,
    `agreed_price`      DECIMAL(10,2) DEFAULT NULL,
    `actual_cost`       DECIMAL(10,2) DEFAULT NULL,
    `commission_rate`   DECIMAL(5,2)  DEFAULT NULL,
    `expected_views`    INT           DEFAULT NULL,
    `actual_views`      INT           DEFAULT NULL,
    `expected_sales`    INT           DEFAULT NULL,
    `actual_sales`      INT           DEFAULT NULL,
    `expected_gmv`      DECIMAL(12,2) DEFAULT NULL,
    `actual_gmv`        DECIMAL(12,2) DEFAULT NULL,
    `content`           VARCHAR(1000) DEFAULT NULL,
    `remark`            VARCHAR(2000) DEFAULT NULL,
    `created_at`        DATETIME      DEFAULT NULL,
    `updated_at`        DATETIME      DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_cooperation_influencer` (`influencer_id`),
    KEY `idx_cooperation_status` (`status`),
    KEY `idx_cooperation_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='达人合作表';

-- ------------------------------------------------------------
-- 直播
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `live_stream` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
    `stream_id`             VARCHAR(255)  NOT NULL,
    `title`                 VARCHAR(255)  NOT NULL,
    `platform`              VARCHAR(50)   NOT NULL COMMENT 'TIKTOK/DOUYIN/TAOBAO_LIVE',
    `shop_id`               VARCHAR(64)           DEFAULT NULL COMMENT '所属店铺ID(tiktok_shop_config.shop_id，形如 SHOP-TT-0001)；存量行为 NULL',
    `influencer_id`         BIGINT                 DEFAULT NULL,
    `status`                VARCHAR(50)   NOT NULL COMMENT 'SCHEDULED/LIVE/ENDED/CANCELLED',
    `scheduled_start_time`  DATETIME              DEFAULT NULL,
    `actual_start_time`     DATETIME              DEFAULT NULL,
    `end_time`              DATETIME              DEFAULT NULL,
    `duration`              INT                   DEFAULT NULL,
    `viewer_count`          INT                   DEFAULT NULL,
    `peak_viewers`          INT                   DEFAULT NULL,
    `like_count`            INT                   DEFAULT NULL,
    `share_count`           INT                   DEFAULT NULL,
    `comment_count`         INT                   DEFAULT NULL,
    `new_followers`         INT                   DEFAULT NULL,
    `total_sales`           DECIMAL(12,2)         DEFAULT NULL,
    `total_orders`          INT                   DEFAULT NULL,
    `total_gmv`             DECIMAL(12,2)         DEFAULT NULL,
    `cover_image_url`       VARCHAR(500)          DEFAULT NULL,
    `products`              VARCHAR(1000)         DEFAULT NULL,
    `created_at`            DATETIME              DEFAULT NULL,
    `updated_at`            DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_live_stream_platform` (`platform`),
    KEY `idx_live_stream_status` (`status`),
    KEY `idx_live_stream_influencer` (`influencer_id`),
    KEY `idx_live_stream_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='直播表';

-- 📌 **存量迁移：给已存在的 live_stream 表补 shop_id 列**（2026-11-10）
--
-- ⚠️ **为什么需要**：上面用的是 `CREATE TABLE IF NOT EXISTS` →
--    对**已经建好的表**（生产/已有环境）**不会补列** →
--    而实体 `LiveStream.shopId` 已经映射到这个列 → 启动后 Hibernate 报
--    「Unknown column 'shop_id'」→ **整个服务起不来**。
--
-- ⚠️ **为什么不能用 `ADD COLUMN IF NOT EXISTS`**：
--    那是 **MariaDB** 的语法，**MySQL 8.0 不支持**（实测直接报错）→
--    → 用 information_schema 判断 + PREPARE 动态执行（幂等，可重复跑）。
--
-- 📌 **存量行的 shop_id 留 NULL**：服务侧 `resolveShopConfig` 会按平台回退 +
--    回填（判据是「这个值还能不能用」，不是「值是不是空」—— 规则 57）。
--    ⚠️ **不能在这里直接 UPDATE 填一个值** → 多店铺时无法判定归属（会静默填错）。
SET @live_stream_has_shop_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'erp_social' AND TABLE_NAME = 'live_stream' AND COLUMN_NAME = 'shop_id'
);
SET @live_stream_ddl := IF(@live_stream_has_shop_id = 0,
    'ALTER TABLE `live_stream` ADD COLUMN `shop_id` VARCHAR(64) DEFAULT NULL COMMENT ''所属店铺ID(tiktok_shop_config.shop_id)''',
    'SELECT ''live_stream.shop_id 已存在，跳过''');
PREPARE live_stream_stmt FROM @live_stream_ddl;
EXECUTE live_stream_stmt;
DEALLOCATE PREPARE live_stream_stmt;

-- ------------------------------------------------------------
-- TikTok 订单
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiktok_order` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `order_id`          VARCHAR(255)  NOT NULL,
    `shop_id`           VARCHAR(255)  NOT NULL,
    `status`            VARCHAR(50)   NOT NULL COMMENT 'PENDING/PAID/CONFIRMED/PROCESSING/SHIPPED/DELIVERED/COMPLETED/CANCELLED/REFUNDED/PARTIAL_REFUND',
    `order_amount`      DECIMAL(12,2) DEFAULT NULL,
    `product_amount`    DECIMAL(12,2) DEFAULT NULL,
    `shipping_fee`      DECIMAL(10,2) DEFAULT NULL,
    `tax_amount`        DECIMAL(10,2) DEFAULT NULL,
    `discount_amount`   DECIMAL(10,2) DEFAULT NULL,
    `buyer_id`          VARCHAR(255)  DEFAULT NULL,
    `buyer_username`    VARCHAR(255)  DEFAULT NULL,
    `recipient_name`    VARCHAR(255)  DEFAULT NULL,
    `recipient_phone`   VARCHAR(50)   DEFAULT NULL,
    `shipping_address`  VARCHAR(500)  DEFAULT NULL,
    `country`           VARCHAR(100)  DEFAULT NULL,
    `state`             VARCHAR(100)  DEFAULT NULL,
    `city`              VARCHAR(100)  DEFAULT NULL,
    `postal_code`       VARCHAR(50)   DEFAULT NULL,
    `item_quantity`     INT           DEFAULT NULL,
    `items`             VARCHAR(2000) DEFAULT NULL,
    `tracking_number`   VARCHAR(255)  DEFAULT NULL,
    `shipping_carrier`  VARCHAR(255)  DEFAULT NULL,
    `shipped_time`      DATETIME      DEFAULT NULL,
    `delivered_time`    DATETIME      DEFAULT NULL,
    `create_time`       DATETIME      NOT NULL,
    `update_time`       DATETIME      DEFAULT NULL,
    `pay_time`          DATETIME      DEFAULT NULL,
    `live_stream_id`    BIGINT                 DEFAULT NULL,
    `influencer_id`     BIGINT                 DEFAULT NULL,
    `commission_amount` DECIMAL(10,2) DEFAULT NULL,
    `commission_rate`   DECIMAL(5,2)  DEFAULT NULL,
    `remark`            VARCHAR(1000) DEFAULT NULL,
    `created_at`        DATETIME      DEFAULT NULL,
    `updated_at`        DATETIME      DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tiktok_order_order_id` (`order_id`),
    KEY `idx_tiktok_order_shop` (`shop_id`),
    KEY `idx_tiktok_order_status` (`status`),
    KEY `idx_tiktok_order_influencer` (`influencer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TikTok订单表';

-- ------------------------------------------------------------
-- TikTok 商品
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiktok_product` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT,
    `product_id`           VARCHAR(255)  NOT NULL,
    `shop_id`              VARCHAR(255)  NOT NULL,
    `product_name`         VARCHAR(255)  NOT NULL,
    `description`          VARCHAR(2000) DEFAULT NULL,
    `category_id`          VARCHAR(255)  DEFAULT NULL,
    `category_name`        VARCHAR(255)  DEFAULT NULL,
    `status`               VARCHAR(50)   NOT NULL COMMENT 'DRAFT/PENDING/ACTIVE/INACTIVE/DELETED/REJECTED',
    `price`                DECIMAL(10,2) NOT NULL,
    `original_price`       DECIMAL(10,2) DEFAULT NULL,
    `currency`             VARCHAR(10)   DEFAULT NULL,
    `stock_quantity`       INT           DEFAULT NULL,
    `main_image_url`       VARCHAR(500)  DEFAULT NULL,
    `image_urls`           VARCHAR(2000) DEFAULT NULL,
    `video_url`            VARCHAR(500)  DEFAULT NULL,
    `skus`                 VARCHAR(3000) DEFAULT NULL,
    `attributes`           VARCHAR(2000) DEFAULT NULL,
    `weight`               INT           DEFAULT NULL,
    `support_cod`          TINYINT(1)    DEFAULT NULL,
    `is_live_product`      TINYINT(1)    DEFAULT NULL,
    `live_commission_rate` DECIMAL(5,2)  DEFAULT NULL,
    `sales_count`          INT           DEFAULT NULL,
    `view_count`           INT           DEFAULT NULL,
    `favorite_count`       INT           DEFAULT NULL,
    `rating`               DECIMAL(3,2)  DEFAULT NULL,
    `review_count`         INT           DEFAULT NULL,
    `local_product_id`     VARCHAR(255)  DEFAULT NULL,
    `external_product_id`  VARCHAR(255)  DEFAULT NULL,
    `create_time`          DATETIME      DEFAULT NULL,
    `update_time`          DATETIME      DEFAULT NULL,
    `publish_time`         DATETIME      DEFAULT NULL,
    `created_at`           DATETIME      DEFAULT NULL,
    `updated_at`           DATETIME      DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tiktok_product_product_id` (`product_id`),
    KEY `idx_tiktok_product_shop` (`shop_id`),
    KEY `idx_tiktok_product_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TikTok商品表';

-- ------------------------------------------------------------
-- TikTok 店铺配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tiktok_shop_config` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `shop_name`          VARCHAR(255) NOT NULL,
    `shop_id`            VARCHAR(255) NOT NULL,
    `access_token`       VARCHAR(500) DEFAULT NULL,
    `refresh_token`      VARCHAR(500) DEFAULT NULL,
    `token_expire_time`  DATETIME     DEFAULT NULL,
    `app_key`            VARCHAR(100) DEFAULT NULL,
    `app_secret`         VARCHAR(100) DEFAULT NULL,
    `status`             VARCHAR(50)  NOT NULL COMMENT 'ACTIVE/INACTIVE/EXPIRED/ERROR',
    `created_at`         DATETIME     DEFAULT NULL,
    `updated_at`         DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tiktok_shop_config_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TikTok店铺配置表';

-- ------------------------------------------------------------
-- 抖音店铺配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `douyin_shop_config` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `shop_name`          VARCHAR(255) NOT NULL,
    `shop_id`            VARCHAR(255) NOT NULL,
    `access_token`       VARCHAR(500) DEFAULT NULL,
    `refresh_token`      VARCHAR(500) DEFAULT NULL,
    `token_expire_time`  DATETIME     DEFAULT NULL,
    `app_key`            VARCHAR(100) DEFAULT NULL,
    `app_secret`         VARCHAR(100) DEFAULT NULL,
    `status`             VARCHAR(50)  NOT NULL COMMENT 'ACTIVE/INACTIVE/EXPIRED/ERROR',
    `created_at`         DATETIME     DEFAULT NULL,
    `updated_at`         DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_douyin_shop_config_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抖音店铺配置表';
