package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;


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
