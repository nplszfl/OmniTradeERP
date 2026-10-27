package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存状态
 */
@Getter
@AllArgsConstructor
public enum InventoryStatus {
    NORMAL("正常"),
    WARNING("预警"),
    DANGER("危险");

    private final String description;

    public static InventoryStatus getByCode(String code) {
        for (InventoryStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        InventoryStatus status = getByCode(code);
        return status == null ? "未知" : status.getDescription();
    }
}
