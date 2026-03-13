-- ===================================================
-- 跨境电商ERP系统 - 完整数据库初始化脚本
-- MySQL 8.0+
-- ===================================================

-- 创建订单数据库
CREATE DATABASE IF NOT EXISTS `erp_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_order`;

-- 订单表
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `platform` VARCHAR(50) NOT NULL COMMENT '平台类型',
    `shop_id` VARCHAR(100) NOT NULL COMMENT '店铺ID',
    `platform_order_no` VARCHAR(100) NOT NULL COMMENT '平台订单号',
    `internal_order_no` VARCHAR(100) NOT NULL COMMENT '内部订单号',
    `buyer_id` VARCHAR(100) COMMENT '买家ID',
    `buyer_name` VARCHAR(200) COMMENT '买家姓名',
    `buyer_email` VARCHAR(200) COMMENT '买家邮箱',
    `buyer_phone` VARCHAR(50) COMMENT '买家手机号',
    `order_amount` DECIMAL(18,2) NOT NULL COMMENT '订单金额',
    `product_amount` DECIMAL(18,2) COMMENT '商品金额',
    `shipping_amount` DECIMAL(18,2) COMMENT '运费金额',
    `tax_amount` DECIMAL(18,2) COMMENT '税费',
    `discount_amount` DECIMAL(18,2) COMMENT '折扣金额',
    `currency_code` VARCHAR(10) NOT NULL COMMENT '货币代码',
    `status` VARCHAR(50) NOT NULL COMMENT '订单状态',
    `payment_status` VARCHAR(50) COMMENT '支付状态',
    `payment_method` VARCHAR(50) COMMENT '支付方式',
    `payment_time` DATETIME COMMENT '支付时间',
    `shipping_method` VARCHAR(100) COMMENT '发货方式',
    `logistics_company` VARCHAR(100) COMMENT '物流公司',
    `tracking_number` VARCHAR(200) COMMENT '物流单号',
    `shipping_time` DATETIME COMMENT '发货时间',
    `recipient_name` VARCHAR(200) COMMENT '收货人姓名',
    `recipient_country` VARCHAR(100) COMMENT '收货人国家',
    `recipient_state` VARCHAR(100) COMMENT '收货人省份/州',
    `recipient_city` VARCHAR(100) COMMENT '收货人城市',
    `recipient_address` VARCHAR(255) COMMENT '收货人详细地址',
    `recipient_postal_code` VARCHAR(20) COMMENT '收货人邮编',
    `recipient_phone` VARCHAR(50) COMMENT '收货人电话',
    `remark` TEXT COMMENT '订单备注',
    `raw_data` TEXT TEXT COMMENT '平台原始数据JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(100) COMMENT '创建人',
    `update_by` VARCHAR(100) COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_order` (`platform`, `platform_order_no`),
    KEY `idx_internal_order_no` (`internal_order_no`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_buyer_email` (`buyer_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 订单商品明细表
DROP TABLE IF EXISTS `t_order_item`;
CREATE TABLE `t_order_item` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `platform_product_id` VARCHAR(100) COMMENT '平台商品ID',
    `internal_product_id` VARCHAR(100) COMMENT '内部商品ID',
    `platform_sku` VARCHAR(100) COMMENT '平台商品SKU',
    `internal_sku` VARCHAR(100) COMMENT '内部商品SKU',
    `product_name` VARCHAR(500) COMMENT '商品名称',
    `product_image` VARCHAR(500) COMMENT '商品图片URL',
    `unit_price` DECIMAL(18,2) NOT NULL COMMENT '商品单价',
    `quantity` INT NOT NULL COMMENT '购买数量',
    `total_amount` DECIMAL(18,2) NOT NULL COMMENT '商品总金额',
    `currency_code` VARCHAR(10) NOT NULL COMMENT '币种',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_internal_sku` (`internal_sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品明细表';

-- ===================================================
-- 商品数据库
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_product` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_product`;

-- 商品主表
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `internal_sku` VARCHAR(100) NOT NULL COMMENT '内部SKU',
    `product_name` VARCHAR(500) NOT NULL COMMENT '商品名称',
    `product_name_en` VARCHAR(500) COMMENT '商品英文名称',
    `category_id` BIGINT COMMENT '商品分类ID',
    `brand` VARCHAR(100) COMMENT '品牌',
    `main_image` VARCHAR(500) COMMENT '主图URL',
    `images` TEXT COMMENT '商品图片JSON数组',
    `description` TEXT COMMENT '商品描述',
    `description_en` TEXT COMMENT '商品英文描述',
    `weight` DECIMAL(10,2) COMMENT '重量(kg)',
    `length` DECIMAL(10,2) COMMENT '长度(cm)',
    `width` DECIMAL(10,2) COMMENT '宽度(cm)',
    `height` DECIMAL(10,2) COMMENT '高度(cm)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0下架 1上架)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(100) COMMENT '创建人',
    `update_by` VARCHAR(100) COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_internal_sku` (`internal_sku`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表';

-- 商品SKU表
DROP TABLE IF EXISTS `t_product_sku`;
CREATE TABLE `t_product_sku` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_code` VARCHAR(100) NOT NULL COMMENT 'SKU编码',
    `sku_name` VARCHAR(200) COMMENT 'SKU名称',
    `specs` TEXT COMMENT '规格属性JSON',
    `cost_price` DECIMAL(18,2) COMMENT '成本价',
    `sale_price` DECIMAL(18,2) COMMENT '销售价',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_code` (`sku_code`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

-- 平台商品映射表
DROP TABLE IF EXISTS `t_platform_product_mapping`;
CREATE TABLE `t_platform_product_mapping` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `internal_sku` VARCHAR(100) NOT NULL COMMENT '内部SKU',
    `platform` VARCHAR(50) NOT NULL COMMENT '平台类型',
    `shop_id` VARCHAR(100) NOT NULL COMMENT '店铺ID',
    `platform_product_id` VARCHAR(100) COMMENT '平台商品ID',
    `platform_sku` VARCHAR(100) COMMENT '平台SKU',
    `mapping_data` TEXT COMMENT '映射数据JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINTINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mapping` (`internal_sku`, `platform`, `shop_id`),
    KEY `idx_platform_sku` (`platform`, `platform_sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台商品映射表';

-- 商品分类表
DROP TABLE IF EXISTS `t_product_category`;
CREATE TABLE `t_product_category` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID',
    `category_name` VARCHAR(200) NOT NULL COMMENT '分类名称',
    `category_name_en` VARCHAR(200) COMMENT '分类英文名',
    `category_code` VARCHAR(100) COMMENT '分类编码',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- ===================================================
-- 库存数据库
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_inventory` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_inventory`;

-- 库存表
DROP TABLE IF EXISTS `t_inventory`;
CREATE TABLE `t_inventory` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `sku_code` VARCHAR(100) NOT NULL COMMENT 'SKU编码',
    `warehouse_id` BIGINT NOT NULL COMMENT '仓库ID',
    `available_qty` INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    `locked_qty` INT NOT NULL DEFAULT 0 COMMENT '锁定库存',
    `total_qty` INT NOT NULL DEFAULT 0 COMMENT '总库存',
    `safety_stock` INT DEFAULT 0 COMMENT '安全库存',
    `min_stock` INT DEFAULT 0 COMMENT '最小库存',
    `max_stock` INT DEFAULT 0 COMMENT '最大库存',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_warehouse` (`sku_code`, `warehouse_id`),
    KEY `idx_sku_code` (`sku_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 库存变动记录表
DROP TABLE IF EXISTS `t_inventory_log`;
CREATE TABLE `t_inventory_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `sku_code` VARCHAR(100) NOT NULL COMMENT 'SKU编码',
    `warehouse_id` BIGINT NOT NULL COMMENT '仓库ID',
    `change_type` VARCHAR(50) NOT NULL COMMENT '变动类型(in/out/lock/unlock)',
    `change_qty` INT NOT NULL COMMENT '变动数量',
    `before_qty` INT NOT NULL COMMENT '变动前数量',
    `after_qty` INT NOT NULL COMMENT '变动后数量',
    `order_no` VARCHAR(100) COMMENT '关联订单号',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_sku_code` (`sku_code`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动记录表';

-- ===================================================
-- 仓库数据库
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_warehouse` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_warehouse`;

-- 仓库表
DROP TABLE IF EXISTS `t_warehouse`;
CREATE TABLE `t_warehouse` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `warehouse_code` VARCHAR(50) NOT NULL COMMENT '仓库编码',
    `warehouse_name` VARCHAR(200) NOT NULL COMMENT '仓库名称',
    `warehouse_type` VARCHAR(50) NOT NULL COMMENT '仓库类型(main/overseas/third_party)',
    `country` VARCHAR(100) COMMENT '所在国家',
    `province` VARCHAR(100) COMMENT '所在省份',
    `city` VARCHAR(100) COMMENT '所在城市',
    `address` VARCHAR(500) COMMENT '详细地址',
    `contact_person` VARCHAR(100) COMMENT '联系人',
    `contact_phone` VARCHAR(50) COMMENT '联系电话',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_code` (`warehouse_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库表';

-- 出入库记录表
DROP TABLE IF EXISTS `t_warehouse_in_out`;
CREATE TABLE `t_warehouse_in_out` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `warehouse_id` BIGINT NOT NULL COMMENT '仓库ID',
    `in_out_type` VARCHAR(50) NOT NULL COMMENT '出入库类型(in/out)',
    `order_no` VARCHAR(100) COMMENT '关联订单号',
    `status` VARCHAR(50) NOT NULL COMMENT '状态(pending/completed/cancelled)',
    `total_items` INT DEFAULT 0 COMMENT '总件数',
    `operator` VARCHAR(100) COMMENT '操作人',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出入库记录表';

-- 出入库明细表
DROP TABLE IF EXISTS `t_warehouse_in_out_item`;
CREATE TABLE `t_warehouse_in_out_item` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `in_out_id` BIGINT NOT NULL COMMENT '出入库记录ID',
    `sku_code` VARCHAR(100) NOT NULL COMMENT 'SKU编码',
    `quantity` INT NOT NULL COMMENT '数量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_in_out_id` (`in_out_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出入库明细表';

-- ===================================================
-- 用户数据库
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_user`;

-- 用户表
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `real_name` VARCHAR(100) COMMENT '真实姓名',
    `email` VARCHAR(200) COMMENT '邮箱',
    `phone` VARCHAR(50) COMMENT '手机号',
    `avatar` VARCHAR(500) COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0禁用 1启用)',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 角色表
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(500) COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 用户角色关联表
DROP TABLE IF EXISTS `t_user_role`;
CREATE TABLE `t_user_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 权限表
DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `permission_code` VARCHAR(100) NOT NULL COMMENT '权限编码',
    `permission_name` VARCHAR(200) NOT NULL COMMENT '权限名称',
    `permission_type` VARCHAR(50) NOT NULL COMMENT '权限类型(menu/button/api)',
    `url` VARCHAR(500) COMMENT '菜单URL/API路径',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 角色权限关联表
DROP TABLE IF EXISTS `t_role_permission`;
CREATE TABLE `t_role_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ===================================================
-- 财务数据库
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_finance` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_finance`;

-- 财务流水表
DROP TABLE IF EXISTS `t_finance_flow`;
CREATE TABLE `t_finance_flow` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `flow_type` VARCHAR(50) NOT NULL COMMENT '流水类型(income/expense/refund)',
    `order_no` VARCHAR(100) COMMENT '关联订单号',
    `amount` DECIMAL(18,2) NOT NULL COMMENT '金额',
    `currency_code` VARCHAR(10) NOT NULL COMMENT '货币代码',
    `category` VARCHAR(100) COMMENT '分类',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='财务流水表';

-- ===================================================
-- 平台数据库
-- ===================================================

CREATE DATABASE IF NOT EXISTS `erp_platform` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_platform`;

-- 平台配置表
DROP TABLE IF EXISTS `t_platform_config`;
CREATE TABLE `t_platform_config` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `platform` VARCHAR(50) NOT NULL COMMENT '平台类型',
    `shop_id` VARCHAR(100) NOT NULL COMMENT '店铺ID',
    `shop_name` VARCHAR(200) COMMENT '店铺名称',
    `api_key` VARCHAR(500) COMMENT 'API Key',
    `api_secret` VARCHAR(500) COMMENT 'API Secret',
    `access_token` TEXT COMMENT 'Access Token',
    `refresh_token` TEXT COMMENT 'Refresh Token',
    `token_expire_time` DATETIME COMMENT 'Token过期时间',
    `api_base_url` VARCHAR(500) COMMENT 'API基础URL',
    `callback_url` VARCHAR(500) COMMENT '回调URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '店铺状态',
    `remark` TEXT COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(100) COMMENT '创建人',
    `update_by` VARCHAR(100)` COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_shop` (`platform`, `shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台配置表';

-- 插入测试数据 - 亚马逊配置
INSERT INTO `t_platform_config`
(`id`, `platform`, `shop_id`, `shop_name`, `api_key`, `api_secret`, `status`)
VALUES
(1, 'amazon', 'US001', '美国亚马逊店铺', 'test_api_key', 'test_api_secret', 1);

-- 插入测试数据 - Shopee配置
INSERT INTO `t_platform_config`
(`id`, `platform`, `shop_id`, `shop_name`, `api_key`, `api_secret`, `status`)
VALUES
(2, 'shopee', 'MY001', '马来西亚Shopee店铺', 'test_api_key', 'test_api_secret', 1);

-- 订单同步日志表
DROP TABLE IF EXISTS `t_sync_log`;
CREATE TABLE `t_sync_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `platform` VARCHAR(50) NOT NULL COMMENT '平台类型',
    `shop_id` VARCHAR(100) NOT NULL COMMENT '店铺ID',
    `sync_type` VARCHAR(50) NOT NULL COMMENT '同步类型(order/product)',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `success_count` INT DEFAULT 0 COMMENT '成功数量',
    `fail_count` INT DEFAULT 0 COMMENT '失败数量',
    `status` VARCHAR(50) NOT NULL COMMENT '状态(running/success/failed)',
    `error_message` TEXT COMMENT '错误信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_platform_shop` (`platform`, `shop_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单同步日志表';

-- ===================================================
-- 完成
-- ===================================================
