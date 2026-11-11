package com.crossborder.tenant.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.dashboard.DailyUsageDTO;
import com.crossborder.tenant.dto.dashboard.UsageStatisticsDTO;
import com.crossborder.tenant.entity.*;
import com.crossborder.tenant.entity.usage.UsageRecord;
import com.crossborder.tenant.repository.TenantRepository;
import com.crossborder.tenant.repository.usage.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 租户使用量统计服务
 */
@Service
@RequiredArgsConstructor
public class UsageStatisticsService {

    private final TenantRepository tenantRepository;
    private final UsageRecordRepository usageRecordRepository;

    private static final long GB = 1024L * 1024 * 1024;
    private static final long MB = 1024L * 1024;

    /**
     * 获取当前月份使用量统计
     */
    public UsageStatisticsDTO getCurrentMonthUsage() {
        return getUsageByPeriod(YearMonth.now());
    }

    /**
     * 获取指定月份使用量统计
     */
    public UsageStatisticsDTO getUsageByPeriod(YearMonth period) {
        String tenantId = TenantContext.getTenant();
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException("租户不存在"));
        
        TenantPlan plan = tenant.getPlan();
        
        // 获取使用量记录
        UsageRecord record = usageRecordRepository
                .findByTenantIdAndBillingPeriod(tenantId, period)
                .orElse(UsageRecord.builder()
                        .tenantId(tenantId)
                        .billingPeriod(period)
                        .build());
        
        return buildUsageStatistics(tenantId, record, plan, period);
    }

    /**
     * 记录API调用（每次调用后更新）
     */
    @Transactional
    public void recordApiCall() {
        String tenantId = TenantContext.getTenant();
        recordUsage(tenantId, "API_CALLS", 1L);
    }

    /**
     * 记录存储用量
     */
    @Transactional
    public void recordStorageUsage(long bytes) {
        String tenantId = TenantContext.getTenant();
        recordUsage(tenantId, "STORAGE", bytes);
    }

    /**
     * 记录AI调用
     */
    @Transactional
    public void recordAiCall() {
        String tenantId = TenantContext.getTenant();
        recordUsage(tenantId, "AI_CALLS", 1L);
    }

    /**
     * 记录订单数量
     */
    @Transactional
    public void recordOrder(int count) {
        String tenantId = TenantContext.getTenant();
        recordUsage(tenantId, "ORDERS", count);
    }

    /**
     * 记录平台数量
     */
    @Transactional
    public void recordPlatform(int count) {
        String tenantId = TenantContext.getTenant();
        recordUsage(tenantId, "PLATFORMS", count);
    }

    private void recordUsage(String tenantId, String type, long amount) {
        YearMonth currentPeriod = YearMonth.now();
        
        UsageRecord record = usageRecordRepository
                .findByTenantIdAndBillingPeriod(tenantId, currentPeriod)
                .orElseGet(() -> {
                    UsageRecord r = UsageRecord.builder()
                            .tenantId(tenantId)
                            .billingPeriod(currentPeriod)
                            .build();
                    return usageRecordRepository.save(r);
                });
        
        switch (type) {
            case "API_CALLS":
                record.setApiCalls(record.getApiCalls() + amount);
                break;
            case "STORAGE":
                record.setStorageBytes(record.getStorageBytes() + amount);
                break;
            case "AI_CALLS":
                record.setAiCalls(record.getAiCalls() + amount);
                break;
            case "ORDERS":
                record.setOrderCount(record.getOrderCount() + (int) amount);
                break;
            case "PLATFORMS":
                record.setPlatformCount(record.getPlatformCount() + (int) amount);
                break;
        }
        
        usageRecordRepository.save(record);
    }

    private UsageStatisticsDTO buildUsageStatistics(String tenantId, UsageRecord record, 
                                                     TenantPlan plan, YearMonth period) {
        // 获取配额限制
        int apiLimit = plan.getOrderLimit();  // 使用orderLimit作为API配额
        long storageLimit = 10L * GB;  // 默认10GB
        int orderLimit = plan.getOrderLimit();
        int platformLimit = plan.getPlatformLimit();
        long aiLimit = plan.getAiQuota() * 1000L;

        // 计算使用百分比
        Double apiPercent = calculatePercent(record.getApiCalls(), apiLimit);
        Double storagePercent = calculatePercent(record.getStorageBytes(), storageLimit);
        Double orderPercent = calculatePercent((long) record.getOrderCount(), orderLimit);
        Double platformPercent = calculatePercent((long) record.getPlatformCount(), platformLimit);
        Double aiPercent = calculatePercent(record.getAiCalls(), aiLimit);

        // 获取7天趋势（模拟数据，实际可从日志表获取）
        List<DailyUsageDTO> dailyTrend = generateDailyTrend();

        return UsageStatisticsDTO.builder()
                .tenantId(tenantId)
                .billingPeriod(period.toString())
                .apiCalls(record.getApiCalls())
                .apiCallsLimit((long) apiLimit)
                .apiCallsPercent(apiPercent)
                .storageBytes(record.getStorageBytes())
                .storageLimit(storageLimit)
                .storagePercent(storagePercent)
                .storageDisplay(formatBytes(record.getStorageBytes()))
                .orderCount(record.getOrderCount())
                .orderLimit(orderLimit)
                .orderPercent(orderPercent)
                .platformCount(record.getPlatformCount())
                .platformLimit(platformLimit)
                .platformPercent(platformPercent)
                .aiCalls(record.getAiCalls())
                .aiCallsLimit(aiLimit)
                .aiCallsPercent(aiPercent)
                .dailyTrend(dailyTrend)
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private Double calculatePercent(long used, long limit) {
        if (limit <= 0) return 0.0;
        double percent = (double) used / limit * 100;
        return Math.min(percent, 100.0);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < MB) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < GB) return String.format("%.2f MB", bytes / (double) MB);
        return String.format("%.2f GB", bytes / (double) GB);
    }

    /**
     * 生成7天趋势数据（实际项目中应从数据库查询）
     */
    private List<DailyUsageDTO> generateDailyTrend() {
        LocalDate today = LocalDate.now();
        return IntStream.rangeClosed(1, 7)
                .mapToObj(i -> {
                    LocalDate date = today.minusDays(7 - i);
                    // 模拟数据
                    long apiCalls = (long) (Math.random() * 1000);
                    int orders = (int) (Math.random() * 50);
                    long storage = (long) (Math.random() * 100 * MB);
                    
                    return DailyUsageDTO.builder()
                            .date(date)
                            .apiCalls(apiCalls)
                            .orders(orders)
                            .storageBytes(storage)
                            .build();
                })
                .collect(Collectors.toList());
    }
}