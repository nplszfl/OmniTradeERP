package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月财务统计
 */
@Data
public class FinanceMonthlyStatistics {
    /** 年份 */
    private Integer year;
    /** 月份 */
    private Integer month;
    /** 收入合计 */
    private BigDecimal totalIncome;
    /** 支出合计 */
    private BigDecimal totalExpense;
    /** 利润合计 */
    private BigDecimal totalProfit;
    /** 流水总笔数 */
    private Integer totalRecordCount;
    /** 日均收入 */
    private BigDecimal avgDailyIncome;
    /** 日均支出 */
    private BigDecimal avgDailyExpense;
    /** 分类统计 */
    private List<CategoryStatistics> categoryStatistics;
}
