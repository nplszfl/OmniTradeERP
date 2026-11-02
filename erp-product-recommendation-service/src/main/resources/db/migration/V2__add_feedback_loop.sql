-- ===================================================
-- v1.9.0 Feedback 闭环 - 真实反馈数据持久化 (MySQL)
-- ===================================================
-- 📌 方言说明：原脚本标注「兼容 H2 (开发) / PostgreSQL (生产)」，
--    实际是 MySQL 方言（AUTO_INCREMENT）。项目无 Postgres 实例，统一到 MySQL。
-- ===================================================

CREATE TABLE IF NOT EXISTS recommend_feedback (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `result_id`             BIGINT        NOT NULL COMMENT '推荐结果ID',
    `candidate_id`          BIGINT        NOT NULL COMMENT '候选ID',
    `sku`                   VARCHAR(64)   COMMENT 'SKU',
    `category`              VARCHAR(64)   COMMENT '类目',
    `platform`              VARCHAR(32)   COMMENT '平台',

    -- 采纳信号: 卖家接受了哪条推荐
    -- ADOPTED  接受并上架
    -- REJECTED 拒绝
    -- IGNORED  未表态
    `feedback_type`         VARCHAR(32)   NOT NULL COMMENT '反馈类型',

    -- 卖家主观打分 1~5
    `seller_rating`         INT           COMMENT '卖家评分1-5',

    -- 真实 outcome (上架后若干天回填)
    `actual_30d_sales`      INT           COMMENT '实际30天销量',
    `actual_30d_profit`     DECIMAL(14,2) COMMENT '实际30天利润',
    `actual_conversion_rate` DOUBLE       COMMENT '实际转化率',

    -- 计算字段（写时缓存，避免重复聚合）
    -- accuracy_score = -1 ~ 1
    --   >0 表示推荐方向正确（采纳后表现好）
    --   <0 表示推荐方向错误
    --   =0 表示中性（未采纳/无 outcome）
    `accuracy_score`        DOUBLE        DEFAULT 0.0 COMMENT '准确度评分 -1~1',

    `feedback_note`         VARCHAR(512)  COMMENT '反馈备注',
    `feedback_by`           VARCHAR(64)   COMMENT '反馈人',

    `created_at`            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_recommend_feedback_result` (`result_id`),
    KEY `idx_recommend_feedback_candidate` (`candidate_id`),
    KEY `idx_recommend_feedback_type` (`feedback_type`),
    KEY `idx_recommend_feedback_category` (`category`, `platform`),
    KEY `idx_recommend_feedback_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐反馈闭环';

-- 权重自适应快照表 - 记录每次权重调优后的状态
CREATE TABLE IF NOT EXISTS recommend_weight_snapshot (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `w_demand`        DOUBLE      NOT NULL COMMENT '需求权重',
    `w_trend`         DOUBLE      NOT NULL COMMENT '趋势权重',
    `w_profit`        DOUBLE      NOT NULL COMMENT '利润权重',
    `w_competition`   DOUBLE      NOT NULL COMMENT '竞争权重',
    `w_quality`       DOUBLE      NOT NULL COMMENT '质量权重',
    `sample_count`    INT         NOT NULL COMMENT '样本数',
    `avg_accuracy`    DOUBLE      COMMENT '平均准确度',
    `adopt_rate`      DOUBLE      COMMENT '采纳率',
    `trigger_reason`  VARCHAR(256) COMMENT '触发原因',
    `created_at`      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_weight_snapshot_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权重自适应快照';

-- ---------------- 示例种子反馈数据 (开发测试使用) ----------------
INSERT INTO recommend_feedback
  (result_id, candidate_id, sku, category, platform, feedback_type, seller_rating,
   actual_30d_sales, actual_30d_profit, actual_conversion_rate, accuracy_score,
   feedback_note, feedback_by, created_at, updated_at)
VALUES
  -- 5 条历史反馈模拟"过去 30 天数据"
  (1, 1, 'AMZ-USB-001', 'Electronics', 'amazon', 'ADOPTED', 5,
   3500, 9800.00, 0.18, 0.85,
   'USB-C 充电器大卖，趋势判断准确', 'seller-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 2, 'AMZ-LED-002', 'Home', 'amazon', 'ADOPTED', 4,
   1900, 3200.00, 0.12, 0.65,
   'LED 灯带还行，季节性对得上', 'seller-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 3, 'AMZ-PET-003', 'Pet', 'amazon', 'REJECTED', 2,
   0, 0.00, 0.00, -0.40,
   '宠物喂食器竞争太激烈，没敢上', 'seller-002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, 4, 'AMZ-FIT-004', 'Sports', 'amazon', 'IGNORED', 1,
   0, 0.00, 0.00, 0.00,
   '瑜伽垫评分不准，毛利率虚高', 'seller-002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (5, 5, 'AMZ-CAM-005', 'Baby', 'amazon', 'ADOPTED', 5,
   2400, 18500.00, 0.22, 0.95,
   '宝宝监视器爆款，强烈推荐', 'seller-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 初始权重快照 (基线)
INSERT INTO recommend_weight_snapshot
  (w_demand, w_trend, w_profit, w_competition, w_quality,
   sample_count, avg_accuracy, adopt_rate, trigger_reason, created_at)
VALUES
  (0.30, 0.20, 0.20, 0.15, 0.15, 0, 0.0, 0.0,
   'INITIAL_BASELINE', CURRENT_TIMESTAMP);
