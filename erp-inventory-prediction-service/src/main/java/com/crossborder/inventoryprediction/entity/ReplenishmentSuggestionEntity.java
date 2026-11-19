package com.crossborder.inventoryprediction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补货建议（对应 {@code erp_inventory_prediction_db.replenishment_suggestion} 表）
 *
 * <p>📌 **2026-11-19 新增**：与 {@link SalesPrediction} 同理 ——
 * 这张表有完整 DDL、有 {@code status/urgency/approved_by} 审批字段，
 * 但**没有实体、没有写路径** → 补货建议**从来没被生成过、也从来没被审批过**。
 *
 * <p>📌 **类名带 {@code Entity} 后缀**：模块里已有一个 DTO 叫
 * {@code com.crossborder.inventoryprediction.dto.ReplenishmentSuggestion}（同名会冲突）。</p>
 */
@Entity
@Table(name = "replenishment_suggestion")
public class ReplenishmentSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 产品ID */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 当前库存 */
    @Column(name = "current_stock", nullable = false)
    private Integer currentStock = 0;

    /** 安全库存 */
    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock = 0;

    /** 预测需求 */
    @Column(name = "predicted_demand", nullable = false)
    private Long predictedDemand = 0L;

    /** 建议补货数量 */
    @Column(name = "suggested_quantity", nullable = false)
    private Integer suggestedQuantity = 0;

    /** 紧急程度（HIGH / MEDIUM / LOW） */
    @Column(name = "urgency", nullable = false)
    private String urgency = "LOW";

    /** 建议原因 */
    @Column(name = "reason")
    private String reason;

    /** 状态（PENDING / APPROVED / REJECTED / COMPLETED） */
    @Column(name = "status")
    private String status = "PENDING";

    /** 审批人 */
    @Column(name = "approved_by")
    private String approvedBy;

    /** 审批时间 */
    @Column(name = "approved_time")
    private LocalDateTime approvedTime;

    /** 完成时间 */
    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    /** 缺货风险 */
    @Column(name = "stockout_risk")
    private BigDecimal stockoutRisk;

    /** 积压风险 */
    @Column(name = "overstock_risk")
    private BigDecimal overstockRisk;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public Integer getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(Integer safetyStock) {
        this.safetyStock = safetyStock;
    }

    public Long getPredictedDemand() {
        return predictedDemand;
    }

    public void setPredictedDemand(Long predictedDemand) {
        this.predictedDemand = predictedDemand;
    }

    public Integer getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public void setSuggestedQuantity(Integer suggestedQuantity) {
        this.suggestedQuantity = suggestedQuantity;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedTime() {
        return approvedTime;
    }

    public void setApprovedTime(LocalDateTime approvedTime) {
        this.approvedTime = approvedTime;
    }

    public LocalDateTime getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }

    public BigDecimal getStockoutRisk() {
        return stockoutRisk;
    }

    public void setStockoutRisk(BigDecimal stockoutRisk) {
        this.stockoutRisk = stockoutRisk;
    }

    public BigDecimal getOverstockRisk() {
        return overstockRisk;
    }

    public void setOverstockRisk(BigDecimal overstockRisk) {
        this.overstockRisk = overstockRisk;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
