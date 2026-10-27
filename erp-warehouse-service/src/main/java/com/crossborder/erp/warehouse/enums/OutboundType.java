package com.crossborder.erp.warehouse.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出库类型枚举
 */
@Getter
@AllArgsConstructor
public enum OutboundType {
    SALE("SALE", "销售出库"),
    TRANSFER("TRANSFER", "调拨出库"),
    RETURN("RETURN", "退货出库"),
    SAMPLE("SAMPLE", "样品出库"),
    DAMAGE("DAMAGE", "报损出库"),
    OTHER("OTHER", "其他出库");

    private final String code;
    private final String description;

    /**
     * 根据编码获取枚举
     */
    public static OutboundType getByCode(String code) {
        for (OutboundType type : values()) {
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
        OutboundType type = getByCode(code);
        return type == null ? code : type.getDescription();
    }
}
