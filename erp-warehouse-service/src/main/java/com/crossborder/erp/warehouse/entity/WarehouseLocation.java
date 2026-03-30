package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库位实体
 */
@Data
@TableName("wms_warehouse_location")
public class WarehouseLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    private String locationCode;
    private String locationName;
    private String locationType;
    private String zone;
    private String row;
    private String column;
    private String level;

    private Integer stockQuantity;
    private Integer maxCapacity;
    private Integer currentCapacity;
    private String status;

    private String pickingPriority;
    private Boolean isPickable;
    private Boolean isStorable;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}