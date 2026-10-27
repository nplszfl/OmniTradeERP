package com.crossborder.erp.finance.dto;

import com.crossborder.erp.finance.enums.FinanceType;
import com.crossborder.erp.finance.enums.RecordStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 财务流水查询 DTO
 */
@Data
public class FinanceRecordQuery {
    /** 账户 ID */
    private Long accountId;
    /** 流水类型 */
    private FinanceType type;
    /** 分类 ID */
    private Long categoryId;
    /** 记录状态 */
    private RecordStatus status;
    /** 起始时间 */
    private LocalDateTime startTime;
    /** 截止时间 */
    private LocalDateTime endTime;
    /** 关键词：同时匹配流水号 / 订单号 / 备注 */
    private String keyword;
    /** 页码，从 1 开始 */
    private Integer page = 1;
    /** 每页条数 */
    private Integer size = 20;
}
