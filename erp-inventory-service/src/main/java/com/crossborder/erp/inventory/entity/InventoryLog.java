package com.crossborder.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.inventory.enums.OperationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水
 */
@Data
@TableName("erp_inventory_log")
public class InventoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 库存记录 ID */
    private Long inventoryId;

    /** 商品 ID */
    private Long productId;

    /** 仓库 ID */
    private Long warehouseId;

    /** 操作数量 */
    private Integer quantity;

    /** 操作类型 */
    private OperationType operationType;

    /** 操作前数量 */
    private Integer beforeQuantity;

    /** 操作后数量 */
    private Integer afterQuantity;

    /** 关联订单号 */
    private String orderNo;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
