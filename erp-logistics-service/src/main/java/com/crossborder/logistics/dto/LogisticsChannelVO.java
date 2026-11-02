package com.crossborder.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流渠道视图
 *
 * <p>对应前端 erp-web/src/api/inventory-alert.ts 的 {@code LogisticsChannel}。</p>
 */
@Data
public class LogisticsChannelVO {

    /** 渠道编码 */
    private String code;

    /** 渠道名称 */
    private String name;

    /** 承运商 */
    private String carrier;

    /** 渠道类型 */
    private String type;

    /** 覆盖国家 */
    private List<String> coverage;

    /** 渠道特点 */
    private List<String> features;

    /** 平均时效（天） */
    private Integer avgDeliveryDays;

    /** 是否支持追踪 */
    private Boolean trackingSupport;

    /** ID（管理端用） */
    private Long id;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createTime;
}
