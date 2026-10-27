package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户统计
 */
@Data
public class AccountStatistics {
    /** 账户 ID */
    private Long accountId;
    /** 账户名称 */
    private String accountName;
    /** 当前余额 */
    private BigDecimal currentBalance;
    /** 期间收入 */
    private BigDecimal income;
    /** 期间支出 */
    private BigDecimal expense;
    /** 期间净额 */
    private BigDecimal netAmount;
    /** 流水笔数 */
    private Integer recordCount;
}
