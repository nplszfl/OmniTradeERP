package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日财务统计
 */
@Data
public class FinanceDailyStatistics {
    /** 统计时间 */
    private LocalDateTime date;
    /** 收入 */
    private BigDecimal income;
    /** 支出 */
    private BigDecimal expense;
    /** 利润 */
    private BigDecimal profit;
    /** 流水笔数 */
    private Integer recordCount;
}
