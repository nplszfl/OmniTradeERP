package com.crossborder.erp.order.dto;

import lombok.Data;

import java.util.List;

/**
 * 订单导出响应
 */
@Data
public class OrderExportResponse {

    /**
     * 导出文件名
     */
    private String fileName;

    /**
     * 导出数据
     */
    private List<OrderExportData> data;

    /**
     * 总数
     */
    private Integer total;

    @Data
    public static class OrderExportData {
        private String platform;
        private String platformOrderNo;
        private String internalOrderNo;
        private String buyerName;
        private String buyerEmail;
        private String buyerCountry;
        private String orderAmount;
        private String currencyCode;
        private String status;
        private String paymentStatus;
        private String trackingNumber;
        private String logisticsCompany;
        private String recipientCountry;
        private String recipientState;
        private String recipientCity;
        private String recipientAddress;
        private String createTime;
        private String paymentTime;
        private String shippingTime;
    }
}