package com.crossborder.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 前端友好的「扁平」分析视图
 *
 * <p>⚠️ 为什么需要这个 DTO：</p>
 * <p>后端原本的 {@link DashboardDTO} 是嵌套结构（overview / platformDistribution /
 * topProducts / salesTrend ...），而前端 erp-web/src/views/analytics/index.vue 直接读
 * {@code data.totalOrders}、{@code data.revenueByPlatform[].revenue}、
 * {@code data.topProducts[].name} 这些扁平字段 —— 两边对不上，页面永远是空的。</p>
 * <p>这个 DTO 的字段名严格对齐前端消费方式，与 {@link DashboardDTO} 并存：
 * 嵌套结构给新调用方，扁平结构给现有前端。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDTO {

    /** 订单总数 */
    private BigDecimal totalOrders;

    /** 总营收 */
    private BigDecimal totalRevenue;

    /** 活跃客户数 */
    private Integer totalCustomers;

    /** 在售 SKU 数 */
    private Integer totalProducts;

    /** 各平台营收 */
    private List<PlatformRevenue> revenueByPlatform;

    /** Top 商品 */
    private List<TopProduct> topProducts;

    /** 客户分布 */
    private List<CustomerDistribution> customersByRegion;

    /** 数据更新时间 */
    private java.time.LocalDateTime lastUpdated;

    /**
     * 平台营收（前端 {@code prop="platform"} / {@code prop="revenue"}）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformRevenue {
        private String platform;
        private BigDecimal revenue;
        private Integer orders;
    }

    /**
     * Top 商品（前端 {@code prop="name"} / {@code prop="sold"} / {@code prop="revenue"}）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProduct {
        private String sku;
        private String name;
        private Integer sold;
        private BigDecimal revenue;
    }

    /**
     * 客户分布
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDistribution {
        private String region;
        private Integer customers;
        private BigDecimal revenue;
    }

    /**
     * 趋势点（前端 {@code prop="date"} / {@code prop="count"} / {@code prop="revenue"}）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private Integer count;
        private BigDecimal revenue;
    }
}
