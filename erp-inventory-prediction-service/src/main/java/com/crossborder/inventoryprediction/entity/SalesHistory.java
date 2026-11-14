package com.crossborder.inventoryprediction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售历史实体（对应 {@code erp_inventory_prediction_db.sales_history} 表）
 *
 * <p>📌 **2026-11-14 新增**：这个模块原先**完全没有持久层** ——
 * 销量历史是用 {@code Random} 现造的
 * （{@code dailySales = baseSales + random.nextInt(20) - 10}），
 * 而 {@code sales_history} 表本来就在（还带 {@code uk_product_date} 唯一键）。
 * → 「预测」是在预测**随机数**，结果完全不可复现。</p>
 */
@Entity
@Table(name = "sales_history")
public class SalesHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 产品ID */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 销售日期 */
    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    /** 销售数量 */
    @Column(name = "sales_quantity", nullable = false)
    private Integer salesQuantity = 0;

    /** 销售金额 */
    @Column(name = "sales_amount")
    private BigDecimal salesAmount = BigDecimal.ZERO;

    /** 订单数量 */
    @Column(name = "order_count")
    private Integer orderCount = 0;

    /** 平均订单金额 */
    @Column(name = "avg_order_amount")
    private BigDecimal avgOrderAmount = BigDecimal.ZERO;

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

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDate salesDate) {
        this.salesDate = salesDate;
    }

    public Integer getSalesQuantity() {
        return salesQuantity;
    }

    public void setSalesQuantity(Integer salesQuantity) {
        this.salesQuantity = salesQuantity;
    }

    public BigDecimal getSalesAmount() {
        return salesAmount;
    }

    public void setSalesAmount(BigDecimal salesAmount) {
        this.salesAmount = salesAmount;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getAvgOrderAmount() {
        return avgOrderAmount;
    }

    public void setAvgOrderAmount(BigDecimal avgOrderAmount) {
        this.avgOrderAmount = avgOrderAmount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
