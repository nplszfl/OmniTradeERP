package com.crossborder.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实时数据看板DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

    /**
     * 概览统计
     */
    private OverviewStats overview;

    /**
     * 销售趋势数据（按天）
     */
    private List<TimeSeriesData> salesTrend;

    /**
     * 平台销售分布
     */
    private List<PlatformSales> platformDistribution;

    /**
     * 热门产品TOP10
     */
    private List<ProductPerformance> topProducts;

    /**
     * 库存预警
     */
    private List<InventoryAlert> inventoryAlerts;

    /**
     * 最近订单
     */
    private List<OrderSummary> recentOrders;

    /**
     * 财务概览
     */
    private FinancialOverview financialOverview;

    /**
     * 数据更新时间
     */
    private LocalDateTime lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewStats {
        private BigDecimal todaySales;
        private BigDecimal yesterdaySales;
        private BigDecimal monthSales;
        private BigDecimal totalOrders;
        private BigDecimal pendingOrders;
        private BigDecimal shippedOrders;
        private BigDecimal completedOrders;
        private Integer activeProducts;
        private Integer lowStockProducts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesData {
        private String date;
        private BigDecimal salesAmount;
        private Integer orderCount;
        private BigDecimal avgOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformSales {
        private String platform;
        private BigDecimal salesAmount;
        private Integer orderCount;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPerformance {
        private String productId;
        private String productName;
        private Integer salesCount;
        private BigDecimal salesAmount;
        private BigDecimal revenue;
        private Integer rank;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryAlert {
        private String productId;
        private String productName;
        private Integer currentStock;
        private Integer safetyStock;
        private String alertLevel; // LOW, CRITICAL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private String orderId;
        private String platform;
        private String status;
        private BigDecimal amount;
        private LocalDateTime orderTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialOverview {
        private BigDecimal totalRevenue;
        private BigDecimal totalCost;
        private BigDecimal grossProfit;
        private BigDecimal profitMargin;
        private BigDecimal pendingPayment;
        private BigDecimal pendingRefund;
    }
}