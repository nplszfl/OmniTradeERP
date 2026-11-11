package com.crossborder.tenant.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.billing.BillDTO;
import com.crossborder.tenant.dto.billing.BillingRuleDTO;
import com.crossborder.tenant.entity.*;
import com.crossborder.tenant.entity.billing.Bill;
import com.crossborder.tenant.entity.billing.BillingRule;
import com.crossborder.tenant.entity.usage.UsageRecord;
import com.crossborder.tenant.repository.*;
import com.crossborder.tenant.repository.billing.BillRepository;
import com.crossborder.tenant.repository.billing.BillingRuleRepository;
import com.crossborder.tenant.repository.usage.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户计费服务
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final TenantRepository tenantRepository;
    private final BillRepository billRepository;
    private final BillingRuleRepository billingRuleRepository;
    private final UsageRecordRepository usageRecordRepository;

    private static final long GB = 1024L * 1024 * 1024;

    // ==================== 账单管理 ====================

    /**
     * 获取当前租户的所有账单
     */
    public List<BillDTO> getBills() {
        String tenantId = TenantContext.getTenant();
        return billRepository.findByTenantIdOrderByBillingPeriodDesc(tenantId)
                .stream()
                .map(this::toBillDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取当前月份的账单
     */
    public BillDTO getCurrentBill() {
        String tenantId = TenantContext.getTenant();
        YearMonth current = YearMonth.now();
        
        return billRepository.findByTenantIdAndBillingPeriod(tenantId, current)
                .map(this::toBillDTO)
                .orElse(null);
    }

    /**
     * 获取待支付账单
     */
    public List<BillDTO> getPendingBills() {
        String tenantId = TenantContext.getTenant();
        return billRepository
                .findByTenantIdAndStatusOrderByDueDateAsc(tenantId, Bill.BillStatus.PENDING)
                .stream()
                .map(this::toBillDTO)
                .collect(Collectors.toList());
    }

    /**
     * 支付账单
     */
    @Transactional
    public BillDTO payBill(String billId, String paymentMethod) {
        String tenantId = TenantContext.getTenant();
        
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BizException("账单不存在"));
        
        if (!bill.getTenantId().equals(tenantId)) {
            throw new BizException("无权限操作此账单");
        }
        
        if (bill.getStatus() != Bill.BillStatus.PENDING) {
            throw new BizException("账单状态不正确");
        }
        
        // 更新账单状态
        bill.setStatus(Bill.BillStatus.PAID);
        bill.setPaymentMethod(paymentMethod);
        bill.setPaidAt(LocalDateTime.now());
        bill.setPaidAmount(bill.getTotalAmount());
        
        bill = billRepository.save(bill);
        return toBillDTO(bill);
    }

    // ==================== 计费规则 ====================

    /**
     * 获取所有计费规则
     */
    public List<BillingRuleDTO> getBillingRules() {
        return billingRuleRepository.findByActiveTrue()
                .stream()
                .map(this::toBillingRuleDTO)
                .collect(Collectors.toList());
    }

    /**
     * 创建计费规则（管理员）
     */
    @Transactional
    public BillingRuleDTO createBillingRule(BillingRuleDTO dto) {
        BillingRule rule = BillingRule.builder()
                .name(dto.getName())
                .resourceType(BillingRule.ResourceType.valueOf(dto.getResourceType()))
                .tierType(BillingRule.TierType.valueOf(dto.getTierType()))
                .freeQuota(dto.getFreeQuota())
                .unitPrice(dto.getUnitPrice())
                .tierConfig(dto.getTierConfig())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
        
        rule = billingRuleRepository.save(rule);
        return toBillingRuleDTO(rule);
    }

    // ==================== 账单生成 ====================

    /**
     * 生成当月账单（定时任务调用）
     */
    @Transactional
    public void generateMonthlyBill(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException("租户不存在"));
        
        YearMonth currentPeriod = YearMonth.now();
        
        // 检查是否已存在
        if (billRepository.findByTenantIdAndBillingPeriod(tenantId, currentPeriod).isPresent()) {
            return; // 已存在则跳过
        }
        
        // 获取当月使用量
        UsageRecord usage = usageRecordRepository
                .findByTenantIdAndBillingPeriod(tenantId, currentPeriod)
                .orElse(UsageRecord.builder()
                        .tenantId(tenantId)
                        .billingPeriod(currentPeriod)
                        .build());
        
        // 计算用量费用
        BigDecimal usageFee = calculateUsageFee(tenant, usage);
        
        // 计算总金额
        BigDecimal subscriptionFee = BigDecimal.valueOf(tenant.getPlan().getPrice());
        BigDecimal totalAmount = subscriptionFee.add(usageFee);
        
        // 创建账单
        Bill bill = Bill.builder()
                .tenantId(tenantId)
                .billingPeriod(currentPeriod)
                .billType(Bill.BillType.USAGE)
                .status(Bill.BillStatus.PENDING)
                .subscriptionFee(subscriptionFee)
                .usageFee(usageFee)
                .totalAmount(totalAmount)
                .dueDate(currentPeriod.atEndOfMonth().atStartOfDay())
                .build();
        
        billRepository.save(bill);
    }

    /**
     * 计算用量费用
     */
    private BigDecimal calculateUsageFee(Tenant tenant, UsageRecord usage) {
        BigDecimal totalFee = BigDecimal.ZERO;
        
        // 获取所有活跃的计费规则
        List<BillingRule> rules = billingRuleRepository.findByActiveTrue();
        
        for (BillingRule rule : rules) {
            BigDecimal fee = calculateFeeByRule(rule, usage);
            totalFee = totalFee.add(fee);
        }
        
        return totalFee.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 根据计费规则计算费用
     */
    private BigDecimal calculateFeeByRule(BillingRule rule, UsageRecord usage) {
        long used = 0;
        
        switch (rule.getResourceType()) {
            case API_CALLS:
                used = usage.getApiCalls();
                break;
            case STORAGE:
                used = usage.getStorageBytes() / GB; // 按GB计算
                break;
            case ORDERS:
                used = usage.getOrderCount();
                break;
            case PLATFORMS:
                used = usage.getPlatformCount();
                break;
            case AI_CALLS:
                used = usage.getAiCalls();
                break;
            default:
                return BigDecimal.ZERO;
        }
        
        // 减去免费额度
        long chargeable = Math.max(0, used - rule.getFreeQuota());
        
        return rule.getUnitPrice().multiply(BigDecimal.valueOf(chargeable));
    }

    // ==================== DTO转换 ====================

    private BillDTO toBillDTO(Bill bill) {
        return BillDTO.builder()
                .id(bill.getId())
                .tenantId(bill.getTenantId())
                .billingPeriod(bill.getBillingPeriod())
                .billType(bill.getBillType().name())
                .status(bill.getStatus().name())
                .subscriptionFee(bill.getSubscriptionFee())
                .usageFee(bill.getUsageFee())
                .discount(bill.getDiscount())
                .totalAmount(bill.getTotalAmount())
                .paidAmount(bill.getPaidAmount())
                .paymentMethod(bill.getPaymentMethod())
                .paidAt(bill.getPaidAt())
                .dueDate(bill.getDueDate())
                .details(bill.getDetails())
                .remark(bill.getRemark())
                .createdAt(bill.getCreatedAt())
                .build();
    }

    private BillingRuleDTO toBillingRuleDTO(BillingRule rule) {
        return BillingRuleDTO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .resourceType(rule.getResourceType().name())
                .tierType(rule.getTierType().name())
                .freeQuota(rule.getFreeQuota())
                .unitPrice(rule.getUnitPrice())
                .tierConfig(rule.getTierConfig())
                .active(rule.getActive())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}