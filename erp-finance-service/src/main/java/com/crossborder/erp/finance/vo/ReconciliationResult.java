package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账结果
 */
@Data
public class ReconciliationResult {
    /** 账户 ID */
    private Long accountId;
    /** 参与对账的流水笔数 */
    private Integer recordCount;
    /** 账户账面余额 */
    private BigDecimal expectedBalance;
    /** 按流水计算的余额 */
    private BigDecimal calculatedBalance;
    /** 是否平账 */
    private Boolean balanced;
    /** 差异说明 */
    private List<String> errors;
    /** 对账时间 */
    private LocalDateTime reconcileTime;
}
