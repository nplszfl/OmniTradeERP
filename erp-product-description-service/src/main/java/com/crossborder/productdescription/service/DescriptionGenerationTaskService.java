package com.crossborder.productdescription.service;

import com.crossborder.productdescription.dto.DescriptionGenerationResponse;

import java.util.List;

/**
 * 描述生成任务审计服务
 *
 * <p>📌 **2026-11-19 新增**：把「描述生成任务」的真实生命周期落库
 * （表 {@code description_generation_task}）——
 * 之前这张表**有 DDL、没代码**，永远是 0 行 → 「跑没跑 / 成没成 / 成几条 / 失败在哪」
 * **没有事实来源**（规则 18：事实标志不能撒谎）。</p>
 */
public interface DescriptionGenerationTaskService {

    /** 任务类型：单条 */
    String TASK_TYPE_SINGLE = "SINGLE";
    /** 任务类型：批量 */
    String TASK_TYPE_BATCH = "BATCH";
    /** 任务类型：多语言 */
    String TASK_TYPE_MULTI_LANGUAGE = "MULTI_LANGUAGE";
    /** 任务类型：A/B 测试 */
    String TASK_TYPE_AB_TEST = "AB_TEST";

    /**
     * 记录一个任务的**开始**（写一行 {@code status=RUNNING}）
     *
     * @param taskType       任务类型
     * @param productId      产品ID（批量可为 null）
     * @param targetLanguage 目标语言
     * @param targetPlatform 目标平台
     * @param totalCount     本次计划生成的条数
     * @return 任务行ID（收尾时用它回填结果）
     */
    Long startTask(String taskType, Long productId, String targetLanguage,
                   String targetPlatform, int totalCount);

    /**
     * 记录任务的**成功结束**
     *
     * @param taskId      任务行ID
     * @param successCount 成功条数
     * @param failedCount  失败条数
     * @param durationMs   耗时（毫秒）
     */
    void completeTask(Long taskId, int successCount, int failedCount, long durationMs);

    /**
     * 记录任务的**失败结束**
     *
     * @param taskId       任务行ID
     * @param successCount 失败前已成功的条数
     * @param failedCount  失败条数
     * @param durationMs   耗时（毫秒）
     * @param errorMessage 错误信息（**如实写**，不吞）
     */
    void failTask(Long taskId, int successCount, int failedCount, long durationMs, String errorMessage);

    /**
     * 按「响应内容」统计成功 / 失败条数。
     *
     * <p>📌 **判据**：响应带 {@code error} 字段的算失败 —— 因为生成失败时
     * 服务会把 {@code content} 置成「生成失败，请稍后重试。」并把异常写进 {@code error}。
     * 换句话说：**只看「返回了内容」会把失败算成成功**（规则 54：全称型结论要看全）。</p>
     */
    int countSuccess(List<DescriptionGenerationResponse> responses);

    /**
     * 查询某个产品的最近任务（按创建时间倒序）
     */
    List<com.crossborder.productdescription.entity.DescriptionGenerationTask> listRecentByProduct(Long productId, int limit);
}
