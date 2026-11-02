-- ===================================================
-- 选品推荐服务 示例种子数据 (dev/test)
-- ===================================================
-- 📌 只在 dev / test profile 加载。生产环境不应该有示例商品，
--    所以 data.sql 不在 prod profile 的 sql.init 里。
-- ===================================================

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

INSERT INTO recommend_feedback
  (result_id, candidate_id, sku, category, platform, feedback_type, seller_rating,
   actual_30d_sales, actual_30d_profit, actual_conversion_rate, accuracy_score,
   feedback_note, feedback_by, created_at, updated_at)
VALUES
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
INSERT INTO recommend_weight_snapshot
  (w_demand, w_trend, w_profit, w_competition, w_quality,
   sample_count, avg_accuracy, adopt_rate, trigger_reason, created_at)
VALUES
  (0.30, 0.20, 0.20, 0.15, 0.15, 0, 0.0, 0.0,
   'INITIAL_BASELINE', CURRENT_TIMESTAMP);
