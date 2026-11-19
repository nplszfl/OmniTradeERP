package com.crossborder.productdescription.dto;

import lombok.Data;

import java.util.List;

/**
 * 前端友好的描述生成请求
 *
 * <p>前端 erp-web/src/api/ai.ts 的 {@code DescriptionRequest}：
 * {@code { sku, language, tone, keywords }}。</p>
 *
 * <p>⚠️ 与内部的 {@link DescriptionGenerationRequest} 刻意分开：
 * 内部 DTO 是按 productId / targetLanguage 设计的，且带 {@code @NotNull} 校验；
 * 前端传的是 sku + language，直接塞进内部 DTO 会被校验拦掉，
 * 而且 unknown 字段会被静默丢弃（tone 直接消失）。</p>
 */
@Data
public class FrontendDescriptionRequest {

    /** 商品 SKU */
    private String sku;

    /**
     * 商品 ID（2026-11-19 新增）。
     *
     * <p>📌 **实测坑**：前端 DTO 原来**只有 sku**，没有 productId → 我传
     * {@code productId: 1001} 时它被**静默丢弃**（Jackson 默认忽略未知字段）
     * → 后端拿不到商品 → LLM 只能吐 {@code [Product Name]} 占位符模板。
     * → 现在**两者都认**：productId 优先，sku 兼底（两种 SKU 口径都支持）。</p>
     */
    private Long productId;

    /** 目标语言：zh / en / ja / es */
    private String language = "zh";

    /** 风格语气：professional / casual / luxury / everyday */
    private String tone;

    /** 关键词 */
    private List<String> keywords;

    /** 目标平台 */
    private String targetPlatform = "general";

    /** 长度：short / medium / long */
    private String length = "medium";
}
