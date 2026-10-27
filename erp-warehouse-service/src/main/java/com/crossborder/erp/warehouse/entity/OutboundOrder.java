package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库单实体
 */
@Data
@TableName("wms_outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private Long warehouseId;
    /** 出库类型，对应 {@link com.crossborder.erp.warehouse.enums.OutboundType} */
    private String outboundType;

    private String customerName;
    private String shippingAddress;
    private String shippingMethod;
    private String trackingNo;

    private LocalDateTime expectedShipTime;
    private LocalDateTime actualShipTime;
    private LocalDateTime outboundTime;

    private Integer totalQuantity;
    private Integer pickedQuantity;
    private Integer packedQuantity;

    /** 0-待出库 1-已出库 -1-已取消 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
