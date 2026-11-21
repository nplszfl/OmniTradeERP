package com.crossborder.aiassistant.repository;

import com.crossborder.aiassistant.entity.IntentRecognitionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 意图识别日志仓储
 *
 * <p>📌 对应表 {@code intent_recognition_log}。
 *    2026-11-20 之前这张表<b>有 DDL 没代码</b> → 永远 0 行。
 */
@Repository
public interface IntentRecognitionLogRepository extends JpaRepository<IntentRecognitionLog, Long> {

    /** 按会话查 */
    List<IntentRecognitionLog> findBySessionIdOrderByCreateTimeAsc(String sessionId);

    /** 按用户查最近 N 条 */
    List<IntentRecognitionLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 统计某意图出现次数（运营看板用） */
    long countByRecognizedIntent(String recognizedIntent);

    /** 时间范围内的日志条数 */
    long countByCreateTimeGreaterThanEqual(LocalDateTime since);

    /** 按意图分页查（管理界面用） */
    Page<IntentRecognitionLog> findByRecognizedIntent(String recognizedIntent, Pageable pageable);
}
