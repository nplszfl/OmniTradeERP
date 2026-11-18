package com.crossborder.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 定价建议生成结果
 *
 * <p>📌 <b>为什么要单独一个结果对象</b>：让「AI 有没有真的参与」<b>可被调用方看见</b>
 * —— {@link #aiGenerated} 为 false 时，{@link #reason} 里一定写明了「AI 未启用/调用失败」，
 * 绝不让人误以为那是模型写的（规则 18 / 19 / 86）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestionResult {

    /** 商品 ID */
    private Long productId;

    /** 建议 ID（落库后回填） */
    private Long suggestionId;

    /** 当前售价 */
    private java.math.BigDecimal currentPrice;

    /** 建议售价 */
    private java.math.BigDecimal suggestedPrice;

    /** 竞品均价 */
    private java.math.BigDecimal competitorAvg;

    /** 预期利润 */
    private java.math.BigDecimal expectedProfit;

    /** 置信度（0-100，由数据完整度算，不是写死的） */
    private java.math.BigDecimal confidenceLevel;

    /** 建议原因（AI 生成，或规则模板 + 明确标注） */
    private String reason;

    /** 建议原因是否由 LLM 生成 */
    private Boolean aiGenerated;

    /** AI 未参与的原因（aiGenerated=false 时有值） */
    private String aiSkipReason;

    /** 真实 token 用量（API 返回的真值；拿不到 = null，不估算） */
    private com.crossborder.erp.llm.LLMTokenUsage usage;
}
