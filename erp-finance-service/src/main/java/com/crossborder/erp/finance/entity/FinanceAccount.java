package com.crossborder.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.finance.enums.AccountStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务账户
 */
@Data
@TableName("fin_account")
public class FinanceAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账户编号 */
    private String accountNo;

    /** 账户名称 */
    private String name;

    /** 账户编码 */
    private String code;

    /** 账户类型：BANK / ALIPAY / WECHAT / PAYPAL ... */
    private String accountType;

    /** 币种 */
    private String currency;

    /** 当前余额 */
    private BigDecimal balance;

    /** 状态 */
    private AccountStatus status;

    /** 银行名称 */
    private String bankName;

    /** 账号 */
    private String accountNumber;

    /** 是否默认账户：1 是 / 0 否 */
    private Integer isDefault;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
