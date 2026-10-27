package com.crossborder.erp.warehouse.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 波次拣货明细拣货状态枚举
 *
 * <p>取值与 {@code database/warehouse-service.sql} 中 {@code wms_wave_picking_detail.pick_status} 列一致。</p>
 */
@Getter
@AllArgsConstructor
public enum PickStatus {
    PENDING("PENDING", "待拣货"),
    PICKING("PICKING", "拣货中"),
    PICKED("PICKED", "已拣货"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    /**
     * 根据编码获取枚举
     */
    public static PickStatus getByCode(String code) {
        for (PickStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据编码获取中文描述
     */
    public static String getDescription(String code) {
        PickStatus status = getByCode(code);
        return status == null ? code : status.getDescription();
    }
}
