package com.crossborder.erp.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.supplier.entity.PurchaseOrder;
import com.crossborder.erp.supplier.entity.PurchaseOrderItem;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.mapper.PurchaseOrderMapper;
import com.crossborder.erp.supplier.mapper.PurchaseOrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 供应商服务 - 采购订单管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> {

    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final SupplierService supplierService;

    /**
     * 创建采购订单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(PurchaseOrder order, List<PurchaseOrderItem> items) {
        // 生成订单号
        if (order.getOrderNo() == null) {
            order.setOrderNo(generateOrderNo());
        }
        if (order.getStatus() == null) {
            order.setStatus("PENDING");
        }
        if (order.getPaymentStatus() == null) {
            order.setPaymentStatus("UNPAID");
        }
        if (order.getCurrency() == null) {
            order.setCurrency("USD");
        }
        
        // 计算订单总金额
        BigDecimal totalAmount = items.stream()
            .map(PurchaseOrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);
        
        // 获取供应商名称
        Supplier supplier = supplierService.getById(order.getSupplierId());
        if (supplier != null) {
            order.setSupplierName(supplier.getName());
        }
        
        save(order);
        
        // 保存订单明细
        for (PurchaseOrderItem item : items) {
            item.setOrderId(order.getId());
            if (item.getReceivedQuantity() == null) {
                item.setReceivedQuantity(0);
            }
            purchaseOrderItemMapper.insert(item);
        }
        
        log.info("创建采购订单: orderNo={}, supplierId={}, totalAmount={}", 
            order.getOrderNo(), order.getSupplierId(), totalAmount);
        return order.getId();
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "PO" + LocalDateTime.now().getYear() + 
               String.format("%05d", System.currentTimeMillis() % 100000);
    }

    /**
     * 更新采购订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(PurchaseOrder order) {
        updateById(order);
        log.info("更新采购订单: id={}", order.getId());
    }

    /**
     * 删除采购订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) {
        // 删除订单明细
        purchaseOrderItemMapper.delete(
            new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, id)
        );
        removeById(id);
        log.info("删除采购订单: id={}", id);
    }

    /**
     * 获取采购订单详情（包含明细）
     */
    public PurchaseOrder getOrderWithItems(Long id) {
        PurchaseOrder order = getById(id);
        if (order != null) {
            List<PurchaseOrderItem> items = getItemsByOrderId(id);
            order.setItems(items);  // 假设实体中有items字段
        }
        return order;
    }

    /**
     * 获取订单明细
     */
    public List<PurchaseOrderItem> getItemsByOrderId(Long orderId) {
        return purchaseOrderItemMapper.selectList(
            new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, orderId)
        );
    }

    /**
     * 分页查询采购订单
     */
    public IPage<PurchaseOrder> pageOrders(Page<PurchaseOrder> page, 
                                            String orderNo,
                                            Long supplierId, 
                                            String status,
                                            String paymentStatus) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orderNo)) {
            wrapper.like(PurchaseOrder::getOrderNo, orderNo);
        }
        if (supplierId != null) {
            wrapper.eq(PurchaseOrder::getSupplierId, supplierId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseOrder::getStatus, status);
        }
        if (StringUtils.hasText(paymentStatus)) {
            wrapper.eq(PurchaseOrder::getPaymentStatus, paymentStatus);
        }
        wrapper.orderByDesc(PurchaseOrder::getCreateTime);
        return page(page, wrapper);
    }

    /**
     * 更新订单状态
     */
    public void updateStatus(Long id, String status) {
        PurchaseOrder order = getById(id);
        if (order != null) {
            order.setStatus(status);
            
            // 到货时更新实际到货时间
            if ("RECEIVED".equals(status)) {
                order.setActualArrivalAt(LocalDateTime.now());
                
                // 更新供应商采购统计
                supplierService.updatePurchaseStats(
                    order.getSupplierId(), 
                    order.getTotalAmount()
                );
            }
            
            updateById(order);
            log.info("更新采购订单状态: id={}, status={}", id, status);
        }
    }

    /**
     * 确认订单
     */
    public void approveOrder(Long id) {
        updateStatus(id, "APPROVED");
        log.info("确认采购订单: id={}", id);
    }

    /**
     * 标记发货
     */
    public void markPurchasing(Long id) {
        updateStatus(id, "PURCHASING");
    }

    /**
     * 标记到货
     */
    public void markReceived(Long id) {
        updateStatus(id, "RECEIVED");
    }

    /**
     * 取消订单
     */
    public void cancelOrder(Long id, String reason) {
        PurchaseOrder order = getById(id);
        if (order != null) {
            order.setStatus("CANCELLED");
            if (reason != null) {
                order.setRemark(order.getRemark() + " | 取消原因: " + reason);
            }
            updateById(order);
            log.info("取消采购订单: id={}", id);
        }
    }

    /**
     * 付款
     */
    public void makePayment(Long id, BigDecimal amount) {
        PurchaseOrder order = getById(id);
        if (order != null) {
            BigDecimal paid = order.getPaidAmount() != null 
                ? order.getPaidAmount().add(amount) 
                : amount;
            order.setPaidAmount(paid);
            
            if (paid.compareTo(order.getTotalAmount()) >= 0) {
                order.setPaymentStatus("PAID");
            } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
                order.setPaymentStatus("PARTIAL");
            }
            
            updateById(order);
            log.info("采购订单付款: id={}, amount={}, paidTotal={}", id, amount, paid);
        }
    }

    /**
     * 添加订单明细
     *
     * 🔴 `t_purchase_order_item.sku` / `quantity` 是 NOT NULL 且**无默认值**，
     *    MyBatis-Plus 的 insert 默认 FieldStrategy.NOT_NULL 会**跳过 null 字段**
     *    → INSERT 里根本没有这两列
     *    → MySQL: Field 'sku' doesn't have a default value → 明细插入失败(500)，
     *       原始 SQL 栈还会泄露给前端。
     *
     * 📌 **守卫放在服务层而不是 Controller**：
     *    `POST /purchase/orders/{id}/item` 是**直接把实体当 @RequestBody 绑定的**
     *    （Jackson 直接写字段，连 setter 都不走）→ Controller 层根本没法逐字段校验。
     *    放服务层 → **所有调用方**（Controller / Feign / 定时任务）都被盖住。
     *
     * 📌 这两个字段**无法推导**（SKU 和数量是调用方的业务数据），
     *    所以只能显式校验并给中文提示，而不是兜底成 0 / 空串
     *    （兜底成 0 会静默算错金额，比报错危险得多）。
     */
    public void addOrderItem(PurchaseOrderItem item) {
        if (item == null) {
            throw new BizException("采购明细不能为空");
        }
        if (item.getSku() == null || item.getSku().isBlank()) {
            throw new BizException("SKU 不能为空");
        }
        if (item.getQuantity() == null) {
            throw new BizException("采购数量不能为空");
        }
        if (item.getQuantity() <= 0) {
            throw new BizException("采购数量必须大于 0");
        }
        // 📌 小计由后端算，不信前端传值
        if (item.getSubtotal() == null) {
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            item.setSubtotal(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        purchaseOrderItemMapper.insert(item);

        // 重新计算订单总金额
        recalculateOrderTotal(item.getOrderId());
    }

    /**
     * 删除订单明细
     */
    public void deleteOrderItem(Long itemId) {
        PurchaseOrderItem item = purchaseOrderItemMapper.selectById(itemId);
        if (item != null) {
            Long orderId = item.getOrderId();
            purchaseOrderItemMapper.deleteById(itemId);
            recalculateOrderTotal(orderId);
        }
    }

    /**
     * 重新计算订单总金额
     */
    private void recalculateOrderTotal(Long orderId) {
        List<PurchaseOrderItem> items = getItemsByOrderId(orderId);
        BigDecimal total = items.stream()
            .map(PurchaseOrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        PurchaseOrder order = getById(orderId);
        if (order != null) {
            order.setTotalAmount(total);
            updateById(order);
        }
    }

    /**
     * 获取供应商的采购订单
     */
    public List<PurchaseOrder> getBySupplierId(Long supplierId) {
        return lambdaQuery()
            .eq(PurchaseOrder::getSupplierId, supplierId)
            .orderByDesc(PurchaseOrder::getCreateTime)
            .list();
    }

    /**
     * 获取待收货订单
     */
    public List<PurchaseOrder> getInTransitOrders() {
        return lambdaQuery()
            .in(PurchaseOrder::getStatus, "PURCHASING", "IN_TRANSIT")
            .orderByAsc(PurchaseOrder::getExpectedArrivalAt)
            .list();
    }

    /**
     * 采购订单统计
     */
    public OrderStats getOrderStats() {
        List<PurchaseOrder> all = list();
        
        int total = all.size();
        int pending = (int) all.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        int inTransit = (int) all.stream()
            .filter(o -> "PURCHASING".equals(o.getStatus()) || "IN_TRANSIT".equals(o.getStatus()))
            .count();
        int received = (int) all.stream().filter(o -> "RECEIVED".equals(o.getStatus())).count();
        
        BigDecimal totalAmount = all.stream()
            .map(PurchaseOrder::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalPaid = all.stream()
            .map(o -> o.getPaidAmount() != null ? o.getPaidAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new OrderStats(total, pending, inTransit, received, totalAmount, totalPaid);
    }

    public record OrderStats(int total, int pending, int inTransit, int received,
                             BigDecimal totalAmount, BigDecimal totalPaid) {}
}