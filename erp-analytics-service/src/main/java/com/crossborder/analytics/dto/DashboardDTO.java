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
     * 订单状态分布
     *
     * <p>看板「订单状态分布」图的数据源。
     * 之前前端这张图是写死的柱子（[5, 15, 45, 30, 3]）。</p>
     */
    private List<OrderStatusCount> orderStatusDistribution;

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
        /** 今日订单笔数（与 todaySales 是两回事：一个数笔数，一个数金额） */
        private BigDecimal todayOrders;
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
    public static class OrderStatusCount {
        /** 订单状态（PENDING / SHIPPED / COMPLETED ...） */
        private String status;
        /** 该状态的订单数 */
        private Long count;
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