package com.crossborder.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定价建议视图（对齐前端）
 *
 * <p>前端 erp-web/src/api/ai.ts 的 {@code PriceSuggest} 接口 + Pricing.vue 的表格列：
 * sku / productName / strategy / currentPrice / suggestedPrice / competitorAvg /
 * expectedProfit / applied。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSuggestionVO {

    /** 建议ID */
    private Long id;

    /** 产品ID */
    private Long productId;

    /** SKU */
    private String sku;

    /** 商品名 */
    private String productName;

    /** 当前售价 */
    private BigDecimal currentPrice;

    /** 建议售价 */
    private BigDecimal suggestedPrice;

    /** 竞品均价 */
    private BigDecimal competitorAvg;

    /** 预期利润（建议价 - 成本） */
    private BigDecimal expectedProfit;

    /** 定价策略 */
    private String strategy;

    /** 建议原因 */
    private String reason;

    /** 置信度（0-100） */
    private BigDecimal confidenceLevel;

    /** 是否已采纳 */
    private Boolean applied;

    /** 生成时间 */
    private LocalDateTime createTime;
}
