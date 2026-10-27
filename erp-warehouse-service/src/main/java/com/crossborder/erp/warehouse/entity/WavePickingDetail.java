package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 波次拣货明细实体
 */
@Data
@TableName("wms_wave_picking_detail")
public class WavePickingDetail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long waveId;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private String skuCode;
    private String productName;

    private Integer pickQuantity;
    private Integer pickedQuantity;
    private Long fromLocationId;
    private String fromLocationCode;

    /** 拣货状态，如 PENDING / PICKING / PICKED */
    private String pickStatus;
    private LocalDateTime pickTime;
    private String picker;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
