-- ===================================================
-- 库存预警服务数据库初始化脚本 (MySQL)
-- ===================================================
-- 数据库: erp_inventory_alert
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

-- 📌 COLLATE 必须显式写成 utf8mb4_unicode_ci：
--    MySQL 8 的服务器默认是 utf8mb4_0900_ai_ci，而 erp_warehouse.wms_warehouse
--    用的是 utf8mb4_unicode_ci。两边 collation 不同 → 一旦在 SQL 里直接
--    JOIN warehouse_code 和 warehouse_id 就会报
--    "Illegal mix of collations (utf8mb4_0900_ai_ci, utf8mb4_unicode_ci)"。
--    预警扫描（LiveInventoryMapper）**故意不在 SQL 里 JOIN** 这两列，
--    改成在 Java 侧比对字符串，两种 collation 都能用。
CREATE DATABASE IF NOT EXISTS `erp_inventory_alert` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_inventory_alert`;

CREATE TABLE IF NOT EXISTS t_inventory_alert (
    `id`              BIGINT       NOT NULL COMMENT '主键ID',
    `rule_id`         BIGINT       COMMENT '关联规则ID',
    `product_id`      BIGINT       NOT NULL COMMENT '商品ID',
    `sku`             VARCHAR(100) NOT NULL COMMENT 'SKU',
    `product_name`    VARCHAR(200) COMMENT '商品名称',
    `warehouse_id`    VARCHAR(50)  COMMENT '仓库ID',
    `warehouse_name`  VARCHAR(100) COMMENT '仓库名称',
    `current_stock`   INT          DEFAULT 0 COMMENT '当前库存',
    `alert_stock`     INT          DEFAULT 0 COMMENT '预警库存阈值',
    `safe_stock`      INT          DEFAULT 0 COMMENT '安全库存',
    `alert_type`      VARCHAR(20)  COMMENT 'LOW/OUT/SAFE',
    `message`         VARCHAR(500) COMMENT '预警信息',
    `status`          VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/DETECTED/HANDLED/IGNORED/CANCELLED',
    `handler_id`      BIGINT       COMMENT '处理人ID',
    `handler_name`    VARCHAR(100) COMMENT '处理人姓名',
    `handle_remark`   VARCHAR(500) COMMENT '处理备注',
    `notified`        TINYINT(1)   DEFAULT 0 COMMENT '是否已通知',
    `notify_type`     VARCHAR(20)  DEFAULT 'ALL' COMMENT '通知类型',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `handled_time`    DATETIME     COMMENT '处理时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_product` (`product_id`),
    KEY `idx_alert_status` (`status`),
    KEY `idx_alert_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='库存预警表';

CREATE TABLE IF NOT EXISTS t_inventory_alert_rule (
    `id`              BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT       COMMENT '租户ID',
    `product_id`      BIGINT       COMMENT '商品ID',
    `sku`             VARCHAR(100) COMMENT 'SKU',
    `product_name`    VARCHAR(200) COMMENT '商品名称',
    `warehouse_id`    VARCHAR(50)  COMMENT '仓库ID',
    `warehouse_name`  VARCHAR(100) COMMENT '仓库名称',
    `safe_stock`      INT          DEFAULT 0 COMMENT '安全库存',
    `alert_stock`     INT          DEFAULT 0 COMMENT '预警库存',
    `min_stock`       INT          DEFAULT 0 COMMENT '最低库存',
    `alert_type`      VARCHAR(20)  COMMENT '预警类型',
    `advance_days`    INT          DEFAULT 0 COMMENT '提前预警天数',
    `notify_type`     VARCHAR(20)  DEFAULT 'ALL' COMMENT '通知类型',
    `notify_to`       VARCHAR(200) COMMENT '通知接收人',
    `email_list`      VARCHAR(500) COMMENT '邮件接收列表',
    `phone_list`      VARCHAR(500) COMMENT '手机接收列表',
    `enabled`         TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    `remark`          VARCHAR(500) COMMENT '备注',
    `creator_id`      BIGINT       COMMENT '创建人ID',
    `creator_name`    VARCHAR(100) COMMENT '创建人姓名',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_rule_product` (`product_id`),
    KEY `idx_alert_rule_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='库存预警规则表';

CREATE TABLE IF NOT EXISTS t_inventory_alert_statistics (
    `id`                        BIGINT      NOT NULL COMMENT '主键ID',
    `statistics_date`           DATE        COMMENT '统计日期',
    `warehouse_id`              VARCHAR(50) COMMENT '仓库ID',
    `warehouse_name`            VARCHAR(100) COMMENT '仓库名称',
    `total_alert_count`         INT         DEFAULT 0 COMMENT '预警总数',
    `pending_count`             INT         DEFAULT 0 COMMENT '待处理数',
    `handled_count`             INT         DEFAULT 0 COMMENT '已处理数',
    `ignored_count`             INT         DEFAULT 0 COMMENT '已忽略数',
    `low_stock_count`           INT         DEFAULT 0 COMMENT '低库存数',
    `out_of_stock_count`        INT         DEFAULT 0 COMMENT '缺货数',
    `safe_stock_warning_count`  INT         DEFAULT 0 COMMENT '安全库存预警数',
    `handle_rate`               DOUBLE      DEFAULT 0 COMMENT '处理率',
    `avg_handle_duration`       DOUBLE      DEFAULT 0 COMMENT '平均处理时长',
    `create_time`               DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_stat_date` (`statistics_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='库存预警统计表';

CREATE TABLE IF NOT EXISTS t_replenishment_suggestion (
    `id`                    BIGINT       NOT NULL COMMENT '主键ID',
    `alert_id`              BIGINT       COMMENT '关联预警ID',
    `rule_id`               BIGINT       COMMENT '关联规则ID',
    `product_id`            BIGINT       NOT NULL COMMENT '商品ID',
    `sku`                   VARCHAR(100) NOT NULL COMMENT 'SKU',
    `product_name`          VARCHAR(200) COMMENT '商品名称',
    `warehouse_id`          VARCHAR(50)  COMMENT '仓库ID',
    `warehouse_name`        VARCHAR(100) COMMENT '仓库名称',
    `current_stock`         INT          DEFAULT 0 COMMENT '当前库存',
    `safe_stock`            INT          DEFAULT 0 COMMENT '安全库存',
    `suggested_quantity`    INT          DEFAULT 0 COMMENT '建议补货数量',
    `suggested_date`        DATETIME     COMMENT '建议补货日期',
    `expected_arrival_date` DATETIME     COMMENT '预计到货日期',
    `purchase_price`        DECIMAL(18,2) DEFAULT 0 COMMENT '采购单价',
    `currency`              VARCHAR(10)  DEFAULT 'USD' COMMENT '币种',
    `estimated_amount`      DECIMAL(18,2) DEFAULT 0 COMMENT '预计金额',
    `supplier_id`           BIGINT       COMMENT '供应商ID',
    `supplier_name`         VARCHAR(200) COMMENT '供应商名称',
    `status`                VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/PURCHASED/CANCELLED',
    `urgency_level`         VARCHAR(20)  COMMENT '紧急程度',
    `remark`                VARCHAR(500) COMMENT '备注',
    `creator_id`            BIGINT       COMMENT '创建人ID',
    `creator_name`          VARCHAR(100) COMMENT '创建人姓名',
    `confirmer_id`          BIGINT       COMMENT '确认人ID',
    `confirmer_name`        VARCHAR(100) COMMENT '确认人姓名',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `confirmed_time`        DATETIME     COMMENT '确认时间',
    PRIMARY KEY (`id`),
    KEY `idx_replenishment_product` (`product_id`),
    KEY `idx_replenishment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='补货建议表';

CREATE TABLE IF NOT EXISTS t_slow_moving_inventory (
    `id`                BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`         VARCHAR(50)  COMMENT '租户ID',
    `product_id`        BIGINT       NOT NULL COMMENT '商品ID',
    `sku`               VARCHAR(100) NOT NULL COMMENT 'SKU',
    `product_name`      VARCHAR(200) COMMENT '商品名称',
    `warehouse_id`      VARCHAR(50)  COMMENT '仓库ID',
    `warehouse_name`    VARCHAR(100) COMMENT '仓库名称',
    `current_stock`     INT          DEFAULT 0 COMMENT '当前库存',
    `sales_last_30_days` INT         DEFAULT 0 COMMENT '近30天销量',
    `sales_last_60_days` INT         DEFAULT 0 COMMENT '近60天销量',
    `sales_last_90_days` INT         DEFAULT 0 COMMENT '近90天销量',
    `turnover_rate`     DOUBLE       DEFAULT 0 COMMENT '周转率',
    `last_sale_date`    DATE         COMMENT '最后销售日期',
    `stagnant_days`     INT          DEFAULT 0 COMMENT '滞销天数',
    `alert_level`       VARCHAR(20)  COMMENT 'LOW/MEDIUM/HIGH',
    `status`            VARCHAR(20)  DEFAULT 'PENDING' COMMENT '状态',
    `notified`          TINYINT(1)   DEFAULT 0 COMMENT '是否已通知',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_slow_product` (`product_id`),
    KEY `idx_slow_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='滞销库存表';
