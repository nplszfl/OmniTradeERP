package com.crossborder.pricing.service.impl;

import com.crossborder.erp.llm.LLMTokenUsage;
import com.crossborder.pricing.dto.AiSuggestionResult;
import com.crossborder.pricing.entity.AiPricingSuggestion;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.AiPricingSuggestionMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.AiPricingSuggestionGenerator;
import com.crossborder.pricing.service.CompetitorScrapeService;
import com.crossborder.pricing.service.CompetitorScrapeService.CompetitorPriceStats;
import com.crossborder.pricing.service.DynamicPricingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 定价建议生成（实现）
 *
 * <p>📌 <b>本类只做一件事：把「算出来的建议」变成「一条落库的建议」</b>。
 * <ul>
 *   <li><b>数字</b>：全部来自 {@link DynamicPricingService}（确定性规则）→ <b>可复现</b>；</li>
 *   <li><b>原因</b>：交给 {@link PricingReasonGenerator}（LLM 生成；不可用时回落规则模板 + 如实标注）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPricingSuggestionGeneratorImpl implements AiPricingSuggestionGenerator {

    private final ProductMapper productMapper;
    private final AiPricingSuggestionMapper suggestionMapper;
    private final CompetitorScrapeService competitorScrapeService;
    private final DynamicPricingService dynamicPricingService;
    private final PricingReasonGenerator reasonGenerator;
    private final ObjectMapper objectMapper;

    /** 📌 **对外的定价策略名必须如实**：数字是规则算的（不是 LLM），别写成「AI 智能定价」 */
    static final String STRATEGY_RULE_BASED = "多因素规则定价（竞品 + 成本 + 需求；非 LLM）";

    @Override
    public AiSuggestionResult generateForProduct(Long productId) {
        if (productId == null) {
            return null;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品不存在，跳过定价建议：productId={}", productId);
            return null;
        }
        if (product.getCostPrice() == null || product.getSellingPrice() == null) {
            // 📌 **缺成本价/售价就没法算建议** → 如实跳过，不编一个数（规则 19）
            log.warn("商品 {} 缺成本价或售价，跳过定价建议（productCode={}）",
                    productId, product.getProductCode());
            return null;
        }

        // ── 1. 竞品数据（确定性）────────────────────────────────────────
        CompetitorPriceStats stats = competitorScrapeService.getCompetitorPriceStats(productId);
        BigDecimal competitorAvg = stats != null ? stats.getAvgPrice() : null;
        long competitorCount = stats != null && stats.getCompetitorCount() != null
                ? stats.getCompetitorCount() : 0L;

        // ── 2. 建议价（确定性规则，非 LLM）────────────────────────────
        BigDecimal suggestedPrice = dynamicPricingService.calculateOptimalPrice(
                productId, product.getCostPrice(), 0, 0);
        if (suggestedPrice == null) {
            log.warn("商品 {} 算不出建议价，跳过", productId);
            return null;
        }

        BigDecimal currentPrice = product.getSellingPrice();
        BigDecimal expectedProfit = suggestedPrice.subtract(product.getCostPrice())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal confidence = computeConfidence(competitorCount, stats, currentPrice, suggestedPrice);

        // ── 3. 建议原因（LLM 生成；不可用则规则模板 + 如实标注）─────────
        PricingReasonGenerator.Reason reason = reasonGenerator.generate(
                product, suggestedPrice, currentPrice, competitorAvg, competitorCount, expectedProfit);

        // ── 4. 落库 ────────────────────────────────────────────────────
        AiPricingSuggestion suggestion = new AiPricingSuggestion();
        suggestion.setProductId(productId);
        suggestion.setCurrentPrice(currentPrice);
        suggestion.setSuggestedPrice(suggestedPrice);
        suggestion.setConfidenceLevel(confidence);
        suggestion.setReason(reason.text());
        suggestion.setFactors(buildFactors(product, suggestedPrice, currentPrice,
                competitorAvg, competitorCount, expectedProfit, reason));
        suggestion.setApplied(false);
        suggestion.setCreateTime(LocalDateTime.now());
        suggestionMapper.insert(suggestion);

        log.info("✅ 定价建议已生成并落库：productId={} 当前价={} → 建议价={} 置信度={} AI生成={}",
                productId, currentPrice, suggestedPrice, confidence, reason.aiGenerated());

        return AiSuggestionResult.builder()
                .productId(productId)
                .suggestionId(suggestion.getId())
                .currentPrice(currentPrice)
                .suggestedPrice(suggestedPrice)
                .competitorAvg(competitorAvg)
                .expectedProfit(expectedProfit)
                .confidenceLevel(confidence)
                .reason(reason.text())
                .aiGenerated(reason.aiGenerated())
                .aiSkipReason(reason.aiGenerated() ? null : reason.skipReason())
                .usage(reason.usage())
                .build();
    }

    @Override
    public int generateForProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (Long productId : productIds) {
            try {
                if (generateForProduct(productId) != null) {
                    ok++;
                }
            } catch (Exception e) {
                // 📌 单个商品失败**不该让整批停掉** —— 但必须**如实记日志**（别静默吞）
                log.error("商品 {} 生成定价建议失败: {}", productId, e.getMessage());
            }
        }
        return ok;
    }

    /**
     * 置信度（0-100）—— <b>按数据完整度算，不是写死的</b>
     *
     * <p>📌 **为什么不能写死**（规则 56：写死的「▲ 12.4%」也是撒谎）：
     * 竞品数据越少、竞品价格越分散、建议价偏离当前价越多 → 置信度越低。
     * 全部由**真实数据**算出来 → 同样的输入得到同样的值（可复现，规则 203）。
     */
    private BigDecimal computeConfidence(long competitorCount, CompetitorPriceStats stats,
                                         BigDecimal currentPrice, BigDecimal suggestedPrice) {
        // ① 竞品数量：0 个 → 20 分；1 个 → 40；2 个 → 60；≥3 → 80（满分 80，剩下的靠一致性）
        BigDecimal score = competitorCount <= 0 ? new BigDecimal("20")
                : competitorCount == 1 ? new BigDecimal("40")
                : competitorCount == 2 ? new BigDecimal("60")
                : new BigDecimal("80");

        // ② 价格一致性：竞品 max/min 比值越小 → 越一致 → 加分（最多 +20）
        if (stats != null && stats.getMinPrice() != null && stats.getMaxPrice() != null
                && stats.getMinPrice().signum() > 0) {
            BigDecimal spread = stats.getMaxPrice().divide(stats.getMinPrice(), 4, RoundingMode.HALF_UP);
            if (spread.compareTo(new BigDecimal("1.10")) <= 0) {
                score = score.add(new BigDecimal("20"));
            } else if (spread.compareTo(new BigDecimal("1.30")) <= 0) {
                score = score.add(new BigDecimal("10"));
            }
        }

        // ③ 建议价偏离当前价越多 → 越不确定（最多 -20）
        if (currentPrice != null && currentPrice.signum() > 0) {
            BigDecimal deviation = suggestedPrice.subtract(currentPrice)
                    .abs().divide(currentPrice, 4, RoundingMode.HALF_UP);
            if (deviation.compareTo(new BigDecimal("0.30")) > 0) {
                score = score.subtract(new BigDecimal("20"));
            } else if (deviation.compareTo(new BigDecimal("0.15")) > 0) {
                score = score.subtract(new BigDecimal("10"));
            }
        }

        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (score.compareTo(new BigDecimal("100")) > 0) {
            score = new BigDecimal("100");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    /** 影响因子 JSON —— 让人能核对「这条建议是基于什么数据算出来的」 */
    private String buildFactors(Product product, BigDecimal suggestedPrice, BigDecimal currentPrice,
                                BigDecimal competitorAvg, long competitorCount,
                                BigDecimal expectedProfit, PricingReasonGenerator.Reason reason) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("productCode", product.getProductCode());
        factors.put("costPrice", product.getCostPrice());
        factors.put("currentPrice", currentPrice);
        factors.put("suggestedPrice", suggestedPrice);
        factors.put("competitorAvg", competitorAvg);
        factors.put("competitorCount", competitorCount);
        factors.put("expectedProfit", expectedProfit);
        factors.put("strategy", STRATEGY_RULE_BASED);
        // 📌 **AI 有没有参与，必须留痕**（规则 18 / 86）
        factors.put("aiGenerated", reason.aiGenerated());
        if (!reason.aiGenerated()) {
            factors.put("aiSkipReason", reason.skipReason());
        }
        LLMTokenUsage usage = reason.usage();
        if (usage != null) {
            factors.put("usage", Map.of(
                    "promptTokens", String.valueOf(usage.promptTokens()),
                    "completionTokens", String.valueOf(usage.completionTokens()),
                    "totalTokens", String.valueOf(usage.totalTokens())));
        }
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception e) {
            log.warn("影响因子序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
