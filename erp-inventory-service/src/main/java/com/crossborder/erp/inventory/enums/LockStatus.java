package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存锁定状态
 */
@Getter
@AllArgsConstructor
public enum LockStatus {
    LOCKED("锁定中"),
    RELEASED("已释放");

    private final String description;

    public static LockStatus getByCode(String code) {
        for (LockStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        LockStatus status = getByCode(code);
        return status == null ? "未知" : status.getDescription();
    }
}
