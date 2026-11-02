package com.crossborder.pricing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格历史记录
 */
@Data
@TableName("price_history")
public class PriceHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 原价格 */
    private BigDecimal oldPrice;

    /** 新价格 */
    private BigDecimal newPrice;

    /** 价格变动（%） */
    private BigDecimal priceChangePercent;

    /** 原利润率（%） */
    private BigDecimal oldProfitMargin;

    /** 新利润率（%） */
    private BigDecimal newProfitMargin;

    /** 调整原因 */
    private String adjustmentReason;

    /** 调整方式（AUTO-自动，MANUAL-手动） */
    private String adjustmentType;

    /** 操作人 */
    private String operator;

    /** 竞品平均价格 */
    private BigDecimal competitorAvgPrice;

    /** 季节性因子 */
    private BigDecimal seasonalFactor;

    /** 库存因子 */
    private BigDecimal inventoryFactor;

    /** 需求因子 */
    private BigDecimal demandFactor;

    /** 定价策略（如「跟随竞品」「提升毛利」「清理库存」） */
    private String pricingStrategy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
