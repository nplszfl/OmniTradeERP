-- ===================================================
-- 租户服务数据库初始化脚本 (MySQL)
-- ===================================================
-- 数据库: erp_tenant
--
-- 📌 这份文件是 **生产 MySQL 的建库脚本**（由 docker-entrypoint-initdb.d 执行）。
--    模块内的 resources/schema.sql 是同一套 DDL 的服务内副本，
--    供 spring.sql.init 在无 MySQL 的环境（dev/test 的 H2）建表用。
--    两份内容必须保持一致 —— scripts/check-sql-schema.py 会做交叉校验。
--
--    字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
--    mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码。
--    每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
--    所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_tenant` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_tenant`;

CREATE TABLE IF NOT EXISTS tenants (
    `id`         VARCHAR(36)  NOT NULL COMMENT '租户ID(UUID)',
    `name`       VARCHAR(100) NOT NULL COMMENT '租户名称',
    `domain`     VARCHAR(100) COMMENT '自定义域名(唯一)',
    `plan`       VARCHAR(50)  NOT NULL DEFAULT 'STARTER' COMMENT '套餐(STARTER/PROFESSIONAL/BUSINESS/ENTERPRISE)',
    `status`     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/SUSPENDED/EXPIRED)',
    `region`     VARCHAR(50)  COMMENT '区域',
    `settings`   TEXT COMMENT '租户配置JSON',
    `expires_at` DATETIME COMMENT '到期时间',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenants_domain` (`domain`),
    KEY `idx_tenants_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

CREATE TABLE IF NOT EXISTS tenant_users (
    `id`             VARCHAR(36)  NOT NULL COMMENT '用户ID(UUID)',
    `tenant_id`      VARCHAR(36)  NOT NULL COMMENT '租户ID',
    `username`       VARCHAR(50)  NOT NULL COMMENT '登录名(唯一)',
    `email`          VARCHAR(100) NOT NULL COMMENT '邮箱(唯一)',
    `password`       VARCHAR(255) NOT NULL COMMENT '密码哈希',
    `phone`          VARCHAR(20)  COMMENT '手机号',
    `name`           VARCHAR(100) COMMENT '姓名',
    `role`           VARCHAR(50)  NOT NULL DEFAULT 'OPERATOR' COMMENT '角色',
    `status`         VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `last_login_at`  DATETIME COMMENT '最后登录时间',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_users_username` (`username`),
    UNIQUE KEY `uk_tenant_users_email` (`email`),
    KEY `idx_tenant_users_tenant_id` (`tenant_id`),
    CONSTRAINT `fk_tenant_users_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `tenants` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户用户表';

CREATE TABLE IF NOT EXISTS tenant_feature_flags (
    `id`           VARCHAR(36)  NOT NULL COMMENT 'ID(UUID)',
    `tenant_id`    VARCHAR(36)  NOT NULL COMMENT '租户ID',
    `feature_key`  VARCHAR(255) NOT NULL COMMENT '功能键',
    `feature_name` VARCHAR(255) COMMENT '功能名称',
    `enabled`      TINYINT(1)   DEFAULT 0 COMMENT '是否启用',
    `config`       TEXT COMMENT '功能配置JSON',
    `description`  VARCHAR(255) COMMENT '描述',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_flags_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户功能开关表';

CREATE TABLE IF NOT EXISTS platform_credentials (
    `id`              VARCHAR(36)  NOT NULL COMMENT 'ID(UUID)',
    `tenant_id`       VARCHAR(36)  NOT NULL COMMENT '租户ID',
    `platform`        VARCHAR(255) NOT NULL COMMENT '平台(AMAZON/SHOPEE/...)',
    `shop_name`       VARCHAR(255) NOT NULL COMMENT '店铺名称',
    `shop_id`         VARCHAR(255) COMMENT '平台店铺ID',
    `credentials`     TEXT COMMENT '加密凭证',
    `client_id`       VARCHAR(255) COMMENT 'Client ID',
    `client_secret`   VARCHAR(255) COMMENT 'Client Secret',
    `developer_id`    VARCHAR(255) COMMENT '开发者ID',
    `refresh_token`   TEXT COMMENT 'Refresh Token',
    `access_token`    TEXT COMMENT 'Access Token',
    `token_expires_at` DATETIME COMMENT 'Token过期时间',
    `marketplace`     VARCHAR(255) COMMENT '市场站点',
    `status`          VARCHAR(255) COMMENT '状态(ACTIVE/EXPIRED/ERROR/INACTIVE)',
    `last_sync_at`    DATETIME COMMENT '最后同步时间',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_platform_credentials_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台凭证表';

CREATE TABLE IF NOT EXISTS tenant_usage_records (
    `id`             VARCHAR(36)  NOT NULL COMMENT 'ID(UUID)',
    `tenant_id`      VARCHAR(36)  NOT NULL COMMENT '租户ID',
    `billing_period` VARCHAR(7)   NOT NULL COMMENT '计费周期(YYYY-MM)',
    `api_calls`      BIGINT       DEFAULT 0 COMMENT 'API调用次数',
    `storage_bytes`  BIGINT       DEFAULT 0 COMMENT '存储字节数',
    `order_count`    INT          DEFAULT 0 COMMENT '订单数',
    `platform_count` INT          DEFAULT 0 COMMENT '平台数',
    `ai_calls`       BIGINT       DEFAULT 0 COMMENT 'AI调用次数',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_usage_tenant_period` (`tenant_id`, `billing_period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户用量记录表';


CREATE TABLE IF NOT EXISTS billing_rules (
    `id`            VARCHAR(36)   NOT NULL COMMENT 'ID(UUID)',
    `name`          VARCHAR(255)  NOT NULL COMMENT '规则名称',
    `resource_type` VARCHAR(255)  NOT NULL COMMENT '资源类型(AI_CALLS/API_CALLS/ORDERS/PLATFORMS/STORAGE)',
    `tier_type`     VARCHAR(255)  NOT NULL COMMENT '计费模式(FIXED/TIERED/VOLUME)',
    `free_quota`    BIGINT        DEFAULT 0 COMMENT '免费额度',
    `unit_price`    DECIMAL(10,4) NOT NULL COMMENT '单价',
    `tier_config`   TEXT COMMENT '阶梯配置JSON',
    `active`        TINYINT(1)    DEFAULT 1 COMMENT '是否启用',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_billing_rules_resource` (`resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费规则表';

CREATE TABLE IF NOT EXISTS tenant_bills (
    `id`               VARCHAR(36)   NOT NULL COMMENT 'ID(UUID)',
    `tenant_id`        VARCHAR(36)   NOT NULL COMMENT '租户ID',
    `billing_period`   VARCHAR(7)    NOT NULL COMMENT '计费周期(YYYY-MM)',
    `bill_type`        VARCHAR(255)  NOT NULL COMMENT '账单类型(SUBSCRIPTION/USAGE/OVERDUE)',
    `status`           VARCHAR(255)  NOT NULL COMMENT '状态(PENDING/PAID/OVERDUE/CANCELLED)',
    `subscription_fee` DECIMAL(10,2) DEFAULT 0 COMMENT '订阅费',
    `usage_fee`        DECIMAL(10,2) DEFAULT 0 COMMENT '用量费',
    `discount`         DECIMAL(10,2) DEFAULT 0 COMMENT '折扣',
    `total_amount`     DECIMAL(10,2) NOT NULL COMMENT '总金额',
    `paid_amount`      DECIMAL(10,2) DEFAULT 0 COMMENT '已付金额',
    `payment_method`   VARCHAR(255)  COMMENT '支付方式',
    `paid_at`          DATETIME COMMENT '支付时间',
    `due_date`         DATETIME COMMENT '到期日',
    `details`          TEXT COMMENT '账单明细JSON',
    `remark`           VARCHAR(255)  COMMENT '备注',
    `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_bills_tenant_period` (`tenant_id`, `billing_period`),
    KEY `idx_tenant_bills_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户账单表';

-- ⚠️⚠️ billing_period 必须是 VARCHAR(7)，不能是 VARBINARY！
-- Hibernate **6.2.x 不支持 java.time.YearMonth**（整个 hibernate-core 里连
-- YearMonthType 都没有）→ 它把 YearMonth 当成 Serializable 类型处理，
-- `ddl-auto: update` 会**把这一列悄悄改成 varbinary(255)**。
-- 后果：表里有数据时，读取时用 Java 反序列化一个 VARCHAR 字符串 →
--   org.hibernate.type.SerializationException: could not deserialize
--   → GET /api/tenant/billing/bills 直接 500。
--   （表里**没数据**时不报错 —— 查不到行就不会反序列化，所以这个坑特别隐蔽）
-- 现在实体上加了 @Convert(converter = YearMonthConverter.class)，
-- 期望类型回到 VARCHAR，这里再显式 ALTER 一次修正已经被改坏的库。
--
-- 先删掉已经被当成二进制垃圾写进去的行（正则不匹配的），
-- 避免 ALTER 时转换失败；后面 seed-tenant.sql 会重新种上正确的行。
DELETE FROM `tenant_bills`
    WHERE `billing_period` IS NOT NULL
      AND NOT (CAST(`billing_period` AS CHAR) REGEXP '^[0-9]{4}-[0-9]{2}$');
DELETE FROM `tenant_usage_records`
    WHERE `billing_period` IS NOT NULL
      AND NOT (CAST(`billing_period` AS CHAR) REGEXP '^[0-9]{4}-[0-9]{2}$');
ALTER TABLE `tenant_bills`
    MODIFY COLUMN `billing_period` VARCHAR(7) NOT NULL COMMENT '计费周期(YYYY-MM)';
ALTER TABLE `tenant_usage_records`
    MODIFY COLUMN `billing_period` VARCHAR(7) NOT NULL COMMENT '计费周期(YYYY-MM)';
