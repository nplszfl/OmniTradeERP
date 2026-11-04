package com.crossborder.productdescription.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 产品描述生成响应DTO
 */
@Data
public class DescriptionGenerationResponse {

    /** 产品描述ID */
    private String descriptionId;

    /** 产品ID */
    private Long productId;

    /** 产品名称 */
    private String productName;

    /** 生成的描述 */
    private String content;

    /** 生成的短标题 */
    private String shortTitle;

    /** 目标语言 */
    private String language;

    /** 目标平台 */
    private String platform;

    /** 版本号（用于A/B测试） */
    private Integer versionNumber;

    /** SEO优化结果 */
    private SEOResult seoResult;

    /** 提炼的产品亮点 */
    List<Highlight> highlights;

    /** 使用的模型 */
    private String model;

    /**
     * 本次生成是否来自<b>模拟实现</b>
     *
     * <p>📌 <b>为什么必须有</b>（2026-11-04 实测）：
     * 之前真实 LLM 调用失败 → catch → <b>静默</b>生成一段模板文本，
     * 而 {@code model} 仍然写着 {@code deepseek-chat} →
     * <b>调用方无法分辨真假</b>，且 token 用量也是按模板长度<b>编造</b>的。
     * <p>→ <b>事实标志不能撒谎</b>。
     */
    private Boolean mock = false;

    /**
     * 降级原因（{@link #mock} 为 true 时有值）
     *
     * <p>📌 例：「未配置 LLM API Key」「真实 API 调用失败：...」
     */
    private String mockReason;

    /** 使用的模板 */
    private String templateId;

    /** 生成时长（毫秒） */
    private Long generationTime;

    /** 消耗的Token数 */
    private TokenUsage usage;

    /** 错误信息 */
    private String error;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * SEO优化结果
     */
    @Data
    public static class SEOResult {
        /** 优化后的标题 */
        private String title;

        /** 优化后的描述 */
        private String description;

        /** 主要关键词 */
        private List<String> primaryKeywords;

        /** 长尾关键词 */
        private List<String> longTailKeywords;

        /** 关键词密度 */
        private Map<String, Double> keywordDensity;

        /** 可读性评分（0-100） */
        private Double readabilityScore;

        /** SEO评分（0-100） */
        private Double seoscore;

        /** 建议改进点 */
        private List<String> suggestions;
    }

    /**
     * 产品亮点
     */
    @Data
    public static class Highlight {
        /** 亮点ID */
        private String id;

        /** 亮点标题 */
        private String title;

        /** 亮点描述 */
        private String description;

        /** 重要性（1-5） */
        private Integer importance;

        /** 对应的产品特性 */
        private String feature;
    }

    /**
     * Token使用情况
     */
    @Data
    public static class TokenUsage {
        /** 提示Token数 */
        private Integer promptTokens;

        /** 完成Token数 */
        private Integer completionTokens;

        /** 总Token数 */
        private Integer totalTokens;
    }
}
