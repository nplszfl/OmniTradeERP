package com.crossborder.pricing.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.dto.PricingRequest;
import com.crossborder.pricing.dto.PricingResponse;
import com.crossborder.pricing.entity.PriceHistory;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.PriceHistoryMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.CompetitorScrapeService;
import com.crossborder.pricing.service.CompetitorScrapeService.CompetitorPriceStats;
import com.crossborder.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能定价服务实现
 *
 * <p>核心算法：
 * <ol>
 *   <li>竞品分析 —— 从 {@link CompetitorScrapeService} 取真实市场价格区间（已落库）；</li>
 *   <li>成本加成 —— 基于成本和目标利润率计算基础价格；</li>
 *   <li>动态调整 —— 季节性、库存、需求因子；</li>
 *   <li>边界检查 —— 确保价格在合理范围内（最低 5% 利润，最高 50% 加成）。</li>
 * </ol>
 *
 * <p>📌 **2026-11-14 修复的 3 个真缺陷**：
 * <ol>
 *   <li><b>假成功</b>：{@code adjustPriceByCompetitors} 原来 {@code setApplied(true)}
 *       却<b>什么都没写</b>（只留了个 {@code TODO: 实际更新数据库中的价格}）
 *       → 定时任务每 30 分钟「调整」一次，商品售价<b>纹丝未动</b>
 *       → 现在真的改 {@code product.selling_price} + 写 {@code price_history}（同一事务）；</li>
 *   <li><b>写死的占位数据</b>：{@code getProductPricingInfo} 返回
 *       {@code new BigDecimal("150.00")} / {@code "145.00"} 等<b>常量</b>
 *       → 现在真查商品表；查不到就<b>如实报「商品不存在」</b>而不是编一个数；</li>
 *   <li><b>写死的成本价</b>：{@code adjustPriceByCompetitors} 里
 *       {@code request.setCostPrice(new BigDecimal("100.00"))} ——
 *       <b>假设所有商品成本都是 100</b> → 现在从商品表读真实成本价。</li>
 * </ol>
 *
 * <p>📌 **不接 LLM** —— 这是**确定性规则**定价，对外策略名必须如实说明（规则 18 / 56）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    /** 📌 **不要写「AI智能定价算法」** —— 那是**谎报事实**（规则 18 / 56）。
     *  实际实现是**确定性规则**（成本 + 利润率 + 季节性 + 库存调整），**不调用任何 LLM**
     *  → 对外字段必须说清楚「这是什么」，否则调用方会以为背后有模型在算。 */
    static final String STRATEGY_RULE_BASED =
            "多因素规则定价（成本 + 利润率 + 季节性 + 库存；非 LLM）";

    /** 占位数据（尚未接入真实数据源）时的说明 —— 让调用方**看得见**这不是真实数据（规则 19 / 43） */
    static final String REASON_PLACEHOLDER_DATA =
            "占位数据：尚未接入商品成本 / 竞品数据源（未落库）";

    // 配置参数
    private static final BigDecimal BASE_PROFIT_MARGIN = new BigDecimal("0.20"); // 20%基础利润率
    private static final BigDecimal MAX_PRICE_INCREASE = new BigDecimal("0.15"); // 最多涨15%
    private static final BigDecimal MAX_PRICE_DECREASE = new BigDecimal("0.10"); // 最多降10%
    private static final BigDecimal MIN_PROFIT_FACTOR = new BigDecimal("1.05"); // 最低5%利润
    private static final BigDecimal MAX_MARKUP_FACTOR = new BigDecimal("1.50"); // 最高50%加成

    private final CompetitorScrapeService competitorScrapeService;
    private final ProductMapper productMapper;
    private final PriceHistoryMapper priceHistoryMapper;

    // =========================================================================
    // 计算
    // =========================================================================

    @Override
    public PricingResponse calculateOptimalPrice(PricingRequest request) {
        if (request == null) {
            throw new BizException("定价请求不能为空");
        }
        log.info("开始计算最优价格 - 产品ID: {}, 产品编码: {}",
                request.getProductId(), request.getProductCode());

        PricingResponse response = new PricingResponse();
        response.setProductId(request.getProductId());
        response.setProductCode(request.getProductCode());
        response.setOriginalPrice(request.getCurrentPrice());
        response.setCalculationTime(LocalDateTime.now());

        // 1. 基于成本和利润率计算基础价格
        BigDecimal basePrice = calculateBasePrice(request.getCostPrice(), request.getTargetProfitMargin());
        log.info("基础价格（成本+利润）: {}", basePrice);

        // 2. 竞品分析 —— 用**库里已落库**的真实竞品数据
        if (Boolean.TRUE.equals(request.getEnableCompetitorAnalysis())) {
            Map<String, BigDecimal> competitorInfo = analyzeCompetitors(request.getProductId());
            if (competitorInfo != null && !competitorInfo.isEmpty()) {
                response.setCompetitorAvgPrice(competitorInfo.get("avg"));
                response.setCompetitorMinPrice(competitorInfo.get("min"));
                response.setCompetitorMaxPrice(competitorInfo.get("max"));

                // 基于竞品调整价格
                BigDecimal marketAdjustedPrice = adjustPriceByMarket(basePrice, competitorInfo);
                log.info("市场调整后价格: {}", marketAdjustedPrice);
                basePrice = marketAdjustedPrice;
            }
        }

        // 3. 季节性调整 —— 按月份动态计算
        if (Boolean.TRUE.equals(request.getEnableSeasonalAdjustment())) {
            BigDecimal seasonalFactor = getSeasonalFactor();
            BigDecimal seasonalAdjustedPrice = basePrice.multiply(BigDecimal.ONE.add(seasonalFactor));
            log.info("季节性因子: {}, 调整后价格: {}", seasonalFactor, seasonalAdjustedPrice);
            basePrice = seasonalAdjustedPrice;
        }

        // 4. 库存调整 —— 默认 0（无外部数据源），保留扩展点
        if (Boolean.TRUE.equals(request.getEnableInventoryAdjustment())) {
            BigDecimal inventoryFactor = getInventoryFactor(request.getProductId());
            if (inventoryFactor != null && inventoryFactor.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal inventoryAdjustedPrice = basePrice.multiply(BigDecimal.ONE.add(inventoryFactor));
                log.info("库存因子: {}, 调整后价格: {}", inventoryFactor, inventoryAdjustedPrice);
                basePrice = inventoryAdjustedPrice;
            }
        }

        // 5. 边界检查
        BigDecimal finalPrice = validatePrice(basePrice, request.getCostPrice());

        // 6. 计算变动
        BigDecimal priceChangePercent = calculatePriceChange(request.getCurrentPrice(), finalPrice);
        BigDecimal profitMargin = calculateProfitMargin(finalPrice, request.getCostPrice());

        // 7. 设置响应
        response.setRecommendedPrice(finalPrice);
        response.setPriceChangePercent(priceChangePercent);
        response.setProfitMargin(profitMargin);
        response.setPricingStrategy(STRATEGY_RULE_BASED);
        response.setAdjustmentReason(generateAdjustmentReason(priceChangePercent));

        // 8. 保存因子贡献
        Map<String, BigDecimal> contributions = new HashMap<>();
        contributions.put("basePrice", request.getCostPrice());
        contributions.put("profitMargin", request.getTargetProfitMargin());
        if (response.getCompetitorAvgPrice() != null) {
            contributions.put("competitorFactor", response.getCompetitorAvgPrice());
        }
        response.setFactorContributions(contributions);

        // 📌 **calculateOptimalPrice 只是「算」** —— 不落库。
        //    落库是 applyDynamicPricing / adjustPriceByCompetitors 的职责。
        response.setApplied(false);

        log.info("最优价格计算完成 - 推荐价格: {}, 预期利润率: {}%",
                finalPrice, profitMargin.multiply(new BigDecimal("100")));
        return response;
    }

    @Override
    public List<PricingResponse> batchCalculateOptimalPrice(List<PricingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.info("批量计算最优价格 - 空集合/空请求，直接返回");
            return List.of();
        }
        log.info("批量计算最优价格 - 数量: {}", requests.size());
        return requests.stream()
                .map(this::calculateOptimalPrice)
                .toList();
    }

    // =========================================================================
    // 落库
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PricingResponse adjustPriceByCompetitors(Long productId) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        log.info("根据竞品调整价格 - 产品ID: {}", productId);

        // 📌 **2026-11-14 修复：不再假设「所有商品成本都是 100」**
        //    （原来是 `request.setCostPrice(new BigDecimal("100.00"))`）。
        //    现在从商品表读真实成本价 / 售价 / 目标利润率。
        Product product = requireProduct(productId);

        PricingRequest request = new PricingRequest();
        request.setProductId(productId);
        request.setProductCode(product.getProductCode());
        request.setCostPrice(product.getCostPrice());
        request.setCurrentPrice(product.getSellingPrice());
        request.setTargetProfitMargin(product.getTargetProfitMargin() != null
                ? product.getTargetProfitMargin()
                : BASE_PROFIT_MARGIN.multiply(new BigDecimal("100")));
        request.setEnableCompetitorAnalysis(true);
        request.setEnableSeasonalAdjustment(true);
        request.setEnableInventoryAdjustment(true);

        PricingResponse response = calculateOptimalPrice(request);

        BigDecimal oldPrice = product.getSellingPrice();
        BigDecimal newPrice = response.getRecommendedPrice();
        if (oldPrice != null && oldPrice.compareTo(newPrice) == 0) {
            // 📌 价格没变 → 就**不是**一次「调整」→ applied=false（规则 80 / 82）
            response.setApplied(false);
            response.setAdjustmentReason("当前价格已是推荐价，未做变更");
            log.info("价格无需调整 - 产品ID: {}, 价格: {}", productId, oldPrice);
            return response;
        }

        // 📌 **真落库**：改商品售价 + 写价格历史（同一事务，要么都成要么都败）
        product.setSellingPrice(newPrice);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        PriceHistory history = new PriceHistory();
        history.setProductId(product.getId());
        history.setProductCode(product.getProductCode());
        history.setOldPrice(oldPrice);
        history.setNewPrice(newPrice);
        history.setPriceChangePercent(response.getPriceChangePercent());
        history.setNewProfitMargin(response.getProfitMargin());
        history.setAdjustmentType("AUTO");
        history.setAdjustmentReason(response.getAdjustmentReason());
        history.setOperator("system");
        history.setCompetitorAvgPrice(response.getCompetitorAvgPrice());
        history.setPricingStrategy(STRATEGY_RULE_BASED);
        history.setCreateTime(LocalDateTime.now());
        priceHistoryMapper.insert(history);

        response.setApplied(true);
        log.info("价格调整完成并已落库 - 产品ID: {}, 原价: {}, 新价: {}",
                productId, oldPrice, newPrice);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PricingResponse manualPriceAdjustment(Long productId, BigDecimal targetPrice, String reason) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        if (targetPrice == null || targetPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("目标价格不能为空且不能为负");
        }
        log.info("手动调整价格 - 产品ID: {}, 目标价格: {}, 原因: {}", productId, targetPrice, reason);

        Product product = requireProduct(productId);

        BigDecimal oldPrice = product.getSellingPrice();

        // 📌 **2026-11-14 修复**：原来这里是 `setApplied(true)` + 一个
        //    `TODO: 实际更新数据库中的价格` → **什么都没写**却告诉调用方「已应用」
        //    = 假成功（规则 80 / 82）。现在真落库。
        product.setSellingPrice(targetPrice);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        PriceHistory history = new PriceHistory();
        history.setProductId(product.getId());
        history.setProductCode(product.getProductCode());
        history.setOldPrice(oldPrice);
        history.setNewPrice(targetPrice);
        history.setPriceChangePercent(calculatePriceChange(oldPrice, targetPrice));
        history.setAdjustmentType("MANUAL");
        history.setAdjustmentReason("手动调整: " + reason);
        history.setOperator("system");
        history.setPricingStrategy(STRATEGY_RULE_BASED);
        history.setCreateTime(LocalDateTime.now());
        priceHistoryMapper.insert(history);

        PricingResponse response = new PricingResponse();
        response.setProductId(productId);
        response.setProductCode(product.getProductCode());
        response.setOriginalPrice(oldPrice);
        response.setRecommendedPrice(targetPrice);
        response.setPriceChangePercent(calculatePriceChange(oldPrice, targetPrice));
        response.setProfitMargin(calculateProfitMargin(targetPrice, product.getCostPrice()));
        response.setPricingStrategy(STRATEGY_RULE_BASED);
        response.setAdjustmentReason("手动调整: " + reason);
        response.setApplied(true);
        response.setCalculationTime(LocalDateTime.now());

        log.info("手动价格调整完成并已落库 - 产品ID: {}, 原价: {}, 新价: {}",
                productId, oldPrice, targetPrice);
        return response;
    }

    // =========================================================================
    // 查询
    // =========================================================================

    @Override
    public PricingResponse getProductPricingInfo(Long productId) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        log.info("获取产品定价信息 - 产品ID: {}", productId);

        // 📌 **2026-11-14 修复**：原来返回的是**写死的常量**
        //    （150.00 / 145.00 / 30%），注释自己都写着「模拟：从数据库获取定价信息」。
        //    → 现在真查商品表；查不到就**如实报「商品不存在」**，而不是编一个数。
        Product product = requireProduct(productId);

        BigDecimal cost = product.getCostPrice();
        BigDecimal selling = product.getSellingPrice();

        PricingResponse response = new PricingResponse();
        response.setProductId(productId);
        response.setProductCode(product.getProductCode());
        response.setOriginalPrice(selling);
        response.setRecommendedPrice(selling);
        response.setProfitMargin(calculateProfitMargin(selling, cost));
        response.setPricingStrategy(STRATEGY_RULE_BASED);
        response.setAdjustmentReason("当前定价信息（未做调整）");
        response.setApplied(false);
        response.setCalculationTime(LocalDateTime.now());
        return response;
    }

    // =========================================================================
    // 私有方法
    // =========================================================================

    /**
     * 基于成本和利润率计算基础价格
     */
    private BigDecimal calculateBasePrice(BigDecimal costPrice, BigDecimal targetProfitMargin) {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("成本价格必须大于0");
        }

        BigDecimal profitMargin = targetProfitMargin != null
                ? targetProfitMargin.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                : BASE_PROFIT_MARGIN;

        return costPrice.multiply(BigDecimal.ONE.add(profitMargin))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 竞品分析 —— 从库里已落库的竞品数据取真实统计。
     *
     * <p>返回 null 表示无竞品数据（不影响后续流程）。</p>
     */
    private Map<String, BigDecimal> analyzeCompetitors(Long productId) {
        if (productId == null) {
            return null;
        }
        try {
            CompetitorPriceStats stats = competitorScrapeService.getCompetitorPriceStats(productId);
            if (stats == null) {
                log.warn("未找到产品 {} 的竞品数据", productId);
                return null;
            }

            Map<String, BigDecimal> info = new HashMap<>();
            info.put("min", stats.getMinPrice());
            info.put("max", stats.getMaxPrice());
            info.put("avg", stats.getAvgPrice());
            return info;
        } catch (Exception e) {
            log.warn("竞品分析失败 - 产品ID: {}, 错误: {}", productId, e.getMessage());
            return null;
        }
    }

    /**
     * 基于市场价格调整
     *
     * <p>规则：
     * <ul>
     *   <li>当前价格低于市场均价 50% 差距上调</li>
     *   <li>当前价格高于市场均价 30% 差距下调</li>
     *   <li>钳制不超过 MAX_PRICE_INCREASE / MAX_PRICE_DECREASE</li>
     * </ul>
     */
    BigDecimal adjustPriceByMarket(BigDecimal currentPrice, Map<String, BigDecimal> competitorInfo) {
        BigDecimal marketAvg = competitorInfo.get("avg");
        if (marketAvg == null) {
            return currentPrice;
        }

        BigDecimal adjusted;
        if (currentPrice.compareTo(marketAvg) < 0) {
            // 低于市场均价 - 上涨 50% 差距
            BigDecimal gap = marketAvg.subtract(currentPrice);
            BigDecimal adjustment = gap.multiply(new BigDecimal("0.5"));
            adjusted = currentPrice.add(adjustment);
        } else if (currentPrice.compareTo(marketAvg) > 0) {
            // 高于市场均价 - 下降 30% 差距
            BigDecimal gap = currentPrice.subtract(marketAvg);
            BigDecimal adjustment = gap.multiply(new BigDecimal("0.3"));
            adjusted = currentPrice.subtract(adjustment);
        } else {
            return currentPrice;
        }

        // 钳制最大涨跌幅
        BigDecimal maxIncrease = currentPrice.multiply(MAX_PRICE_INCREASE);
        BigDecimal maxDecrease = currentPrice.multiply(MAX_PRICE_DECREASE);
        if (adjusted.subtract(currentPrice).compareTo(maxIncrease) > 0) {
            adjusted = currentPrice.add(maxIncrease);
        } else if (currentPrice.subtract(adjusted).compareTo(maxDecrease) > 0) {
            adjusted = currentPrice.subtract(maxDecrease);
        }

        return adjusted.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取季节性因子 —— 按月份动态计算
     *
     * <p>春季旺季 (3-5月): +2%
     * 黑五购物季 (11-12月): +5%
     * 其他月份: 0%</p>
     */
    BigDecimal getSeasonalFactor() {
        int month = LocalDateTime.now().getMonthValue();
        if (month >= 3 && month <= 5) {
            return new BigDecimal("0.02");
        }
        if (month == 11 || month == 12) {
            return new BigDecimal("0.05");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取库存因子 —— 默认 0
     *
     * <p>暂未对接 InventoryService，预留扩展点。子类可重写此方法集成实际库存数据。</p>
     */
    BigDecimal getInventoryFactor(Long productId) {
        return BigDecimal.ZERO;
    }

    /**
     * 验证价格边界
     *
     * <ul>
     *   <li>最低：成本 × 1.05（保证 5% 利润）</li>
     *   <li>最高：成本 × 1.50（不超过 50% 加成）</li>
     * </ul>
     */
    BigDecimal validatePrice(BigDecimal price, BigDecimal costPrice) {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }

        BigDecimal minPrice = costPrice.multiply(MIN_PROFIT_FACTOR);
        if (price.compareTo(minPrice) < 0) {
            price = minPrice;
        }

        BigDecimal maxPrice = costPrice.multiply(MAX_MARKUP_FACTOR);
        if (price.compareTo(maxPrice) > 0) {
            price = maxPrice;
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算价格变动百分比
     */
    private BigDecimal calculatePriceChange(BigDecimal originalPrice, BigDecimal newPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return newPrice.subtract(originalPrice)
                .divide(originalPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算利润率
     */
    private BigDecimal calculateProfitMargin(BigDecimal sellingPrice, BigDecimal costPrice) {
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) == 0
                || costPrice == null || costPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return sellingPrice.subtract(costPrice)
                .divide(sellingPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 生成调整原因
     */
    private String generateAdjustmentReason(BigDecimal priceChangePercent) {
        if (priceChangePercent == null || priceChangePercent.compareTo(BigDecimal.ZERO) == 0) {
            return "价格无需调整";
        }

        if (priceChangePercent.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("价格上涨%.2f%%，基于竞品分析和市场需求", priceChangePercent);
        }

        return String.format("价格下降%.2f%%，基于市场竞争力分析", priceChangePercent.abs());
    }

    /** 取商品（查不到就**如实报错**，不编数据）。 */
    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException("商品不存在: " + productId);
        }
        return product;
    }
}
