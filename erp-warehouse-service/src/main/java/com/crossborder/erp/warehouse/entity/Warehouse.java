package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仓库实体
 */
@Data
@TableName("wms_warehouse")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String warehouseCode;
    private String name;
    private String aliasName;
    private String country;
    private String province;
    private String city;
    private String district;
    private String address;
    private String contact;
    private String phone;
    private String email;

    private Integer status;
    private String warehouseType;
    private BigDecimal totalArea;
    private BigDecimal usableArea;
    private Integer capacity;

    private String timeZone;
    private String currency;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}