package com.crossborder.anomaly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 异常检测统计
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 检测总数 */
    private Long total;

    /** 待复核 */
    private Long pending;

    /** 已确认异常 */
    private Long confirmed;

    /** 已驳回（判定正常） */
    private Long rejected;

    /** 高危订单数（HIGH + CRITICAL） */
    private Long highRisk;

    /** 平均风险分 */
    private Double avgRiskScore;

    /** 异常率（%） */
    private Double anomalyRate;
}
