package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量记账结果
 */
@Data
public class BatchFinanceResult {
    /** 成功笔数 */
    private Integer successCount;
    /** 失败笔数 */
    private Integer failedCount;
    /** 成功的流水 ID */
    private List<Long> successIds;
    /** 失败原因 */
    private List<String> failedReasons;
}
