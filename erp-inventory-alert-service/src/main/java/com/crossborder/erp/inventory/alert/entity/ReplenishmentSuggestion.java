package com.crossborder.erp.inventory.alert.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补货建议
 */
@Data
@TableName("t_replenishment_suggestion")
public class ReplenishmentSuggestion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的预警ID
     */
    private Long alertId;

    /**
     * 预警规则ID
     */
    private Long ruleId;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * SKU
     */
    private String sku;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 仓库ID
     */
    private String warehouseId;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 当前库存
     */
    private Integer currentStock;

    /**
     * 安全库存
     */
    private Integer safeStock;

    /**
     * 建议补货数量
     */
    private Integer suggestedQuantity;

    /**
     * 建议补货日期
     */
    private LocalDateTime suggestedDate;

    /**
     * 预计到货日期
     */
    private LocalDateTime expectedArrivalDate;

    /**
     * 采购单价
     */
    private BigDecimal purchasePrice;

    /**
     * 采购币种
     */
    private String currency;

    /**
     * 预估采购金额
     */
    private BigDecimal estimatedAmount;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 状态：PENDING(待确认)、CONFIRMED(已确认)、CANCELLED(已取消)、PURCHASED(已采购)
     */
    private String status;

    /**
     * 紧急程度：HIGH(高)、MEDIUM(中)、LOW(低)
     * 由服务层根据当前库存与安全库存的缺口比例计算
     */
    private String urgencyLevel;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 创建人名称
     */
    private String creatorName;

    /**
     * 确认人ID
     */
    private Long confirmerId;

    /**
     * 确认人名称
     */
    private String confirmerName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private LocalDateTime confirmedTime;
}