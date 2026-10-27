package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 库位查询DTO
 */
@Data
public class LocationQueryDTO {
    private Long warehouseId;
    private String locationCode;
    private String locationType;
    private String zone;
    private String status;
    private Boolean isPickable;
    private Boolean isStorable;
    private Integer page = 1;
    private Integer size = 20;
}
