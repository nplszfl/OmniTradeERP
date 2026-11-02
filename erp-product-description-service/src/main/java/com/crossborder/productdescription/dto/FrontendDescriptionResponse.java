package com.crossborder.productdescription.dto;

import lombok.Data;

import java.util.List;

/**
 * 前端友好的描述生成响应
 *
 * <p>前端 erp-web/src/views/ai/Description.vue 直接读
 * {@code result.title} / {@code result.subtitle} / {@code result.body} /
 * {@code result.bulletPoints} / {@code result.seoKeywords}。</p>
 *
 * <p>⚠️ 之前前端拿到的是后端的 {@code DescriptionGenerationResponse}
 * （content 是纯文本、SEO 在 seoResult 里、卖点叫 highlights），
 * 于是前端只能 {@code JSON.parse(r.description)} 然后失败回退到 mock。</p>
 */
@Data
public class FrontendDescriptionResponse {

    /** 主标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 正文（支持换行） */
    private String body;

    /** 核心卖点 */
    private List<String> bulletPoints;

    /** SEO 关键词 */
    private List<String> seoKeywords;

    /** 使用的模板 */
    private String templateId;

    /** 生成耗时（毫秒） */
    private Long generationTime;

    /** 出错信息（生成失败时非空） */
    private String error;
}
