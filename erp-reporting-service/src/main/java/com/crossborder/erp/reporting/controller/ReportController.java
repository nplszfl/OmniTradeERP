package com.crossborder.erp.reporting.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.reporting.dto.ReportQueryDTO;
import com.crossborder.erp.reporting.dto.ReportGenerateRequest;
import com.crossborder.erp.reporting.dto.ReportListItemDTO;
import com.crossborder.erp.reporting.entity.SalesReport;
import com.crossborder.erp.reporting.entity.InventoryReport;
import com.crossborder.erp.reporting.entity.FinanceReport;
import com.crossborder.erp.reporting.service.ReportTaskService;
import com.crossborder.erp.reporting.service.SalesReportService;
import com.crossborder.erp.reporting.service.InventoryReportService;
import com.crossborder.erp.reporting.service.FinanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报表控制器
 */
@Slf4j
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final SalesReportService salesReportService;
    private final InventoryReportService inventoryReportService;
    private final FinanceReportService financeReportService;
    private final ReportTaskService reportTaskService;

    // ========== 销售报表 ==========

    @PostMapping("/sales/daily")
    public Result<SalesReport> generateDailySalesReport(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String storeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        SalesReport report = salesReportService.generateDailyReport(platform, storeId, date);
        return Result.success(report);
    }

    @PostMapping("/sales/weekly")
    public Result<SalesReport> generateWeeklySalesReport(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String storeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate weekStart) {
        SalesReport report = salesReportService.generateWeeklyReport(platform, storeId, weekStart);
        return Result.success(report);
    }

    @PostMapping("/sales/monthly")
    public Result<SalesReport> generateMonthlySalesReport(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String storeId,
            @RequestParam int year,
            @RequestParam int month) {
        SalesReport report = salesReportService.generateMonthlyReport(platform, storeId, year, month);
        return Result.success(report);
    }

    @GetMapping("/sales")
    public Result<IPage<SalesReport>> querySalesReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String storeId) {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType(reportType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPlatform(platform);
        query.setStoreId(storeId);
        return Result.success(salesReportService.queryReports(query, page, size));
    }

    @GetMapping("/sales/export")
    public Result<byte[]> exportSalesReport(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String platform) {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType(reportType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPlatform(platform);
        return Result.success(salesReportService.exportExcel(query));
    }

    // ========== 库存报表 ==========

    @PostMapping("/inventory/daily")
    public Result<InventoryReport> generateDailyInventoryReport(
            @RequestParam(required = false) String warehouseId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        InventoryReport report = inventoryReportService.generateDailyReport(warehouseId, date);
        return Result.success(report);
    }

    @GetMapping("/inventory")
    public Result<IPage<InventoryReport>> queryInventoryReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String warehouseId) {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType(reportType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setWarehouseId(warehouseId);
        return Result.success(inventoryReportService.queryReports(query, page, size));
    }

    @GetMapping("/inventory/export")
    public Result<byte[]> exportInventoryReport(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String warehouseId) {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType(reportType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setWarehouseId(warehouseId);
        return Result.success(inventoryReportService.exportExcel(query));
    }

    // ========== 财务报表 ==========

    @PostMapping("/finance/daily")
    public Result<FinanceReport> generateDailyFinanceReport(
            @RequestParam(required = false) String warehouseId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        FinanceReport report = financeReportService.generateDailyReport(warehouseId, date);
        return Result.success(report);
    }

    @GetMapping("/finance")
    public Result<IPage<FinanceReport>> queryFinanceReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String warehouseId) {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType(reportType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setWarehouseId(warehouseId);
        return Result.success(financeReportService.queryReports(query, page, size));
    }

    @GetMapping("/finance/export")
    public Result<byte[]> exportFinanceReport(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String warehouseId) {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setReportType(reportType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setWarehouseId(warehouseId);
        return Result.success(financeReportService.exportExcel(query));
    }

    // ========== 报表台账 ==========

    /**
     * 报表台账列表
     *
     * <p>⚠️ 这个接口之前不存在 —— 前端 erp-web/src/views/report/index.vue 调用
     * {@code GET /report/list} 直接 404，页面靠 catch 分支的 mock 数据撑着，
     * 看上去「有数据」，实际是前端写死的假数据。</p>
     */
    @GetMapping("/list")
    public Result<List<ReportListItemDTO>> listReports() {
        return Result.success(reportTaskService.list());
    }

    /**
     * 提交报表生成
     */
    @PostMapping("/generate")
    public Result<ReportListItemDTO> generateReport(@RequestBody ReportGenerateRequest request) {
        return Result.success(reportTaskService.generate(request));
    }

    /**
     * 可用的报表模板
     */
    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates() {
        return Result.success(reportTaskService.templates());
    }

    /**
     * 删除一条报表台账记录
     *
     * <p>📌 这个接口之前**不存在** → 前端的「删除」按钮只改本地数组
     * 并弹「已删除」→ **后端数据一条没动**，刷新页面原样回来（规则 51 / 72）。</p>
     *
     * <p>⚠️ 口径写在 **URL 上**（{@code /report/{type}/{id}}）：
     * 报表分属 sales / inventory / finance 三张表，id 在三张表里各自自增
     * → 光给 id 不知道该删哪张表 → 必须带 type。</p>
     */
    @DeleteMapping("/{type}/{id}")
    public Result<Void> deleteReport(@PathVariable String type, @PathVariable Long id) {
        reportTaskService.delete(type, id);
        return Result.success();
    }
}