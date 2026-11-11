package com.crossborder.tenant.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.*;
import com.crossborder.tenant.dto.dashboard.DashboardDTO;
import com.crossborder.tenant.dto.dashboard.DailyUsageDTO;
import com.crossborder.tenant.dto.dashboard.UsageStatisticsDTO;
import com.crossborder.tenant.entity.*;
import com.crossborder.tenant.entity.billing.Bill;
import com.crossborder.tenant.entity.usage.UsageRecord;
import com.crossborder.tenant.repository.*;
import com.crossborder.tenant.repository.billing.BillRepository;
import com.crossborder.tenant.repository.usage.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户配置管理服务
 */
@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantRepository tenantRepository;
    private final TenantFeatureFlagRepository featureFlagRepository;
    private final BillRepository billRepository;
    private final UsageRecordRepository usageRecordRepository;

    // ==================== 套餐管理 ====================

    /**
     * 获取当前套餐信息
     */
    public TenantDTO getCurrentPlan() {
        String tenantId = TenantContext.getTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException("租户不存在"));
        return toTenantDTO(tenant);
    }

    /**
     * 变更套餐（升级/降级/续订）
     */
    @Transactional
    public TenantDTO changePlan(PlanChangeRequest request) {
        String tenantId = TenantContext.getTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException("租户不存在"));

        TenantPlan currentPlan = tenant.getPlan();
        TenantPlan targetPlan = TenantPlan.valueOf(request.getTargetPlan().toUpperCase());

        // 判断变更类型
        boolean isUpgrade = targetPlan.getPrice() > currentPlan.getPrice();
        String changeType = isUpgrade ? "UPGRADE" : "DOWNGRADE";

        // 更新套餐
        tenant.setPlan(targetPlan);
        
        // 续订时更新到期时间
        if ("RENEW".equals(request.getChangeType())) {
            if (tenant.getExpiresAt() == null || tenant.getExpiresAt().isBefore(LocalDateTime.now())) {
                tenant.setExpiresAt(LocalDateTime.now().plusDays(30));
            } else {
                tenant.setExpiresAt(tenant.getExpiresAt().plusDays(30));
            }
        }
        
        tenant = tenantRepository.save(tenant);

        // 创建订阅账单
        createSubscriptionBill(tenant, targetPlan);

        return toTenantDTO(tenant);
    }

    private void createSubscriptionBill(Tenant tenant, TenantPlan plan) {
        YearMonth currentPeriod = YearMonth.now();
        
        // 检查是否已存在本月账单
        Optional<Bill> existingBill = billRepository.findByTenantIdAndBillingPeriod(
                tenant.getId(), currentPeriod);
        
        if (existingBill.isEmpty() && plan.getPrice() > 0) {
            Bill bill = Bill.builder()
                    .tenantId(tenant.getId())
                    .billingPeriod(currentPeriod)
                    .billType(Bill.BillType.SUBSCRIPTION)
                    .status(Bill.BillStatus.PENDING)
                    .subscriptionFee(BigDecimal.valueOf(plan.getPrice()))
                    .totalAmount(BigDecimal.valueOf(plan.getPrice()))
                    .dueDate(currentPeriod.atEndOfMonth().atStartOfDay())
                    .build();
            billRepository.save(bill);
        }
    }

    // ==================== 功能开关 ====================

    /**
     * 获取租户所有功能开关
     */
    public List<FeatureFlagDTO> getFeatureFlags() {
        String tenantId = TenantContext.getTenant();
        List<TenantFeatureFlag> flags = featureFlagRepository.findByTenantId(tenantId);
        
        // 如果没有，初始化默认功能开关
        if (flags.isEmpty()) {
            flags = initializeDefaultFeatures(tenantId);
        }
        
        return flags.stream().map(this::toFeatureFlagDTO).collect(Collectors.toList());
    }

    /**
     * 初始化默认功能开关
     */
    @Transactional
    public List<TenantFeatureFlag> initializeDefaultFeatures(String tenantId) {
        List<TenantFeatureFlag> flags = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : TenantFeatureFlag.DEFAULT_FEATURES.entrySet()) {
            TenantFeatureFlag flag = TenantFeatureFlag.builder()
                    .tenantId(tenantId)
                    .featureKey(entry.getKey())
                    .featureName(entry.getValue())
                    .enabled(false)
                    .build();
            flags.add(featureFlagRepository.save(flag));
        }
        
        return flags;
    }

    /**
     * 更新功能开关
     */
    @Transactional
    public FeatureFlagDTO updateFeatureFlag(String featureKey, Boolean enabled, String config) {
        String tenantId = TenantContext.getTenant();
        
        TenantFeatureFlag flag = featureFlagRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .orElseThrow(() -> new BizException("功能开关不存在: " + featureKey));
        
        flag.setEnabled(enabled);
        if (config != null) {
            flag.setConfig(config);
        }
        
        flag = featureFlagRepository.save(flag);
        return toFeatureFlagDTO(flag);
    }

    /**
     * 检查功能是否启用
     */
    public boolean isFeatureEnabled(String featureKey) {
        String tenantId = TenantContext.getTenant();
        
        return featureFlagRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .map(TenantFeatureFlag::getEnabled)
                .orElse(false);
    }

    // ==================== 私有方法 ====================

    private TenantDTO toTenantDTO(Tenant tenant) {
        return TenantDTO.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .domain(tenant.getDomain())
                .plan(tenant.getPlan())
                .status(tenant.getStatus())
                .region(tenant.getRegion())
                .settings(tenant.getSettings())
                .expiresAt(tenant.getExpiresAt())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    private FeatureFlagDTO toFeatureFlagDTO(TenantFeatureFlag flag) {
        return FeatureFlagDTO.builder()
                .id(flag.getId())
                .tenantId(flag.getTenantId())
                .featureKey(flag.getFeatureKey())
                .featureName(flag.getFeatureName())
                .enabled(flag.getEnabled())
                .config(flag.getConfig())
                .description(flag.getDescription())
                .build();
    }
}