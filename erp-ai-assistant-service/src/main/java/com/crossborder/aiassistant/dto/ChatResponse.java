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

    /**
     * 本次回复是否来自<b>模拟实现</b>
     *
     * <p>📌 <b>为什么必须有这个字段</b>（2026-11-04 实测）：
     * 之前真实 LLM 调用失败（404/超时/鉴权错）时，代码 catch 之后
     * <b>静默降级</b>成一段 canned 文本，而响应里 {@code model} 仍然写着
     * {@code deepseek-chat} → <b>调用方（前端/接口消费方）完全无法区分</b>
     * 「这是真 AI 回答」和「这是一段固定模板」。
     * <p>→ 结果是 <b>AI 功能上线后从来没真的调通过一次</b>，
     * 但界面看起来完全正常。
     * <p>📌 <b>事实标志不能撒谎</b> —— 降级必须<b>可被看见</b>。
     *
     * <p>📌 {@code true} 的两种情况：
     * <ul>
     *   <li>没配 API Key（{@code llm.enabled=false} 或 key 为空）→ 从来就没打算调真实 API</li>
     *   <li>配了但真实调用失败 → <b>降级</b>（此时看 {@link #mockReason}）</li>
     * </ul>
     */
    private Boolean mock = false;

    /**
     * 降级原因（{@link #mock} 为 true 时有值）
     *
     * <p>📌 例：「未配置 LLM API Key」「真实 API 调用失败：LLM API调用失败」
     * → 运维一眼能看出「AI 到底有没有真的工作」。
     */
    private String mockReason;

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
