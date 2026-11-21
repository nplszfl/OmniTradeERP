package com.crossborder.aiassistant.repository;

import com.crossborder.aiassistant.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户反馈仓储
 *
 * <p>📌 对应表 {@code user_feedback}。
 *    2026-11-20 之前这张表<b>有 DDL 没代码</b> → 永远 0 行。
 */
@Repository
public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {

    /** 按会话查反馈 */
    List<UserFeedback> findBySessionIdOrderByCreateTimeDesc(String sessionId);

    /** 按用户查最近 N 条 */
    List<UserFeedback> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 统计某类型反馈数量（quality 看板用） */
    long countByFeedbackType(String feedbackType);

    /** 「有帮助」反馈数 —— 用于累加 knowledge_base.helpful_count */
    long countByMessageIdAndFeedbackType(Long messageId, String feedbackType);

    /** 分页查全部反馈（管理界面用） */
    Page<UserFeedback> findAllByOrderByCreateTimeDesc(Pageable pageable);
}
