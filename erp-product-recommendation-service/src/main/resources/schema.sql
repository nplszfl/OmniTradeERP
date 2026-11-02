-- ===================================================
-- 选品推荐服务 schema (MySQL)
-- ===================================================
-- 📌 这份 schema.sql 是**真正会被执行**的那份。
--    之前建表脚本只放在 db/migration/V1__init_recommend_tables.sql 和
--    V2__add_feedback_loop.sql，但本模块**没有 Flyway 依赖** →
--    那两个 .sql 永远不会被执行 → 4 张表从来没被建出来，
--    任何一次 Mapper 查询都会抛 "Table doesn't exist"。
--
--    现在：
--      - 建表 DDL → 本文件（schema.sql），由 spring.sql.init 执行
--      - 示例种子数据 → data.sql（仅 dev/test）
--      - db/migration/*.sql 保留为历史归档，但已不被引用
--
--    方言：MySQL（项目权威数据库，compose 只有 mysql:8.0）。
--    H2 以 MODE=MySQL 启动时接受同样的语法 → 一份 DDL 两边都能跑。
--
--    主键用 AUTO_INCREMENT 是对的：实体的 @TableId 是 IdType.AUTO
--    （由数据库生成自增主键），不是 MyBatis-Plus 默认的 ASSIGN_ID 雪花 ID。
-- ===================================================

CREATE TABLE IF NOT EXISTS recommend_candidate (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sku`                 VARCHAR(64)   NOT NULL COMMENT 'SKU',
    `title`               VARCHAR(256)  COMMENT '商品标题',
    `category`            VARCHAR(64)   COMMENT '类目',
    `platform`            VARCHAR(32)   COMMENT '平台',
    `cost_price`          DECIMAL(12,2) COMMENT '成本价',
    `suggest_price`       DECIMAL(12,2) COMMENT '建议售价',
    `bsr_rank`            INT           COMMENT 'BSR排名',
    `monthly_searches`    BIGINT        COMMENT '月搜索量',
    `last_30d_sales`      BIGINT        COMMENT '近30天销量',
    `review_count`        INT           COMMENT '评论数',
    `avg_rating`          DOUBLE        COMMENT '平均评分',
    `category_avg_price`  DECIMAL(12,2) COMMENT '类目均价',
    `gross_margin`        DOUBLE        COMMENT '毛利率',
    `trend_score`         DOUBLE        COMMENT '趋势分',
    `seasonality`         DOUBLE        COMMENT '季节性',
    `competition_intensity` DOUBLE      COMMENT '竞争强度',
    `remark`              VARCHAR(512)  COMMENT '备注',
    `created_at`          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_recommend_candidate_platform` (`platform`),
    KEY `idx_recommend_candidate_category` (`category`),
    KEY `idx_recommend_candidate_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选品候选池';
CREATE TABLE IF NOT EXISTS recommend_result (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `candidate_id`          BIGINT        NOT NULL COMMENT '候选ID',
    `sku`                   VARCHAR(64)   COMMENT 'SKU',
    `score`                 DOUBLE        COMMENT '综合得分',
    `recommend_level`       VARCHAR(32)   COMMENT '推荐等级',
    `rank_position`         INT           COMMENT '排名位次',
    `expected_monthly_profit` DOUBLE      COMMENT '预期月利润',
    `expected_monthly_sales` INT          COMMENT '预期月销量',
    `risk_score`            DOUBLE        COMMENT '风险分',
    `triggered_rules`       VARCHAR(512)  COMMENT '命中规则',
    `key_strengths`         VARCHAR(1024) COMMENT '核心优势',
    `key_risks`             VARCHAR(1024) COMMENT '主要风险',
    `adoption_status`       VARCHAR(32)   DEFAULT 'PENDING' COMMENT '采纳状态',
    `created_at`            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_recommend_result_score` (`score`),
    KEY `idx_recommend_result_level` (`recommend_level`),
    KEY `idx_recommend_result_candidate` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐结果';

CREATE TABLE IF NOT EXISTS recommend_feedback (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `result_id`             BIGINT        NOT NULL COMMENT '推荐结果ID',
    `candidate_id`          BIGINT        NOT NULL COMMENT '候选ID',
    `sku`                   VARCHAR(64)   COMMENT 'SKU',
    `category`              VARCHAR(64)   COMMENT '类目',
    `platform`              VARCHAR(32)   COMMENT '平台',
    `feedback_type`         VARCHAR(32)   NOT NULL COMMENT '反馈类型',
    `seller_rating`         INT           COMMENT '卖家评分1-5',
    `actual_30d_sales`      INT           COMMENT '实际30天销量',
    `actual_30d_profit`     DECIMAL(14,2) COMMENT '实际30天利润',
    `actual_conversion_rate` DOUBLE       COMMENT '实际转化率',
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
