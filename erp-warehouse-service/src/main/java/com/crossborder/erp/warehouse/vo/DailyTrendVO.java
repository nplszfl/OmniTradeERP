package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;


/**
 * 每日趋势VO
 */
@Data
class DailyTrendVO {
    private String date;
    private Integer inboundQuantity;
    private Integer outboundQuantity;
    private Integer netQuantity;
}
