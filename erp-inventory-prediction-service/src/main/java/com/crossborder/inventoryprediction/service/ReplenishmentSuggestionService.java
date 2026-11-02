package com.crossborder.inventoryprediction.service;

import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestion;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestionVO;

import java.util.List;

/**
 * 库存补货建议服务
 */
public interface ReplenishmentSuggestionService {

    /**
     * 生成补货建议
     * @param productId 产品ID
     * @return 补货建议列表
     */
    List<ReplenishmentSuggestion> generateSuggestions(Long productId);

    /**
     * 批量生成补货建议
     * @param productIds 产品ID列表
     * @return 补货建议列表
     */
    List<ReplenishmentSuggestion> batchGenerateSuggestions(List<Long> productIds);

    /**
     * 获取紧急补货建议（库存低于安全库存）
     * @return 紧急补货建议
     */
    List<ReplenishmentSuggestion> getUrgentReplenishments();

    /**
     * 查询补货建议列表（前端视图）
     *
     * <p>前端 {@code inventoryPredictApi.listSuggestions()} 调用的接口，
     * 返回字段已对齐前端消费方式（sku / predicted30DaySales / recommendedReplenishment ...）。</p>
     *
     * @param productIds 需要查询的产品ID；为空则查询全部待补货产品
     * @param limit      返回条数上限
     * @return 补货建议列表（按紧急程度倒序）
     */
    List<ReplenishmentSuggestionVO> listSuggestions(List<Long> productIds, int limit);

    /**
     * 计算最优补货量
     * @param productId 产品ID
     * @param targetDate 目标日期
     * @return 最优补货量
     */
    Integer calculateOptimalReplenishmentQuantity(Long productId, java.time.LocalDate targetDate);
}