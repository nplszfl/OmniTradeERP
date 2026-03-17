package com.crossborder.aiassistant.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * AI对话请求DTO
 */
@Data
public class ChatRequest {

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 会话ID（空表示新会话） */
    private String sessionId;

    /** 用户消息 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 语言（zh-中文，en-英文，auto-自动检测） */
    private String language = "auto";

    /** 是否使用RAG（检索增强生成） */
    private Boolean useRAG = true;

    /** 是否返回思考过程 */
    private Boolean showThinking = false;

    /** 流式响应 */
    private Boolean stream = false;

    /** 上下文信息 */
    private Map<String, Object> context;

    /** 模型选择（gpt-4、deepseek-chat等） */
    private String model;

    /** 温度参数（0.0-2.0） */
    private Double temperature = 0.7;

    /** 最大Token数 */
    private Integer maxTokens = 2000;
}
