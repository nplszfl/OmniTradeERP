package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 波次明细VO
 */
@Data
class WavePickingDetailVO {
    private Long id;
    private Long waveId;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private String skuCode;
    private String productName;
    
    private Integer pickQuantity;
    private Integer pickedQuantity;
    private String fromLocationCode;
    private String fromLocationName;
    
    private String pickStatus;
    private String pickStatusName;
    private LocalDateTime pickTime;
    private String picker;
    
    private String remark;
}
