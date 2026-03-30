package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

/**
 * 库位VO
 */
@Data
public class LocationVO {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private String locationCode;
    private String locationName;
    private String locationType;
    private String zone;
    private String row;
    private String column;
    private String level;
    
    private Integer stockQuantity;
    private Integer maxCapacity;
    private Integer currentCapacity;
    private String status;
    
    private Boolean isPickable;
    private Boolean isStorable;
    
    private String remark;
}

/**
 * 入库单VO
 */
@Data
public class InboundOrderVO {
    private Long id;
    private String orderNo;
    private Long warehouseId;
    private String warehouseName;
    private String inboundType;
    private String inboundTypeName;
    
    private String supplierCode;
    private String supplierName;
    private String purchaseOrderNo;
    
    private LocalDateTime expectedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private LocalDateTime inboundTime;
    
    private Integer totalQuantity;
    private Integer receivedQuantity;
    private Integer differenceQuantity;
    
    private String status;
    private String statusName;
    
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    
    // 明细
    private List<InboundDetailVO> details;
}

/**
 * 入库明细VO
 */
@Data
class InboundDetailVO {
    private Long id;
    private Long orderId;
    private Long productId;
    private String skuCode;
    private String productName;
    
    private Integer expectedQuantity;
    private Integer receivedQuantity;
    private Integer differenceQuantity;
    
    private String locationCode;
    private String batchNo;
    private LocalDateTime productionDate;
    private LocalDateTime expiryDate;
    
    private BigDecimal unitCost;
    private BigDecimal totalAmount;
    
    private String status;
    private String remark;
}

/**
 * 出库单VO
 */
@Data
public class OutboundOrderVO {
    private Long id;
    private String orderNo;
    private Long warehouseId;
    private String warehouseName;
    private String outboundType;
    private String outboundTypeName;
    
    private String orderNo2;
    private String customerName;
    private String shippingAddress;
    private String shippingMethod;
    private String trackingNo;
    
    private LocalDateTime expectedShipTime;
    private LocalDateTime actualShipTime;
    
    private Integer totalQuantity;
    private Integer pickedQuantity;
    private Integer packedQuantity;
    
    private String status;
    private String statusName;
    
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    
    // 明细
    private List<OutboundDetailVO> details;
}

/**
 * 出库明细VO
 */
@Data
class OutboundDetailVO {
    private Long id;
    private Long orderId;
    private Long productId;
    private String skuCode;
    private String productName;
    
    private Integer quantity;
    private Integer pickedQuantity;
    
    private String locationCode;
    private String batchNo;
    
    private String status;
    private String remark;
}

/**
 * 调拨单VO
 */
@Data
public class TransferOrderVO {
    private Long id;
    private String transferNo;
    private Long fromWarehouseId;
    private String fromWarehouseName;
    private Long toWarehouseId;
    private String toWarehouseName;
    private String transferType;
    
    private LocalDateTime expectedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private LocalDateTime transferTime;
    
    private Integer totalQuantity;
    private Integer inTransitQuantity;
    private Integer receivedQuantity;
    
    private String status;
    private String statusName;
    
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    
    // 明细
    private List<TransferDetailVO> details;
}

/**
 * 调拨明细VO
 */
@Data
class TransferDetailVO {
    private Long id;
    private Long orderId;
    private Long productId;
    private String skuCode;
    private String productName;
    
    private Integer quantity;
    private Integer inTransitQuantity;
    private Integer receivedQuantity;
    
    private String fromLocationCode;
    private String toLocationCode;
    private String batchNo;
    
    private String status;
}

/**
 * 波次拣货VO
 */
@Data
public class WavePickingVO {
    private Long id;
    private String waveNo;
    private String waveType;
    private String waveTypeName;
    private String waveStatus;
    private String waveStatusName;
    
    private Integer orderCount;
    private Integer skuCount;
    private Integer totalQuantity;
    private Integer pickedQuantity;
    private BigDecimal progress;
    
    private String priority;
    private String priorityName;
    private String assignUser;
    private LocalDateTime assignTime;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    
    // 明细
    private List<WavePickingDetailVO> details;
}

/**
 * 波次明细VO
 */
@Data
class WavePickingDetailVO {
    private Long id;
    private Long waveId;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private String skuCode;
    private String productName;
    
    private Integer pickQuantity;
    private Integer pickedQuantity;
    private String fromLocationCode;
    private String fromLocationName;
    
    private String pickStatus;
    private String pickStatusName;
    private LocalDateTime pickTime;
    private String picker;
    
    private String remark;
}

/**
 * 包装材料VO
 */
@Data
public class PackingMaterialVO {
    private Long id;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String specifications;
    
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private String unit;
    
    private Integer stockQuantity;
    private Integer minStock;
    private Integer maxStock;
    private String stockStatus;
    
    private BigDecimal unitPrice;
    private BigDecimal totalValue;
    
    private String supplierCode;
    private String supplierName;
    
    private String status;
    private String statusName;
    
    private String remark;
}

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

/**
 * 每日趋势VO
 */
@Data
class DailyTrendVO {
    private String date;
    private Integer inboundQuantity;
    private Integer outboundQuantity;
    private Integer netQuantity;
}