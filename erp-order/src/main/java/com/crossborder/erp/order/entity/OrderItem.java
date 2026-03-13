package com.crossborder.erp.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单商品明细
 * 一个订单可能包含多个商品
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 订单ID
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 平台商品ID（平台原始商品ID）
     */
    @TableField("platform_product_id")
    private String platformProductId;

    /**
     * 内部商品ID（系统商品库ID）
     */
    @TableField("internal_product_id")
    private String internalProductId;

    /**
     * 平台商品SKU
     */
    @TableField("platform_sku")
    private String platformSku;

    /**
     * 内部商品SKU
     */
    @TableField("internal_sku")
    private String internalSku;

    /**
     * 商品名称
     */
    @TableField("product_name")
    private String productName;

    /**
     * 商品图片URL
     */
    @TableField("product_image")
    private String productImage;

    /**
     * 商品单价
     */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 商品总金额
     */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 币种
     */
    @TableField("currency_code")
    private String currencyCode;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
