package com.crossborder.inventoryprediction.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 预测响应DTO
 */
@Data
public class PredictionResponse {

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 预测开始日期 */
    private LocalDate startDate;

    /** 预测结束日期 */
    private LocalDate endDate;

    /** 预测天数 */
    private int predictionDays;

    /** 总预测销量 */
    private Long totalPredictedSales;

    /** 平均日销量 */
    private Double avgDailySales;

    /** 峰值日销量 */
    private Long peakDailySales;

    /** 峰值日期 */
    private LocalDate peakDate;

    /** 谷值日销量 */
    private Long troughDailySales;

    /** 谷值日期 */
    private LocalDate troughDate;

    /** 每日预测明细 */
    private List<DailyPrediction> dailyPredictions;

    /** 置信区间下限 */
    private Long lowerBound;

    /** 置信区间上限 */
    private Long upperBound;

    /** 预测准确度（0-1） */
    private Double accuracy;

    /** 模型类型 */
    private String modelType;

    /** 预测时间 */
    private java.time.LocalDateTime predictionTime;

    /**
     * 每日预测明细
     */
    @Data
    public static class DailyPrediction {
        /** 日期 */
        private LocalDate date;

        /** 预测销量 */
        private Long predictedSales;

        /** 下限 */
        private Long lowerBound;

        /** 上限 */
        private Long upperBound;

        /** 置信度 */
        private Double confidence;
    }
}
