package com.crossborder.erp.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存操作类型
 */
@Getter
@AllArgsConstructor
public enum OperationType {
    INBOUND("入库"),
    OUTBOUND("出库"),
    TRANSFER("调拨"),
    LOCK("锁定"),
    RELEASE("释放"),
    ADJUST("盘点调整");

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
