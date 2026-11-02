-- ===================================================
-- AI 订单异常检测服务数据库初始化脚本 (MySQL)
-- ===================================================
-- 数据库: erp_anomaly
--
-- 表清单:
--   t_anomaly_record  异常检测记录
--
-- 说明: 这个服务之前是「无状态」的 —— 检测算完就丢，业务方查不到历史、
--      也没法人工复核、没法统计异常率。2026-11-02 起补上落库。
--
-- 📌 表结构与 erp-anomaly-detection-service/src/main/resources/schema.sql 保持一致
--    （后者是服务内副本，供 dev/test 的 H2 建表用）。
-- ===================================================

-- ===================================================
-- 字符集必须显式声明：MySQL 官方镜像默认 LANG 为空（POSIX locale），
-- mysql 客户端会退化成 latin1 连接，导致本文件里的 UTF-8 中文被双重编码
-- （UTF-8 字节被当作 latin1 再转 UTF-8），数据库里存成乱码。
-- 每个 init 文件由 docker-entrypoint 用独立的 mysql 会话执行，
-- 所以 SET NAMES 必须写在每个文件内部，容器级 locale 兜底不可靠。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `erp_anomaly` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `erp_anomaly`;

DROP TABLE IF EXISTS `t_anomaly_record`;
CREATE TABLE `t_anomaly_record` (
    `id`                   BIGINT         NOT NULL COMMENT '主键ID',
    `order_id`             VARCHAR(64)    NOT NULL COMMENT '订单ID',
    `order_no`             VARCHAR(64)    COMMENT '订单号',
    `platform`             VARCHAR(30)    COMMENT '平台(AMAZON/SHOPEE/EBAY/LAZADA/TIKTOK)',
    `order_amount`         DECIMAL(18,2)  COMMENT '订单金额',
    `risk_score`           INT            DEFAULT 0 COMMENT '综合风险评分 0-100',
    `risk_level`           VARCHAR(20)    COMMENT '风险等级(LOW/MEDIUM/HIGH/CRITICAL)',
    `anomaly_flag`         TINYINT(1)     DEFAULT 0 COMMENT '是否异常',
    `anomaly_types`        VARCHAR(500)   COMMENT '异常类型JSON数组',
    `triggered_rules`      VARCHAR(1000)  COMMENT '命中规则JSON数组',
    `ai_score`             INT            DEFAULT 0 COMMENT 'AI评分',
    `rule_score`           INT            DEFAULT 0 COMMENT '规则引擎评分',
    `recommendation`       VARCHAR(20)    COMMENT '处置建议(ALLOW/REVIEW/HOLD/REJECT)',
    `explanation`          VARCHAR(1000)  COMMENT '检测说明',
    `detection_latency_ms` BIGINT         COMMENT '检测耗时(毫秒)',
    `status`               VARCHAR(20)    DEFAULT 'PENDING' COMMENT '复核状态(PENDING/CONFIRMED/REJECTED)',
    `confirmed_by`         VARCHAR(50)    COMMENT '复核人',
    `confirmed_at`         DATETIME       COMMENT '复核时间',
    `confirm_remark`       VARCHAR(500)   COMMENT '复核备注',
    `create_time`          DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_status` (`status`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单异常检测记录表';
