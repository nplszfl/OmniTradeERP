package com.crossborder.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 包装材料实体
 */
@Data
@TableName("wms_packing_material")
public class PackingMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;
    private String materialName;
    private String materialType;
    private String specifications;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;

    private Integer stockQuantity;
    private Integer minStock;
    private Integer maxStock;
    private String unit;

    private BigDecimal unitPrice;
    private String supplierCode;
    private String supplierName;

    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}