package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存锁定类型
 */
@Getter
@AllArgsConstructor
public enum LockType {
    RESERVATION("预留"),
    ORDER("订单占用"),
    CHECK("盘点冻结"),
    OTHER("其他");

    private final String description;

    public static LockType getByCode(String code) {
        for (LockType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        LockType type = getByCode(code);
        return type == null ? "未知" : type.getDescription();
    }
}
