package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.DashboardDTO;
import com.crossborder.analytics.dto.DashboardDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 实时数据看板服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    @Value("${analytics.dashboard.refresh-interval:30000}")
    private long refreshInterval;

    @Value("${analytics.dashboard.max-data-points:1000}")
    private int maxDataPoints;

    /**
     * 获取完整的数据看板
     */
    public DashboardDTO getDashboard() {
        log.info("Fetching dashboard data...");
        
        DashboardDTO dashboard = DashboardDTO.builder()
                .overview(getOverviewStats())
                .salesTrend(getSalesTrend())
                .platformDistribution(getPlatformDistribution())
                .topProducts(getTopProducts())
                .inventoryAlerts(getInventoryAlerts())
                .recentOrders(getRecentOrders())
                .financialOverview(getFinancialOverview())
                .lastUpdated(LocalDateTime.now())
                .build();
        
        log.info("Dashboard data fetched successfully");
        return dashboard;
    }

    /**
     * 获取今日销售趋势（按小时）
     */
    public List<TimeSeriesData> getTodaySalesTrend() {
        List<TimeSeriesData> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        // 生成24小时数据（实际应该从数据库获取）
        for (int hour = 0; hour < 24; hour++) {
            trend.add(TimeSeriesData.builder()
                    .date(String.format("%02d:00", hour))
                    .salesAmount(generateRandomSales())
                    .orderCount(generateRandomOrderCount())
                    .avgOrderValue(BigDecimal.valueOf(45.5).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        
        return trend;
    }

    /**
     * 获取销售趋势（按天）
     */
    public List<TimeSeriesData> getSalesTrend() {
        List<TimeSeriesData> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        // 生成最近30天数据
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trend.add(TimeSeriesData.builder()
                    .date(date.toString())
                    .salesAmount(generateRandomSales().multiply(BigDecimal.valueOf(10)))
                    .orderCount(generateRandomOrderCount() * 10)
                    .avgOrderValue(BigDecimal.valueOf(45.5).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        
        return trend;
    }

    /**
     * 获取概览统计
     */
    public OverviewStats getOverviewStats() {
        return OverviewStats.builder()
                .todaySales(generateRandomSales())
                .yesterdaySales(generateRandomSales())
                .monthSales(generateRandomSales().multiply(BigDecimal.valueOf(30)))
                .totalOrders(BigDecimal.valueOf(1250))
                .pendingOrders(BigDecimal.valueOf(45))
                .shippedOrders(BigDecimal.valueOf(320))
                .completedOrders(BigDecimal.valueOf(885))
                .activeProducts(328)
                .lowStockProducts(12)
                .build();
    }

    /**
     * 获取平台销售分布
     */
    public List<PlatformSales> getPlatformDistribution() {
        List<PlatformSales> distribution = new ArrayList<>();
        
        distribution.add(PlatformSales.builder()
                .platform("Amazon")
                .salesAmount(BigDecimal.valueOf(15000))
                .orderCount(150)
                .percentage(BigDecimal.valueOf(35.5))
                .build());
        
        distribution.add(PlatformSales.builder()
                .platform("Shopee")
                .salesAmount(BigDecimal.valueOf(12000))
                .orderCount(200)
                .percentage(BigDecimal.valueOf(28.3))
                .build());
        
        distribution.add(PlatformSales.builder()
                .platform("eBay")
                .salesAmount(BigDecimal.valueOf(8000))
                .orderCount(80)
                .percentage(BigDecimal.valueOf(18.9))
                .build());
        
        distribution.add(PlatformSales.builder()
                .platform("Lazada")
                .salesAmount(BigDecimal.valueOf(4500))
                .orderCount(60)
                .percentage(BigDecimal.valueOf(10.6))
                .build());
        
        distribution.add(PlatformSales.builder()
                .platform("TikTok")
                .salesAmount(BigDecimal.valueOf(2700))
                .orderCount(35)
                .percentage(BigDecimal.valueOf(6.4))
                .build());
        
        return distribution;
    }

    /**
     * 获取热门产品TOP10
     */
    public List<ProductPerformance> getTopProducts() {
        List<ProductPerformance> products = new ArrayList<>();
        String[] productNames = {"iPhone 15 Pro Case", "Wireless Earbuds", "Smart Watch Band", 
                "USB-C Cable", "Phone Screen Protector", "Portable Charger", 
                "Bluetooth Speaker", "Laptop Stand", "Mouse Pad", "Webcam Cover"};
        
        for (int i = 0; i < 10; i++) {
            products.add(ProductPerformance.builder()
                    .productId("P" + String.format("%04d", i + 1))
                    .productName(productNames[i])
                    .salesCount(150 - i * 10)
                    .salesAmount(BigDecimal.valueOf(150 - i * 10).multiply(BigDecimal.valueOf(45)))
                    .revenue(BigDecimal.valueOf(150 - i * 10).multiply(BigDecimal.valueOf(45)))
                    .rank(i + 1)
                    .build());
        }
        
        return products;
    }

    /**
     * 获取库存预警
     */
    public List<InventoryAlert> getInventoryAlerts() {
        List<InventoryAlert> alerts = new ArrayList<>();
        
        alerts.add(InventoryAlert.builder()
                .productId("P0001")
                .productName("iPhone 15 Pro Case")
                .currentStock(5)
                .safetyStock(20)
                .alertLevel("CRITICAL")
                .build());
        
        alerts.add(InventoryAlert.builder()
                .productId("P0002")
                .productName("Wireless Earbuds")
                .currentStock(15)
                .safetyStock(30)
                .alertLevel("LOW")
                .build());
        
        alerts.add(InventoryAlert.builder()
                .productId("P0003")
                .productName("Smart Watch Band")
                .currentStock(8)
                .safetyStock(25)
                .alertLevel("CRITICAL")
                .build());
        
        return alerts;
    }

    /**
     * 获取最近订单
     */
    public List<OrderSummary> getRecentOrders() {
        List<OrderSummary> orders = new ArrayList<>();
        String[] platforms = {"Amazon", "Shopee", "eBay", "Lazada", "TikTok"};
        String[] statuses = {"PENDING", "SHIPPED", "COMPLETED", "PROCESSING"};
        
        for (int i = 0; i < 10; i++) {
            orders.add(OrderSummary.builder()
                    .orderId("ORD" + System.currentTimeMillis() + i)
                    .platform(platforms[i % platforms.length])
                    .status(statuses[i % statuses.length])
                    .amount(BigDecimal.valueOf(Math.random() * 200 + 20).setScale(2, RoundingMode.HALF_UP))
                    .orderTime(LocalDateTime.now().minusMinutes(i * 15))
                    .build());
        }
        
        return orders;
    }

    /**
     * 获取财务概览
     */
    public FinancialOverview getFinancialOverview() {
        return FinancialOverview.builder()
                .totalRevenue(BigDecimal.valueOf(125000))
                .totalCost(BigDecimal.valueOf(75000))
                .grossProfit(BigDecimal.valueOf(50000))
                .profitMargin(BigDecimal.valueOf(40).setScale(2, RoundingMode.HALF_UP))
                .pendingPayment(BigDecimal.valueOf(3500))
                .pendingRefund(BigDecimal.valueOf(800))
                .build();
    }

    // 生成随机销售金额
    private BigDecimal generateRandomSales() {
        return BigDecimal.valueOf(Math.random() * 5000 + 1000)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // 生成随机订单数量
    private int generateRandomOrderCount() {
        return (int) (Math.random() * 50 + 10);
    }
}