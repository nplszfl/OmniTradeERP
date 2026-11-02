package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.AnalyticsSummaryDTO;
import com.crossborder.analytics.dto.DashboardDTO;
import com.crossborder.analytics.repository.AnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DashboardService 单元测试
 *
 * <p>核心验证点：<b>查不到数据时返回空/0，绝不返回随机数</b>。
 * 之前这个 Service 全部用 Math.random() 造数据，
 * 单测根本无从下手 —— 因为根本没有 Repository 可以 mock。</p>
 */
@DisplayName("DashboardService 单元测试")
class DashboardServiceTest {

    private AnalyticsRepository repository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalyticsRepository.class);
        service = new DashboardService(repository);
    }

    private AnalyticsRepository.SalesSummary emptySummary() {
        return new AnalyticsRepository.SalesSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0);
    }

    private AnalyticsRepository.SalesSummary fullSummary() {
        return new AnalyticsRepository.SalesSummary(
                new BigDecimal("1200.00"), new BigDecimal("900.00"),
                new BigDecimal("15000.00"), new BigDecimal("88000.00"),
                320, 12, 45, 263);
    }

    // ---------------------------------------------------------------- 概览

    @Test
    @DisplayName("getOverviewStats - 库里有数据时字段一一对应")
    void getOverviewStats_mapsRealValues() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getActiveProductCount()).thenReturn(328);
        when(repository.getLowStockProductCount()).thenReturn(12);

        DashboardDTO.OverviewStats s = service.getOverviewStats();

        assertEquals(0, s.getTodaySales().compareTo(new BigDecimal("1200.00")));
        assertEquals(0, s.getYesterdaySales().compareTo(new BigDecimal("900.00")));
        assertEquals(0, s.getMonthSales().compareTo(new BigDecimal("15000.00")));
        assertEquals(0, s.getTotalOrders().compareTo(new BigDecimal("320")));
        assertEquals(0, s.getPendingOrders().compareTo(new BigDecimal("12")));
        assertEquals(0, s.getShippedOrders().compareTo(new BigDecimal("45")));
        assertEquals(0, s.getCompletedOrders().compareTo(new BigDecimal("263")));
        assertEquals(328, s.getActiveProducts());
        assertEquals(12, s.getLowStockProducts());
    }

    @Test
    @DisplayName("getOverviewStats - 库为空时返回 0，不是随机数")
    void getOverviewStats_emptyReturnsZero() {
        when(repository.getSalesSummary()).thenReturn(emptySummary());
        when(repository.getActiveProductCount()).thenReturn(0);
        when(repository.getLowStockProductCount()).thenReturn(0);

        DashboardDTO.OverviewStats s = service.getOverviewStats();

        assertEquals(0, s.getTodaySales().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.getTotalOrders().compareTo(BigDecimal.ZERO));
        assertEquals(0, s.getActiveProducts());
    }

    // ---------------------------------------------------------------- 趋势

    @Test
    @DisplayName("getTrend - 日期/订单数/营收映射正确")
    void getTrend_mapsValues() {
        List<AnalyticsRepository.DailyAggregate> aggs = List.of(
                new AnalyticsRepository.DailyAggregate(LocalDate.of(2026, 11, 1), 10L, new BigDecimal("1000.00")),
                new AnalyticsRepository.DailyAggregate(LocalDate.of(2026, 11, 2), 5L, new BigDecimal("250.50")));
        when(repository.getDailyAggregates(anyInt())).thenReturn(aggs);

        List<AnalyticsSummaryDTO.TrendPoint> pts = service.getTrend(30);

        assertEquals(2, pts.size());
        assertEquals("2026-11-01", pts.get(0).getDate());
        assertEquals(10, pts.get(0).getCount());
        assertEquals(0, pts.get(0).getRevenue().compareTo(new BigDecimal("1000.00")));
        assertEquals(5, pts.get(1).getCount());
        assertEquals(0, pts.get(1).getRevenue().compareTo(new BigDecimal("250.50")));
    }

    @Test
    @DisplayName("getTrend - 无数据时返回空列表")
    void getTrend_empty() {
        when(repository.getDailyAggregates(anyInt())).thenReturn(List.of());
        assertTrue(service.getTrend(30).isEmpty());
    }

    @Test
    @DisplayName("getSalesTrend - 客单价按订单数算，不是写死的 45.5")
    void getSalesTrend_avgOrderValue() {
        List<AnalyticsRepository.DailyAggregate> aggs = List.of(
                new AnalyticsRepository.DailyAggregate(LocalDate.of(2026, 11, 1), 2L, new BigDecimal("100.00")));
        when(repository.getDailyAggregates(anyInt())).thenReturn(aggs);

        List<DashboardDTO.TimeSeriesData> trend = service.getSalesTrend();

        assertEquals(1, trend.size());
        assertEquals(0, trend.get(0).getAvgOrderValue().compareTo(new BigDecimal("50.00")));
    }

    // ---------------------------------------------------------------- 平台

    @Test
    @DisplayName("getPlatformDistribution - 百分比按实际营收占比算")
    void getPlatformDistribution_computesPercentage() {
        when(repository.getPlatformAggregates()).thenReturn(List.of(
                new AnalyticsRepository.PlatformAggregate("Amazon", 100L, new BigDecimal("750.00"), BigDecimal.ZERO),
                new AnalyticsRepository.PlatformAggregate("Shopee", 50L, new BigDecimal("250.00"), BigDecimal.ZERO)));

        List<DashboardDTO.PlatformSales> dist = service.getPlatformDistribution();

        assertEquals(2, dist.size());
        assertEquals("Amazon", dist.get(0).getPlatform());
        assertEquals(0, dist.get(0).getPercentage().compareTo(new BigDecimal("75.00")));
        assertEquals(0, dist.get(1).getPercentage().compareTo(new BigDecimal("25.00")));
    }

    @Test
    @DisplayName("getPlatformDistribution - 空数据返回空列表")
    void getPlatformDistribution_empty() {
        when(repository.getPlatformAggregates()).thenReturn(List.of());
        assertTrue(service.getPlatformDistribution().isEmpty());
    }

    // ---------------------------------------------------------------- Top 商品

    @Test
    @DisplayName("getTopProducts - 排名从 1 连续递增")
    void getTopProducts_ranksSequentially() {
        when(repository.getTopProducts(anyInt())).thenReturn(List.of(
                new AnalyticsRepository.ProductAggregate("SKU-A", "商品A", 100L, new BigDecimal("5000.00"), BigDecimal.ZERO),
                new AnalyticsRepository.ProductAggregate("SKU-B", "商品B", 50L, new BigDecimal("2000.00"), BigDecimal.ZERO)));

        List<DashboardDTO.ProductPerformance> top = service.getTopProducts();

        assertEquals(2, top.size());
        assertEquals(1, top.get(0).getRank());
        assertEquals(2, top.get(1).getRank());
        assertEquals("SKU-A", top.get(0).getProductId());
        assertEquals(100, top.get(0).getSalesCount());
    }

    @Test
    @DisplayName("getTopProducts - 无数据返回空列表，不返回编造的 iPhone 商品")
    void getTopProducts_empty() {
        when(repository.getTopProducts(anyInt())).thenReturn(List.of());
        assertTrue(service.getTopProducts().isEmpty());
    }

    // ---------------------------------------------------------------- 库存预警

    @Test
    @DisplayName("getInventoryAlerts - 映射真实预警")
    void getInventoryAlerts_mapsValues() {
        when(repository.getStockAlerts(anyInt())).thenReturn(List.of(
                new AnalyticsRepository.StockAlert("SKU-1", "商品1", 5, 20, "CRITICAL")));

        List<DashboardDTO.InventoryAlert> alerts = service.getInventoryAlerts();

        assertEquals(1, alerts.size());
        assertEquals("SKU-1", alerts.get(0).getProductId());
        assertEquals(5, alerts.get(0).getCurrentStock());
        assertEquals(20, alerts.get(0).getSafetyStock());
        assertEquals("CRITICAL", alerts.get(0).getAlertLevel());
    }

    // ---------------------------------------------------------------- 订单状态分布

    @Test
    @DisplayName("getOrderStatusDistribution - 映射真实状态分布")
    void getOrderStatusDistribution_mapsValues() {
        when(repository.getOrderStatusDistribution()).thenReturn(List.of(
                new AnalyticsRepository.OrderStatusCount("SHIPPED", 14),
                new AnalyticsRepository.OrderStatusCount("COMPLETED", 7)));

        List<DashboardDTO.OrderStatusCount> dist = service.getOrderStatusDistribution();

        assertEquals(2, dist.size());
        assertEquals("SHIPPED", dist.get(0).getStatus());
        assertEquals(14L, dist.get(0).getCount());
        assertEquals("COMPLETED", dist.get(1).getStatus());
        assertEquals(7L, dist.get(1).getCount());
    }

    @Test
    @DisplayName("getOrderStatusDistribution - 库为空时返回空列表，不是编的数据")
    void getOrderStatusDistribution_emptyReturnsEmptyList() {
        when(repository.getOrderStatusDistribution()).thenReturn(List.of());

        assertTrue(service.getOrderStatusDistribution().isEmpty());
    }

    @Test
    @DisplayName("getDashboard - 带上订单状态分布")
    void getDashboard_includesOrderStatusDistribution() {
        stubAllForDashboard();
        when(repository.getOrderStatusDistribution()).thenReturn(List.of(
                new AnalyticsRepository.OrderStatusCount("PENDING", 6)));

        DashboardDTO dto = service.getDashboard();

        assertNotNull(dto.getOrderStatusDistribution());
        assertEquals(1, dto.getOrderStatusDistribution().size());
        assertEquals("PENDING", dto.getOrderStatusDistribution().get(0).getStatus());
        assertEquals(6L, dto.getOrderStatusDistribution().get(0).getCount());
    }

    /**
     * getDashboard 会串起十几个 Repository 查询，全部 stub 掉才能跑通。
     * 这里集中放一处，避免以后新增字段时漏 stub 又变成 NPE。
     */
    private void stubAllForDashboard() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getActiveProductCount()).thenReturn(10);
        when(repository.getLowStockProductCount()).thenReturn(2);
        when(repository.getTodayOrderCount()).thenReturn(7L);
        when(repository.getDailyAggregates(anyInt())).thenReturn(List.of());
        when(repository.getPlatformAggregates()).thenReturn(List.of());
        when(repository.getTopProducts(anyInt())).thenReturn(List.of());
        when(repository.getStockAlerts(anyInt())).thenReturn(List.of());
        when(repository.getRecentOrders(anyInt())).thenReturn(List.of());
        when(repository.getTotalCost()).thenReturn(new BigDecimal("500.00"));
        when(repository.getPendingPaymentAmount()).thenReturn(BigDecimal.ZERO);
        when(repository.getRefundAmount()).thenReturn(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- 今日订单数

    @Test
    @DisplayName("getOverviewStats - todayOrders 取的是今日订单笔数，不是金额")
    void getOverviewStats_todayOrdersIsCount() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getActiveProductCount()).thenReturn(328);
        when(repository.getLowStockProductCount()).thenReturn(12);
        when(repository.getTodayOrderCount()).thenReturn(9L);

        DashboardDTO.OverviewStats s = service.getOverviewStats();

        // 金额与笔数是两回事：todaySales=1200.00，todayOrders=9
        assertEquals(0, s.getTodaySales().compareTo(new BigDecimal("1200.00")));
        assertEquals(0, s.getTodayOrders().compareTo(new BigDecimal("9")));
    }

    @Test
    @DisplayName("getOverviewStats - 没有今日订单时是 0，不是 null")
    void getOverviewStats_todayOrdersDefaultsToZero() {
        when(repository.getSalesSummary()).thenReturn(emptySummary());
        when(repository.getActiveProductCount()).thenReturn(0);
        when(repository.getLowStockProductCount()).thenReturn(0);
        when(repository.getTodayOrderCount()).thenReturn(0L);

        DashboardDTO.OverviewStats s = service.getOverviewStats();

        assertNotNull(s.getTodayOrders());
        assertEquals(0, s.getTodayOrders().compareTo(BigDecimal.ZERO));
    }

    // ---------------------------------------------------------------- 最近订单

    @Test
    @DisplayName("getRecentOrders - 映射真实订单")
    void getRecentOrders_mapsValues() {
        when(repository.getRecentOrders(anyInt())).thenReturn(List.of(
                new AnalyticsRepository.RecentOrder("1001", "Amazon", "COMPLETED",
                        new BigDecimal("299.00"), LocalDateTime.of(2026, 11, 2, 10, 30))));

        List<DashboardDTO.OrderSummary> orders = service.getRecentOrders();

        assertEquals(1, orders.size());
        assertEquals("1001", orders.get(0).getOrderId());
        assertEquals("Amazon", orders.get(0).getPlatform());
        assertEquals(0, orders.get(0).getAmount().compareTo(new BigDecimal("299.00")));
        assertEquals(LocalDateTime.of(2026, 11, 2, 10, 30), orders.get(0).getOrderTime());
    }

    @Test
    @DisplayName("getRecentOrders - 无数据返回空列表")
    void getRecentOrders_empty() {
        when(repository.getRecentOrders(anyInt())).thenReturn(List.of());
        assertTrue(service.getRecentOrders().isEmpty());
    }

    // ---------------------------------------------------------------- 财务

    @Test
    @DisplayName("getFinancialOverview - 毛利与利润率按真实营收成本算")
    void getFinancialOverview_computesMargin() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getTotalCost()).thenReturn(new BigDecimal("40000.00"));
        when(repository.getPendingPaymentAmount()).thenReturn(new BigDecimal("3500.00"));
        when(repository.getRefundAmount()).thenReturn(new BigDecimal("800.00"));

        DashboardDTO.FinancialOverview f = service.getFinancialOverview();

        assertEquals(0, f.getTotalRevenue().compareTo(new BigDecimal("88000.00")));
        assertEquals(0, f.getTotalCost().compareTo(new BigDecimal("40000.00")));
        assertEquals(0, f.getGrossProfit().compareTo(new BigDecimal("48000.00")));
        assertEquals(0, f.getProfitMargin().compareTo(new BigDecimal("54.55")));
        assertEquals(0, f.getPendingPayment().compareTo(new BigDecimal("3500.00")));
    }

    @Test
    @DisplayName("getFinancialOverview - 成本为 0 时利润率返回 0，不抛除零")
    void getFinancialOverview_zeroCostNoDivideByZero() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getTotalCost()).thenReturn(BigDecimal.ZERO);
        when(repository.getPendingPaymentAmount()).thenReturn(BigDecimal.ZERO);
        when(repository.getRefundAmount()).thenReturn(BigDecimal.ZERO);

        DashboardDTO.FinancialOverview f = service.getFinancialOverview();
        assertEquals(0, f.getProfitMargin().compareTo(BigDecimal.ZERO));
    }

    // ---------------------------------------------------------------- 扁平摘要

    @Test
    @DisplayName("getSummary - 字段形状不变（前端依赖）")
    void getSummary_keepsShape() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getActiveProductCount()).thenReturn(328);
        when(repository.getLowStockProductCount()).thenReturn(12);
        when(repository.getPlatformAggregates()).thenReturn(List.of(
                new AnalyticsRepository.PlatformAggregate("Amazon", 100L, new BigDecimal("750.00"), BigDecimal.ZERO)));
        when(repository.getTopProducts(anyInt())).thenReturn(List.of(
                new AnalyticsRepository.ProductAggregate("SKU-A", "商品A", 100L, new BigDecimal("5000.00"), BigDecimal.ZERO)));
        when(repository.getRecentOrders(anyInt())).thenReturn(List.of());
        when(repository.getTotalCost()).thenReturn(BigDecimal.ZERO);
        when(repository.getPendingPaymentAmount()).thenReturn(BigDecimal.ZERO);
        when(repository.getRefundAmount()).thenReturn(BigDecimal.ZERO);

        AnalyticsSummaryDTO s = service.getSummary();

        assertNotNull(s.getRevenueByPlatform());
        assertNotNull(s.getTopProducts());
        assertNotNull(s.getCustomersByRegion());
        assertEquals(1, s.getRevenueByPlatform().size());
        assertEquals("Amazon", s.getRevenueByPlatform().get(0).getPlatform());
        assertEquals(1, s.getTopProducts().size());
        assertEquals("SKU-A", s.getTopProducts().get(0).getSku());
    }

    // ---------------------------------------------------------------- 客户分布

    @Test
    @DisplayName("getSummary - customersByRegion 来自按国家聚合的真实查询，不是 recentOrders")
    void getSummary_customersByRegionUsesAggregatedQuery() {
        when(repository.getSalesSummary()).thenReturn(fullSummary());
        when(repository.getActiveProductCount()).thenReturn(10);
        when(repository.getLowStockProductCount()).thenReturn(1);
        when(repository.getPlatformAggregates()).thenReturn(List.of());
        when(repository.getTopProducts(anyInt())).thenReturn(List.of());
        when(repository.getRecentOrders(anyInt())).thenReturn(List.of(
                // 最近的订单：平台名 + 金额
                new AnalyticsRepository.RecentOrder("IO-1", "amazon", "SHIPPED",
                        new BigDecimal("199.00"), LocalDateTime.of(2026, 3, 1, 10, 0))));
        when(repository.getTotalCost()).thenReturn(BigDecimal.ZERO);
        when(repository.getPendingPaymentAmount()).thenReturn(BigDecimal.ZERO);
        when(repository.getRefundAmount()).thenReturn(BigDecimal.ZERO);
        // 真正的客户分布：按收货国家聚合
        when(repository.getCustomerDistribution()).thenReturn(List.of(
                new AnalyticsRepository.RegionAggregate("US", 12L, new BigDecimal("3000.00"))));

        AnalyticsSummaryDTO s = service.getSummary();

        assertEquals(1, s.getCustomersByRegion().size());
        assertEquals("US", s.getCustomersByRegion().get(0).getRegion());
        assertEquals(12, s.getCustomersByRegion().get(0).getCustomers());
        // totalCustomers 必须等于各地区客户数之和（12），而不是最近订单数（1）
        assertEquals(12, s.getTotalCustomers());
    }

    // ---------------------------------------------------------------- 客户分布

    @Test
    @DisplayName("getCustomerDistribution - 按国家聚合真实数据")
    void getCustomerDistribution_mapsRegions() {
        when(repository.getCustomerDistribution()).thenReturn(List.of(
                new AnalyticsRepository.RegionAggregate("US", 50L, new BigDecimal("5000.00")),
                new AnalyticsRepository.RegionAggregate("DE", 20L, new BigDecimal("2000.00"))));

        List<AnalyticsSummaryDTO.CustomerDistribution> dist = service.getCustomerDistribution();

        assertEquals(2, dist.size());
        assertEquals("US", dist.get(0).getRegion());
        assertEquals(50, dist.get(0).getCustomers());
        assertEquals(0, dist.get(0).getRevenue().compareTo(new BigDecimal("5000.00")));
    }

    @Test
    @DisplayName("getCustomerDistribution - 无数据返回空列表")
    void getCustomerDistribution_empty() {
        when(repository.getCustomerDistribution()).thenReturn(List.of());
        assertTrue(service.getCustomerDistribution().isEmpty());
    }

    // ---------------------------------------------------------------- 小时趋势

    @Test
    @DisplayName("getTodaySalesTrend - 固定 24 个点，没数据的小时为 0")
    void getTodaySalesTrend_always24Points() {
        List<AnalyticsRepository.HourlyAggregate> hours = new ArrayList<>();
        hours.add(new AnalyticsRepository.HourlyAggregate(9, 4L, new BigDecimal("800.00")));
        when(repository.getHourlyAggregates()).thenReturn(hours);

        List<DashboardDTO.TimeSeriesData> trend = service.getTodaySalesTrend();

        assertEquals(24, trend.size());
        assertEquals(4, trend.get(9).getOrderCount());
        assertEquals(0, trend.get(9).getSalesAmount().compareTo(new BigDecimal("800.00")));
        assertEquals(0, trend.get(9).getAvgOrderValue().compareTo(new BigDecimal("200.00")));
        assertEquals(0, trend.get(0).getOrderCount());
    }
}
