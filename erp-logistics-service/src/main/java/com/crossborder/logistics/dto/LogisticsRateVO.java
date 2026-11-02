package com.crossborder.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流费率视图
 *
 * <p>对应前端 erp-web/src/api/inventory-alert.ts 的 {@code LogisticsRate}。</p>
 */
@Data
public class LogisticsRateVO {

    private Long id;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 承运商 */
    private String carrier;

    /** 配送方式 */
    private String shippingMethod;

    private String fromCountry;

    private String toCountry;

    private BigDecimal weightRangeMin;

    private BigDecimal weightRangeMax;

    private BigDecimal basePrice;

    private BigDecimal pricePerKg;

    private Integer estimatedDays;

    private String currency;

    private Boolean isActive;

    private LocalDateTime createTime;
}
