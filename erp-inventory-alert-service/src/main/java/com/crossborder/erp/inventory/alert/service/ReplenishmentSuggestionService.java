package com.crossborder.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.entity.InventoryAlertRule;
import com.crossborder.erp.inventory.alert.entity.ReplenishmentSuggestion;
import com.crossborder.erp.inventory.alert.mapper.ReplenishmentSuggestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 补货建议服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplenishmentSuggestionService extends ServiceImpl<ReplenishmentSuggestionMapper, ReplenishmentSuggestion> {

    /**
     * 根据预警自动生成补货建议
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSuggestionByAlert(InventoryAlert alert, InventoryAlertRule rule) {
        // 检查是否已存在该预警的补货建议
        ReplenishmentSuggestion existing = getBaseMapper().selectList(
                new LambdaQueryWrapper<ReplenishmentSuggestion>()
                        .eq(ReplenishmentSuggestion::getAlertId, alert.getId())
                        .ne(ReplenishmentSuggestion::getStatus, "CANCELLED")
        ).stream().findFirst().orElse(null);

        if (existing != null) {
            log.warn("补货建议已存在: alertId={}, suggestionId={}", alert.getId(), existing.getId());
            return existing.getId();
        }

        ReplenishmentSuggestion suggestion = new ReplenishmentSuggestion();
        suggestion.setAlertId(alert.getId());
        suggestion.setRuleId(rule.getId());
        suggestion.setProductId(rule.getProductId());
        suggestion.setSku(rule.getSku());
        suggestion.setProductName(rule.getProductName());
        suggestion.setWarehouseId(rule.getWarehouseId());
        suggestion.setWarehouseName(rule.getWarehouseName());
        suggestion.setCurrentStock(alert.getCurrentStock());
        suggestion.setSafeStock(rule.getSafeStock());
        
        // 计算建议补货数量 = 安全库存 - 当前库存 + 安全缓冲(20%)
        int suggestedQty = (rule.getSafeStock() - alert.getCurrentStock());
        int buffer = (int) (suggestedQty * 0.2);
        suggestedQty = suggestedQty + buffer;
        suggestion.setSuggestedQuantity(Math.max(suggestedQty, 1)); // 至少补1件

        // 计算紧急程度（基于当前库存与安全库存的缺口比例）
        suggestion.setUrgencyLevel(calculateUrgencyLevel(alert.getCurrentStock(), rule.getSafeStock()));

        // 设置建议日期（当天）
        suggestion.setSuggestedDate(LocalDateTime.now());
        
        // 预计到货日期（根据提前天数）
        if (rule.getAdvanceDays() != null && rule.getAdvanceDays() > 0) {
            suggestion.setExpectedArrivalDate(LocalDateTime.now().plusDays(rule.getAdvanceDays()));
        } else {
            suggestion.setExpectedArrivalDate(LocalDateTime.now().plusDays(7)); // 默认7天
        }
        
        // 状态为待确认
        suggestion.setStatus("PENDING");
        
        save(suggestion);
        log.info("创建补货建议成功: suggestionId={}, sku={}, quantity={}", 
                suggestion.getId(), suggestion.getSku(), suggestion.getSuggestedQuantity());
        
        return suggestion.getId();
    }

    /**
     * 批量生成补货建议
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateSuggestions(List<InventoryAlert> alerts, List<InventoryAlertRule> rules) {
        int count = 0;
        for (int i = 0; i < alerts.size() && i < rules.size(); i++) {
            try {
                createSuggestionByAlert(alerts.get(i), rules.get(i));
                count++;
            } catch (Exception e) {
                log.error("创建补货建议失败: alertId={}, error={}", alerts.get(i).getId(), e.getMessage());
            }
        }
        log.info("批量创建补货建议完成: count={}", count);
        return count;
    }

    /**
     * 分页查询补货建议
     */
    public IPage<ReplenishmentSuggestion> querySuggestions(Integer page, Integer size, 
                                                            String warehouseId, String status,
                                                            LocalDateTime startTime, LocalDateTime endTime) {
        Page<ReplenishmentSuggestion> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<ReplenishmentSuggestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(warehouseId), ReplenishmentSuggestion::getWarehouseId, warehouseId)
               .eq(StringUtils.hasText(status), ReplenishmentSuggestion::getStatus, status)
               .ge(startTime != null, ReplenishmentSuggestion::getCreateTime, startTime)
               .le(endTime != null, ReplenishmentSuggestion::getCreateTime, endTime)
               .orderByDesc(ReplenishmentSuggestion::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    /**
     * 查询待确认的补货建议
     */
    public List<ReplenishmentSuggestion> getPendingSuggestions() {
        return lambdaQuery()
                .eq(ReplenishmentSuggestion::getStatus, "PENDING")
                .orderByAsc(ReplenishmentSuggestion::getSuggestedDate)
                .list();
    }

    /**
     * 确认补货建议
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmSuggestion(Long suggestionId, Long confirmerId, String confirmerName, String remark) {
        ReplenishmentSuggestion suggestion = getById(suggestionId);
        if (suggestion == null) {
            throw new RuntimeException("补货建议不存在: " + suggestionId);
        }
        
        if (!"PENDING".equals(suggestion.getStatus())) {
            throw new RuntimeException("只能确认待确认状态的补货建议");
        }
        
        suggestion.setStatus("CONFIRMED");
        suggestion.setConfirmerId(confirmerId);
        suggestion.setConfirmerName(confirmerName);
        suggestion.setRemark(remark);
        suggestion.setConfirmedTime(LocalDateTime.now());
        
        updateById(suggestion);
        log.info("确认补货建议: suggestionId={}, confirmer={}", suggestionId, confirmerName);
    }

    /**
     * 取消补货建议
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelSuggestion(Long suggestionId, String remark) {
        ReplenishmentSuggestion suggestion = getById(suggestionId);
        if (suggestion == null) {
            throw new RuntimeException("补货建议不存在: " + suggestionId);
        }
        
        suggestion.setStatus("CANCELLED");
        suggestion.setRemark(remark);
        
        updateById(suggestion);
        log.info("取消补货建议: suggestionId={}", suggestionId);
    }

    /**
     * 标记为已采购
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsPurchased(Long suggestionId, Long purchaseOrderId) {
        ReplenishmentSuggestion suggestion = new ReplenishmentSuggestion();
        suggestion.setId(suggestionId);
        suggestion.setStatus("PURCHASED");
        suggestion.setRemark("已生成采购单，订单ID: " + purchaseOrderId);
        
        updateById(suggestion);
        log.info("补货建议已采购: suggestionId={}, purchaseOrderId={}", suggestionId, purchaseOrderId);
    }

    /**
     * 更新采购价格信息
     */
    public void updatePurchaseInfo(Long suggestionId, BigDecimal purchasePrice, String currency,
                                    Long supplierId, String supplierName, BigDecimal estimatedAmount) {
        ReplenishmentSuggestion suggestion = getById(suggestionId);
        if (suggestion == null) {
            return;
        }
        
        suggestion.setPurchasePrice(purchasePrice);
        suggestion.setCurrency(currency);
        suggestion.setSupplierId(supplierId);
        suggestion.setSupplierName(supplierName);
        suggestion.setEstimatedAmount(estimatedAmount);
        
        updateById(suggestion);
    }

    /**
     * 统计待确认补货建议数量
     */
    public Long countPendingSuggestions(String warehouseId) {
        return lambdaQuery()
                .eq(StringUtils.hasText(warehouseId), ReplenishmentSuggestion::getWarehouseId, warehouseId)
                .eq(ReplenishmentSuggestion::getStatus, "PENDING")
                .count();
    }

    /**
     * 计算补货紧急程度
     * 依据当前库存相对安全库存的缺口比例：
     * - 缺口 &gt;= 80%  → HIGH   （库存几乎耗尽，需立即补货）
     * - 缺口 &gt;= 50%  → MEDIUM （库存已明显低于安全线）
     * - 其余           → LOW    （库存尚可，按计划补货）
     *
     * @param currentStock 当前库存
     * @param safeStock    安全库存
     * @return HIGH / MEDIUM / LOW
     */
    private String calculateUrgencyLevel(Integer currentStock, Integer safeStock) {
        if (currentStock == null || safeStock == null || safeStock <= 0) {
            return "LOW";
        }
        if (currentStock <= 0) {
            return "HIGH";
        }
        double gapRatio = (double) (safeStock - currentStock) / safeStock;
        if (gapRatio >= 0.8) {
            return "HIGH";
        }
        if (gapRatio >= 0.5) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * 预估总采购金额
     */
    public BigDecimal estimateTotalAmount(String warehouseId) {
        List<ReplenishmentSuggestion> suggestions = lambdaQuery()
                .eq(StringUtils.hasText(warehouseId), ReplenishmentSuggestion::getWarehouseId, warehouseId)
                .eq(ReplenishmentSuggestion::getStatus, "PENDING")
                .list();
        
        return suggestions.stream()
                .filter(s -> s.getEstimatedAmount() != null)
                .map(ReplenishmentSuggestion::getEstimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}