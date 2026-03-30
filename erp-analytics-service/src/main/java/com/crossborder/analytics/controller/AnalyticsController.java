package com.crossborder.analytics.controller;

import com.crossborder.analytics.dto.*;
import com.crossborder.analytics.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据分析API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final DashboardService dashboardService;
    private final SalesForecastService salesForecastService;
    private final ROIAnalysisService roiAnalysisService;
    private final CompetitorAnalysisService competitorAnalysisService;

    // ========== 数据看板 API ==========

    /**
     * 获取完整数据看板
     * GET /api/v1/analytics/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> getDashboard() {
        log.info("GET /api/v1/analytics/dashboard");
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    /**
     * 获取今日销售趋势（按小时）
     * GET /api/v1/analytics/dashboard/today-trend
     */
    @GetMapping("/dashboard/today-trend")
    public ResponseEntity<List<DashboardDTO.TimeSeriesData>> getTodaySalesTrend() {
        log.info("GET /api/v1/analytics/dashboard/today-trend");
        return ResponseEntity.ok(dashboardService.getTodaySalesTrend());
    }

    /**
     * 获取销售趋势（按天）
     * GET /api/v1/analytics/dashboard/sales-trend
     */
    @GetMapping("/dashboard/sales-trend")
    public ResponseEntity<List<DashboardDTO.TimeSeriesData>> getSalesTrend() {
        log.info("GET /api/v1/analytics/dashboard/sales-trend");
        return ResponseEntity.ok(dashboardService.getSalesTrend());
    }

    /**
     * 获取概览统计
     * GET /api/v1/analytics/dashboard/overview
     */
    @GetMapping("/dashboard/overview")
    public ResponseEntity<DashboardDTO.OverviewStats> getOverviewStats() {
        log.info("GET /api/v1/analytics/dashboard/overview");
        return ResponseEntity.ok(dashboardService.getOverviewStats());
    }

    /**
     * 获取平台销售分布
     * GET /api/v1/analytics/dashboard/platform-distribution
     */
    @GetMapping("/dashboard/platform-distribution")
    public ResponseEntity<List<DashboardDTO.PlatformSales>> getPlatformDistribution() {
        log.info("GET /api/v1/analytics/dashboard/platform-distribution");
        return ResponseEntity.ok(dashboardService.getPlatformDistribution());
    }

    /**
     * 获取热门产品TOP10
     * GET /api/v1/analytics/dashboard/top-products
     */
    @GetMapping("/dashboard/top-products")
    public ResponseEntity<List<DashboardDTO.ProductPerformance>> getTopProducts() {
        log.info("GET /api/v1/analytics/dashboard/top-products");
        return ResponseEntity.ok(dashboardService.getTopProducts());
    }

    /**
     * 获取库存预警
     * GET /api/v1/analytics/dashboard/inventory-alerts
     */
    @GetMapping("/dashboard/inventory-alerts")
    public ResponseEntity<List<DashboardDTO.InventoryAlert>> getInventoryAlerts() {
        log.info("GET /api/v1/analytics/dashboard/inventory-alerts");
        return ResponseEntity.ok(dashboardService.getInventoryAlerts());
    }

    /**
     * 获取最近订单
     * GET /api/v1/analytics/dashboard/recent-orders
     */
    @GetMapping("/dashboard/recent-orders")
    public ResponseEntity<List<DashboardDTO.OrderSummary>> getRecentOrders() {
        log.info("GET /api/v1/analytics/dashboard/recent-orders");
        return ResponseEntity.ok(dashboardService.getRecentOrders());
    }

    // ========== 销售预测 API ==========

    /**
     * 获取产品销售预测
     * GET /api/v1/analytics/forecast/{productId}
     */
    @GetMapping("/forecast/{productId}")
    public ResponseEntity<SalesForecastDTO> getSalesForecast(
            @PathVariable String productId,
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/v1/analytics/forecast/{}", productId);
        return ResponseEntity.ok(salesForecastService.getSalesForecast(productId, days));
    }

    /**
     * 批量预测多个产品
     * POST /api/v1/analytics/forecast/batch
     */
    @PostMapping("/forecast/batch")
    public ResponseEntity<List<SalesForecastDTO>> batchForecast(
            @RequestBody List<String> productIds,
            @RequestParam(defaultValue = "30") int days) {
        log.info("POST /api/v1/analytics/forecast/batch");
        return ResponseEntity.ok(salesForecastService.batchForecast(productIds, days));
    }

    // ========== ROI分析 API ==========

    /**
     * 获取ROI分析
     * GET /api/v1/analytics/roi
     */
    @GetMapping("/roi")
    public ResponseEntity<ROIAnalysisDTO> getROIAnalysis(
            @RequestParam String dimension,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /api/v1/analytics/roi?dimension={}", dimension);
        return ResponseEntity.ok(roiAnalysisService.getROIAnalysis(dimension, startDate, endDate));
    }

    /**
     * 获取产品ROI分析
     * GET /api/v1/analytics/roi/product
     */
    @GetMapping("/roi/product")
    public ResponseEntity<ROIAnalysisDTO> getProductROI(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /api/v1/analytics/roi/product");
        return ResponseEntity.ok(roiAnalysisService.getProductROI(startDate, endDate));
    }

    /**
     * 获取平台ROI分析
     * GET /api/v1/analytics/roi/platform
     */
    @GetMapping("/roi/platform")
    public ResponseEntity<ROIAnalysisDTO> getPlatformROI(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /api/v1/analytics/roi/platform");
        return ResponseEntity.ok(roiAnalysisService.getPlatformROI(startDate, endDate));
    }

    // ========== 竞品分析 API ==========

    /**
     * 获取竞品分析
     * GET /api/v1/analytics/competitor/{productId}
     */
    @GetMapping("/competitor/{productId}")
    public ResponseEntity<CompetitorAnalysisDTO> getCompetitorAnalysis(
            @PathVariable String productId,
            @RequestParam(defaultValue = "PRICE") String analysisType) {
        log.info("GET /api/v1/analytics/competitor/{}", productId);
        return ResponseEntity.ok(competitorAnalysisService.getCompetitorAnalysis(productId, analysisType));
    }

    /**
     * 健康检查
     * GET /api/v1/analytics/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}