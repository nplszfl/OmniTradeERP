package com.crossborder.erp.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import lombok.Data;


/**
 * 波次创建DTO
 */
@Data
public class WaveCreateDTO {
    private String waveType;
    private String priority;
    private java.util.List<Long> orderIds;
    private String remark;
}
