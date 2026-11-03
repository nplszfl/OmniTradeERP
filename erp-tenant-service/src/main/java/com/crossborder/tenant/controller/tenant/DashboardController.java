package com.crossborder.tenant.controller.tenant;

import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.dashboard.DailyUsageDTO;
import com.crossborder.tenant.dto.dashboard.DashboardDTO;
import com.crossborder.tenant.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 租户仪表盘控制器
 */
@Tag(name = "仪表盘", description = "租户数据概览和统计分析")
@RestController
@RequestMapping("/api/tenant/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取仪表盘数据")
    @GetMapping
    public ResponseEntity<DashboardDTO> getDashboard(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(dashboardService.getDashboard());
        }
    }

    @Operation(summary = "获取使用量趋势")
    @GetMapping("/trend")
    public ResponseEntity<List<DailyUsageDTO>> getUsageTrend(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(dashboardService.getUsageTrend());
        }
    }

    @Operation(summary = "获取配额使用概览")
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuotaOverview(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(dashboardService.getQuotaOverview());
        }
    }
}