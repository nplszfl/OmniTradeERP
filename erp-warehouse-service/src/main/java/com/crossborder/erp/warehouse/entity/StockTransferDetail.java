package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存调拨明细实体
 */
@Data
@TableName("wms_stock_transfer_detail")
public class StockTransferDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long transferOrderId;
    private Long productId;
    private String skuCode;
    private String productName;

    private Integer quantity;
    private Integer inTransitQuantity;
    private Integer receivedQuantity;

    private String fromLocationCode;
    private String toLocationCode;
    private String batchNo;

    /** 明细状态，如 PENDING / TRANSFERRED / RECEIVED */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
