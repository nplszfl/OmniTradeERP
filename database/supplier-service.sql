-- ===================================================
-- 供应商管理服务 (erp-supplier-service) 数据库初始化脚本
-- MySQL 8.0+
-- 对应实体: Supplier / PurchaseOrder / PurchaseOrderItem / SupplierEvaluation
-- ===================================================

-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_supplier` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_supplier`;

-- ------------------------------------------------------------
-- 供应商表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_supplier`;
CREATE TABLE `t_supplier` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '供应商名称',
    `contact_person` VARCHAR(50) COMMENT '联系人',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `email` VARCHAR(100) COMMENT '邮箱',
    `type` VARCHAR(30) COMMENT '供应商类型(FACTORY/TRADER/AGENT) —— 与前端下拉取值一致',
    `country` VARCHAR(50) COMMENT '国家',
    `city` VARCHAR(50) COMMENT '城市',
    `address` VARCHAR(255) COMMENT '详细地址',
    `category` VARCHAR(100) COMMENT '主营类目',
    `cooperation_start_at` DATETIME COMMENT '合作开始时间',
    `total_purchase_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '累计采购金额',
    `total_orders` INT DEFAULT 0 COMMENT '累计订单数',
    `rating` DECIMAL(3,2) DEFAULT 0.00 COMMENT '综合评分',
    `level` VARCHAR(20) COMMENT '供应商等级(A/B/C)',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/INACTIVE)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_supplier_name` (`name`),
    KEY `idx_supplier_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ------------------------------------------------------------
-- 采购订单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_purchase_order`;
CREATE TABLE `t_purchase_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '采购单号',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `supplier_name` VARCHAR(100) COMMENT '供应商名称(冗余)',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/CONFIRMED/SHIPPED/RECEIVED/CANCELLED)',
    `total_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '订单总金额',
    `paid_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '已付金额',
    `currency` VARCHAR(10) DEFAULT 'USD' COMMENT '币种',
    `expected_arrival_at` DATETIME COMMENT '预计到货时间',
    `actual_arrival_at` DATETIME COMMENT '实际到货时间',
    `payment_method` VARCHAR(30) COMMENT '支付方式',
    `payment_status` VARCHAR(20) DEFAULT 'UNPAID' COMMENT '支付状态',
    `purchaser_id` BIGINT COMMENT '采购员ID',
    `purchaser_name` VARCHAR(50) COMMENT '采购员姓名',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_po_supplier` (`supplier_id`),
    KEY `idx_po_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单表';

-- ------------------------------------------------------------
-- 采购订单明细表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_purchase_order_item`;
CREATE TABLE `t_purchase_order_item` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '采购单ID',
    `sku` VARCHAR(64) NOT NULL COMMENT 'SKU编码',
    `product_name` VARCHAR(200) COMMENT '商品名称',
    `category` VARCHAR(100) COMMENT '类目',
    `quantity` INT NOT NULL COMMENT '采购数量',
    `unit` VARCHAR(20) COMMENT '单位',
    `unit_price` DECIMAL(18,4) DEFAULT 0.0000 COMMENT '单价',
    `subtotal` DECIMAL(18,2) DEFAULT 0.00 COMMENT '小计',
    `received_quantity` INT DEFAULT 0 COMMENT '已收货数量',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_poi_order` (`order_id`),
    KEY `idx_poi_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

-- ------------------------------------------------------------
-- 供应商评估表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_supplier_evaluation`;
CREATE TABLE `t_supplier_evaluation` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `quality_score` INT COMMENT '质量评分',
    `delivery_score` INT COMMENT '交期评分',
    `price_score` INT COMMENT '价格评分',
    `service_score` INT COMMENT '服务评分',
    `tech_score` INT COMMENT '技术评分',
    `total_score` DECIMAL(5,2) COMMENT '综合得分',
    `conclusion` VARCHAR(500) COMMENT '评估结论',
    `advantages` VARCHAR(500) COMMENT '优势',
    `improvements` VARCHAR(500) COMMENT '改进建议',
    `remark` VARCHAR(500) COMMENT '备注',
    `evaluator_id` BIGINT COMMENT '评估人ID',
    `evaluator_name` VARCHAR(50) COMMENT '评估人姓名',
    `evaluate_at` DATETIME COMMENT '评估时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_eval_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商评估表';
