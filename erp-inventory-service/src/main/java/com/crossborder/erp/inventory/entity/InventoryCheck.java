package com.crossborder.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.inventory.enums.CheckStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存盘点单
 */
@Data
@TableName("erp_inventory_check")
public class InventoryCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号 */
    private String checkNo;

    /** 仓库 ID */
    private Long warehouseId;

    /** 盘点状态 */
    private CheckStatus checkStatus;

    /** 盘盈金额 */
    private BigDecimal totalProfit;

    /** 盘亏金额 */
    private BigDecimal totalLoss;

    /** 净盈亏金额 */
    private BigDecimal netProfit;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
