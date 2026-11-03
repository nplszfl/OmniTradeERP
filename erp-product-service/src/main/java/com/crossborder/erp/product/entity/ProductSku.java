package com.crossborder.erp.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品SKU实体
 *
 * <p>📌 <b>字段与 {@code t_product_sku} 表结构一一对应</b>：
 * 之前实体用的是 {@code sku / name / barCode / attributes / weight / length /
 * width / height / stockQuantity / safetyStock}，
 * 而库里实际是 {@code sku_code / sku_name / specs / ...}，
 * 且<b>根本没有</b> bar_code / weight / length / width / height /
 * stock_quantity / safety_stock 这些列。
 * MyBatis-Plus 默认驼峰转下划线映射 →
 * 实体字段 {@code name} 会映射到列 {@code name}，该列不存在 →
 * 一旦有人调用这个 Mapper，SQL 直接报
 * {@code Unknown column 'name' in 'field list'}。
 * 另外 {@code sku_code} 是 NOT NULL 且无默认值 → INSERT 也会失败。</p>
 *
 * <p>📌 以 <b>DDL + 种子数据</b>为准（database/init.sql、database/seed-data.sql），
 * 它们是线上真正生效的结构。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Data
@TableName("t_product_sku")
public class ProductSku {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 商品ID
     */
    @TableField("product_id")
    private Long productId;

    /**
     * SKU编码
     */
    @TableField("sku_code")
    private String skuCode;

    /**
     * SKU名称
     */
    @TableField("sku_name")
    private String skuName;

    /**
     * 规格属性（JSON格式，如：{"color":"红色","size":"XL"}）
     */
    @TableField("specs")
    private String specs;

    /**
     * 采购成本
     */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /**
     * 销售价格
     */
    @TableField("sale_price")
    private BigDecimal salePrice;

    /**
     * 状态（0禁用 1启用）
     */
    @TableField("status")
    private Integer status;

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
    @TableField("deleted")
    private Integer deleted;
}
