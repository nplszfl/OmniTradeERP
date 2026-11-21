package com.crossborder.aiassistant.repository;

import com.crossborder.aiassistant.entity.SentimentAnalysisLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 情感分析日志仓储
 *
 * <p>📌 对应表 {@code sentiment_analysis_log}。
 *    2026-11-20 之前这张表<b>有 DDL 没代码</b> → 永远 0 行。
 */
@Repository
public interface SentimentAnalysisLogRepository extends JpaRepository<SentimentAnalysisLog, Long> {

    /** 按会话查（回溯一次对话里的所有情感分析） */
    List<SentimentAnalysisLog> findBySessionIdOrderByCreateTimeAsc(String sessionId);

    /** 按用户查最近 N 条 */
    List<SentimentAnalysisLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 统计某种情感的数量（运营看板用） */
    long countBySentiment(String sentiment);

    /** 时间范围内的日志条数 */
    long countByCreateTimeGreaterThanEqual(LocalDateTime since);

    /** 按情感分页查（管理界面用） */
    Page<SentimentAnalysisLog> findBySentiment(String sentiment, Pageable pageable);
}
