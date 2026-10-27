package com.crossborder.erp.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务账户视图对象
 *
 * <p>字段名与前端契约 {@code erp-web/src/api/finance.ts} 的 {@code FinanceAccount} 保持一致：
 * <ul>
 *   <li>{@code accountName} — 后端实体字段为 {@code name}，此处做名称映射</li>
 *   <li>{@code status} — 前端用数字 1/0 表示启用/停用，后端实体用 {@code AccountStatus} 枚举</li>
 *   <li>{@code isDefault} — 是否默认账户，1 是 / 0 否</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Data
public class FinanceAccountVO {

    /** 账户 ID */
    private Long id;

    /** 账号（银行账号 / 支付渠道账号，对应实体 accountNumber） */
    private String accountNo;

    /** 系统账户编号（对应实体 accountNo，服务端自动生成） */
    private String accountCode;

    /** 账户名称 */
    private String accountName;

    /** 账户类型：bank / alipay / paypal / worldfirst / payoneer / other */
    private String accountType;

    /** 银行 / 渠道名称 */
    private String bankName;

    /** 币种 */
    private String currency;

    /** 当前余额 */
    private BigDecimal balance;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 是否默认账户：1 是 / 0 否 */
    private Integer isDefault;

    /** 创建时间 */
    private LocalDateTime createTime;
}
