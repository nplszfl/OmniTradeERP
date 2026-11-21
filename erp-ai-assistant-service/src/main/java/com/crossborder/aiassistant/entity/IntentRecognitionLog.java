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
 * 意图识别日志 —— 落库实体
 *
 * <p>对应表 {@code intent_recognition_log}（schema {@code erp_ai_assistant_db}）。
 *
 * <p>📌 **2026-11-20 新增**：这张表**早就建好了 DDL**，但
 * {@code AIAssistantServiceImpl#recognizeIntent} 只把结果返回给调用方，
 * <b>从不落库</b> → 这张表<b>永远是 0 行</b>。
 * 后果：
 * <ul>
 *   <li>「用户最常问什么」「意图分布」<b>没有数据源</b>；</li>
 *   <li>客服无法回溯「上周用户都在问什么」；</li>
 *   <li>表上建好的 {@code idx_recognized_intent} 索引<b>永远用不上</b>。</li>
 * </ul>
 *
 * @since 1.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "intent_recognition_log", indexes = {
        @Index(name = "idx_recognized_intent", columnList = "recognized_intent"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_session_id", columnList = "session_id")
})
public class IntentRecognitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID（可空） */
    @Column(name = "user_id")
    private Long userId;

    /** 会话ID */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 用户输入文本 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** 识别出的意图 */
    @Column(name = "recognized_intent", length = 50)
    private String recognizedIntent;

    /** 置信度（0~1） */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /** 提取到的实体（JSON 数组） */
    @Column(columnDefinition = "json")
    private String entities;

    /** 处理耗时（毫秒） */
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @CreationTimestamp
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
