package com.crossborder.aiassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 情感分析日志 —— 落库实体
 *
 * <p>对应表 {@code sentiment_analysis_log}（schema {@code erp_ai_assistant_db}）。
 *
 * <p>📌 **2026-11-20 新增**：这张表**早就建好了 DDL**，但
 * {@code AIAssistantServiceImpl#analyzeSentiment} 只把结果返回给调用方，
 * <b>从不落库</b> → 这张表<b>永远是 0 行</b>。
 * 后果：
 * <ul>
 *   <li>「用户情绪趋势」「差评预警」这类运营功能<b>没有数据源</b>；</li>
 *   <li>情感分析接口每次调用都<b>重新计算一遍</b> → 浪费 CPU，且无法回溯历史；</li>
 *   <li>表上建好的 {@code idx_sentiment} / {@code idx_user_id} 索引<b>永远用不上</b>。</li>
 * </ul>
 *
 * @since 1.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sentiment_analysis_log", indexes = {
        @Index(name = "idx_sentiment", columnList = "sentiment"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_session_id", columnList = "session_id")
})
public class SentimentAnalysisLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID（可空：未登录的公开咨询） */
    @Column(name = "user_id")
    private Long userId;

    /** 会话ID */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 被分析的文本 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** 情感：positive / negative / neutral */
    @Column(nullable = false, length = 20)
    private String sentiment;

    /** 置信度（0~1） */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /** 情感分数（-1 ~ 1） */
    @Column(precision = 5, scale = 4)
    private BigDecimal score;

    @CreationTimestamp
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
