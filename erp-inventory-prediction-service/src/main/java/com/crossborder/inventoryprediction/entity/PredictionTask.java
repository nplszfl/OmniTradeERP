package com.crossborder.inventoryprediction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 预测任务记录（对应 {@code erp_inventory_prediction_db.prediction_task} 表）
 *
 * <p>📌 **2026-11-19 新增**：这张表就是**任务审计表** ——
 * 每次跑预测都写一行（类型 / 状态 / 起止 / 耗时 / 结果摘要 / 错误信息）。
 *
 * <p>📌 **为什么要它**：没有它的话，「预测跑没跑过、成没成功」**只能看日志**，
 * 而日志会滚走、也会被删 → 无法回答「昨天那次预测为什么没有结果」，
 * 也无法区分「没跑」与「跑了但失败」（规则 89/101：
 * <b>job success ≠ 事情做成了</b>，这里同理 —— <b>task row 才是事实</b>）。</p>
 */
@Entity
@Table(name = "prediction_task")
public class PredictionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务类型（SALES_PREDICTION / REPLENISHMENT_SUGGESTION） */
    @Column(name = "task_type", nullable = false)
    private String taskType;

    /** 产品ID（批量任务为 null） */
    @Column(name = "product_id")
    private Long productId;

    /** 状态（PENDING / RUNNING / SUCCESS / FAILED） */
    @Column(name = "status")
    private String status = "PENDING";

    /** 开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 结果摘要 */
    @Column(name = "result_summary")
    private String resultSummary;

    /** 错误信息 */
    @Column(name = "error_message")
    private String errorMessage;

    /** 创建时间 */
    @Column(name = "create_time")
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
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
