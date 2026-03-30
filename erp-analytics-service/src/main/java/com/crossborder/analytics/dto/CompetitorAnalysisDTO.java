package com.crossborder.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 竞品分析DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAnalysisDTO {

    /**
     * 分析类型: PRICE, SALES, REVENUE, REVIEW, TREND
     */
    private String analysisType;

    /**
     * 目标产品ID
     */
    private String productId;

    /**
     * 目标产品名称
     */
    private String productName;

    /**
     * 竞品数量
     */
    private Integer competitorCount;

    /**
     * 竞品列表
     */
    private List<CompetitorInfo> competitors;

    /**
     * 价格对比
     */
    private PriceComparison priceComparison;

    /**
     * 销售对比
     */
    private SalesComparison salesComparison;

    /**
     * 市场占有率
     */
    private MarketShare marketShare;

    /**
     * 趋势分析
     */
    private List<TrendData> trends;

    /**
     * 建议
     */
    private List<String> recommendations;

    /**
     * 更新时间
     */
    private LocalDateTime lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorInfo {
        private String competitorId;
        private String competitorName;
        private String platform;
        private BigDecimal price;
        private Integer salesCount;
        private BigDecimal revenue;
        private BigDecimal rating;
        private Integer reviewCount;
        private String url;
        private BigDecimal priceDiff; // 与目标产品的价格差
        private String pricePosition; // HIGHER, LOWER, EQUAL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceComparison {
        private BigDecimal ourPrice;
        private BigDecimal avgCompetitorPrice;
        private BigDecimal lowestCompetitorPrice;
        private BigDecimal highestCompetitorPrice;
        private String pricePosition; // COMPETITIVE, PREMIUM, BUDGET
        private BigDecimal priceDiffPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesComparison {
        private Integer ourSalesCount;
        private Integer avgCompetitorSales;
        private Integer topCompetitorSales;
        private String salesPosition;
        private BigDecimal marketSharePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketShare {
        private BigDecimal ourShare;
        private List<CompetitorShare> competitorShares;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorShare {
        private String competitorName;
        private BigDecimal sharePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private String period;
        private String ourTrend;
        private List<CompetitorTrend> competitorTrends;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorTrend {
        private String competitorName;
        private String trend; // UP, DOWN, STABLE
        private BigDecimal changePercent;
    }
}