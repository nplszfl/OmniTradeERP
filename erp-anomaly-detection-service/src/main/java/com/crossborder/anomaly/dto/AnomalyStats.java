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

    /**
     * ⚠️ 以下三个字段是前端 Anomaly.vue 直接读的：
     *     {@code stats.total} / {@code stats.normal} / {@code stats.anomaly} / {@code stats.rate}
     * 前端是 {@code stats.value = await anomalyApi.stats()} 直接赋值，
     * 所以接口必须返<b>裸对象</b>，不能包 Result。
     */
    /** 正常订单数（未判定为异常） */
    private Long normal;

    /** 异常订单数 */
    private Long anomaly;

    /** 异常率（%），与 anomalyRate 同值，别名给前端用 */
    private Double rate;

    /**
     * ⚠️ normal / anomaly / rate 是<b>模型判定</b>结果（anomaly_flag），
     * 与 pending / confirmed / rejected 这套<b>人工复核状态</b>是两回事。
     * 前端 Anomaly.vue 的「正常订单 / 异常订单 / 异常率」用的是这三个。
     */
}
