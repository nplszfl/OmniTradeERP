package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;


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
