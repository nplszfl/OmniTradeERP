package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    /**
     * 注意：row / column / level 是 SQL 保留字。
     *
     * <p>🔴 这里不只是**列名**要避开，<b>别名</b>也要避开：
     * MyBatis-Plus 生成 SELECT 时，属性名与列名不一致会自动补 {@code AS 属性名}，
     * 于是产生 {@code row_no AS row, column_no AS column, level_no AS level} ——
     * {@code ROW} / {@code COLUMN} / {@code LEVEL} 在 MySQL 8 里<b>全是保留字</b>，
     * 不加反引号就是语法错误：
     * {@code SQLSyntaxErrorException: ... near 'row,column_no AS column,level_no AS level'}
     *
     * <p>因此属性名统一用 {@code rowNo/columnNo/levelNo}，
     * 列名保持 {@code row_no/column_no/level_no} —— 两边都不碰保留字，
     * MP 就不会生成别名，SQL 也就合法了。
     * 对外 JSON 契约仍由 {@code LocationVO} 的 row/column/level 保持不变。</p>
     *
     * <p>⚠️ 入参也要兼容：前端创建/编辑库位提交的是 {@code row}/{@code column}/{@code level}
     * （见 {@code erp-web/src/api/warehouse.ts}），所以用 {@code @JsonAlias} 把旧字段名
     * 也收进来 —— 否则改名后这三个字段会**静默丢失**（Jackson 默认忽略未知属性），
     * 表现为「新建库位后排/列/层是空的」。</p>
     */
    @TableField("row_no")
    @JsonAlias({"row"})
    private String rowNo;
    @TableField("column_no")
    @JsonAlias({"column"})
    private String columnNo;
    @TableField("level_no")
    @JsonAlias({"level"})
    private String levelNo;

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