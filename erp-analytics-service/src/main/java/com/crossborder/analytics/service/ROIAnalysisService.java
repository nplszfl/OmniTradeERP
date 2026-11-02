package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.ROIAnalysisDTO;
import com.crossborder.analytics.dto.ROIAnalysisDTO.ROIDetail;
import com.crossborder.analytics.dto.ROIAnalysisDTO.ROISummary;
import com.crossborder.analytics.dto.ROIAnalysisDTO.ROITrend;
import com.crossborder.analytics.dto.ROIAnalysisDTO.TopPerformer;
import com.crossborder.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ROI 分析服务
 *
 * <p>⚠️ 之前这里是纯模拟：写死的 125000 营收 / 75000 成本 / 2500 订单、
 * 写死的 "iPhone 15 Pro Case" 商品名和 4 周随机趋势。
 * 现在全部改成查 {@code erp_order} / {@code erp_product} 的真实数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ROIAnalysisService {

    private final AnalyticsRepository repository;

    @Value("${analytics.roi.default-period-days:30}")
    private int defaultPeriodDays;

    @Value("${analytics.roi.currency:USD}")
    private String currency;

    /**
     * 获取 ROI 分析
     *
     * @param dimension PRODUCT / PLATFORM / CAMPAIGN
     */
    public ROIAnalysisDTO getROIAnalysis(String dimension, LocalDate startDate, LocalDate endDate) {
        log.info("Generating ROI analysis for dimension: {}, from {} to {}",
                dimension, startDate, endDate);

        ROISummary summary = calculateSummary(dimension, startDate, endDate);
        List<ROIDetail> details = getDetails(dimension, startDate, endDate);
        List<ROITrend> trends = getTrends(startDate, endDate);
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
     * 计算 ROI 汇总
     */
    private ROISummary calculateSummary(String dimension, LocalDate startDate, LocalDate endDate) {
        AnalyticsRepository.PeriodAggregate agg = repository.getPeriodAggregate(startDate, endDate);

        BigDecimal revenue = agg.revenue();
        BigDecimal cost = agg.cost();
        BigDecimal profit = revenue.subtract(cost);

        // ROI = 利润 / 成本 × 100；成本为 0 时没法算，返回 0
        BigDecimal overallROI = cost.signum() == 0 ? BigDecimal.ZERO
                : profit.divide(cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roas = cost.signum() == 0 ? BigDecimal.ZERO
                : revenue.divide(cost, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);

        // 客单价 = 营收 / 订单数
        BigDecimal aov = agg.orderCount() == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(agg.orderCount()), 2, RoundingMode.HALF_UP);

        return ROISummary.builder()
                .totalRevenue(revenue)
                .totalCost(cost)
                .totalProfit(profit)
                .overallROI(overallROI)
                .roas(roas)
                .totalOrders((int) agg.orderCount())
                .aov(aov)
                // ⚠️ 转化率需要访问量数据，项目里没有埋点/流量表，无法计算，返回 0 而不是编 3.5%
                .conversionRate(BigDecimal.ZERO)
                .build();
    }

    /**
     * 获取明细数据
     */
    private List<ROIDetail> getDetails(String dimension, LocalDate startDate, LocalDate endDate) {
        List<ROIDetail> details = new ArrayList<>();

        if ("PRODUCT".equalsIgnoreCase(dimension)) {
            for (AnalyticsRepository.ProductAggregate a
                    : repository.getProductAggregates(startDate, endDate, 20)) {
                details.add(toDetail(a.sku(), a.productName(), a.revenue(), a.cost(),
                        (int) a.sold()));
            }
        } else if ("PLATFORM".equalsIgnoreCase(dimension)) {
            for (AnalyticsRepository.PlatformAggregate a
                    : repository.getPlatformAggregates(startDate, endDate)) {
                details.add(toDetail(a.platform(), a.platform(), a.revenue(), a.cost(),
                        (int) a.orderCount()));
            }
        }
        // CAMPAIGN 维度：项目里没有广告投放表（t_ad_* / t_campaign 均不存在），
        // 无数据可查，返回空列表而不是编造。

        return details;
    }

    private ROIDetail toDetail(String id, String name, BigDecimal revenue,
                               BigDecimal cost, int orders) {
        BigDecimal profit = revenue.subtract(cost);
        BigDecimal roi = cost.signum() == 0 ? BigDecimal.ZERO
                : profit.divide(cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roas = cost.signum() == 0 ? BigDecimal.ZERO
                : revenue.divide(cost, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        BigDecimal aov = orders == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);

        return ROIDetail.builder()
                .id(id)
                .name(name)
                .revenue(revenue)
                .cost(cost)
                .profit(profit)
                .roi(roi)
                .roas(roas)
                .orders(orders)
                .aov(aov)
                .build();
    }

    /**
     * 获取趋势数据（按周）
     */
    private List<ROITrend> getTrends(LocalDate startDate, LocalDate endDate) {
        List<ROITrend> trends = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        for (AnalyticsRepository.WeeklyAggregate w
                : repository.getWeeklyAggregates(startDate, endDate)) {
            BigDecimal profit = w.revenue().subtract(w.cost());
            BigDecimal roi = w.cost().signum() == 0 ? BigDecimal.ZERO
                    : profit.divide(w.cost(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            trends.add(ROITrend.builder()
                    .period(fmt.format(w.weekStart()))
                    .revenue(w.revenue())
                    .cost(w.cost())
                    .roi(roi)
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
                .collect(Collectors.toList());
    }

    /**
     * 获取产品维度 ROI 分析
     */
    public ROIAnalysisDTO getProductROI(LocalDate startDate, LocalDate endDate) {
        return getROIAnalysis("PRODUCT", startDate, endDate);
    }

    /**
     * 获取平台维度 ROI 分析
     */
    public ROIAnalysisDTO getPlatformROI(LocalDate startDate, LocalDate endDate) {
        return getROIAnalysis("PLATFORM", startDate, endDate);
    }

    /**
     * 获取广告投放 ROI 分析
     *
     * <p>⚠️ 项目里没有广告投放数据表，详情列表会是空的。</p>
     */
    public ROIAnalysisDTO getCampaignROI(LocalDate startDate, LocalDate endDate) {
        return getROIAnalysis("CAMPAIGN", startDate, endDate);
    }
}
