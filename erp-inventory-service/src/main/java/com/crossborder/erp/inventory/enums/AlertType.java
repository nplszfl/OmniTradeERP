package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存预警类型
 */
@Getter
@AllArgsConstructor
public enum AlertType {
    LOW_STOCK("低库存预警"),
    OUT_OF_STOCK("缺货预警"),
    SLOW_MOVING("滞销预警");

    private final String description;

    public static AlertType getByCode(String code) {
        for (AlertType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        AlertType type = getByCode(code);
        return type == null ? "未知" : type.getDescription();
    }
}
