package com.crossborder.erp.finance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 余额操作类型
 */
@Getter
@AllArgsConstructor
public enum OperationType {
    INCREASE("增加"),
    DECREASE("减少");

    private final String description;

    public static OperationType getByCode(String code) {
        for (OperationType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        OperationType type = getByCode(code);
        return type == null ? "未知" : type.getDescription();
    }
}
