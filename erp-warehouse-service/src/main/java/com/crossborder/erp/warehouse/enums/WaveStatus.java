package com.crossborder.erp.warehouse.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 波次拣货状态枚举
 *
 * <p>取值与 {@code database/warehouse-service.sql} 中 {@code wms_wave_picking.wave_status} 列一致。</p>
 */
@Getter
@AllArgsConstructor
public enum WaveStatus {
    CREATED("CREATED", "已创建"),
    ASSIGNED("ASSIGNED", "已分配"),
    PICKING("PICKING", "拣货中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    /**
     * 根据编码获取枚举
     */
    public static WaveStatus getByCode(String code) {
        for (WaveStatus status : values()) {
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
        WaveStatus status = getByCode(code);
        return status == null ? code : status.getDescription();
    }
}
