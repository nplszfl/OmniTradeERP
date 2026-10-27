package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入库明细实体
 */
@Data
@TableName("wms_inbound_detail")
public class InboundDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long inboundOrderId;
    private Long productId;
    private String skuCode;
    private String productName;

    /** 计划/实际入库数量 */
    private Integer quantity;
    private Integer expectedQuantity;
    private Integer receivedQuantity;
    private Integer differenceQuantity;

    private String locationCode;
    private String batchNo;
    private LocalDateTime productionDate;
    private LocalDateTime expiryDate;

    private BigDecimal unitCost;
    private BigDecimal totalAmount;

    /** 明细状态，如 PENDING / RECEIVED */
    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
