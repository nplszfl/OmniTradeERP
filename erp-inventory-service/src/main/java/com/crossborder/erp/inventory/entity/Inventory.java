package com.crossborder.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.inventory.enums.InventoryStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存记录
 */
@Data
@TableName("erp_inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** 仓库 ID */
    private Long warehouseId;

    /** 库位 ID */
    private Long locationId;

    /** SKU 编码 */
    private String skuCode;

    /** 总数量 */
    private Integer quantity;

    /** 可用数量 */
    private Integer availableQuantity;

    /** 锁定数量 */
    private Integer lockedQuantity;

    /** 安全库存 */
    private Integer safetyStock;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 状态 */
    private InventoryStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
