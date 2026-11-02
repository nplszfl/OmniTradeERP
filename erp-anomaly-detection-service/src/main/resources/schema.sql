-- ===================================================
-- AI 订单异常检测服务 schema
-- ===================================================
-- ⚠️ 这份脚本之前是 H2 方言，且 prod profile 指向 PostgreSQL，
--    但整个仓库既没有 Postgres 实例（compose 只有 mysql:8.0），
--    也没有任何 PostgreSQL 版 DDL —— prod profile 是纯空配置。
--    现统一到 MySQL（项目权威数据库，见 docs/BUILD-FIX-2026-10-27.md）。
--
-- 📌 为什么 MySQL 方言能同时跑在 H2（dev/test）上：
--    H2 以 MODE=MySQL 启动时接受 ENGINE=InnoDB / CHARSET /
--    内联 KEY / 反引号 / 列级 COMMENT 等 MySQL 语法。
--    H2 独有语法（如 CREATE TABLE ... 的 MySQL 保留字）已在下面避开。
-- ===================================================

CREATE TABLE IF NOT EXISTS t_anomaly_record (
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
