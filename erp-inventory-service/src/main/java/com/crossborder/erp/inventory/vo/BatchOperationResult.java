package com.crossborder.erp.inventory.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量操作结果
 */
@Data
public class BatchOperationResult {

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failedCount;

    /** 成功明细 */
    private List<String> successList;

    /** 失败明细 */
    private List<String> failedList;
}
