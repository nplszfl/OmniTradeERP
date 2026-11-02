package com.crossborder.recommend.engine;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.recommend.dto.CandidateFeatures;
import com.crossborder.recommend.dto.RecommendationScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringEngineTest {

    private ScoringEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ScoringEngine();
    }

    @Test
    @DisplayName("STRONG_BUY: 高需求+上升趋势+健康毛利+蓝海+品质验证")
    void strongBuyScenario() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(1L).sku("AMZ-CAM-005").category("Baby").platform("amazon")
            .monthlySearches(200000L).last30dSales(8000L).bsrRank(50)
            .reviewCount(8000).avgRating(4.9)
            .grossMargin(0.65).trendScore(0.9).seasonality(0.9)
            .competitionIntensity(0.10)
            .costPrice(22.0).suggestPrice(65.0)
            .build();

        RecommendationScore r = engine.score(f);

        assertNotNull(r);
        assertEquals("AMZ-CAM-005", r.getSku());
        assertTrue(r.getScore() >= 75, "STRONG_BUY 应 ≥ 75, 实际 " + r.getScore());
        assertEquals("STRONG_BUY", r.getRecommendLevel());
        assertTrue(r.getExpectedMonthlySales() > 0);
        assertTrue(r.getExpectedMonthlyProfit() > 0);
        assertTrue(r.getRiskScore() < 60);
        assertNotNull(r.getTriggeredRules());
        assertTrue(r.getTriggeredRules().contains("STRONG_BUY_THRESHOLD"));
        assertTrue(r.getTriggeredRules().contains("STRONG_UPTREND"));
        assertNotNull(r.getBreakdown());
        // qualityScore = 0.4*normalize(8000) + 0.6*(4.9/5) = 0.4*1 + 0.6*0.98 = 0.988 = 98.8%
        assertEquals(98.8, r.getBreakdown().getQualityScore(), 0.5);
    }

    @Test
    @DisplayName("BUY: 中高综合得分")
    void buyScenario() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(2L).sku("AMZ-USB-001").category("Electronics").platform("amazon")
            .monthlySearches(85000L).last30dSales(3200L).bsrRank(250)
            .reviewCount(4500).avgRating(4.5)
            .grossMargin(0.55).trendScore(0.65).seasonality(0.7)
            .competitionIntensity(0.45)
            .costPrice(6.5).suggestPrice(24.9)
            .build();

        RecommendationScore r = engine.score(f);

        assertNotNull(r);
        // 高需求+高毛利+品质+趋势中等，综合应 ≥ 60
        assertTrue(r.getScore() >= 60, "BUY 应 ≥ 60, 实际 " + r.getScore());
        assertTrue(List.of("BUY", "STRONG_BUY").contains(r.getRecommendLevel()),
            "应为 BUY/STRONG_BUY, 实际 " + r.getRecommendLevel());
    }

    @Test
    @DisplayName("HOLD: 中等水平")
    void holdScenario() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(3L).sku("AMZ-LED-002").category("Home").platform("amazon")
            .monthlySearches(42000L).last30dSales(1800L).bsrRank(1200)
            .reviewCount(1200).avgRating(4.2)
            .grossMargin(0.40).trendScore(0.45).seasonality(0.6)
            .competitionIntensity(0.55)
            .costPrice(3.2).suggestPrice(15.9)
            .build();

        RecommendationScore r = engine.score(f);

        assertNotNull(r);
        // 中等水平
        assertTrue(r.getScore() >= 40 && r.getScore() < 75,
            "应处于 HOLD/BUY 区间, 实际 " + r.getScore());
        assertTrue(List.of("HOLD", "BUY").contains(r.getRecommendLevel()),
            "应为 HOLD/BUY, 实际 " + r.getRecommendLevel());
    }

    @Test
    @DisplayName("SKIP: 红海低质低需求")
    void skipScenario() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(4L).sku("AMZ-FIT-004").category("Sports").platform("amazon")
            .monthlySearches(15000L).last30dSales(600L).bsrRank(3200)
            .reviewCount(200).avgRating(3.8)
            .grossMargin(0.20).trendScore(-0.10).seasonality(0.3)
            .competitionIntensity(0.85)
            .costPrice(5.0).suggestPrice(19.9)
            .build();

        RecommendationScore r = engine.score(f);

        assertNotNull(r);
        assertTrue(r.getScore() < 50, "SKIP 应 < 50, 实际 " + r.getScore());
        assertTrue(r.getRiskScore() >= 60, "高风险预期 ≥ 60, 实际 " + r.getRiskScore());
        assertTrue(List.of("HOLD", "SKIP").contains(r.getRecommendLevel()),
            "应为 HOLD/SKIP, 实际 " + r.getRecommendLevel());
        assertTrue(r.getKeyRisks() != null && !r.getKeyRisks().isEmpty(),
            "应有关键风险提示");
    }

    @Test
    @DisplayName("null 输入安全：所有特征为 null 时不抛异常")
    void nullInputSafe() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(99L).sku("NULL-TEST")
            .build();

        RecommendationScore r = engine.score(f);

        assertNotNull(r);
        assertEquals("NULL-TEST", r.getSku());
        // 全 null 应返回有效分数（基于默认 0）
        assertTrue(r.getScore() >= 0 && r.getScore() <= 100);
        assertNotNull(r.getRecommendLevel());
    }

    @Test
    @DisplayName("边界：评分始终在 0~100 范围内")
    void scoreInValidRange() {
        // 极值: 全满分
        CandidateFeatures max = CandidateFeatures.builder()
            .monthlySearches(100000L).last30dSales(5000L).bsrRank(1)
            .reviewCount(5000).avgRating(5.0)
            .grossMargin(1.0).trendScore(1.0).seasonality(1.0)
            .competitionIntensity(0.0)
            .costPrice(1.0).suggestPrice(100.0)
            .build();

        RecommendationScore rMax = engine.score(max);
        assertTrue(rMax.getScore() <= 100.0, "Score 超过 100: " + rMax.getScore());
        assertEquals("STRONG_BUY", rMax.getRecommendLevel());

        // 极值: 全 0
        CandidateFeatures min = CandidateFeatures.builder()
            .monthlySearches(0L).last30dSales(0L).bsrRank(100000)
            .reviewCount(0).avgRating(0.0)
            .grossMargin(0.0).trendScore(-1.0).seasonality(0.0)
            .competitionIntensity(1.0)
            .build();

        RecommendationScore rMin = engine.score(min);
        assertTrue(rMin.getScore() >= 0.0, "Score 低于 0: " + rMin.getScore());
        assertEquals("SKIP", rMin.getRecommendLevel());
    }

    @Test
    @DisplayName("季节性加成：trend>0 且 seasonality>=0.7 时趋势分应被放大")
    void seasonalityBoost() {
        CandidateFeatures withSeason = CandidateFeatures.builder()
            .trendScore(0.5).seasonality(0.8)
            .build();
        CandidateFeatures noSeason = CandidateFeatures.builder()
            .trendScore(0.5).seasonality(0.3)
            .build();

        double scoreWith = engine.calcTrendScore(withSeason);
        double scoreNo = engine.calcTrendScore(noSeason);

        assertTrue(scoreWith > scoreNo,
            "季节性加成生效: with=" + scoreWith + " > no=" + scoreNo);
    }

    @Test
    @DisplayName("normalize 工具方法边界")
    void normalizeBoundaries() {
        assertEquals(0.0, ScoringEngine.normalize(null, 0, 100), 0.001);
        assertEquals(0.0, ScoringEngine.normalize(0, 0, 100), 0.001);
        assertEquals(1.0, ScoringEngine.normalize(100, 0, 100), 0.001);
        assertEquals(0.5, ScoringEngine.normalize(50, 0, 100), 0.001);
        assertEquals(0.0, ScoringEngine.normalize(-10, 0, 100), 0.001);
        assertEquals(1.0, ScoringEngine.normalize(200, 0, 100), 0.001);
    }

    @Test
    @DisplayName("clamp01 工具方法边界")
    void clamp01Boundaries() {
        assertEquals(0.0, ScoringEngine.clamp01(-1), 0.001);
        assertEquals(0.0, ScoringEngine.clamp01(0), 0.001);
        assertEquals(0.5, ScoringEngine.clamp01(0.5), 0.001);
        assertEquals(1.0, ScoringEngine.clamp01(1), 0.001);
        assertEquals(1.0, ScoringEngine.clamp01(2), 0.001);
        assertEquals(0.0, ScoringEngine.clamp01(Double.NaN), 0.001);
    }

    @Test
    @DisplayName("推荐等级阈值：75/60/40 边界")
    void levelThresholds() {
        assertEquals("STRONG_BUY", engine.determineLevel(100));
        assertEquals("STRONG_BUY", engine.determineLevel(75));
        assertEquals("BUY", engine.determineLevel(74.99));
        assertEquals("BUY", engine.determineLevel(60));
        assertEquals("HOLD", engine.determineLevel(59.99));
        assertEquals("HOLD", engine.determineLevel(40));
        assertEquals("SKIP", engine.determineLevel(39.99));
        assertEquals("SKIP", engine.determineLevel(0));
    }

    @Test
    @DisplayName("预期月销量：trend=1 → +30% boost")
    void monthlySalesEstimation() {
        CandidateFeatures uptrend = CandidateFeatures.builder()
            .last30dSales(1000L).trendScore(1.0).seasonality(0.5)
            .build();
        CandidateFeatures flat = CandidateFeatures.builder()
            .last30dSales(1000L).trendScore(0.0).seasonality(0.5)
            .build();

        int salesUp = engine.estimateMonthlySales(uptrend);
        int salesFlat = engine.estimateMonthlySales(flat);

        // seasonality=0.5 → seasonalFactor = 0.5 + 0.5 = 1.0
        // trend=0 → trendFactor = 1.0, sales=1000
        // trend=1 → trendFactor = 1.0 + 1*0.3 = 1.3, sales=1300
        assertEquals(1000, salesFlat);
        assertEquals(1300, salesUp);
        assertTrue(salesUp > salesFlat, "上升趋势应比平稳预期销量高");
    }

    @Test
    @DisplayName("预期月利润 = 销量 × (售价 - 成本) × 毛利率")
    void monthlyProfitCalculation() {
        CandidateFeatures f = CandidateFeatures.builder()
            .last30dSales(1000L).trendScore(0.0).seasonality(0.5)
            .costPrice(10.0).suggestPrice(25.0).grossMargin(0.5)
            .build();

        int sales = engine.estimateMonthlySales(f);
        double profit = engine.estimateMonthlyProfit(f, sales);

        // 销量 1000 * (25-10)=15 * 0.5 = 7500
        assertEquals(7500.0, profit, 0.01);
    }

    // ==================== v1.9.0 动态权重测试 ====================

    @Test
    @DisplayName("setActiveWeights: 合法权重可生效并被 score 使用")
    void setActiveWeightsValid() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(1L).sku("X").category("C").platform("amazon")
            .monthlySearches(50000L).last30dSales(2000L)
            .bsrRank(500).reviewCount(2000).avgRating(4.5)
            .grossMargin(0.5).trendScore(0.5).seasonality(0.5).competitionIntensity(0.4)
            .costPrice(10.0).suggestPrice(25.0)
            .build();

        // 默认权重打分
        double scoreDefault = engine.score(f).getScore();

        // 调整为 demand 主导
        engine.setActiveWeights(new double[]{0.50, 0.20, 0.10, 0.10, 0.10});
        double scoreDemandHeavy = engine.score(f).getScore();

        // 调整为 trend 主导
        engine.setActiveWeights(new double[]{0.10, 0.50, 0.20, 0.10, 0.10});
        double scoreTrendHeavy = engine.score(f).getScore();

        // 同一候选在不同权重下应有不同得分
        assertNotEquals(scoreDefault, scoreDemandHeavy, 0.01);
        assertNotEquals(scoreDemandHeavy, scoreTrendHeavy, 0.01);
    }

    @Test
    @DisplayName("setActiveWeights: 非法权重应抛异常")
    void setActiveWeightsInvalid() {
        // 长度不对
        assertThrows(BizException.class,
            () -> engine.setActiveWeights(new double[]{0.5, 0.5}));
        // 总和不对
        assertThrows(BizException.class,
            () -> engine.setActiveWeights(new double[]{0.5, 0.2, 0.2, 0.1, 0.1}));
        // 负数
        assertThrows(BizException.class,
            () -> engine.setActiveWeights(new double[]{0.4, -0.1, 0.3, 0.2, 0.2}));
    }

    @Test
    @DisplayName("resetWeights: 重置为默认值")
    void resetWeights() {
        engine.setActiveWeights(new double[]{0.10, 0.10, 0.30, 0.40, 0.10});
        engine.resetWeights();

        double[] w = engine.getActiveWeights();
        assertEquals(ScoringEngine.W_DEMAND, w[0], 0.001);
        assertEquals(ScoringEngine.W_TREND, w[1], 0.001);
        assertEquals(ScoringEngine.W_PROFIT, w[2], 0.001);
        assertEquals(ScoringEngine.W_COMPETITION, w[3], 0.001);
        assertEquals(ScoringEngine.W_QUALITY, w[4], 0.001);
    }

    @Test
    @DisplayName("getActiveWeights: 返回副本，外部修改不影响内部状态")
    void getActiveWeightsReturnsCopy() {
        double[] w = engine.getActiveWeights();
        w[0] = 999.0;
        double[] w2 = engine.getActiveWeights();
        assertNotEquals(999.0, w2[0], 0.001);
    }

    @Test
    @DisplayName("score(f, weights): 显式传权重不影响 activeWeights")
    void scoreWithExplicitWeights() {
        CandidateFeatures f = CandidateFeatures.builder()
            .candidateId(1L).sku("X").category("C").platform("amazon")
            .monthlySearches(10000L).last30dSales(500L)
            .bsrRank(2000).reviewCount(500).avgRating(4.0)
            .grossMargin(0.3).trendScore(0.3).seasonality(0.3).competitionIntensity(0.5)
            .costPrice(10.0).suggestPrice(25.0)
            .build();

        double[] before = engine.getActiveWeights();
        engine.score(f, new double[]{0.10, 0.10, 0.10, 0.60, 0.10});
        double[] after = engine.getActiveWeights();

        // activeWeights 应不变
        assertArrayEquals(before, after, 0.001);
    }
}