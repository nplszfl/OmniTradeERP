package com.crossborder.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.inventory.enums.LockStatus;
import com.crossborder.erp.inventory.enums.LockType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存锁定记录
 */
@Data
@TableName("erp_inventory_lock")
public class InventoryLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 库存记录 ID */
    private Long inventoryId;

    /** 商品 ID */
    private Long productId;

    /** 仓库 ID */
    private Long warehouseId;

    /** 锁定数量 */
    private Integer lockQuantity;

    /** 已使用（已释放）数量 */
    private Integer usedQuantity;

    /** 锁定类型 */
    private LockType lockType;

    /** 关联订单号 */
    private String orderNo;

    /** 备注 */
    private String remark;

    /** 锁定状态 */
    private LockStatus lockStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
