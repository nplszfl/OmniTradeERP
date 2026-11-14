package com.crossborder.inventoryprediction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 库存实体（对应 {@code erp_inventory_prediction_db.inventory} 表）
 *
 * <p>📌 **2026-11-14 新增**：原先 {@code getCurrentStock()} 是
 * {@code (int) (productId * 50 + random.nextInt(100))} —— 纯随机造库存。</p>
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 产品ID */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 仓库ID */
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /** 当前库存数量 */
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    /** 安全库存 */
    @Column(name = "safety_stock")
    private Integer safetyStock = 0;

    /** 可用库存 */
    @Column(name = "available_quantity")
    private Integer availableQuantity = 0;

    /** 锁定数量 */
    @Column(name = "locked_quantity")
    private Integer lockedQuantity = 0;

    /** 上次补货日期 */
    @Column(name = "last_replenish_date")
    private LocalDateTime lastReplenishDate;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

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

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(Integer safetyStock) {
        this.safetyStock = safetyStock;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Integer getLockedQuantity() {
        return lockedQuantity;
    }

    public void setLockedQuantity(Integer lockedQuantity) {
        this.lockedQuantity = lockedQuantity;
    }

    public LocalDateTime getLastReplenishDate() {
        return lastReplenishDate;
    }

    public void setLastReplenishDate(LocalDateTime lastReplenishDate) {
        this.lastReplenishDate = lastReplenishDate;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
