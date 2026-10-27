package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 分类统计
 */
@Data
public class CategoryStatistics {
    /** 分类 ID */
    private Long categoryId;
    /** 分类名称 */
    private String categoryName;
    /** 收入合计 */
    private BigDecimal income;
    /** 支出合计 */
    private BigDecimal expense;
    /** 净额 */
    private BigDecimal netAmount;
    /** 流水笔数 */
    private Integer recordCount;
}
