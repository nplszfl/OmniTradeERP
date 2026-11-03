package com.crossborder.tenant.controller.tenant;

import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.dashboard.UsageStatisticsDTO;
import com.crossborder.tenant.service.UsageStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Map;

/**
 * 使用量统计控制器
 */
@Tag(name = "使用量统计", description = "租户使用量查询和记录")
@RestController
@RequestMapping("/api/tenant/usage")
@RequiredArgsConstructor
public class UsageStatisticsController {

    private final UsageStatisticsService usageStatisticsService;

    @Operation(summary = "获取当前月份使用量")
    @GetMapping("/current")
    public ResponseEntity<UsageStatisticsDTO> getCurrentMonthUsage(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(usageStatisticsService.getCurrentMonthUsage());
        }
    }

    @Operation(summary = "获取指定月份使用量")
    @GetMapping("/period")
    public ResponseEntity<UsageStatisticsDTO> getUsageByPeriod(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period,
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(usageStatisticsService.getUsageByPeriod(period));
        }
    }

    @Operation(summary = "记录API调用（内部接口）")
    @PostMapping("/record/api")
    public ResponseEntity<Map<String, String>> recordApiCall() {
        usageStatisticsService.recordApiCall();
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }

    @Operation(summary = "记录存储用量（内部接口）")
    @PostMapping("/record/storage")
    public ResponseEntity<Map<String, String>> recordStorage(@RequestParam long bytes) {
        usageStatisticsService.recordStorageUsage(bytes);
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }

    @Operation(summary = "记录AI调用（内部接口）")
    @PostMapping("/record/ai")
    public ResponseEntity<Map<String, String>> recordAiCall() {
        usageStatisticsService.recordAiCall();
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }
}