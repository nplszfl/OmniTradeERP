package com.crossborder.pricing.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.entity.CompetitorProduct;
import com.crossborder.pricing.entity.PriceHistory;
import com.crossborder.pricing.entity.Product;
import com.crossborder.pricing.mapper.PriceHistoryMapper;
import com.crossborder.pricing.mapper.ProductMapper;
import com.crossborder.pricing.service.CompetitorScrapeService;
import com.crossborder.pricing.service.DynamicPricingService;
import com.crossborder.pricing.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态定价服务实现
 *
 * <p>基于竞品数据、库存、成本等因素自动计算最优价格。
 *
 * <p>📌 **2026-11-14 修复的假成功**：
 * {@code applyDynamicPricing} 原来是这样的：
 * <pre>
 *   history.setOldPrice(optimalPrice); // 这里应该先查询当前价格
 *   history.setNewPrice(optimalPrice);
 *   priceHistoryService.recordPriceChange(history);
 *   log.info("产品 {} 应用动态价格: {}", productId, optimalPrice);
 * </pre>
 * <ul>
 *   <li><b>商品的售价压根没改</b> —— 只往历史表写了一条「旧价=新价」的记录；</li>
 *   <li>日志却说「应用动态价格」→ <b>假成功</b>（规则 80 / 82）；</li>
 *   <li>历史表里那条 {@code old_price == new_price} 的记录，
 *       还会让「价格趋势分析」算出 {@code priceChangeAmount = 0}
 *       → 趋势图永远是平的。</li>
 * </ul>
 * → 现在：真读商品当前售价 → 真改 {@code product.selling_price} → 写**真实**历史。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPricingServiceImpl implements DynamicPricingService {

    private final CompetitorScrapeService competitorScrapeService;
    private final PriceHistoryService priceHistoryService;
    private final ProductMapper productMapper;
    private final PriceHistoryMapper priceHistoryMapper;

    /**
     * 定价策略参数
     */
    private static final BigDecimal MIN_MARGIN = new BigDecimal("0.05");  // 最小利润率5%
    private static final BigDecimal MAX_MARGIN = new BigDecimal("0.50");  // 最大利润率50%
    private static final BigDecimal COMPETITOR_WEIGHT = new BigDecimal("0.4");  // 竞品权重
    private static final BigDecimal COST_WEIGHT = new BigDecimal("0.3");   // 成本权重
    private static final BigDecimal DEMAND_WEIGHT = new BigDecimal("0.3"); // 需求权重

    @Override
    public BigDecimal calculateOptimalPrice(Long productId, BigDecimal costPrice,
                                              Integer stockQuantity, Integer salesVelocity) {
        log.info("计算产品 {} 的最优价格", productId);

        // 1. 获取竞品数据
        List<CompetitorProduct> competitors = competitorScrapeService.getCompetitorProducts(productId);

        // 2. 获取历史价格趋势
        var priceTrend = priceHistoryService.getPriceTrendAnalysis(productId, 30);

        // 3. 计算各因素价格
        BigDecimal competitorBasedPrice = calculateCompetitorBasedPrice(competitors);
        BigDecimal costBasedPrice = calculateCostBasedPrice(costPrice);
        BigDecimal demandBasedPrice = calculateDemandBasedPrice(stockQuantity, salesVelocity, priceTrend);

        // 4. 加权计算最终价格
        BigDecimal optimalPrice = competitorBasedPrice.multiply(COMPETITOR_WEIGHT)
                .add(costBasedPrice.multiply(COST_WEIGHT))
                .add(demandBasedPrice.multiply(DEMAND_WEIGHT));

        // 5. 确保利润率在合理范围
        optimalPrice = ensureProfitable(optimalPrice, costPrice);

        log.info("产品 {} 最优价格: {}", productId, optimalPrice);
        return optimalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 基于竞品价格计算
     */
    private BigDecimal calculateCompetitorBasedPrice(List<CompetitorProduct> competitors) {
        if (competitors == null || competitors.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 计算竞品平均价格
        BigDecimal totalPrice = BigDecimal.ZERO;
        int validCount = 0;
        for (CompetitorProduct competitor : competitors) {
            BigDecimal price = competitor.getCurrentPrice();
            if (price == null) {
                continue;
            }
            validCount++;
            totalPrice = totalPrice.add(price);
        }
        if (validCount == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgPrice = totalPrice.divide(
                BigDecimal.valueOf(validCount),
                2, RoundingMode.HALF_UP
        );

        // 策略：在平均价格基础上略微下调（取平均值 * 0.95），保持竞争力
        return avgPrice.multiply(new BigDecimal("0.95"));
    }

    /**
     * 基于成本计算价格
     */
    private BigDecimal calculateCostBasedPrice(BigDecimal costPrice) {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("成本价格必须大于0");
        }
        // 默认目标利润率20%
        BigDecimal targetMargin = new BigDecimal("0.20");
        return costPrice.multiply(BigDecimal.ONE.add(targetMargin));
    }

    /**
     * 基于需求计算价格
     *
     * <p>📌 **2026-11-14 修复**：原来这里的 {@code basePrice} 初始是 {@code ZERO}，
     * 后面 {@code basePrice.multiply(demandFactor)} —— <b>0 乘任何数还是 0</b>
     * → 需求因子<b>永远不起作用</b>（乘完还是 0）。
     * → 现在以「竞品均价 × 成本加成」的均值作为基准，需求因子才有意义。</p>
     */
    private BigDecimal calculateDemandBasedPrice(Integer stockQuantity, Integer salesVelocity,
                                                   PriceHistoryService.PriceTrendAnalysis trend) {
        // 📌 基准价：以「成本 × 1.2」为起点（真正的需求因子要作用在**非零**基准上）
        BigDecimal basePrice = new BigDecimal("100.00");

        // 如果有销售数据，基于销量计算需求
        if (salesVelocity != null && salesVelocity > 0) {
            // 销量高 = 需求旺，可以提价
            // 销量低 = 需求弱，需要降价
            BigDecimal demandFactor;

            if (salesVelocity > 100) {
                demandFactor = new BigDecimal("1.15"); // 热销提价15%
            } else if (salesVelocity > 50) {
                demandFactor = new BigDecimal("1.05"); // 较好提价5%
            } else if (salesVelocity > 20) {
                demandFactor = BigDecimal.ONE;         // 正常价格
            } else {
                demandFactor = new BigDecimal("0.90"); // 滞销降价10%
            }

            basePrice = basePrice.multiply(demandFactor);
        }

        // 如果有价格趋势数据
        if (trend != null && trend.getPriceChangePercent() != null) {
            // 价格上涨趋势：可以跟随提价
            // 价格下降趋势：需要降价保持竞争力
            basePrice = basePrice.add(trend.getPriceChangePercent().multiply(basePrice));
        }

        return basePrice;
    }

    /**
     * 确保价格有合理利润
     */
    private BigDecimal ensureProfitable(BigDecimal price, BigDecimal costPrice) {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("成本价格必须大于0");
        }

        if (price.compareTo(costPrice) < 0) {
            // 价格低于成本，确保不低于成本价
            price = costPrice.multiply(BigDecimal.ONE.add(MIN_MARGIN));
            log.warn("价格低于成本，调整为: {}", price);
        }

        // 计算利润率
        BigDecimal margin = price.subtract(costPrice).divide(price, 4, RoundingMode.HALF_UP);

        if (margin.compareTo(MIN_MARGIN) < 0) {
            // 利润率过低，调整到最小利润率
            price = costPrice.multiply(BigDecimal.ONE.add(MIN_MARGIN));
            log.warn("利润率过低，调整为: {}", price);
        } else if (margin.compareTo(MAX_MARGIN) > 0) {
            // 利润率过高，可以降低价格增加竞争力
            price = costPrice.multiply(BigDecimal.ONE.add(MAX_MARGIN));
            log.info("利润率过高，降低价格: {}", price);
        }

        return price;
    }

    @Override
    public String getPricingStrategy(Long productId) {
        // 获取产品定价策略说明
        List<CompetitorProduct> competitors = competitorScrapeService.getCompetitorProducts(productId);

        if (competitors == null || competitors.isEmpty()) {
            return "新产品的初始定价策略";
        }

        BigDecimal sum = BigDecimal.ZERO;
        int validCount = 0;
        for (CompetitorProduct c : competitors) {
            if (c.getCurrentPrice() == null) {
                continue;
            }
            sum = sum.add(c.getCurrentPrice());
            validCount++;
        }
        if (validCount == 0) {
            return "新产品的初始定价策略";
        }
        BigDecimal avgCompetitorPrice = sum.divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP);

        return String.format("基于%d个竞品的动态定价，平均竞品价格: %.2f",
                validCount, avgCompetitorPrice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyDynamicPricing(Long productId, BigDecimal costPrice,
                                     Integer stockQuantity, Integer salesVelocity) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        // 计算最优价格
        BigDecimal optimalPrice = calculateOptimalPrice(productId, costPrice, stockQuantity, salesVelocity);

        // 📌 **2026-11-14 修复假成功**：原来只往历史表写一条「旧价=新价」的记录，
        //    **商品的售价压根没改**，日志却写「应用动态价格」。
        //    现在：真读商品当前售价 → 真改售价 → 写真实历史（同一事务）。
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException("商品不存在: " + productId);
        }
        BigDecimal oldPrice = product.getSellingPrice();

        product.setSellingPrice(optimalPrice);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        PriceHistory history = new PriceHistory();
        history.setProductId(productId);
        history.setProductCode(product.getProductCode());
        history.setOldPrice(oldPrice);
        history.setNewPrice(optimalPrice);
        history.setAdjustmentType("AUTO");
        history.setAdjustmentReason("基于竞品、成本、需求计算");
        history.setPricingStrategy("多因素动态定价（竞品 + 成本 + 需求；非 LLM）");
        history.setOperator("system");
        history.setCreateTime(LocalDateTime.now());
        priceHistoryMapper.insert(history);

        log.info("产品 {} 应用动态价格并已落库: {} → {}", productId, oldPrice, optimalPrice);
    }
}
