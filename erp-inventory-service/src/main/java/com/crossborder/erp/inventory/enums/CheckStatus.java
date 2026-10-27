package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 盘点单状态
 */
@Getter
@AllArgsConstructor
public enum CheckStatus {
    PENDING("待盘点"),
    COMPLETED("已完成");

    private final String description;

    public static CheckStatus getByCode(String code) {
        for (CheckStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        CheckStatus status = getByCode(code);
        return status == null ? "未知" : status.getDescription();
    }
}
