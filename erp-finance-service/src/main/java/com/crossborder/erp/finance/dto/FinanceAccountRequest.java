package com.crossborder.erp.finance.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务账户请求对象
 *
 * <p>接收前端 {@code erp-web/src/views/finance/index.vue} 的账户表单：
 * <ul>
 *   <li>{@code accountName} — 后端实体字段为 {@code name}，此处做名称映射</li>
 *   <li>{@code accountType} — 前端小写取值（bank/alipay/paypal/...），入库统一转大写</li>
 *   <li>{@code status} — 前端 1 启用 / 0 停用</li>
 *   <li>{@code balance} — 创建时作为「初始余额」入账；更新时忽略（余额只能通过记账变更）</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class FinanceAccountRequest {

    /** 账户 ID（更新时必填） */
    private Long id;

    /** 账户名称 */
    private String accountName;

    /** 账户编号 */
    private String accountNo;

    /** 账户类型：bank / alipay / paypal / worldfirst / payoneer / other */
    private String accountType;

    /** 银行 / 渠道名称 */
    private String bankName;

    /** 币种 */
    private String currency;

    /** 初始余额 */
    private BigDecimal balance;

    /** 是否默认账户：1 是 / 0 否 */
    private Integer isDefault;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
