package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.CompetitorAnalysisDTO.*;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorAnalysisService {

    @Value("${analytics.competitor.update-interval:3600000}")
    private long updateInterval;

    @Value("${analytics.competitor.max-competitors:50}")
    private int maxCompetitors;

    /**
     * 获取竞品分析
     */
    public CompetitorAnalysisDTO getCompetitorAnalysis(String productId, String analysisType) {
        log.info("Generating competitor analysis for product: {}, type: {}", productId, analysisType);
        
        // 获取竞品列表
        List<CompetitorInfo> competitors = getCompetitors(productId);
        
        // 价格对比
        PriceComparison priceComparison = calculatePriceComparison(productId, competitors);
        
        // 销售对比
        SalesComparison salesComparison = calculateSalesComparison(productId, competitors);
        
        // 市场占有率
        MarketShare marketShare = calculateMarketShare(competitors);
        
        // 趋势分析
        List<TrendData> trends = generateTrends();
        
        // 建议
        List<String> recommendations = generateRecommendations(priceComparison, salesComparison);
        
        return CompetitorAnalysisDTO.builder()
                .analysisType(analysisType)
                .productId(productId)
                .productName("Product " + productId)
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
        
        String[] competitorNames = {"Competitor A Store", "BestDeals Shop", "SuperSavings", 
                "QualityGoods", "FastShip Plus", "TopRated Seller", "Prime Products"};
        String[] platforms = {"Amazon", "Amazon", "eBay", "Shopee", "eBay", "Lazada", "Shopee"};
        double[] prices = {44.99, 39.99, 35.99, 49.99, 42.99, 38.99, 41.99};
        int[] sales = {520, 480, 350, 280, 220, 180, 150};
        double[] ratings = {4.5, 4.3, 4.1, 4.7, 4.2, 4.4, 4.6};
        int[] reviews = {1200, 980, 650, 1500, 420, 780, 920};
        
        BigDecimal ourPrice = BigDecimal.valueOf(42.99);
        
        for (int i = 0; i < competitorNames.length; i++) {
            BigDecimal price = BigDecimal.valueOf(prices[i]);
            BigDecimal priceDiff = price.subtract(ourPrice);
            String pricePosition = priceDiff.compareTo(BigDecimal.ZERO) > 0 ? "HIGHER" : 
                    priceDiff.compareTo(BigDecimal.ZERO) < 0 ? "LOWER" : "EQUAL";
            
            competitors.add(CompetitorInfo.builder()
                    .competitorId("C" + String.format("%03d", i + 1))
                    .competitorName(competitorNames[i])
                    .platform(platforms[i])
                    .price(price)
                    .salesCount(sales[i])
                    .revenue(BigDecimal.valueOf(sales[i] * prices[i]).setScale(2, RoundingMode.HALF_UP))
                    .rating(BigDecimal.valueOf(ratings[i]).setScale(1, RoundingMode.HALF_UP))
                    .reviewCount(reviews[i])
                    .url("https://example.com/competitor/" + (i + 1))
                    .priceDiff(priceDiff.setScale(2, RoundingMode.HALF_UP))
                    .pricePosition(pricePosition)
                    .build());
        }
        
        return competitors;
    }

    /**
     * 计算价格对比
     */
    private PriceComparison calculatePriceComparison(String productId, List<CompetitorInfo> competitors) {
        BigDecimal ourPrice = BigDecimal.valueOf(42.99);
        
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
        if (ourPrice.compareTo(avgPrice) < 0) {
            pricePosition = "COMPETITIVE";
        } else if (ourPrice.compareTo(avgPrice) > 0) {
            pricePosition = "PREMIUM";
        } else {
            pricePosition = "BUDGET";
        }
        
        BigDecimal priceDiffPercent = avgPrice.subtract(ourPrice)
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

    /**
     * 计算销售对比
     */
    private SalesComparison calculateSalesComparison(String productId, List<CompetitorInfo> competitors) {
        int ourSales = 380;
        
        double avgSales = competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount)
                .average()
                .orElse(0);
        
        int topSales = competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount)
                .max()
                .orElse(0);
        
        String salesPosition;
        if (ourSales > avgSales) {
            salesPosition = "ABOVE_AVG";
        } else if (ourSales < avgSales) {
            salesPosition = "BELOW_AVG";
        } else {
            salesPosition = "AT_AVG";
        }
        
        int totalSales = ourSales + competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount)
                .sum();
        BigDecimal marketSharePercent = BigDecimal.valueOf(ourSales * 100.0 / totalSales)
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
    private MarketShare calculateMarketShare(List<CompetitorInfo> competitors) {
        int ourSales = 380;
        int totalSales = ourSales + competitors.stream()
                .mapToInt(CompetitorInfo::getSalesCount)
                .sum();
        
        BigDecimal ourShare = BigDecimal.valueOf(ourSales * 100.0 / totalSales)
                .setScale(2, RoundingMode.HALF_UP);
        
        List<CompetitorShare> competitorShares = new ArrayList<>();
        for (CompetitorInfo c : competitors) {
            BigDecimal share = BigDecimal.valueOf(c.getSalesCount() * 100.0 / totalSales)
                    .setScale(2, RoundingMode.HALF_UP);
            competitorShares.add(CompetitorShare.builder()
                    .competitorName(c.getCompetitorName())
                    .sharePercent(share)
                    .build());
        }
        
        // 按份额排序
        competitorShares.sort((a, b) -> b.getSharePercent().compareTo(a.getSharePercent()));
        
        return MarketShare.builder()
                .ourShare(ourShare)
                .competitorShares(competitorShares)
                .build();
    }

    /**
     * 生成趋势数据
     */
    private List<TrendData> generateTrends() {
        List<TrendData> trends = new ArrayList<>();
        
        String[] periods = {"Week 1", "Week 2", "Week 3", "Week 4"};
        String[] ourTrends = {"UP", "UP", "DOWN", "UP"};
        String[][] compTrends = {{"UP", "DOWN"}, {"DOWN", "UP"}, {"UP", "UP"}, {"STABLE", "UP"}};
        
        for (int i = 0; i < periods.length; i++) {
            List<CompetitorTrend> competitorTrendList = new ArrayList<>();
            competitorTrendList.add(CompetitorTrend.builder()
                    .competitorName("Competitor A")
                    .trend(compTrends[i][0])
                    .changePercent(BigDecimal.valueOf(Math.random() * 20 - 5)
                            .setScale(2, RoundingMode.HALF_UP))
                    .build());
            
            trends.add(TrendData.builder()
                    .period(periods[i])
                    .ourTrend(ourTrends[i])
                    .competitorTrends(competitorTrendList)
                    .build());
        }
        
        return trends;
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
        }
        
        if (salesComparison.getOurSalesCount() < salesComparison.getAvgCompetitorSales()) {
            recommendations.add("销售量低于竞品平均水平，建议加强推广");
        }
        
        if (BigDecimal.valueOf(20).compareTo(priceComparison.getPriceDiffPercent()) > 0) {
            recommendations.add("价格差异较小，可通过差异化服务提升竞争力");
        }
        
        recommendations.add("建议定期监控竞品价格调整，及时应对市场变化");
        
        return recommendations;
    }
}