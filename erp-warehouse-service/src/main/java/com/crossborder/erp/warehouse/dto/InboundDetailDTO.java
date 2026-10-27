package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import lombok.Data;


/**
 * 入库明细DTO
 */
@Data
class InboundDetailDTO {
    private Long productId;
    private String skuCode;
    private Integer quantity;
    private String locationCode;
    private String batchNo;
    private String productionDate;
    private BigDecimal unitCost;
    private String remark;
}
