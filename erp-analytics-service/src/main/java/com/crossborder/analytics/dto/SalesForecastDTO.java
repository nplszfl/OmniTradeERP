package com.crossborder.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售预测DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesForecastDTO {

    /**
     * 产品ID
     */
    private String productId;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 预测开始日期
     */
    private LocalDate startDate;

    /**
     * 预测结束日期
     */
    private LocalDate endDate;

    /**
     * 预测结果列表
     */
    private List<ForecastData> forecasts;

    /**
     * 历史销售数据
     */
    private List<HistoricalData> historicalData;

    /**
     * 预测摘要
     */
    private ForecastSummary summary;

    /**
     * 预测模型类型
     */
    private String modelType;

    /**
     * 预测准确度指标
     */
    private AccuracyMetrics accuracyMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastData {
        private LocalDate date;
        private BigDecimal predictedSales;
        private BigDecimal predictedRevenue;
        private Integer predictedQuantity;
        private BigDecimal confidenceLower;
        private BigDecimal confidenceUpper;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalData {
        private LocalDate date;
        private BigDecimal actualSales;
        private Integer actualQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastSummary {
        private BigDecimal totalPredictedSales;
        private BigDecimal totalPredictedRevenue;
        private Integer totalPredictedQuantity;
        private BigDecimal avgDailySales;
        private BigDecimal growthRate; // 相比上期
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccuracyMetrics {
        private BigDecimal mape; // Mean Absolute Percentage Error
        private BigDecimal mae;  // Mean Absolute Error
        private BigDecimal rmse; // Root Mean Square Error
        private BigDecimal accuracy; // 准确率
        private String grade; // 评估等级
    }
}