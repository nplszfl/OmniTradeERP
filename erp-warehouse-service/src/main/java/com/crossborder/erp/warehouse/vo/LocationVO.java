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
    /**
     * 状态：1-可用 0-不可用
     *
     * <p>前端 {@code erp-web/src/views/warehouse/index.vue} 用 {@code row.status === 1} 判断
     * 「可用 / 不可用」，{@code erp-web/src/views/inventory/index.vue} 用 {@code l.status === 1}
     * 过滤可用库位，因此这里必须是数字而非领域字符串。</p>
     */
    private Integer status;
    
    private Boolean isPickable;
    private Boolean isStorable;
    
    private String remark;
}
