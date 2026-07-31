package com.crossborder.erp.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单高级查询请求
 */
@Data
public class OrderQueryRequest {

    /**
     * 当前页码
     */
    private Long current = 1L;

    /**
     * 每页大小
     */
    private Long size = 10L;

    /**
     * 平台（amazon/ebay/shopee/lazada/tiktok）
     */
    private String platform;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 支付状态
     */
    private String paymentStatus;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 最小订单金额
     */
    private BigDecimal minAmount;

    /**
     * 最大订单金额
     */
    private BigDecimal maxAmount;

    /**
     * 买家国家
     */
    private String buyerCountry;

    /**
     * 商品SKU（模糊匹配）
     */
    private String productSku;

    /**
     * 买家姓名（模糊匹配）
     */
    private String buyerName;

    /**
     * 买家邮箱（模糊匹配）
     */
    private String buyerEmail;

    /**
     * 物流单号（模糊匹配）
     */
    private String trackingNumber;

    /**
     * 内部订单号（模糊匹配）
     */
    private String internalOrderNo;

    /**
     * 平台订单号（模糊匹配）
     */
    private String platformOrderNo;
}