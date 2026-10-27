package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Data;


/**
 * 库位VO
 */
@Data
public class LocationVO {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private String locationCode;
    private String locationName;
    private String locationType;
    private String zone;
    private String row;
    private String column;
    private String level;
    
    private Integer stockQuantity;
    private Integer maxCapacity;
    private Integer currentCapacity;
    private String status;
    
    private Boolean isPickable;
    private Boolean isStorable;
    
    private String remark;
}
