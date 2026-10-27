package com.crossborder.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 财务统计快照
 */
@Data
@TableName("fin_statistics")
public class FinanceStatistics {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 统计维度：DAILY / MONTHLY */
    private String dimension;

    /** 账户 ID */
    private Long accountId;

    /** 收入合计 */
    private BigDecimal totalIncome;

    /** 支出合计 */
    private BigDecimal totalExpense;

    /** 利润 */
    private BigDecimal profit;

    /** 流水笔数 */
    private Integer recordCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
