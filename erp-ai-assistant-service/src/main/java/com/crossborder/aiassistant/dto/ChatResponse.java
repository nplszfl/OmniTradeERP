package com.crossborder.aiassistant.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI对话响应DTO
 */
@Data
public class ChatResponse {

    /** 响应ID */
    private String responseId;

    /** 会话ID */
    private String sessionId;

    /** AI回复内容 */
    private String content;

    /** 思考过程（RAG检索信息） */
    private String thinking;

    /** 检索到的知识片段 */
    List<KnowledgeChunk> retrievedChunks;

    /** 语言 */
    private String language;

    /** 模型 */
    private String model;

    /** 消耗的Token数 */
    private TokenUsage usage;

    /** 响应时间（毫秒） */
    private Long responseTime;

    /** 流式响应标识 */
    private Boolean stream = false;

    /** 错误信息 */
    private String error;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 知识片段
     */
    @Data
    public static class KnowledgeChunk {
        /** 知识ID */
        private String chunkId;

        /** 知识内容 */
        private String content;

        /** 相似度 */
        private Double similarity;

        /** 知识来源 */
        private String source;

        /** 知识分类 */
        private String category;
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
