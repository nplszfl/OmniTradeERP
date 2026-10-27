package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存盘点结果实体
 */
@Data
@TableName("wms_inventory_check_result")
public class InventoryCheckResult {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long checkId;
    private Long warehouseId;
    private Long locationId;
    private String locationCode;
    private Long productId;
    private String skuCode;
    private String productName;

    /** 账面数量 */
    private Integer bookQuantity;
    /** 实盘数量 */
    private Integer actualQuantity;
    /** 差异数量 = 实盘 - 账面，正数为盘盈，负数为盘亏 */
    private Integer diffQty;

    /** 盘点状态，如 PENDING / CONFIRMED */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
