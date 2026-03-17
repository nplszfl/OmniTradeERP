package com.crossborder.inventoryprediction.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 预测请求DTO
 */
@Data
public class PredictionRequest {

    /** 产品ID */
    @NotNull(message = "产品ID不能为空")
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 预测天数 */
    @Min(value = 1, message = "预测天数必须大于0")
    private int predictionDays = 30;

    /** 预测开始日期 */
    private LocalDate startDate;

    /** 是否使用季节性模型 */
    private Boolean useSeasonalModel = true;

    /** 是否使用趋势模型 */
    private Boolean useTrendModel = true;

    /** 置信度水平（0.9表示90%置信区间） */
    private Double confidenceLevel = 0.9;
}
