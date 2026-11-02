package com.crossborder.pricing.service;

import com.crossborder.pricing.dto.PricingSuggestionVO;

import java.util.List;

/**
 * 定价建议服务（面向前端）
 *
 * <p>前端 erp-web/src/views/ai/Pricing.vue 调的 4 个接口此前后端完全没有 → 404，
 * 页面一直靠 mock 兜底撑着。现在补齐：</p>
 * <ul>
 *   <li>{@code GET  /api/pricing/tasks}          → 建议列表</li>
 *   <li>{@code GET  /api/pricing/suggest/{sku}}  → 单个 SKU 的建议</li>
 *   <li>{@code GET  /api/pricing/history/{sku}}  → 价格变更历史</li>
 *   <li>{@code POST /api/pricing/apply/{sku}}    → 采纳建议价</li>
 * </ul>
 */
public interface PricingSuggestionService {

    /**
     * 查询建议列表
     *
     * @param limit 返回条数上限
     * @return 建议列表（按生成时间倒序）
     */
    List<PricingSuggestionVO> listSuggestions(int limit);

    /**
     * 按 SKU 查询建议
     *
     * @param sku 商品SKU
     * @return 建议详情
     */
    PricingSuggestionVO getSuggestionBySku(String sku);

    /**
     * 按 SKU 查询价格变更历史
     *
     * @param sku  商品SKU
     * @param days 回溯天数
     * @return 历史记录（倒序）
     */
    List<PricingSuggestionVO> getHistoryBySku(String sku, int days);

    /**
     * 采纳建议价
     *
     * @param sku       商品SKU
     * @param finalPrice 最终成交价
     * @return 采纳后的建议记录
     */
    PricingSuggestionVO applySuggestion(String sku, java.math.BigDecimal finalPrice);
}
