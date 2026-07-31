package com.crossborder.erp.order.dto;

import lombok.Data;

import java.util.List;

/**
 * 订单批量操作请求
 */
@Data
public class OrderBatchRequest {

    /**
     * 订单ID列表
     */
    private List<Long> orderIds;

    /**
     * 操作类型：update_status/mark_shipped/export
     */
    private String operation;

    /**
     * 目标状态（用于update_status操作）
     */
    private String targetStatus;

    /**
     * 物流单号（用于mark_shipped操作）
     */
    private String trackingNumber;

    /**
     * 物流公司（用于mark_shipped操作）
     */
    private String logisticsCompany;
}