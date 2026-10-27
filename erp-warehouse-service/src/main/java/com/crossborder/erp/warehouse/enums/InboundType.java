package com.crossborder.erp.warehouse.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 入库类型枚举
 */
@Getter
@AllArgsConstructor
public enum InboundType {
    PURCHASE("PURCHASE", "采购入库"),
    RETURN("RETURN", "退货入库"),
    TRANSFER("TRANSFER", "调拨入库"),
    PRODUCTION("PRODUCTION", "生产入库"),
    OTHER("OTHER", "其他入库");

    private final String code;
    private final String description;

    /**
     * 根据编码获取枚举
     */
    public static InboundType getByCode(String code) {
        for (InboundType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据编码获取中文描述
     */
    public static String getDescription(String code) {
        InboundType type = getByCode(code);
        return type == null ? code : type.getDescription();
    }
}
