package com.crossborder.recommend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 选品推荐结果 VO（对齐前端 Recommend.vue 的字段）
 *
 * <p>前端表格直接读：{@code sku / productName / category / score / expectedMargin / reason}，
 * 后端 {@code RecommendResult} 里没有 productName、category、expectedMargin 三个字段，
 * 需要用 candidate 补齐。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class RecommendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String sku;

    /** 商品名（来自候选商品表） */
    private String productName;

    /** 类目（来自候选商品表） */
    private String category;

    /** 推荐得分 0-100 */
    private Double score;

    /** 推荐等级：STRONG_BUY / BUY / HOLD / SKIP */
    private String recommendLevel;

    /** 预期利润率（%） */
    private Double expectedMargin;

    /** 预期月利润 */
    private Double expectedMonthlyProfit;

    /** 风险分 */
    private Double riskScore;

    /** 推荐理由 */
    private String reason;

    /** 采纳状态 */
    private String adoptionStatus;

    private LocalDateTime createdAt;
}
