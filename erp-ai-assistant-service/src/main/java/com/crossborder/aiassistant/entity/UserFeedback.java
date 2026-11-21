package com.crossborder.aiassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户反馈 —— 落库实体
 *
 * <p>对应表 {@code user_feedback}（schema {@code erp_ai_assistant_db}）。
 *
 * <p>📌 **2026-11-20 新增**：这张表**早就建好了 DDL**，但全仓**没有任何一行代码**
 * 往里写 → 这张表<b>永远是 0 行</b>。
 * 后果：
 * <ul>
 *   <li>客服回答好不好，<b>系统自己不知道</b> → 无法评估 AI 质量；</li>
 *   <li>{@code knowledge_base.helpful_count} 这个字段<b>永远是 0</b>
 *       （它本该靠反馈累加）；</li>
 *   <li>「最需要改进的问答」这类优化闭环<b>无法启动</b>。</li>
 * </ul>
 *
 * @since 1.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_feedback", indexes = {
        @Index(name = "idx_feedback_type", columnList = "feedback_type"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_session_id", columnList = "session_id")
})
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 反馈用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 会话ID */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 被反馈的消息ID（可空：整场会话级反馈） */
    @Column(name = "message_id")
    private Long messageId;

    /** 反馈类型：helpful / not_helpful / incorrect */
    @Column(name = "feedback_type", nullable = false, length = 20)
    private String feedbackType;

    /** 评分（1~5） */
    private Integer rating;

    /** 评论 */
    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
