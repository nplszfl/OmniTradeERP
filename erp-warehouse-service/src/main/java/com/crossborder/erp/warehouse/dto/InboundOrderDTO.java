package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 入库单DTO
 */
@Data
public class InboundOrderDTO {
    private Long id;
    private Long warehouseId;
    private String inboundType;
    private String supplierCode;
    private String supplierName;
    private String purchaseOrderNo;
    private String expectedArrivalDate;
    private String remark;
    
    // 明细
    private java.util.List<InboundDetailDTO> details;
}
