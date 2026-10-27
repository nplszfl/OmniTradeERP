package com.crossborder.erp.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务流水请求对象
 *
 * <p>接收前端 {@code erp-web/src/views/finance/index.vue} 的流水表单：
 * {@code type} 使用小写取值（income / expense / refund）。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class FinanceFlowRequest {

    /** 流水 ID（更新时必填） */
    private Long id;

    /** 流水类型：income / expense / refund */
    private String type;

    /** 金额 */
    private BigDecimal amount;

    /** 账户 ID */
    private Long accountId;

    /** 分类 ID */
    private Long categoryId;

    /** 关联订单号 */
    private String orderNo;

    /** 备注 */
    private String remark;
}
