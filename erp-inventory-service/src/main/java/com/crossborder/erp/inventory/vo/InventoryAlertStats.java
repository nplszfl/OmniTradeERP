package com.crossborder.erp.inventory.vo;

import lombok.Data;

/**
 * 库存预警统计
 */
@Data
public class InventoryAlertStats {

    /** 待处理预警数 */
    private Long pendingCount;

    /** 已处理预警数 */
    private Long processedCount;

    /** 低库存预警数 */
    private Long lowStockCount;

    /** 缺货预警数 */
    private Long outOfStockCount;

    /** 滞销预警数 */
    private Long slowMovingCount;
}
