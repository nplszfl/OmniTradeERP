package com.crossborder.erp.inventory.vo;

import lombok.Data;

/**
 * 批量库存操作项
 */
@Data
public class InventoryBatchOperation {

    /** 商品 ID */
    private Long productId;

    /** 仓库 ID */
    private Long warehouseId;

    /** 数量 */
    private Integer quantity;

    /** 备注 */
    private String remark;
}
