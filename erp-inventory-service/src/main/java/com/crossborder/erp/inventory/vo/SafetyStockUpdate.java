package com.crossborder.erp.inventory.vo;

import lombok.Data;

/**
 * 安全库存更新项
 */
@Data
public class SafetyStockUpdate {

    /** 库存记录 ID */
    private Long inventoryId;

    /** 新的安全库存 */
    private Integer safetyStock;
}
