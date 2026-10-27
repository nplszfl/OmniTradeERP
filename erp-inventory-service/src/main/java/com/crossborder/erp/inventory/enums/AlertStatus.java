package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存预警处理状态
 */
@Getter
@AllArgsConstructor
public enum AlertStatus {
    PENDING("待处理"),
    PROCESSED("已处理");

    private final String description;

    public static AlertStatus getByCode(String code) {
        for (AlertStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        AlertStatus status = getByCode(code);
        return status == null ? "未知" : status.getDescription();
    }
}
