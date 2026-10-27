package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import lombok.Data;


/**
 * 仓库统计VO
 */
@Data
public class WarehouseStatisticsVO {
    private Long warehouseId;
    private String warehouseName;
    
    // 库存统计
    private Integer totalSku;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer lockedQuantity;
    
    // 金额统计
    private BigDecimal totalValue;
    
    // 库位统计
    private Integer totalLocations;
    private Integer usedLocations;
    private Integer idleLocations;
    private BigDecimal utilizationRate;
    
    // 订单统计
    private Integer todayInbound;
    private Integer todayOutbound;
    private Integer pendingInbound;
    private Integer pendingOutbound;
    
    // 预警统计
    private Integer lowStockAlert;
    private Integer outOfStockAlert;
    private Integer expiringAlert;
}
