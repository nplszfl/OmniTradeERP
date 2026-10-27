package com.crossborder.erp.inventory.vo;

import com.crossborder.erp.inventory.enums.InventoryStatus;
import lombok.Data;

/**
 * 库存查询条件
 */
@Data
public class InventoryQuery {

    /** 仓库 ID */
    private Long warehouseId;

    /** 商品 ID */
    private Long productId;

    /** SKU 编码（模糊匹配） */
    private String skuCode;

    /** 库存状态 */
    private InventoryStatus status;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;
}
