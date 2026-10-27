package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 出库单DTO
 */
@Data
public class OutboundOrderDTO {
    private Long id;
    private Long warehouseId;
    private String outboundType;
    private String orderNo;
    private String customerName;
    private String shippingAddress;
    private String shippingMethod;
    private String remark;
    
    // 明细
    private java.util.List<OutboundDetailDTO> details;
}
