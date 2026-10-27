package com.crossborder.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.inventory.enums.AlertStatus;
import com.crossborder.erp.inventory.enums.AlertType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存预警
 */
@Data
@TableName("erp_inventory_alert")
public class InventoryAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 库存记录 ID */
    private Long inventoryId;

    /** 商品 ID */
    private Long productId;

    /** 仓库 ID */
    private Long warehouseId;

    /** 预警类型 */
    private AlertType alertType;

    /** 预警时库存数量 */
    private Integer currentQty;

    /** 安全库存 */
    private Integer safetyStock;

    /** 处理状态 */
    private AlertStatus alertStatus;

    /** 处理人 */
    private String handler;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理备注 */
    private String handleRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
