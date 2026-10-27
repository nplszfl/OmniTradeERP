package com.crossborder.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.purchase.mapper.PurchasePlanMapper;
import com.crossborder.erp.purchase.service.PurchasePlanService;
import com.crossborder.erp.purchase.entity.PurchasePlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购计划服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePlanServiceImpl extends ServiceImpl<PurchasePlanMapper, PurchasePlan> implements PurchasePlanService {

    @Override
    public PurchasePlan generatePlan(Long productId, String sku, String warehouseId, Integer quantity, Long supplierId) {
        log.info("生成采购计划: productId={}, sku={}, warehouseId={}, quantity={}, supplierId={}", productId, sku, warehouseId, quantity, supplierId);
        PurchasePlan plan = new PurchasePlan();
        plan.setProductId(productId);
        plan.setSku(sku);
        plan.setWarehouseId(warehouseId);
        plan.setPlanQuantity(quantity);
        plan.setSupplierId(supplierId);
        plan.setPlanType("AUTO");
        plan.setPlanNo("PP" + System.currentTimeMillis());
        plan.setRequiredDate(LocalDate.now().plusDays(7));
        plan.setPriority("NORMAL");
        plan.setStatus("PENDING");
        plan.setTotalAmount(BigDecimal.ZERO);
        baseMapper.insert(plan);
        return plan;
    }

    @Override
    public List<PurchasePlan> batchGeneratePlans(List<Long> productIds, String warehouseId) {
        log.info("批量生成采购计划: productIds={}, warehouseId={}", productIds.size(), warehouseId);
        return productIds.stream().map(id -> generatePlan(id, "SKU-" + id, warehouseId, 100, null)).toList();
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<PurchasePlan> queryPlans(int page, int size, String status, String warehouseId) {
        LambdaQueryWrapper<PurchasePlan> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(PurchasePlan::getStatus, status);
        }
        if (warehouseId != null && !warehouseId.isBlank()) {
            wrapper.eq(PurchasePlan::getWarehouseId, warehouseId);
        }
        wrapper.orderByDesc(PurchasePlan::getRequiredDate);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }

    @Override
    public void confirmPlan(Long planId, Long userId, String userName) {
        log.info("确认采购计划: planId={}, userId={}, userName={}", planId, userId, userName);
        PurchasePlan plan = baseMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus("CONFIRMED");
            baseMapper.updateById(plan);
        }
    }

    @Override
    public void cancelPlan(Long planId, String reason) {
        log.info("取消采购计划: planId={}, reason={}", planId, reason);
        PurchasePlan plan = baseMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus("CANCELLED");
            plan.setRemark(reason);
            baseMapper.updateById(plan);
        }
    }

    @Override
    public List<PurchasePlan> getPendingPlans(String warehouseId) {
        return list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PurchasePlan>()
                .eq(PurchasePlan::getStatus, "PENDING")
                .eq(warehouseId != null, PurchasePlan::getWarehouseId, warehouseId));
    }
}