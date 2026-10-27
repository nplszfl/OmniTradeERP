package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;


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
