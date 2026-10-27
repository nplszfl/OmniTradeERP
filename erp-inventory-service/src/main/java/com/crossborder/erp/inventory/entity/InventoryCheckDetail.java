package com.crossborder.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存盘点明细
 */
@Data
@TableName("erp_inventory_check_detail")
public class InventoryCheckDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单 ID */
    private Long checkId;

    /** 库存记录 ID */
    private Long inventoryId;

    /** 盘点数量 */
    private Integer checkQty;

    /** 盘点前数量 */
    private Integer beforeQty;

    /** 差异数量 */
    private Integer diffQty;

    /** 差异金额 */
    private BigDecimal diffAmount;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
