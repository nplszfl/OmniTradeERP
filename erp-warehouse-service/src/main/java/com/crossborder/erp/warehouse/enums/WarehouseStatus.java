package com.crossborder.erp.warehouse.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 仓库状态枚举
 */
@Getter
@AllArgsConstructor
public enum WarehouseStatus {
    ACTIVE(1, "启用"),
    INACTIVE(0, "停用"),
    MAINTENANCE(2, "维护中");

    private final int code;
    private final String description;
}