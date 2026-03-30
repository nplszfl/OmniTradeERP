package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 波次拣货实体
 */
@Data
@TableName("wms_wave_picking")
public class WavePicking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String waveNo;
    private String waveType;
    private String waveStatus;

    private Integer orderCount;
    private Integer skuCount;
    private Integer totalQuantity;

    private String priority;
    private String assignUser;
    private LocalDateTime assignTime;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

/**
 * 波次明细
 */
@Data
@TableName("wms_wave_picking_detail")
class WavePickingDetail {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long waveId;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private String skuCode;

    private Integer pickQuantity;
    private Integer pickedQuantity;
    private Long fromLocationId;
    private String fromLocationCode;

    private String pickStatus;
    private LocalDateTime pickTime;
    private String picker;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}