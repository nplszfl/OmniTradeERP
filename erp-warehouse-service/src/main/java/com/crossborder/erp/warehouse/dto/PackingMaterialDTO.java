package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 包装材料DTO
 */
@Data
public class PackingMaterialDTO {
    private Long id;
    private String materialCode;
    private String materialName;
    private String materialType;
    private String specifications;
    private Double weight;
    private Integer quantity;
    private String supplierName;
    private String remark;
}
