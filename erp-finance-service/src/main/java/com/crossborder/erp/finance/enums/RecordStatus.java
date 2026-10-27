package com.crossborder.erp.finance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 财务记录状态
 */
@Getter
@AllArgsConstructor
public enum RecordStatus {
    PENDING("待确认"),
    COMPLETED("已完成"),
    VOIDED("已作废");

    private final String description;

    public static RecordStatus getByCode(String code) {
        for (RecordStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescription(String code) {
        RecordStatus status = getByCode(code);
        return status == null ? "未知" : status.getDescription();
    }
}
