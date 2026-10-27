package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务流水视图对象
 *
 * <p>字段名与前端契约 {@code erp-web/src/api/finance.ts} 的 {@code FinanceFlow} 保持一致：
 * <ul>
 *   <li>{@code type} — income / expense / refund（小写，与前端下拉框取值一致）</li>
 *   <li>{@code accountName} / {@code categoryName} — 由账户、分类表关联带出，列表不返回冗余主键</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class FinanceFlowVO {

    /** 流水 ID */
    private Long id;

    /** 流水号 */
    private String recordNo;

    /** 流水类型：income / expense / refund */
    private String type;

    /** 金额 */
    private BigDecimal amount;

    /** 账户 ID */
    private Long accountId;

    /** 账户名称 */
    private String accountName;

    /** 分类 ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 关联订单号 */
    private String orderNo;

    /** 备注 */
    private String remark;

    /** 状态：pending / completed / voided */
    private String status;

    /** 记账前余额 */
    private BigDecimal beforeBalance;

    /** 记账后余额 */
    private BigDecimal afterBalance;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 创建人 */
    private String createBy;
}
