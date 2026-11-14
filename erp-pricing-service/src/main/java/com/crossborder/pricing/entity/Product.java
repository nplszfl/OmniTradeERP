package com.crossborder.pricing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品实体（对应 {@code erp_pricing.product} 表）
 *
 * <p>📌 **2026-11-14 补齐字段**：DDL 里有但实体原来缺的这几列，
 * 导致 MyBatis-Plus 读写时**看不到**它们：
 * <ul>
 *   <li>{@code target_profit_margin} —— 目标利润率（%），
 *       <b>定价计算要用</b>（原来在服务里被写死成 20%）；</li>
 *   <li>{@code enable_competitor_analysis} / {@code enable_seasonal_adjustment} /
 *       {@code enable_inventory_adjustment} —— 三个开关位（默认全开）；</li>
 *   <li>{@code min_price} / {@code max_price} —— 商品自定义的售价上下限。</li>
 * </ul>
 * 这些列在 DDL 里都有默认值，所以补齐字段**不会**破坏现有写入
 * （MyBatis-Plus 只在字段非 null 时才把该列放进 INSERT）。
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 当前售价 */
    private BigDecimal sellingPrice;

    /** SKU */
    private String sku;

    /** 平台ID（Amazon、eBay、Shopee等） */
    private Long platformId;

    /** 平台产品ID */
    private String platformProductId;

    /** 状态（1-上架，0-下架） */
    private Integer status;

    /** 目标利润率（%）—— DDL 默认 20.00 */
    private BigDecimal targetProfitMargin;

    /** 是否启用竞品分析 —— DDL 默认 1 */
    private Boolean enableCompetitorAnalysis;

    /** 是否启用季节性调整 —— DDL 默认 1 */
    private Boolean enableSeasonalAdjustment;

    /** 是否启用库存调整 —— DDL 默认 1 */
    private Boolean enableInventoryAdjustment;

    /** 最低售价（可选，商品级下限） */
    private BigDecimal minPrice;

    /** 最高售价（可选，商品级上限） */
    private BigDecimal maxPrice;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
