package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入库单实体
 */
@Data
@TableName("wms_inbound_order")
public class InboundOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private Long warehouseId;
    /** 入库类型，对应 {@link com.crossborder.erp.warehouse.enums.InboundType} */
    private String inboundType;

    private String supplierCode;
    private String supplierName;
    private String purchaseOrderNo;

    private LocalDateTime expectedArrivalTime;
    private LocalDateTime actualArrivalTime;
    private LocalDateTime inboundTime;

    private Integer totalQuantity;
    private Integer receivedQuantity;
    private Integer differenceQuantity;

    /** 0-待入库 1-已入库 -1-已取消 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
