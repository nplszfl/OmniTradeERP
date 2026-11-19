package com.crossborder.inventoryprediction.repository;

import com.crossborder.inventoryprediction.entity.PredictionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 预测任务 Repository（任务审计表）
 *
 * <p>📌 **2026-11-19 新增**（配合补写路径）。
 * 📌 「跑没跑过、成没成功」的事实来源是**这张表**，不是日志（规则 101：job success ≠ 事情做成了）。
 */
@Repository
public interface PredictionTaskRepository extends JpaRepository<PredictionTask, Long> {

    /** 最近 N 次任务（按创建时间倒序） */
    List<PredictionTask> findTop50ByOrderByCreateTimeDesc();

    /** 某类型的最近 N 次任务 */
    List<PredictionTask> findTop20ByTaskTypeOrderByCreateTimeDesc(String taskType);
}
