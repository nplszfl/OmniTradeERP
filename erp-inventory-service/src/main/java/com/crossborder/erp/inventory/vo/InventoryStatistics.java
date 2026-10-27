package com.crossborder.erp.inventory.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存统计
 */
@Data
public class InventoryStatistics {

    /** SKU 总数 */
    private Integer totalSku;

    /** 总数量 */
    private Integer totalQuantity;

    /** 可用数量合计 */
    private Integer availableQuantity;

    /** 锁定数量合计 */
    private Integer lockedQuantity;

    /** 正常状态库存数 */
    private Integer normalCount;

    /** 预警状态库存数 */
    private Integer warningCount;

    /** 危险状态库存数 */
    private Integer dangerCount;

    /** 库存总金额 */
    private BigDecimal totalAmount;

    /** 期间入库数量 */
    private Integer inboundQuantity;

    /** 期间出库数量 */
    private Integer outboundQuantity;

    /** 期间净变化数量 */
    private Integer netQuantity;
}
