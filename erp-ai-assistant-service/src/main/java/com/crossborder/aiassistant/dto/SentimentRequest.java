package com.crossborder.aiassistant.dto;

import lombok.Data;

/**
 * 情感分析请求（带上下文）
 *
 * <p>📌 **2026-11-20 新增**：原先只有 {@code POST /sentiment}（裸字符串 body），
 * <b>没有 userId / sessionId</b> → 即使落库了也不知道是谁的对话。
 * 这个 DTO 把上下文补齐，让 {@code sentiment_analysis_log} 表真能用于运营分析。</p>
 *
 * @since 1.9.0
 */
@Data
public class SentimentRequest {

    /** 待分析文本（必填） */
    private String text;

    /** 用户ID（可空） */
    private Long userId;

    /** 会话ID（可空） */
    private String sessionId;
}
