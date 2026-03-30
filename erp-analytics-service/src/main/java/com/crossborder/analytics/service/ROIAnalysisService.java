package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.ROIAnalysisDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ROI分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ROIAnalysisService {

    @Value("${analytics.roi.default-period-days:30}")
    private int defaultPeriodDays;

    @Value("${analytics.roi.currency:USD}")
    private String currency;

    /**
     * 获取ROI分析
     */
    public ROIAnalysisDTO getROIAnalysis(String dimension, LocalDate startDate, LocalDate endDate) {
        log.info("Generating ROI analysis for dimension: {}, from {} to {}", 
                dimension, startDate, endDate);
        
        // 获取汇总数据
        ROISummary summary = calculateSummary(dimension, startDate, endDate);
        
        // 获取明细数据
        List<ROIDetail> details = getDetails(dimension, startDate, endDate);
        
        // 获取趋势数据
        List<ROITrend> trends = getTrends(startDate, endDate);
        
        // 获取最佳 performer
        List<TopPerformer> topPerformers = getTopPerformers(details);
        
        return ROIAnalysisDTO.builder()
                .dimension(dimension)
                .startDate(startDate)
                .endDate(endDate)
                .summary(summary)
                .details(details)
                .trends(trends)
                .topPerformers(topPerformers)
                .build();
    }

    /**
     * 计算ROI汇总
     */
    private ROISummary calculateSummary(String dimension, LocalDate startDate, LocalDate endDate) {
        // 模拟数据
        BigDecimal totalRevenue = BigDecimal.valueOf(125000).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCost = BigDecimal.valueOf(75000).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProfit = totalRevenue.subtract(totalCost);
        BigDecimal overallROI = totalProfit.divide(totalCost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roas = totalRevenue.divide(totalCost, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        
        return ROISummary.builder()
                .totalRevenue(totalRevenue)
                .totalCost(totalCost)
                .totalProfit(totalProfit)
                .overallROI(overallROI)
                .roas(roas)
                .totalOrders(2500)
                .aov(BigDecimal.valueOf(50).setScale(2, RoundingMode.HALF_UP))
                .conversionRate(BigDecimal.valueOf(3.5).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * 获取明细数据
     */
    private List<ROIDetail> getDetails(String dimension, LocalDate startDate, LocalDate endDate) {
        List<ROIDetail> details = new ArrayList<>();
        
        if ("PRODUCT".equalsIgnoreCase(dimension)) {
            String[] products = {"iPhone 15 Pro Case", "Wireless Earbuds", "Smart Watch Band", 
                    "USB-C Cable", "Phone Screen Protector"};
            double[] revenues = {25000, 20000, 15000, 12000, 10000};
            double[] costs = {12000, 11000, 8000, 5000, 4000};
            int[] orders = {550, 440, 330, 280, 220};
            
            for (int i = 0; i < products.length; i++) {
                BigDecimal revenue = BigDecimal.valueOf(revenues[i]).setScale(2, RoundingMode.HALF_UP);
                BigDecimal cost = BigDecimal.valueOf(costs[i]).setScale(2, RoundingMode.HALF_UP);
                BigDecimal profit = revenue.subtract(cost);
                BigDecimal roi = profit.divide(cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal roas = revenue.divide(cost, 4, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
                
                details.add(ROIDetail.builder()
                        .id("P" + String.format("%04d", i + 1))
                        .name(products[i])
                        .revenue(revenue)
                        .cost(cost)
                        .profit(profit)
                        .roi(roi)
                        .roas(roas)
                        .orders(orders[i])
                        .aov(revenue.divide(BigDecimal.valueOf(orders[i]), 2, RoundingMode.HALF_UP))
                        .build());
            }
        } else if ("PLATFORM".equalsIgnoreCase(dimension)) {
            String[] platforms = {"Amazon", "Shopee", "eBay", "Lazada", "TikTok"};
            double[] revenues = {45000, 35000, 25000, 12000, 8000};
            double[] costs = {25000, 20000, 15000, 8000, 7000};
            
            for (int i = 0; i < platforms.length; i++) {
                BigDecimal revenue = BigDecimal.valueOf(revenues[i]).setScale(2, RoundingMode.HALF_UP);
                BigDecimal cost = BigDecimal.valueOf(costs[i]).setScale(2, RoundingMode.HALF_UP);
                BigDecimal profit = revenue.subtract(cost);
                BigDecimal roi = profit.divide(cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                
                details.add(ROIDetail.builder()
                        .id("PL" + (i + 1))
                        .name(platforms[i])
                        .revenue(revenue)
                        .cost(cost)
                        .profit(profit)
                        .roi(roi)
                        .roas(revenue.divide(cost, 4, RoundingMode.HALF_UP))
                        .orders((int) (revenues[i] / 50))
                        .aov(BigDecimal.valueOf(50))
                        .build());
            }
        }
        
        return details;
    }

    /**
     * 获取趋势数据
     */
    private List<ROITrend> getTrends(LocalDate startDate, LocalDate endDate) {
        List<ROITrend> trends = new ArrayList<>();
        
        // 按周生成趋势
        for (int i = 0; i < 4; i++) {
            double revenue = 25000 + Math.random() * 10000;
            double cost = revenue * (0.5 + Math.random() * 0.2);
            double roi = ((revenue - cost) / cost) * 100;
            
            trends.add(ROITrend.builder()
                    .period("Week " + (i + 1))
                    .revenue(BigDecimal.valueOf(revenue).setScale(2, RoundingMode.HALF_UP))
                    .cost(BigDecimal.valueOf(cost).setScale(2, RoundingMode.HALF_UP))
                    .roi(BigDecimal.valueOf(roi).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        
        return trends;
    }

    /**
     * 获取最佳 performer
     */
    private List<TopPerformer> getTopPerformers(List<ROIDetail> details) {
        return details.stream()
                .sorted(Comparator.comparing(ROIDetail::getRoi).reversed())
                .limit(3)
                .map(d -> TopPerformer.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .roi(d.getRoi())
                        .revenue(d.getRevenue())
                        .type("TOP_ROI")
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取产品维度ROI分析
     */
    public ROIAnalysisDTO getProductROI(LocalDate startDate, LocalDate endDate) {
        return getROIAnalysis("PRODUCT", startDate, endDate);
    }

    /**
     * 获取平台维度ROI分析
     */
    public ROIAnalysisDTO getPlatformROI(LocalDate startDate, LocalDate endDate) {
        return getROIAnalysis("PLATFORM", startDate, endDate);
    }

    /**
     * 获取广告投放ROI分析
     */
    public ROIAnalysisDTO getCampaignROI(LocalDate startDate, LocalDate endDate) {
        return getROIAnalysis("CAMPAIGN", startDate, endDate);
    }
}