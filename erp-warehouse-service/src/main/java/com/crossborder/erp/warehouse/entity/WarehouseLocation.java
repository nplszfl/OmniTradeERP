package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 库位实体
 */
@Data
@TableName("wms_warehouse_location")
public class WarehouseLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    /** 库位上存放的商品（库位即 SKU 维度） */
    private Long productId;
    private String locationCode;
    private String locationName;
    private String locationType;
    private String zone;
    /** 注意：row / column / level 是 SQL 保留字，统一映射到 *_no 列 */
    @TableField("row_no")
    private String row;
    @TableField("column_no")
    private String column;
    @TableField("level_no")
    private String level;

    private Integer stockQuantity;
    /** 已锁定（占用）数量 */
    private Integer lockedQuantity;
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