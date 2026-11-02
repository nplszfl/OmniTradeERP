package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.AnalyticsSummaryDTO;
import com.crossborder.analytics.dto.DashboardDTO;
import com.crossborder.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据看板服务
 *
 * <p>⚠️ 之前这个 Service 全部用 {@code Math.random()} 造数据 ——
 * 看板上的销售额、订单数、Top 商品全是编的，每次刷新还不一样。
 * 现在全部改成从 {@link AnalyticsRepository} 真实查库。</p>
 *
 * <p>查询不到数据时返回空列表 / 0，<b>不再编造</b>。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AnalyticsRepository repository;

    /**
     * 获取完整的数据看板
     */
    public DashboardDTO getDashboard() {
        log.info("Fetching dashboard data from database...");

        DashboardDTO dashboard = DashboardDTO.builder()
                .overview(getOverviewStats())
                .salesTrend(getSalesTrend())
                .platformDistribution(getPlatformDistribution())
                .topProducts(getTopProducts())
                .inventoryAlerts(getInventoryAlerts())
                .recentOrders(getRecentOrders())
                .orderStatusDistribution(getOrderStatusDistribution())
                .financialOverview(getFinancialOverview())
                .lastUpdated(LocalDateTime.now())
                .build();

        log.info("Dashboard data fetched successfully");
        return dashboard;
    }

    /**
     * 获取今日销售趋势（按小时）
     *
     * <p>按 {@code t_order.create_time} 的小时分组统计。</p>
     */
    public List<DashboardDTO.TimeSeriesData> getTodaySalesTrend() {
        Map<Integer, AnalyticsRepository.HourlyAggregate> byHour =
                repository.getHourlyAggregates().stream()
                        .collect(Collectors.toMap(
                                AnalyticsRepository.HourlyAggregate::hour, h -> h, (a, b) -> a));

        List<DashboardDTO.TimeSeriesData> trend = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            AnalyticsRepository.HourlyAggregate agg = byHour.get(hour);
            long count = agg == null ? 0L : agg.orderCount();
            BigDecimal amount = agg == null ? BigDecimal.ZERO : agg.revenue();
            trend.add(DashboardDTO.TimeSeriesData.builder()
                    .date(String.format("%02d:00", hour))
                    .salesAmount(amount)
                    .orderCount((int) count)
                    .avgOrderValue(count == 0 ? BigDecimal.ZERO
                            : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))
                    .build());
        }
        return trend;
    }

    /**
     * 获取销售趋势（按天）
     */
    public List<DashboardDTO.TimeSeriesData> getSalesTrend() {
        return getSalesTrend(30);
    }

    /**
     * 获取销售趋势（按天，可指定天数）
     */
    public List<DashboardDTO.TimeSeriesData> getSalesTrend(int days) {
        List<DashboardDTO.TimeSeriesData> trend = new ArrayList<>();
        for (AnalyticsRepository.DailyAggregate agg : repository.getDailyAggregates(days)) {
            trend.add(DashboardDTO.TimeSeriesData.builder()
                    .date(agg.date().toString())
                    .salesAmount(agg.revenue())
                    .orderCount((int) agg.orderCount())
                    .avgOrderValue(agg.orderCount() == 0 ? BigDecimal.ZERO
                            : agg.revenue().divide(BigDecimal.valueOf(agg.orderCount()), 2,
                            RoundingMode.HALF_UP))
                    .build());
        }
        return trend;
    }

    /**
     * 获取概览统计
     */
    public DashboardDTO.OverviewStats getOverviewStats() {
        AnalyticsRepository.SalesSummary s = repository.getSalesSummary();
        return DashboardDTO.OverviewStats.builder()
                .todaySales(s.todaySales())
                .todayOrders(BigDecimal.valueOf(repository.getTodayOrderCount()))
                .yesterdaySales(s.yesterdaySales())
                .monthSales(s.monthSales())
                .totalOrders(BigDecimal.valueOf(s.totalOrders()))
                .pendingOrders(BigDecimal.valueOf(s.pendingOrders()))
                .shippedOrders(BigDecimal.valueOf(s.shippedOrders()))
                .completedOrders(BigDecimal.valueOf(s.completedOrders()))
                .activeProducts(repository.getActiveProductCount())
                .lowStockProducts(repository.getLowStockProductCount())
                .build();
    }

    /**
     * 获取平台销售分布
     *
     * <p>percentage 是该平台营收占全部平台营收的比例。</p>
     */
    public List<DashboardDTO.PlatformSales> getPlatformDistribution() {
        List<AnalyticsRepository.PlatformAggregate> aggs = repository.getPlatformAggregates();
        BigDecimal total = aggs.stream()
                .map(AnalyticsRepository.PlatformAggregate::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return aggs.stream()
                .map(a -> DashboardDTO.PlatformSales.builder()
                        .platform(a.platform())
                        .salesAmount(a.revenue())
                        .orderCount((int) a.orderCount())
                        .percentage(total.signum() == 0 ? BigDecimal.ZERO
                                : a.revenue().multiply(BigDecimal.valueOf(100))
                                        .divide(total, 2, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取热门产品TOP10
     */
    public List<DashboardDTO.ProductPerformance> getTopProducts() {
        List<AnalyticsRepository.ProductAggregate> aggs = repository.getTopProducts(10);
        List<DashboardDTO.ProductPerformance> out = new ArrayList<>();
        int rank = 1;
        for (AnalyticsRepository.ProductAggregate a : aggs) {
            out.add(DashboardDTO.ProductPerformance.builder()
                    .productId(a.sku())
                    .productName(a.productName())
                    .salesCount((int) a.sold())
                    .salesAmount(a.revenue())
                    .revenue(a.revenue())
                    .rank(rank++)
                    .build());
        }
        return out;
    }

    /**
     * 获取库存预警
     */
    public List<DashboardDTO.InventoryAlert> getInventoryAlerts() {
        return repository.getStockAlerts(20).stream()
                .map(a -> DashboardDTO.InventoryAlert.builder()
                        .productId(a.sku())
                        .productName(a.productName())
                        .currentStock(a.currentStock())
                        .safetyStock(a.safetyStock())
                        .alertLevel(a.alertLevel())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取订单状态分布
     *
     * <p>看板「订单状态分布」图的数据源。
     * 之前这张图在前端是写死的柱子数字，现在改成真实状态分布。</p>
     */
    public List<DashboardDTO.OrderStatusCount> getOrderStatusDistribution() {
        return repository.getOrderStatusDistribution().stream()
                .map(s -> DashboardDTO.OrderStatusCount.builder()
                        .status(s.status())
                        .count(s.count())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取最近订单
     */
    public List<DashboardDTO.OrderSummary> getRecentOrders() {
        return repository.getRecentOrders(10).stream()
                .map(o -> DashboardDTO.OrderSummary.builder()
                        .orderId(o.orderId())
                        .platform(o.platform())
                        .status(o.status())
                        .amount(o.amount())
                        .orderTime(o.orderTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取财务概览
     */
    public DashboardDTO.FinancialOverview getFinancialOverview() {
        BigDecimal revenue = repository.getSalesSummary().totalSales();
        BigDecimal cost = repository.getTotalCost();
        BigDecimal profit = revenue.subtract(cost);
        // 成本为 0 通常意味着 t_product_sku 里没有对应的成本价（数据缺失），
        // 这时报 100% 毛利是误导，直接返回 0。
        BigDecimal margin = (revenue.signum() == 0 || cost.signum() == 0) ? BigDecimal.ZERO
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);

        return DashboardDTO.FinancialOverview.builder()
                .totalRevenue(revenue)
                .totalCost(cost)
                .grossProfit(profit)
                .profitMargin(margin)
                .pendingPayment(repository.getPendingPaymentAmount())
                .pendingRefund(repository.getRefundAmount())
                .build();
    }

    /**
     * 获取扁平版的看板数据（对齐前端消费字段）
     *
     * <p>前端 erp-web/src/views/analytics/index.vue 读的是
     * {@code data.totalOrders} / {@code data.revenueByPlatform[].revenue} /
     * {@code data.topProducts[].name}，而不是嵌套的
     * {@code data.overview.totalOrders}。这里做一次形状转换。</p>
     */
    public AnalyticsSummaryDTO getSummary() {
        DashboardDTO src = getDashboard();
        DashboardDTO.OverviewStats overview = src.getOverview();

        List<AnalyticsSummaryDTO.PlatformRevenue> platforms;
        if (src.getPlatformDistribution() == null) {
            platforms = new ArrayList<>();
        } else {
            platforms = src.getPlatformDistribution().stream()
                    .map(p -> AnalyticsSummaryDTO.PlatformRevenue.builder()
                            .platform(p.getPlatform())
                            .revenue(p.getSalesAmount())
                            .orders(p.getOrderCount())
                            .build())
                    .collect(Collectors.toList());
        }

        List<AnalyticsSummaryDTO.TopProduct> products;
        if (src.getTopProducts() == null) {
            products = new ArrayList<>();
        } else {
            products = src.getTopProducts().stream()
                    .map(p -> AnalyticsSummaryDTO.TopProduct.builder()
                            .sku(p.getProductId())
                            .name(p.getProductName())
                            .sold(p.getSalesCount())
                            .revenue(p.getRevenue())
                            .build())
                    .collect(Collectors.toList());
        }

        // 客户分布必须来自「按收货国家聚合」的真实查询。
        // 之前这里是从 recentOrders（只有最近 10 笔）逐条 map 出来的，
        // 于是 region 填的是**平台名**、每条都算 1 个客户、同一地区重复出现多次 ——
        // 字段名叫 customersByRegion，数据却既不是客户也不是地区。
        List<AnalyticsSummaryDTO.CustomerDistribution> customers = getCustomerDistribution();

        return AnalyticsSummaryDTO.builder()
                .totalOrders(overview == null ? BigDecimal.ZERO : overview.getTotalOrders())
                .totalRevenue(src.getFinancialOverview() == null
                        ? BigDecimal.ZERO : src.getFinancialOverview().getTotalRevenue())
                .totalCustomers(customers.stream()
                        .mapToInt(AnalyticsSummaryDTO.CustomerDistribution::getCustomers)
                        .sum())
                .totalProducts(overview == null ? 0 : overview.getActiveProducts())
                .revenueByPlatform(platforms)
                .topProducts(products)
                .customersByRegion(customers)
                .lastUpdated(src.getLastUpdated())
                .build();
    }

    /**
     * 获取扁平版订单/营收趋势
     *
     * @param days 回溯天数
     * @return 每日 {date, count, revenue}
     */
    public List<AnalyticsSummaryDTO.TrendPoint> getTrend(int days) {
        List<AnalyticsSummaryDTO.TrendPoint> out = new ArrayList<>();
        for (AnalyticsRepository.DailyAggregate agg : repository.getDailyAggregates(days)) {
            out.add(AnalyticsSummaryDTO.TrendPoint.builder()
                    .date(agg.date().toString())
                    .count((int) agg.orderCount())
                    .revenue(agg.revenue())
                    .build());
        }
        return out;
    }

    /**
     * 获取扁平版客户分布
     */
    public List<AnalyticsSummaryDTO.CustomerDistribution> getCustomerDistribution() {
        return repository.getCustomerDistribution().stream()
                .map(r -> AnalyticsSummaryDTO.CustomerDistribution.builder()
                        .region(r.region())
                        .customers((int) r.customers())
                        .revenue(r.revenue())
                        .build())
                .collect(Collectors.toList());
    }
}
