package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.CompetitorAnalysisDTO;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.CompetitorInfo;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.CompetitorShare;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.CompetitorTrend;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.MarketShare;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.PriceComparison;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.SalesComparison;
import com.crossborder.analytics.dto.CompetitorAnalysisDTO.TrendData;
import com.crossborder.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 竞品分析服务
 *
 * <p>⚠️ 之前这里是整段编造的数据：写死的 "Competitor A Store" 等 7 个假竞品、
 * 写死的 42.99 我方价格、写死的 380 销量、随机生成的周趋势。
 * 现在改成查 {@code erp_pricing.competitor_product} 里定价服务真实抓取的竞品数据；
 * 库里没有对应 SKU 时返回空结果，不编。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorAnalysisService {

    private final AnalyticsRepository repository;

    @Value("${analytics.competitor.update-interval:3600000}")
    private long updateInterval;

    @Value("${analytics.competitor.max-competitors:50}")
    private int maxCompetitors;

    /**
     * 获取竞品分析
     */
    public CompetitorAnalysisDTO getCompetitorAnalysis(String productId, String analysisType) {
        log.info("Generating competitor analysis for product: {}, type: {}", productId, analysisType);

        List<CompetitorInfo> competitors = getCompetitors(productId);

        PriceComparison priceComparison = calculatePriceComparison(competitors);
        SalesComparison salesComparison = calculateSalesComparison(productId, competitors);
        MarketShare marketShare = calculateMarketShare(productId, competitors);

        // ⚠️ 竞品的历史趋势需要竞品价格的时间序列。
        //    competitor_product 表只存最新快照（scrape_time），
        //    没有历史价表，所以趋势只能返回空列表而不是编 4 周随机值。
        List<TrendData> trends = new ArrayList<>();

        List<String> recommendations = generateRecommendations(priceComparison, salesComparison);

        return CompetitorAnalysisDTO.builder()
                .analysisType(analysisType)
                .productId(productId)
                .productName(productId)
                .competitorCount(competitors.size())
                .competitors(competitors)
                .priceComparison(priceComparison)
                .salesComparison(salesComparison)
                .marketShare(marketShare)
                .trends(trends)
                .recommendations(recommendations)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * 获取竞品列表
     */
    private List<CompetitorInfo> getCompetitors(String productId) {
        List<CompetitorInfo> competitors = new ArrayList<>();

        BigDecimal ourPrice = resolveOurPrice(productId);
        List<AnalyticsRepository.CompetitorRow> rows =
                repository.getCompetitors(productId, maxCompetitors);

        int idx = 1;
        for (AnalyticsRepository.CompetitorRow r : rows) {
            BigDecimal priceDiff = r.price().subtract(ourPrice);
            String pricePosition = priceDiff.compareTo(BigDecimal.ZERO) > 0 ? "HIGHER"
                    : priceDiff.compareTo(BigDecimal.ZERO) < 0 ? "LOWER" : "EQUAL";

            competitors.add(CompetitorInfo.builder()
                    .competitorId("C" + String.format("%03d", idx++))
                    .competitorName(r.competitorName())
                    .platform(r.platform())
                    .price(r.price())
                    .salesCount((int) r.salesVolume())
                    .revenue(r.price().multiply(BigDecimal.valueOf(r.salesVolume()))
                            .setScale(2, RoundingMode.HALF_UP))
                    .rating(r.rating())
                    .reviewCount((int) r.reviewCount())
                    .url(null)
                    .priceDiff(priceDiff.setScale(2, RoundingMode.HALF_UP))
                    .pricePosition(pricePosition)
                    .build());
        }
        return competitors;
    }

    /**
     * 我方当前售价
     *
     * <p>库里查不到时返回 0（而不是编 42.99）。</p>
     */
    private BigDecimal resolveOurPrice(String productId) {
        AnalyticsRepository.ProductPrice p = repository.getProductPrice(productId);
        return p == null ? BigDecimal.ZERO : p.salePrice();
    }

    /**
     * 计算价格对比
     */
    private PriceComparison calculatePriceComparison(List<CompetitorInfo> competitors) {
        BigDecimal ourPrice = resolveOurPriceFrom(competitors);

        if (competitors.isEmpty()) {
            return PriceComparison.builder()
                    .ourPrice(ourPrice)
                    .avgCompetitorPrice(BigDecimal.ZERO)
                    .lowestCompetitorPrice(BigDecimal.ZERO)
                    .highestCompetitorPrice(BigDecimal.ZERO)
                    .pricePosition("UNKNOWN")
                    .priceDiffPercent(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal avgPrice = competitors.stream()
                .map(CompetitorInfo::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(competitors.size()), 2, RoundingMode.HALF_UP);

        BigDecimal lowestPrice = competitors.stream()
                .map(CompetitorInfo::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal highestPrice = competitors.stream()
                .map(CompetitorInfo::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        String pricePosition;
        if (ourPrice.signum() == 0) {
            pricePosition = "UNKNOWN";
        } else if (ourPrice.compareTo(avgPrice) < 0) {
            pricePosition = "COMPETITIVE";
        } else if (ourPrice.compareTo(avgPrice) > 0) {
            pricePosition = "PREMIUM";
        } else {
            pricePosition = "BUDGET";
        }

        BigDecimal priceDiffPercent = ourPrice.signum() == 0 ? BigDecimal.ZERO
                : avgPrice.subtract(ourPrice)
                        .divide(ourPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        return PriceComparison.builder()
                .ourPrice(ourPrice)
                .avgCompetitorPrice(avgPrice)
                .lowestCompetitorPrice(lowestPrice)
                .highestCompetitorPrice(highestPrice)
                .pricePosition(pricePosition)
                .priceDiffPercent(priceDiffPercent)
                .build();
    }

    private BigDecimal resolveOurPriceFrom(List<CompetitorInfo> competitors) {
        // 竞品的 priceDiff = 竞品价 - 我方价，所以反推我方价
        if (competitors.isEmpty()) {
            return BigDecimal.ZERO;
        }
        CompetitorInfo first = competitors.get(0);
        if (first.getPriceDiff() == null) {
            return BigDecimal.ZERO;
        }
        return first.getPrice().subtract(first.getPriceDiff()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算销售对比
     */
    private SalesComparison calculateSalesComparison(String productId,
                                                     List<CompetitorInfo> competitors) {
        int ourSales = (int) repository.getProductSalesCount(productId);

        double avgSales = competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount)
                .average()
                .orElse(0);

        int topSales = competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount)
                .max()
                .orElse(0);

        String salesPosition;
        if (competitors.isEmpty()) {
            salesPosition = "UNKNOWN";
        } else if (ourSales > avgSales) {
            salesPosition = "ABOVE_AVG";
        } else if (ourSales < avgSales) {
            salesPosition = "BELOW_AVG";
        } else {
            salesPosition = "AT_AVG";
        }

        long totalSales = ourSales + competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount).sum();
        BigDecimal marketSharePercent = totalSales == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(ourSales * 100.0 / totalSales)
                        .setScale(2, RoundingMode.HALF_UP);

        return SalesComparison.builder()
                .ourSalesCount(ourSales)
                .avgCompetitorSales((int) avgSales)
                .topCompetitorSales(topSales)
                .salesPosition(salesPosition)
                .marketSharePercent(marketSharePercent)
                .build();
    }

    /**
     * 计算市场占有率
     */
    private MarketShare calculateMarketShare(String productId, List<CompetitorInfo> competitors) {
        int ourSales = (int) repository.getProductSalesCount(productId);
        long totalSales = ourSales + competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount).sum();

        BigDecimal ourShare = totalSales == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(ourSales * 100.0 / totalSales)
                        .setScale(2, RoundingMode.HALF_UP);

        List<CompetitorShare> competitorShares = new ArrayList<>();
        for (CompetitorInfo c : competitors) {
            BigDecimal share = totalSales == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(c.getSalesCount() * 100.0 / totalSales)
                            .setScale(2, RoundingMode.HALF_UP);
            competitorShares.add(CompetitorShare.builder()
                    .competitorName(c.getCompetitorName())
                    .sharePercent(share)
                    .build());
        }

        competitorShares.sort((a, b) -> b.getSharePercent().compareTo(a.getSharePercent()));

        return MarketShare.builder()
                .ourShare(ourShare)
                .competitorShares(competitorShares)
                .build();
    }

    /**
     * 生成建议
     */
    private List<String> generateRecommendations(PriceComparison priceComparison,
                                                 SalesComparison salesComparison) {
        List<String> recommendations = new ArrayList<>();

        if ("COMPETITIVE".equals(priceComparison.getPricePosition())) {
            recommendations.add("当前价格具有竞争力，建议保持或适当上调");
        } else if ("PREMIUM".equals(priceComparison.getPricePosition())) {
            recommendations.add("价格高于平均水平，考虑调整定价策略");
        } else if ("BUDGET".equals(priceComparison.getPricePosition())) {
            recommendations.add("价格与竞品持平，可通过差异化服务提升竞争力");
        } else if ("UNKNOWN".equals(priceComparison.getPricePosition())) {
            recommendations.add("暂无可对比的竞品数据，请先在定价服务中配置竞品采集");
        }

        if ("BELOW_AVG".equals(salesComparison.getSalesPosition())) {
            recommendations.add("销售量低于竞品平均水平，建议加强推广");
        }

        recommendations.add("建议定期监控竞品价格调整，及时应对市场变化");
        return recommendations;
    }
}
