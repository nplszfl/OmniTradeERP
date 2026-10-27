package com.crossborder.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.finance.enums.FinanceType;
import com.crossborder.erp.finance.enums.RecordStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务流水记录
 */
@Data
@TableName("fin_record")
public class FinanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号 */
    private String recordNo;

    /** 账户 ID */
    private Long accountId;

    /** 流水类型 */
    private FinanceType type;

    /** 金额 */
    private BigDecimal amount;

    /** 分类 ID */
    private Long categoryId;

    /** 关联订单号 */
    private String orderNo;

    /** 记账前余额 */
    private BigDecimal beforeBalance;

    /** 记账后余额 */
    private BigDecimal afterBalance;

    /** 状态 */
    private RecordStatus status;

    /** 作废时间 */
    private LocalDateTime voidTime;

    /** 作废原因 */
    private String voidReason;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
