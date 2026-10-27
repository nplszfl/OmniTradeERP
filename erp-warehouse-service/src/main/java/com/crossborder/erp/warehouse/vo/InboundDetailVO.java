package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;


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
