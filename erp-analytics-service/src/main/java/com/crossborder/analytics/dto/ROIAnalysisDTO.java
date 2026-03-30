package com.crossborder.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ROI分析DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ROIAnalysisDTO {

    /**
     * 分析维度: PRODUCT, PLATFORM, CHANNEL, CAMPAIGN
     */
    private String dimension;

    /**
     * 分析起始日期
     */
    private LocalDate startDate;

    /**
     * 分析结束日期
     */
    private LocalDate endDate;

    /**
     * ROI汇总
     */
    private ROISummary summary;

    /**
     * 明细数据
     */
    private List<ROIDetail> details;

    /**
     * 趋势数据
     */
    List<ROITrend> trends;

    /**
     * 最佳 performer
     */
    private List<TopPerformer> topPerformers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ROISummary {
        private BigDecimal totalRevenue;
        private BigDecimal totalCost;
        private BigDecimal totalProfit;
        private BigDecimal overallROI;
        private BigDecimal roas; // Return on Ad Spend
        private Integer totalOrders;
        private BigDecimal aov; // Average Order Value
        private BigDecimal conversionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ROIDetail {
        private String id; // productId, platform, channel, campaign
        private String name;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private BigDecimal roi;
        private BigDecimal roas;
        private Integer orders;
        private BigDecimal aov;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ROITrend {
        private String period;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal roi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopPerformer {
        private String id;
        private String name;
        private BigDecimal roi;
        private BigDecimal revenue;
        private String type;
    }
}