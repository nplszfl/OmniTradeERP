package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.DashboardDTO;
import com.crossborder.analytics.dto.DashboardDTO.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据看板服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dashboardService, "refreshInterval", 30000L);
        ReflectionTestUtils.setField(dashboardService, "maxDataPoints", 1000);
    }

    @Test
    void testGetDashboard() {
        // 执行
        DashboardDTO dashboard = dashboardService.getDashboard();

        // 验证
        assertNotNull(dashboard);
        assertNotNull(dashboard.getOverview());
        assertNotNull(dashboard.getSalesTrend());
        assertNotNull(dashboard.getPlatformDistribution());
        assertNotNull(dashboard.getTopProducts());
        assertNotNull(dashboard.getInventoryAlerts());
        assertNotNull(dashboard.getRecentOrders());
        assertNotNull(dashboard.getFinancialOverview());
        assertNotNull(dashboard.getLastUpdated());
    }

    @Test
    void testGetOverviewStats() {
        // 执行
        OverviewStats stats = dashboardService.getOverviewStats();

        // 验证
        assertNotNull(stats);
        assertNotNull(stats.getTodaySales());
        assertNotNull(stats.getMonthSales());
        assertTrue(stats.getTotalOrders().intValue() > 0);
        assertTrue(stats.getActiveProducts() > 0);
    }

    @Test
    void testGetSalesTrend() {
        // 执行
        List<TimeSeriesData> trend = dashboardService.getSalesTrend();

        // 验证
        assertNotNull(trend);
        assertEquals(30, trend.size()); // 最近30天
        assertNotNull(trend.get(0).getDate());
        assertNotNull(trend.get(0).getSalesAmount());
    }

    @Test
    void testGetTodaySalesTrend() {
        // 执行
        List<TimeSeriesData> trend = dashboardService.getTodaySalesTrend();

        // 验证
        assertNotNull(trend);
        assertEquals(24, trend.size()); // 24小时
    }

    @Test
    void testGetPlatformDistribution() {
        // 执行
        List<PlatformSales> distribution = dashboardService.getPlatformDistribution();

        // 验证
        assertNotNull(distribution);
        assertFalse(distribution.isEmpty());
        
        // 验证百分比总和
        BigDecimal total = distribution.stream()
                .map(PlatformSales::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(100.0, total.doubleValue(), 0.1);
    }

    @Test
    void testGetTopProducts() {
        // 执行
        List<ProductPerformance> products = dashboardService.getTopProducts();

        // 验证
        assertNotNull(products);
        assertEquals(10, products.size());
        
        // 验证排名顺序
        for (int i = 0; i < products.size(); i++) {
            assertEquals(i + 1, products.get(i).getRank());
        }
    }

    @Test
    void testGetInventoryAlerts() {
        // 执行
        List<InventoryAlert> alerts = dashboardService.getInventoryAlerts();

        // 验证
        assertNotNull(alerts);
        assertFalse(alerts.isEmpty());
        
        // 验证库存预警逻辑
        for (InventoryAlert alert : alerts) {
            assertTrue(alert.getCurrentStock() < alert.getSafetyStock());
            assertTrue("LOW".equals(alert.getAlertLevel()) || 
                      "CRITICAL".equals(alert.getAlertLevel()));
        }
    }

    @Test
    void testGetRecentOrders() {
        // 执行
        List<OrderSummary> orders = dashboardService.getRecentOrders();

        // 验证
        assertNotNull(orders);
        assertFalse(orders.isEmpty());
        
        // 验证订单金额
        for (OrderSummary order : orders) {
            assertNotNull(order.getOrderId());
            assertTrue(order.getAmount().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    void testGetFinancialOverview() {
        // 执行
        FinancialOverview overview = dashboardService.getFinancialOverview();

        // 验证
        assertNotNull(overview);
        assertTrue(overview.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(overview.getTotalCost().compareTo(BigDecimal.ZERO) > 0);
        
        // 验证利润计算
        BigDecimal expectedProfit = overview.getTotalRevenue().subtract(overview.getTotalCost());
        assertEquals(0, expectedProfit.compareTo(overview.getGrossProfit()));
    }
}