package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 调拨单DTO
 */
@Data
public class TransferOrderDTO {
    private Long id;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String transferType;
    private String expectedArrivalDate;
    private String remark;
    
    // 明细
    private java.util.List<TransferDetailDTO> details;
}
