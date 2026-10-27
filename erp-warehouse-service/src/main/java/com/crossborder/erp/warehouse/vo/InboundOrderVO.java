package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;


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
