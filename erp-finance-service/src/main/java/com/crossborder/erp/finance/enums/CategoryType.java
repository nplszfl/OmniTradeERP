package com.crossborder.erp.finance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 财务分类类型
 */
@Getter
@AllArgsConstructor
public enum CategoryType {
    INCOME("收入类"),
    EXPENSE("支出类");

    private final String description;

    public static CategoryType getByCode(String code) {
        for (CategoryType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        CategoryType type = getByCode(code);
        return type == null ? "未知" : type.getDescription();
    }
}
