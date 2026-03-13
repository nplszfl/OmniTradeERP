package com.crossborder.erp.common.constant;

/**
 * 订单状态枚举
 */
public enum OrderStatus {

    PENDING_PAYMENT("pending_payment", "待付款", 1),
    PENDING_SHIPMENT("pending_shipment", "待发货", 2),
    SHIPPED("shipped", "已发货", 3),
    DELIVERED("delivered", "已送达", 4),
    CANCELLED("cancelled", "已取消", 5),
    REFUNDED("refunded", "已退款", 6),
    CLOSED("closed", "已关闭", 7);

    private final String code;
    private final String name;
    private final Integer sort;

    OrderStatus(String code, String name, Integer sort) {
        this.code = code;
        this.name = name;
        this.sort = sort;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getSort() {
        return sort;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status code: " + code);
    }
}
