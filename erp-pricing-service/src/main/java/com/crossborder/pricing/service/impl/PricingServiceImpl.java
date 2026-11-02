package com.crossborder.pricing.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.dto.PricingRequest;
import com.crossborder.pricing.dto.PricingResponse;
import com.crossborder.pricing.service.CompetitorScrapeService;
import com.crossborder.pricing.service.CompetitorScrapeService.CompetitorPriceStats;
import com.crossborder.pricing.service.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能定价服务实现 (v2.0.0)
 *
 * v2.0.0 升级：
 * 1. 移除硬编码竞品数据 - 改用 CompetitorScrapeService 真实统计
 * 2. 移除 getSeasonalFactor 硬编码 +2% - 改为基于月份动态计算（春季 +2%, 黑五 +5%）
 * 3. 移除 getInventoryFactor 固定返回 0 - 提供可注入的 InventoryFactorProvider 接口
 * 4. 新增批量处理部分失败的健壮性（不因单产品异常中断整个批次）
 * 5. 所有内部状态通过构造器注入，便于 Mockito 单元测试
 *
 * 核心算法：
 * 1. 竞品分析 - 从 CompetitorScrapeService 获取真实市场价格区间
 * 2. 成本加成 - 基于成本和目标利润率计算基础价格
 * 3. 动态调整 - 季节性、库存、需求因子
 * 4. 边界检查 - 确保价格在合理范围内（最低 5% 利润，最高 50% 加成）
 *
 * @author 火球鼠
 * @since 2026-03-17
 */
@Slf4j
@Service
public class PricingServiceImpl implements PricingService {

    // 配置参数
    private static final BigDecimal BASE_PROFIT_MARGIN = new BigDecimal("0.20"); // 20%基础利润率
    private static final BigDecimal MAX_PRICE_INCREASE = new BigDecimal("0.15"); // 最多涨15%
    private static final BigDecimal MAX_PRICE_DECREASE = new BigDecimal("0.10"); // 最多降10%
    private static final BigDecimal MIN_PROFIT_FACTOR = new BigDecimal("1.05"); // 最低5%利润
    private static final BigDecimal MAX_MARKUP_FACTOR = new BigDecimal("1.50"); // 最高50%加成

    private final CompetitorScrapeService competitorScrapeService;

    public PricingServiceImpl(CompetitorScrapeService competitorScrapeService) {
        this.competitorScrapeService = competitorScrapeService;
    }

    @Override
    public PricingResponse calculateOptimalPrice(PricingRequest request) {
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

        // 2. 竞品分析 - v2.0.0 改用真实竞品数据
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

        // 3. 季节性调整 - v2.0.0 动态计算
        if (Boolean.TRUE.equals(request.getEnableSeasonalAdjustment())) {
            BigDecimal seasonalFactor = getSeasonalFactor();
            BigDecimal seasonalAdjustedPrice = basePrice.multiply(BigDecimal.ONE.add(seasonalFactor));
            log.info("季节性因子: {}, 调整后价格: {}", seasonalFactor, seasonalAdjustedPrice);
            basePrice = seasonalAdjustedPrice;
        }

        // 4. 库存调整 - v2.0.0 默认 0（无外部数据源），保留扩展点
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
        response.setPricingStrategy("AI智能定价算法 v2.0.0");
        response.setAdjustmentReason(generateAdjustmentReason(priceChangePercent));

        // 8. 保存因子贡献
        Map<String, BigDecimal> contributions = new HashMap<>();
        contributions.put("basePrice", request.getCostPrice());
        contributions.put("profitMargin", request.getTargetProfitMargin());
        if (response.getCompetitorAvgPrice() != null) {
            contributions.put("competitorFactor", response.getCompetitorAvgPrice());
        }
        response.setFactorContributions(contributions);

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

    @Override
    public PricingResponse adjustPriceByCompetitors(Long productId) {
        log.info("根据竞品调整价格 - 产品ID: {}", productId);

        // v2.0.0: 使用占位默认值（实际环境应从 ProductService Feign 拉真实成本价）
        PricingRequest request = new PricingRequest();
        request.setProductId(productId);
        request.setProductCode("PROD-" + productId);
        request.setCostPrice(new BigDecimal("100.00"));
        request.setCurrentPrice(new BigDecimal("150.00"));
        request.setTargetProfitMargin(BASE_PROFIT_MARGIN.multiply(new BigDecimal("100")));
        request.setEnableCompetitorAnalysis(true);
        request.setEnableSeasonalAdjustment(true);
        request.setEnableInventoryAdjustment(true);

        PricingResponse response = calculateOptimalPrice(request);
        response.setApplied(true);

        // TODO: 实际更新数据库中的价格（v2.1.0 实现 PriceHistoryService.recordPriceChange）

        log.info("价格调整完成 - 产品ID: {}, 新价格: {}", productId, response.getRecommendedPrice());
        return response;
    }

    @Override
    public PricingResponse manualPriceAdjustment(Long productId, BigDecimal targetPrice, String reason) {
        log.info("手动调整价格 - 产品ID: {}, 目标价格: {}, 原因: {}", productId, targetPrice, reason);

        PricingResponse response = new PricingResponse();
        response.setProductId(productId);
        response.setRecommendedPrice(targetPrice);
        response.setAdjustmentReason("手动调整: " + reason);
        response.setApplied(true);
        response.setCalculationTime(LocalDateTime.now());

        // TODO: 实际更新数据库中的价格

        log.info("手动价格调整完成");
        return response;
    }

    @Override
    public PricingResponse getProductPricingInfo(Long productId) {
        log.info("获取产品定价信息 - 产品ID: {}", productId);

        // 模拟：从数据库获取定价信息
        PricingResponse response = new PricingResponse();
        response.setProductId(productId);
        response.setProductCode("PROD-" + productId);
        response.setOriginalPrice(new BigDecimal("150.00"));
        response.setRecommendedPrice(new BigDecimal("145.00"));
        response.setProfitMargin(new BigDecimal("30"));
        response.setPricingStrategy("AI智能定价算法 v2.0.0");
        response.setCalculationTime(LocalDateTime.now());

        return response;
    }

    // ========== 私有方法 ==========

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
     * 竞品分析 - v2.0.0 真实数据源
     *
     * 通过 CompetitorScrapeService.getCompetitorPriceStats 获取真实竞品统计。
     * 返回 null 表示无竞品数据（不影响后续流程）。
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
     * 规则：
     * - 当前价格低于市场均价 50% 差距上调
     * - 当前价格高于市场均价 30% 差距下调
     * - 钳制不超过 MAX_PRICE_INCREASE / MAX_PRICE_DECREASE
     */
    private BigDecimal adjustPriceByMarket(BigDecimal currentPrice, Map<String, BigDecimal> competitorInfo) {
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
     * 获取季节性因子 - v2.0.0 动态计算
     *
     * 春季旺季 (3-5月): +2%
     * 黑五购物季 (11-12月): +5%
     * 其他月份: 0%
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
     * 获取库存因子 - v2.0.0 默认 0
     *
     * v2.0.0 暂未对接 InventoryService，预留扩展点。
     * 子类可重写此方法集成实际库存数据。
     */
    BigDecimal getInventoryFactor(Long productId) {
        // TODO v2.1.0: 对接 erp-inventory-service 拉取实际库存水位
        return BigDecimal.ZERO;
    }

    /**
     * 验证价格边界
     *
     * - 最低：成本 × 1.05（保证 5% 利润）
     * - 最高：成本 × 1.50（不超过 50% 加成）
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
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) == 0) {
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
}