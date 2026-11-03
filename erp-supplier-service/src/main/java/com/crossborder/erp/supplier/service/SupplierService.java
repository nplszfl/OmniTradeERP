package com.crossborder.erp.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService extends ServiceImpl<SupplierMapper, Supplier> {

    /**
     * 创建供应商
     */
    public Long createSupplier(Supplier supplier) {
        // 🔴 必填校验：`t_supplier.name` 是 NOT NULL 且**无默认值**，
        //    MyBatis-Plus 的 insert 默认 FieldStrategy.NOT_NULL 会**跳过 null 字段**
        //    → INSERT 里根本没有这一列 → MySQL 报
        //    "Field 'name' doesn't have a default value" → 整个插入失败，
        //    而且错误信息是**原始 SQL 栈**直接泄露给前端。
        if (supplier == null) {
            throw new BizException("供应商不能为空");
        }
        if (supplier.getName() == null || supplier.getName().trim().isEmpty()) {
            throw new BizException("供应商名称不能为空");
        }
        if (supplier.getStatus() == null) {
            supplier.setStatus("ACTIVE");
        }
        if (supplier.getLevel() == null) {
            supplier.setLevel("B");
        }
        if (supplier.getRating() == null) {
            supplier.setRating(BigDecimal.valueOf(3.0));
        }
        if (supplier.getTotalOrders() == null) {
            supplier.setTotalOrders(0);
        }
        if (supplier.getTotalPurchaseAmount() == null) {
            supplier.setTotalPurchaseAmount(BigDecimal.ZERO);
        }
        
        save(supplier);
        log.info("创建供应商成功: {}", supplier.getName());
        return supplier.getId();
    }

    /**
     * 分页查询供应商
     */
    public IPage<Supplier> pageSuppliers(Page<Supplier> page, String keyword, String type, 
                                          String level, String country) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Supplier::getName, keyword)
                             .or().like(Supplier::getContactPerson, keyword)
                             .or().like(Supplier::getPhone, keyword));
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Supplier::getType, type);
        }
        if (StringUtils.hasText(level)) {
            wrapper.eq(Supplier::getLevel, level);
        }
        if (StringUtils.hasText(country)) {
            wrapper.eq(Supplier::getCountry, country);
        }
        
        wrapper.orderByDesc(Supplier::getTotalPurchaseAmount, Supplier::getRating);
        
        return page(page, wrapper);
    }

    /**
     * 更新采购统计
     */
    public void updatePurchaseStats(Long supplierId, BigDecimal amount) {
        Supplier supplier = getById(supplierId);
        if (supplier != null) {
            supplier.setTotalOrders(supplier.getTotalOrders() + 1);
            supplier.setTotalPurchaseAmount(
                supplier.getTotalPurchaseAmount().add(amount)
            );
            
            // 自动调整等级
            if (supplier.getTotalPurchaseAmount().compareTo(new BigDecimal("100000")) >= 0) {
                supplier.setLevel("A");
            } else if (supplier.getTotalPurchaseAmount().compareTo(new BigDecimal("30000")) >= 0) {
                supplier.setLevel("B");
            } else {
                supplier.setLevel("C");
            }
            
            updateById(supplier);
            log.info("供应商采购统计更新: supplierId={}, totalOrders={}, totalAmount={}", 
                supplierId, supplier.getTotalOrders(), supplier.getTotalPurchaseAmount());
        }
    }

    /**
     * 获取A级供应商
     */
    public List<Supplier> getTopSuppliers() {
        return lambdaQuery()
            .eq(Supplier::getLevel, "A")
            .orderByDesc(Supplier::getTotalPurchaseAmount)
            .list();
    }

    /**
     * 获取供应商统计
     */
    public SupplierStats getStats() {
        List<Supplier> all = list();
        
        int total = all.size();
        int aLevel = (int) all.stream().filter(s -> "A".equals(s.getLevel())).count();
        int active = (int) all.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();
        
        BigDecimal totalAmount = all.stream()
            .map(Supplier::getTotalPurchaseAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new SupplierStats(total, aLevel, active, totalAmount);
    }

    public record SupplierStats(int total, int aLevelCount, int activeCount, BigDecimal totalAmount) {}
}