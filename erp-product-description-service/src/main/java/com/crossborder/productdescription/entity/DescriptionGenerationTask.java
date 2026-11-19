package com.crossborder.productdescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 描述生成任务（表 {@code description_generation_task}）
 *
 * <p>📌 **2026-11-19 新增**：这张表**早就建好了**（DDL 里连 {@code total_count/success_count/
 * failed_count/duration_ms/error_message} 都设计齐了），但**整个代码库没有任何一行引用它**
 * → 表永远是 0 行 → 「这一批描述生成到底跑没跑、成没成、成几条、失败在哪」**没有事实来源**，
 * 页面只能靠前端自己编。
 * → 现在把**生成任务**的真实生命周期落库（规则 18：事实标志不能撒谎）。</p>
 *
 * <p>📌 **谁写这行**：
 * <ul>
 *   <li>{@code SINGLE} —— 单条生成（{@code POST /generate}、{@code /generate/internal}）；</li>
 *   <li>{@code BATCH} —— 批量生成（{@code POST /batch-generate}）；</li>
 *   <li>{@code MULTI_LANGUAGE} —— 多语言生成（{@code POST /multi-language}）；</li>
 *   <li>{@code AB_TEST} —— A/B 版本生成（{@code POST /ab-test}）。</li>
 * </ul>
 * <b>单条生成不再单独记一行</b>（否则批量会写 N+1 行，语义乱）——
 * 只在最外层入口记一条，{@code total_count/success_count/failed_count} 记真实条数。</p>
 */
@TableName("description_generation_task")
public class DescriptionGenerationTask {

    /** 任务ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型（SINGLE、BATCH、MULTI_LANGUAGE、AB_TEST） */
    private String taskType;

    /** 产品ID（NULL 表示批量） */
    private Long productId;

    /** 目标语言 */
    private String targetLanguage;

    /** 目标平台 */
    private String targetPlatform;

    /** 状态（PENDING、RUNNING、SUCCESS、FAILED） */
    private String status;

    /** 总数量 */
    private Integer totalCount;

    /** 成功数量 */
    private Integer successCount;

    /** 失败数量 */
    private Integer failedCount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public String getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
