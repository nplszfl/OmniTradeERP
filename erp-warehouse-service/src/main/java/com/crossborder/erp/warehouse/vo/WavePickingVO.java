package com.crossborder.erp.warehouse.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;


/**
 * 波次拣货VO
 */
@Data
public class WavePickingVO {
    private Long id;
    private String waveNo;
    private String waveType;
    private String waveTypeName;
    private String waveStatus;
    private String waveStatusName;
    
    private Integer orderCount;
    private Integer skuCount;
    private Integer totalQuantity;
    private Integer pickedQuantity;
    private BigDecimal progress;
    
    private String priority;
    private String priorityName;
    private String assignUser;
    private LocalDateTime assignTime;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    
    // 明细
    private List<WavePickingDetailVO> details;
}
