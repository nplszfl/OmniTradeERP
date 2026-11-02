package com.crossborder.logistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流费率
 *
 * <p>对应表 {@code t_logistics_rate}（见 database/logistics-service.sql）。</p>
 * <p>按「起运国 → 目的国 + 重量区间」定价。</p>
 */
@Data
@TableName("t_logistics_rate")
public class LogisticsRate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 承运商 */
    private String carrier;

    /** 配送方式 */
    private String shippingMethod;

    /** 起运国 */
    private String fromCountry;

    /** 目的国 */
    private String toCountry;

    /** 重量下限（kg） */
    private BigDecimal weightRangeMin;

    /** 重量上限（kg） */
    private BigDecimal weightRangeMax;

    /** 基础价 */
    private BigDecimal basePrice;

    /** 每公斤加价 */
    private BigDecimal pricePerKg;

    /** 预计时效（天） */
    private Integer estimatedDays;

    /** 币种 */
    private String currency;

    /** 是否启用 */
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
