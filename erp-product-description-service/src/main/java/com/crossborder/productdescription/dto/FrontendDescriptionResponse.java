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

    /**
     * 本次生成是否来自<b>模拟实现</b>
     *
     * <p>📌 透传自 {@code DescriptionGenerationResponse.mock} ——
     * 前端据此提示用户「这是模板回复，不是真 AI 生成的」。
     * <p>📌 之前没有这个字段 → 真实 LLM 挂了之后，页面上看起来和真的一模一样。
     */
    private Boolean mock = false;

    /**
     * 降级原因（{@link #mock} 为 true 时有值）
     */
    private String mockReason;
}
