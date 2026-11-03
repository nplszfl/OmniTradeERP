package com.crossborder.tenant.controller.tenant;

import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.*;
import com.crossborder.tenant.entity.TenantPlan;
import com.crossborder.tenant.service.BillingService;
import com.crossborder.tenant.service.DashboardService;
import com.crossborder.tenant.service.TenantConfigService;
import com.crossborder.tenant.service.UsageStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 租户配置管理控制器
 */
@Tag(name = "租户配置", description = "套餐管理、功能开关、配置管理")
@RestController
@RequestMapping("/api/tenant/config")
@RequiredArgsConstructor
public class TenantConfigController {

    private final TenantConfigService tenantConfigService;

    @Operation(summary = "获取当前套餐信息")
    @GetMapping("/plan")
    public ResponseEntity<TenantDTO> getCurrentPlan(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(tenantConfigService.getCurrentPlan());
        }
    }

    @Operation(summary = "变更套餐")
    @PostMapping("/plan/change")
    public ResponseEntity<TenantDTO> changePlan(@RequestBody PlanChangeRequest request) {
        return ResponseEntity.ok(tenantConfigService.changePlan(request));
    }

    @Operation(summary = "获取可用套餐列表")
    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> getAvailablePlans() {
        List<Map<String, Object>> plans = java.util.Arrays.stream(TenantPlan.values())
                .map(plan -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("name", plan.name());
                    m.put("price", plan.getPrice());
                    m.put("orderLimit", plan.getOrderLimit());
                    m.put("platformLimit", plan.getPlatformLimit());
                    m.put("aiQuota", plan.getAiQuota());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(plans);
    }

    @Operation(summary = "获取功能开关列表")
    @GetMapping("/features")
    public ResponseEntity<List<FeatureFlagDTO>> getFeatureFlags(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(tenantConfigService.getFeatureFlags());
        }
    }

    @Operation(summary = "更新功能开关")
    @PutMapping("/features/{featureKey}")
    public ResponseEntity<FeatureFlagDTO> updateFeatureFlag(
            @PathVariable String featureKey,
            @RequestParam Boolean enabled,
            @RequestParam(required = false) String config,
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(tenantConfigService.updateFeatureFlag(featureKey, enabled, config));
        }
    }

    @Operation(summary = "检查功能是否启用")
    @GetMapping("/features/{featureKey}/enabled")
    public ResponseEntity<Map<String, Boolean>> isFeatureEnabled(
            @PathVariable String featureKey,
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(Map.of("enabled", tenantConfigService.isFeatureEnabled(featureKey)));
        }
    }
}