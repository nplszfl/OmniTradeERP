package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存批次实体
 */
@Data
@TableName("wms_inventory_batch")
public class InventoryBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private Long productId;
    private String skuCode;
    private Long warehouseId;
    private Long locationId;

    private Integer quantity;
    private Integer availableQuantity;
    private Integer lockedQuantity;
    private Integer damagedQuantity;
    private Integer expiredQuantity;

    private String batchStatus;
    private LocalDateTime productionDate;
    private LocalDateTime expiryDate;
    private LocalDateTime inboundDate;

    private String supplierCode;
    private String supplierName;
    private String purchaseOrderNo;
    private BigDecimal unitCost;
    private BigDecimal totalAmount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}