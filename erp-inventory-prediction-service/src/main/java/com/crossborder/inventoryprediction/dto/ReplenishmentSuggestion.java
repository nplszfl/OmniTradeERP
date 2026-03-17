package com.crossborder.inventoryprediction.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 补货建议DTO
 */
@Data
public class ReplenishmentSuggestion {

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 当前库存 */
    private Integer currentStock;

    /** 安全库存 */
    private Integer safetyStock;

    /** 预测期间总需求 */
    private Long predictedDemand;

    /** 预测结束日期 */
    private LocalDate predictionEndDate;

    /** 建议补货数量 */
    private Integer suggestedQuantity;

    /** 建议补货日期 */
    private LocalDate suggestedDate;

    /** 紧急程度（HIGH, MEDIUM, LOW） */
    private String urgency;

    /** 建议原因 */
    private String reason;

    /** 是否需要补货 */
    private Boolean needReplenishment;

    /** 预计到货日期 */
    private LocalDate expectedArrivalDate;

    /** 预计补货成本 */
    private java.math.BigDecimal estimatedCost;

    /** 预测准确度 */
    private Double predictionAccuracy;

    /** 风险评估 */
    private RiskAssessment riskAssessment;

    /**
     * 风险评估
     */
    @Data
    public static class RiskAssessment {
        /** 缺货风险（0-1） */
        private Double stockoutRisk;

        /** 过期风险（0-1） */
        private Double overstockRisk;

        /** 总体风险等级（HIGH, MEDIUM, LOW） */
        private String riskLevel;

        /** 风险描述 */
        private String riskDescription;
    }
}
