package com.crossborder.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 运费计算结果
 *
 * <p>对应前端 erp-web/src/api/inventory-alert.ts 的 {@code ShippingRateResult}。</p>
 */
@Data
public class ShippingRateResult {

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 承运商 */
    private String carrier;

    /** 总价 */
    private BigDecimal totalPrice;

    /** 币种 */
    private String currency;

    /** 预计天数 */
    private Integer estimatedDays;

    /** 时效描述（如「3-6天」） */
    private String transitTime;

    /** 是否推荐（价格/时效综合最优） */
    private Boolean isRecommended;
}
