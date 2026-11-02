package com.crossborder.logistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流渠道
 *
 * <p>对应表 {@code t_logistics_channel}（见 database/logistics-service.sql）。</p>
 */
@Data
@TableName("t_logistics_channel")
public class LogisticsChannel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 渠道编码（DHL_EXPRESS） */
    private String channelCode;

    /** 渠道名称（DHL国际快递） */
    private String channelName;

    /** 承运商（DHL） */
    private String carrier;

    /** 渠道类型（EXPRESS / STANDARD / ECONOMY / SPECIAL） */
    private String type;

    /** 覆盖国家（JSON 数组字符串） */
    private String coverage;

    /** 特点（JSON 数组字符串） */
    private String features;

    /** 平均时效（天） */
    private Integer avgDeliveryDays;

    /** 是否支持追踪 */
    private Boolean trackingSupport;

    /** 是否启用 */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
