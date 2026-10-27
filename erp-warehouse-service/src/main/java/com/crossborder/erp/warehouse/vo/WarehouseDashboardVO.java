package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;


/**
 * 仓库仪表盘VO
 */
@Data
public class WarehouseDashboardVO {
    // 今日概览
    private Integer todayInboundOrders;
    private Integer todayOutboundOrders;
    private Integer todayInboundQuantity;
    private Integer todayOutboundQuantity;
    
    // 库存概览
    private Integer totalSku;
    private Integer totalQuantity;
    private Integer lowStockCount;
    private Integer outOfStockCount;
    
    // 待处理
    private Integer pendingInbound;
    private Integer pendingOutbound;
    private Integer pendingTransfer;
    private Integer pendingPick;
    
    // 库位利用率
    private BigDecimal locationUtilization;
    
    // 库存预警
    private List<Map<String, Object>> stockAlerts;
    
    // 近7天趋势
    private List<DailyTrendVO> weeklyTrend;
}
