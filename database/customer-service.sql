-- ===================================================
-- 客户管理服务 (erp-customer-service) 数据库初始化脚本
-- MySQL 8.0+
-- 对应实体: Customer / CustomerTag / CustomerGroup / CustomerTagRelation / CustomerFollowRecord
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_customer` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_customer`;

-- ------------------------------------------------------------
-- 客户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer`;
CREATE TABLE `t_customer` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '客户姓名',
    `email` VARCHAR(200) COMMENT '客户邮箱',
    `phone` VARCHAR(50) COMMENT '联系电话',
    `platform` VARCHAR(50) COMMENT '来源平台(AMAZON/EBAY/SHOPIEE/LAZADA/TIKTOK等)',
    `platform_customer_id` VARCHAR(100) COMMENT '平台客户ID',
    `country` VARCHAR(50) COMMENT '国家/地区',
    `state` VARCHAR(100) COMMENT '省份/州',
    `city` VARCHAR(100) COMMENT '城市',
    `address` VARCHAR(500) COMMENT '详细地址',
    `postal_code` VARCHAR(20) COMMENT '邮政编码',
    `level` VARCHAR(20) DEFAULT 'NEW' COMMENT '客户等级(VIP/NORMAL/NEW)',
    `total_orders` INT DEFAULT 0 COMMENT '订单总数',
    `total_spent` DECIMAL(18,4) DEFAULT 0.0000 COMMENT '累计消费金额',
    `avg_order_value` DECIMAL(18,4) DEFAULT 0.0000 COMMENT '平均订单金额',
    `last_order_at` DATETIME COMMENT '最后下单时间',
    `remark` VARCHAR(500) COMMENT '客户备注',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/INACTIVE/BLOCKED)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_platform` (`platform`, `platform_customer_id`),
    KEY `idx_customer_email` (`email`),
    KEY `idx_customer_phone` (`phone`),
    KEY `idx_customer_country` (`country`),
    KEY `idx_customer_level` (`level`),
    KEY `idx_customer_status` (`status`),
    KEY `idx_customer_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户表';

-- ------------------------------------------------------------
-- 客户标签表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer_tag`;
CREATE TABLE `t_customer_tag` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `color` VARCHAR(20) COMMENT '标签颜色(如 #FF5722)',
    `description` VARCHAR(500) COMMENT '标签描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/INACTIVE)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`name`),
    KEY `idx_tag_status` (`status`),
    KEY `idx_tag_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户标签表';

-- ------------------------------------------------------------
-- 客户分组表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer_group`;
CREATE TABLE `t_customer_group` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分组名称',
    `description` VARCHAR(500) COMMENT '分组描述',
    `member_count` INT DEFAULT 0 COMMENT '成员数量(冗余统计字段)',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/INACTIVE)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`name`),
    KEY `idx_group_status` (`status`),
    KEY `idx_group_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户分组表';

-- ------------------------------------------------------------
-- 客户标签关联表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer_tag_relation`;
CREATE TABLE `t_customer_tag_relation` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_relation` (`customer_id`, `tag_id`),
    KEY `idx_tag_relation_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户标签关联表';

-- ------------------------------------------------------------
-- 客户跟进记录表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer_follow_record`;
CREATE TABLE `t_customer_follow_record` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `follow_type` VARCHAR(20) DEFAULT 'OTHER' COMMENT '跟进方式(PHONE/EMAIL/WECHAT/VISIT/OTHER)',
    `content` VARCHAR(1000) COMMENT '跟进内容',
    `follow_time` DATETIME COMMENT '本次跟进时间',
    `next_follow_time` DATETIME COMMENT '下次跟进时间',
    `result` VARCHAR(20) DEFAULT 'PENDING' COMMENT '跟进结果(SUCCESS/PENDING/FAILED)',
    `follow_user_id` BIGINT COMMENT '跟进人ID',
    `follow_user_name` VARCHAR(100) COMMENT '跟进人姓名',
    `follower_name` VARCHAR(100) COMMENT '跟进人展示名',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_follow_customer_id` (`customer_id`),
    KEY `idx_follow_type` (`follow_type`),
    KEY `idx_follow_result` (`result`),
    KEY `idx_follow_next_time` (`next_follow_time`),
    KEY `idx_follow_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户跟进记录表';
