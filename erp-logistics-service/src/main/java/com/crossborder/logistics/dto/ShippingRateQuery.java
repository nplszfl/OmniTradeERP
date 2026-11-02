package com.crossborder.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 运费计算请求
 *
 * <p>对应前端 erp-web/src/api/inventory-alert.ts 的 {@code ShippingRateQuery}。</p>
 */
@Data
public class ShippingRateQuery {

    /** 起运国 */
    private String fromCountry;

    /** 起运邮编 */
    private String fromPostalCode;

    /** 目的国 */
    private String toCountry;

    /** 目的邮编 */
    private String toPostalCode;

    /** 重量（kg） */
    private BigDecimal weight;

    /** 长（cm） */
    private BigDecimal length;

    /** 宽（cm） */
    private BigDecimal width;

    /** 高（cm） */
    private BigDecimal height;
}
