package com.crossborder.erp.order.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatus {
    PENDING("PENDING", "待处理"),
    CONFIRMED("CONFIRMED", "已确认"),
    PROCESSING("PROCESSING", "处理中"),
    SHIPPED("SHIPPED", "已发货"),
    DELIVERED("DELIVERED", "已送达"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消"),
    REFUNDED("REFUNDED", "已退款");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}