package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 出库明细实体
 */
@Data
@TableName("wms_outbound_detail")
public class OutboundDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long outboundOrderId;
    private Long productId;
    private String skuCode;
    private String productName;

    private Integer quantity;
    private Integer pickedQuantity;
    private Integer packedQuantity;

    private String locationCode;
    private String batchNo;

    /** 明细状态，如 PENDING / PICKED / SHIPPED */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
