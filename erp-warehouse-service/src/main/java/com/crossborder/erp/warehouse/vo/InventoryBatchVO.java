package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 库存批次VO
 */
@Data
public class InventoryBatchVO {
    private Long id;
    private String batchNo;
    private Long productId;
    private String skuCode;
    private String productName;
    
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationCode;
    
    private Integer quantity;
    private Integer availableQuantity;
    private Integer lockedQuantity;
    private Integer damagedQuantity;
    
    private String batchStatus;
    private String batchStatusName;
    
    private LocalDateTime productionDate;
    private LocalDateTime expiryDate;
    private LocalDateTime inboundDate;
    private Integer remainingDays;
    
    private String supplierCode;
    private String supplierName;
    private String purchaseOrderNo;
    private BigDecimal unitCost;
    private BigDecimal totalAmount;
    
    private String remark;
}
