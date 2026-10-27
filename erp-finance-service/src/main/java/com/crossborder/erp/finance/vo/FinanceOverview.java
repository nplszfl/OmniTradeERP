package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务总览
 */
@Data
public class FinanceOverview {
    /** 收入合计 */
    private BigDecimal totalIncome;
    /** 支出合计 */
    private BigDecimal totalExpense;
    /** 利润 */
    private BigDecimal profit;
    /** 利润率（百分比） */
    private BigDecimal profitRate;
    /** 账户余额合计 */
    private BigDecimal accountBalance;
    /** 流水笔数 */
    private Integer recordCount;
}
