package com.crossborder.erp.finance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 财务账户状态
 */
@Getter
@AllArgsConstructor
public enum AccountStatus {
    ACTIVE("启用"),
    INACTIVE("停用"),
    DELETED("已删除");

    private final String description;

    public static AccountStatus getByCode(String code) {
        for (AccountStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        AccountStatus status = getByCode(code);
        return status == null ? "未知" : status.getDescription();
    }
}
