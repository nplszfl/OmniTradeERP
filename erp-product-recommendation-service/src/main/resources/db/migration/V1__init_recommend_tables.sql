-- ===================================================
-- 选品推荐服务 schema (MySQL)
-- ===================================================
-- 📌 方言说明：这份脚本之前标注「兼容 H2 (开发) / PostgreSQL (生产)」，
--    实际内容是 MySQL 方言（AUTO_INCREMENT）—— 在 PostgreSQL 上直接语法报错，
--    而项目里根本没有 Postgres 实例（compose 只有 mysql:8.0）。
--    现在统一到 MySQL：dev/test 用 H2（MODE=MySQL），prod 用 MySQL 8.0。
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

-- ---------------- 示例种子数据 (开发环境使用) ----------------
INSERT INTO recommend_candidate
  (sku, title, category, platform, cost_price, suggest_price, bsr_rank, monthly_searches,
   last_30d_sales, review_count, avg_rating, category_avg_price, gross_margin,
   trend_score, seasonality, competition_intensity, remark, created_at, updated_at)
VALUES
  ('AMZ-USB-001', 'USB-C 100W 快速充电器', 'Electronics', 'amazon', 6.5, 24.9, 250,
   85000, 3200, 4500, 4.5, 22.0, 0.55, 0.65, 0.7, 0.45,
   '蓝海机会，趋势强劲', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('AMZ-LED-002', 'LED 灯带 RGB 5米', 'Home', 'amazon', 3.2, 15.9, 1200,
   42000, 1800, 1200, 4.2, 16.0, 0.40, 0.45, 0.6, 0.55,
   '中等竞争，季节性强', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('AMZ-PET-003', '宠物自动喂食器', 'Pet', 'amazon', 18.0, 49.9, 850,
   22000, 900, 320, 4.0, 52.0, 0.30, 0.30, 0.4, 0.65,
   '竞争激烈', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('AMZ-FIT-004', '瑜伽垫加厚防滑', 'Sports', 'amazon', 5.0, 19.9, 3200,
   15000, 600, 200, 3.8, 22.0, 0.20, -0.10, 0.3, 0.85,
   '红海低质', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('AMZ-CAM-005', '宝宝监视器 1080P', 'Baby', 'amazon', 22.0, 65.0, 480,
   56000, 2200, 2800, 4.6, 68.0, 0.45, 0.75, 0.8, 0.40,
   '黄金推荐品', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
