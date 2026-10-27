package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存调拨单实体
 */
@Data
@TableName("wms_stock_transfer")
public class StockTransfer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String transferNo;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    /** 调拨类型，如 TRANSFER / RETURN */
    private String transferType;

    /** 单商品调拨时冗余记录，便于直接扣减库存 */
    private Long productId;
    private String skuCode;
    private Integer quantity;
    private String fromLocationCode;
    private String toLocationCode;

    private Integer totalQuantity;
    private Integer inTransitQuantity;
    private Integer receivedQuantity;

    private LocalDateTime expectedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private LocalDateTime transferTime;

    /** 0-待调拨 1-已调拨 2-已收货 -1-已取消 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
