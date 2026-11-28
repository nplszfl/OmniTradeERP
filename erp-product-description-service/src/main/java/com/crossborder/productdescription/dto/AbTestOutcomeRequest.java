package com.crossborder.productdescription.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回填 A/B 测试结果的请求
 *
 * <p>📌 <b>「获胜版本」不在请求里</b>：它<b>由两个实测值算出来</b>
 * （A &gt; B → A；A &lt; B → B；相等 → TIE）。
 * 否则运营可能把「B 赢」写成「A 赢」→ 表里的结论和原始指标<b>对不上</b>，
 * 而表上又有 {@code winner} 和 {@code confidence} 两个索引 ——
 * <b>按胜者做的报表直接就是错的</b>。</p>
 */
@Data
public class AbTestOutcomeRequest {

    /** 版本A的指标实测值 */
    @NotNull(message = "版本A的指标值不能为空")
    private BigDecimal metricAValue;

    /** 版本B的指标实测值 */
    @NotNull(message = "版本B的指标值不能为空")
    private BigDecimal metricBValue;

    /** 置信度（0-1）；不知道就留空（不编） */
    private BigDecimal confidence;

    /** 测试结束时间；不传就是「现在」 */
    private LocalDateTime endTime;
}
