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
