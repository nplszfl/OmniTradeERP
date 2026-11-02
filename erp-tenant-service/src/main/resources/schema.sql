-- ===================================================
-- 租户服务 schema (MySQL)
-- ===================================================
-- ⚠️ 这份脚本之前是 PostgreSQL 版，而且**漏了 5 张表**：
--    实体有 7 张表（tenants / tenant_users / tenant_feature_flags /
--    platform_credentials / tenant_usage_records / billing_rules / tenant_bills），
--    旧脚本只建了 tenants / tenant_users / tenant_subscriptions / ai_usage_records，
--    后两张更是全仓库无人引用的孤儿表。
--
--    为什么 schema.sql 不是可有可无的：
--    本模块用的是 JPA，ddl-auto: update 会让 Hibernate 自己建表，
--    所以「表缺失」不会立刻报错。但 Hibernate 生成的是 MySQL 方言
--    （尾部带 `engine=InnoDB`），在非 MySQL 模式的 H2 上会失败，
--    而失败只以 WARN 打印 → 上下文照样启动 → 测试“通过”但表不存在。
--    显式提供本文件可以保证表结构可控、不依赖 Hibernate 的方言推断。
--
--    方言：MySQL（项目权威数据库，compose 只有 mysql:8.0）。
--    H2 以 MODE=MySQL 启动时接受同样的语法 → 一份 DDL 两边都能跑。
--
-- 📌 主键：实体用 @GeneratedValue(strategy = GenerationType.UUID)，
--    主键由应用生成 UUID 字符串，不是数据库自增 → 列宽给 VARCHAR(36)。
-- 📌 枚举：实体用 @Enumerated(EnumType.STRING) → 存字符串，不是 MySQL ENUM。
-- 📌 YearMonth：Hibernate 6.2 没有 YearMonth 基本类型，会序列化成 varbinary。
--    这里显式声明为 VARCHAR(7)（形如 2026-11），语义更可读且能按字符串比较。
-- ===================================================

-- ---------------- 租户表 ----------------
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

-- ---------------- 租户用户表 ----------------
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

-- ---------------- 租户功能开关表 ----------------
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

-- ---------------- 平台凭证表 ----------------
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

-- ---------------- 租户用量记录表 ----------------
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

-- ---------------- 计费规则表 ----------------
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

-- ---------------- 账单表 ----------------
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
