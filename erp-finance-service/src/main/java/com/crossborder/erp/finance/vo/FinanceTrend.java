package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务趋势
 */
@Data
public class FinanceTrend {
    /** 日期标签（MM-DD） */
    private List<String> dates;
    /** 收入序列 */
    private List<BigDecimal> incomes;
    /** 支出序列 */
    private List<BigDecimal> expenses;
    /** 利润序列 */
    private List<BigDecimal> profits;
    /** 收入环比变化率（百分比） */
    private BigDecimal incomeChangeRate;
    /** 支出环比变化率（百分比） */
    private BigDecimal expenseChangeRate;
}
