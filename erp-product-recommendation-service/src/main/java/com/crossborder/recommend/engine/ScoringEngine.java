package com.crossborder.recommend.engine;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.recommend.dto.CandidateFeatures;
import com.crossborder.recommend.dto.RecommendationScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 选品评分引擎 - 5 维度加权算法
 *
 *   demandScore       权重 0.30 - 需求强度 (搜索量 + 销量)
 *   trendScore        权重 0.20 - 趋势动能
 *   profitScore       权重 0.20 - 利润空间 (毛利率)
 *   competitionScore  权重 0.15 - 竞争可行性 (高分=蓝海)
 *   qualityScore      权重 0.15 - 商品质量 (评论数 + 星级)
 *
 *   最终 rawScore 经 clamp01 + scale(0~100) 映射到百分制
 */
@Slf4j
@Component
public class ScoringEngine {

    public static final double W_DEMAND = 0.30;
    public static final double W_TREND = 0.20;
    public static final double W_PROFIT = 0.20;
    public static final double W_COMPETITION = 0.15;
    public static final double W_QUALITY = 0.15;

    /**
     * 当前生效权重（可由 WeightTuner 动态调整）
     * 默认值 = 上面 5 个常量
     */
    private volatile double[] activeWeights = new double[]{
        W_DEMAND, W_TREND, W_PROFIT, W_COMPETITION, W_QUALITY
    };

    /**
     * 评分主入口 - 使用当前权重
     */
    public RecommendationScore score(CandidateFeatures f) {
        return score(f, activeWeights);
    }

    /**
     * 评分主入口 - 显式传入权重（用于回滚/A/B 测试）
     */
    public RecommendationScore score(CandidateFeatures f, double[] weights) {
        validateWeights(weights);
        double demand = calcDemandScore(f);
        double trend = calcTrendScore(f);
        double profit = calcProfitScore(f);
        double competition = calcCompetitionScore(f);
        double quality = calcQualityScore(f);

        double rawScore = demand * weights[0]
            + trend * weights[1]
            + profit * weights[2]
            + competition * weights[3]
            + quality * weights[4];

        // 映射 0~1 → 0~100
        double finalScore = clamp01(rawScore) * 100.0;

        // 推荐等级
        String level = determineLevel(finalScore);

        // 风险分数 (高竞争 + 低利润率 + 低需求 → 高风险)
        double risk = calcRiskScore(f, demand, profit, competition);

        // 预期月销量/利润（粗略估计）
        int expectedMonthlySales = estimateMonthlySales(f);
        double expectedMonthlyProfit = estimateMonthlyProfit(f, expectedMonthlySales);

        // 规则命中 & 关键优势/风险
        List<String> triggered = collectTriggeredRules(f, level, finalScore, risk);
        List<String> strengths = collectStrengths(f, demand, trend, profit, competition, quality);
        List<String> risks = collectRisks(f, demand, profit, competition, quality, risk);

        RecommendationScore.DimensionBreakdown breakdown = RecommendationScore.DimensionBreakdown.builder()
            .demandScore(demand * 100)
            .trendScore(trend * 100)
            .profitScore(profit * 100)
            .competitionScore(competition * 100)
            .qualityScore(quality * 100)
            .build();

        return RecommendationScore.builder()
            .candidateId(f.getCandidateId())
            .sku(f.getSku())
            .score(round2(finalScore))
            .recommendLevel(level)
            .expectedMonthlySales(expectedMonthlySales)
            .expectedMonthlyProfit(round2(expectedMonthlyProfit))
            .riskScore(round2(clamp01(risk) * 100))
            .triggeredRules(triggered)
            .keyStrengths(strengths)
            .keyRisks(risks)
            .breakdown(breakdown)
            .build();
    }

    /**
     * 需求强度 = 搜索量 + 销量 归一化
     */
    double calcDemandScore(CandidateFeatures f) {
        double searchN = normalize(f.getMonthlySearches(), 0, 100000);
        double salesN = normalize(f.getLast30dSales(), 0, 5000);
        return clamp01(0.5 * searchN + 0.5 * salesN);
    }

    /**
     * 趋势动能 = trendScore * 季节性加权
     * trendScore ∈ [-1, 1]  → 映射到 [0, 1]
     * 季节性高 = 趋势加成
     */
    double calcTrendScore(CandidateFeatures f) {
        double trend = f.getTrendScore() == null ? 0.0 : f.getTrendScore();
        double trendN = clamp01((trend + 1) / 2);  // -1..1 → 0..1
        double seasonality = f.getSeasonality() == null ? 0.5 : f.getSeasonality();
        // 季节性因子作为放大器: 在高季节性 (>=0.7) 阶段且 trend>0 时加成
        double amp = (trend > 0 && seasonality >= 0.7) ? 1.15 : 1.0;
        return clamp01(trendN * amp);
    }

    /**
     * 利润空间 = 毛利率直接归一化
     */
    double calcProfitScore(CandidateFeatures f) {
        double gm = f.getGrossMargin() == null ? 0.0 : f.getGrossMargin();
        // 毛利 0~0.6 线性映射 0~1
        return clamp01(gm / 0.6);
    }

    /**
     * 竞争可行性 = 1 - 竞争强度 + BSR 奖励
     * BSR 排名 1~100000 反向归一化 (低排名=高分)
     */
    double calcCompetitionScore(CandidateFeatures f) {
        double intensity = f.getCompetitionIntensity() == null ? 0.5 : f.getCompetitionIntensity();
        double bsrN = 1.0 - normalize(f.getBsrRank(), 1, 100000);
        double baseScore = (1.0 - clamp01(intensity)) * 0.7 + bsrN * 0.3;
        return clamp01(baseScore);
    }

    /**
     * 商品质量 = 评论数（社交证明）+ 星级
     */
    double calcQualityScore(CandidateFeatures f) {
        double reviewN = normalize(f.getReviewCount(), 0, 5000);
        double rating = f.getAvgRating() == null ? 0.0 : f.getAvgRating();
        double ratingN = clamp01(rating / 5.0);
        return clamp01(0.4 * reviewN + 0.6 * ratingN);
    }

    /**
     * 风险分数 0~1
     * 高竞争 + 低利润 + 低需求 → 高风险
     */
    double calcRiskScore(CandidateFeatures f, double demand, double profit, double competition) {
        double intensity = f.getCompetitionIntensity() == null ? 0.5 : f.getCompetitionIntensity();
        double demandRisk = 1.0 - demand;
        double profitRisk = 1.0 - profit;
        double compRisk = clamp01(intensity);
        return clamp01(0.35 * demandRisk + 0.35 * profitRisk + 0.30 * compRisk);
    }

    /**
     * 推荐等级划分
     *   STRONG_BUY ≥ 75
     *   BUY         60~75
     *   HOLD        40~60
     *   SKIP        < 40
     */
    String determineLevel(double finalScore) {
        if (finalScore >= 75) return "STRONG_BUY";
        if (finalScore >= 60) return "BUY";
        if (finalScore >= 40) return "HOLD";
        return "SKIP";
    }

    /**
     * 预期月销量（基于历史 30 天销量线性外推，乘以趋势系数）
     */
    int estimateMonthlySales(CandidateFeatures f) {
        long last30 = f.getLast30dSales() == null ? 0 : f.getLast30dSales();
        double trend = f.getTrendScore() == null ? 0 : f.getTrendScore();
        double trendFactor = 1.0 + trend * 0.3;  // trend=1 → +30%
        double seasonality = f.getSeasonality() == null ? 0.5 : f.getSeasonality();
        double seasonalFactor = 0.5 + seasonality;  // 0.5 ~ 1.5
        return (int) Math.round(last30 * trendFactor * seasonalFactor);
    }

    /**
     * 预期月利润 = 预期月销量 * (售价 - 成本) * 毛利率
     */
    double estimateMonthlyProfit(CandidateFeatures f, int monthlySales) {
        double price = f.getSuggestPrice() == null ? 0 : f.getSuggestPrice();
        double cost = f.getCostPrice() == null ? 0 : f.getCostPrice();
        double gm = f.getGrossMargin() == null ? 0 : f.getGrossMargin();
        double unitProfit = Math.max(0, price - cost) * gm;
        return monthlySales * unitProfit;
    }

    List<String> collectTriggeredRules(CandidateFeatures f, String level, double finalScore, double risk) {
        List<String> rules = new ArrayList<>();
        if ("STRONG_BUY".equals(level)) rules.add("STRONG_BUY_THRESHOLD");
        if ("BUY".equals(level)) rules.add("BUY_THRESHOLD");
        if (risk >= 70) rules.add("HIGH_RISK");
        if (f.getMonthlySearches() != null && f.getMonthlySearches() >= 50000) rules.add("HIGH_SEARCH_VOLUME");
        if (f.getTrendScore() != null && f.getTrendScore() >= 0.5) rules.add("STRONG_UPTREND");
        if (f.getSeasonality() != null && f.getSeasonality() >= 0.8) rules.add("PEAK_SEASON");
        if (f.getGrossMargin() != null && f.getGrossMargin() >= 0.4) rules.add("HEALTHY_MARGIN");
        if (f.getCompetitionIntensity() != null && f.getCompetitionIntensity() <= 0.3) rules.add("BLUE_OCEAN");
        if (f.getReviewCount() != null && f.getReviewCount() >= 1000 && f.getAvgRating() != null && f.getAvgRating() >= 4.3)
            rules.add("PROVEN_QUALITY");
        return rules;
    }

    List<String> collectStrengths(CandidateFeatures f, double demand, double trend, double profit,
                                   double competition, double quality) {
        List<String> s = new ArrayList<>();
        if (demand >= 0.7) s.add("高需求：搜索量和销量都处于高位");
        if (trend >= 0.7) s.add("上升趋势：trend 强劲，预期未来继续增长");
        if (profit >= 0.7) s.add("健康利润：毛利率充足，单品贡献利润可观");
        if (competition >= 0.7) s.add("蓝海机会：竞争强度低，BSR 排名靠前");
        if (quality >= 0.7) s.add("品质验证：评论基数大且星级高");
        return s;
    }

    List<String> collectRisks(CandidateFeatures f, double demand, double profit,
                                double competition, double quality, double risk) {
        List<String> r = new ArrayList<>();
        if (demand < 0.3) r.add("需求疲软：搜索量/销量不足");
        if (profit < 0.3) r.add("利润薄：毛利率低，需谨慎评估");
        if (competition < 0.3) r.add("竞争激烈：红海市场，进入壁垒高");
        if (quality < 0.3) r.add("质量未验证：评论少或星级偏低");
        if (risk >= 70) r.add("高综合风险");
        return r;
    }

    /**
     * 工具方法：归一化 [0, rangeMax]
     */
    static double normalize(Number val, double min, double max) {
        if (val == null) return 0.0;
        double v = val.doubleValue();
        if (max <= min) return 0.0;
        return clamp01((v - min) / (max - min));
    }

    static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 动态设置生效权重（由 WeightTuner 调用）
     */
    public void setActiveWeights(double[] weights) {
        validateWeights(weights);
        this.activeWeights = weights.clone();
        log.info("ScoringEngine 权重已更新: demand={} trend={} profit={} competition={} quality={}",
            weights[0], weights[1], weights[2], weights[3], weights[4]);
    }

    /**
     * 获取当前生效权重副本
     */
    public double[] getActiveWeights() {
        return activeWeights.clone();
    }

    /**
     * 重置为默认权重
     */
    public void resetWeights() {
        this.activeWeights = new double[]{W_DEMAND, W_TREND, W_PROFIT, W_COMPETITION, W_QUALITY};
        log.info("ScoringEngine 权重已重置为默认值");
    }

    static void validateWeights(double[] w) {
        if (w == null || w.length != 5) {
            throw new BizException("权重数组必须是长度 5");
        }
        double sum = 0;
        for (double v : w) {
            if (Double.isNaN(v) || v < 0 || v > 1) {
                throw new BizException("权重值必须在 [0,1] 范围内");
            }
            sum += v;
        }
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new BizException("权重总和必须接近 1.0 (实际=" + sum + ")");
        }
    }
}