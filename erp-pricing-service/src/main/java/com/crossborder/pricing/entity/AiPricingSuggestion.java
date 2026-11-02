package com.crossborder.pricing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 定价建议实体
 *
 * <p>对应表 {@code ai_pricing_suggestion}（见 database/pricing-service.sql）。</p>
 * <p>前端 {@code GET /api/pricing/tasks} 返回的就是这个表的记录。</p>
 */
@Data
@TableName("ai_pricing_suggestion")
public class AiPricingSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品ID */
    private Long productId;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 建议价格 */
    private BigDecimal suggestedPrice;

    /** 置信度（0-100） */
    private BigDecimal confidenceLevel;

    /** 建议原因 */
    private String reason;

    /** 影响因子（JSON） */
    private String factors;

    /** 是否已应用 */
    private Boolean applied;

    /** 应用时间 */
    private LocalDateTime applyTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 产品编码（非表字段，从 product 表关联带出，给前端显示） */
    @TableField(exist = false)
    private String productCode;

    /** 产品名称（非表字段） */
    @TableField(exist = false)
    private String productName;

    /** SKU（非表字段） */
    @TableField(exist = false)
    private String sku;

    /** 定价策略（非表字段） */
    @TableField(exist = false)
    private String pricingStrategy;
}
