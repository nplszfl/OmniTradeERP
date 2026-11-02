package com.crossborder.inventoryprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 补货建议视图（对齐前端）
 *
 * <p>前端 erp-web/src/api/ai.ts 的 {@code InventoryPrediction} 接口：
 * sku / productName / currentStock / predicted30DaySales /
 * recommendedReplenishment / safetyStock / urgency。</p>
 *
 * <p>⚠️ 与 {@link ReplenishmentSuggestion} 的差异：
 * 后端叫 {@code productSku}，前端叫 {@code sku}；
 * 后端叫 {@code suggestedQuantity}，前端叫 {@code recommendedReplenishment}。
 * 这里做显式映射，避免前端到处写兼容代码。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentSuggestionVO {

    /** 商品SKU */
    private String sku;

    /** 商品名 */
    private String productName;

    /** 当前库存 */
    private Integer currentStock;

    /** 预测 30 天销量 */
    private Integer predicted30DaySales;

    /** 建议补货量 */
    private Integer recommendedReplenishment;

    /** 安全库存 */
    private Integer safetyStock;

    /** 紧急程度（HIGH / MEDIUM / LOW） */
    private String urgency;

    /** 紧急程度中文标签 */
    private String urgencyLabel;

    /** 预计缺货日期 */
    private LocalDate expectedStockoutDate;

    /** 缺货风险 */
    private String stockoutRisk;

    /** 建议补货日期 */
    private LocalDate suggestedReplenishmentDate;

    /** 原因说明 */
    private String reason;

    /** 优先级 */
    private Integer priority;

    /** 日均销量 */
    private BigDecimal dailySales;

    /** 预测准确率 */
    private BigDecimal predictionAccuracy;
}
