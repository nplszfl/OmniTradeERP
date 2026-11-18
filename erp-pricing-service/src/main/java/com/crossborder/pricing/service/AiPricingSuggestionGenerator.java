package com.crossborder.pricing.service;

import com.crossborder.pricing.dto.AiSuggestionResult;

import java.util.List;

/**
 * AI 定价建议生成（把建议**落库**到 {@code ai_pricing_suggestion}）
 *
 * <p>📌 <b>为什么需要这个服务（2026-11-18 实测出的功能缺口）</b>：
 * {@code ai_pricing_suggestion} 表、实体、mapper、service、controller、前端页面<b>全都在</b>，
 * 但**全仓没有任何一处往这张表写数据** → 活库里这张表 <b>0 行</b>
 * → 前端「AI 定价建议」页（{@code /pricing/tasks}、{@code /pricing/suggest/{sku}}）
 * <b>永远返回空列表</b> —— 页面看着正常，数据是空的（规则 80 / 86 的形态）。
 * → 这个服务补的就是那条<b>缺失的写路径</b>。
 *
 * <p>📌 <b>AI 边界（说清楚，不对外谎报，规则 18 / 56）</b>：
 * <ul>
 *   <li><b>数字</b>（建议价 / 预期利润 / 置信度）= <b>确定性规则</b>算的
 *       （{@code DynamicPricingService.calculateOptimalPrice}）→ <b>不调用 LLM</b>、<b>可复现</b>；</li>
 *   <li><b>建议原因</b> = <b>LLM 生成</b>（把因子翻译成一句人话）→ 这是 LLM 的天然落点；</li>
 *   <li>LLM 不可用时：原因回落到<b>规则模板</b>，并**如实标注「AI 未启用/调用失败」**
 *       → 绝不假装是 AI 写的（规则 18 / 19）。</li>
 * </ul>
 */
public interface AiPricingSuggestionGenerator {

    /**
     * 为单个商品生成定价建议并落库
     *
     * @param productId 商品 ID
     * @return 生成结果（含是否由 AI 生成、token 用量）；失败时返回 null（不抛给定时任务）
     */
    AiSuggestionResult generateForProduct(Long productId);

    /**
     * 为一批商品生成定价建议
     *
     * @param productIds 商品 ID 列表
     * @return 成功生成的条数
     */
    int generateForProducts(List<Long> productIds);
}
