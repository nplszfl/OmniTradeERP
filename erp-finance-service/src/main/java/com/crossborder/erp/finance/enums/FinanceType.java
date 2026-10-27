package com.crossborder.erp.finance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 财务流水类型
 */
@Getter
@AllArgsConstructor
public enum FinanceType {
    INCOME("收入"),
    EXPENSE("支出"),
    REFUND("退款");

    private final String description;

    public static FinanceType getByCode(String code) {
        for (FinanceType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        FinanceType type = getByCode(code);
        return type == null ? "未知" : type.getDescription();
    }
}
