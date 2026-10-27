package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;


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
