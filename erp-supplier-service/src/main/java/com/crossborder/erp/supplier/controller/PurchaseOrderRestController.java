package com.crossborder.erp.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.supplier.entity.PurchaseOrder;
import com.crossborder.erp.supplier.entity.PurchaseOrderItem;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.service.PurchaseOrderService;
import com.crossborder.erp.supplier.service.SupplierService;
import com.crossborder.erp.supplier.vo.PurchaseOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.crossborder.erp.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 采购单 REST 控制器 —— 对齐前端 {@code erp-web/src/api/purchase.ts}
 *
 * <p>⚠️ 之前前端 purchase.ts 的 7 个调用<b>全部 404</b>：
 * 采购单的实体/Service 在 erp-supplier-service（根路径 /supplier/purchase），
 * 但前端 purchase.ts 走的是 /purchase/orders —— 两条完全不同的契约。
 * 供应商域的 {@code /supplier/purchase/**} 保持不变（前端 supplier.ts 在用），
 * 这里另起一套给 purchase.ts。</p>
 *
 * <p>响应形态按前端消费方式决定：</p>
 * <ul>
 *   <li>{@code list.value = await purchaseApi.list(filter)} → 必须是<b>裸数组</b>，
 *       包成 PageResult 前端 el-table 拿到的是对象，渲染不出来</li>
 *   <li>{@code detail.value = await purchaseApi.detail(id)} → 裸对象</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/purchase")
@RequiredArgsConstructor
public class PurchaseOrderRestController {

    private final PurchaseOrderService purchaseOrderService;
    private final SupplierService supplierService;

    /**
     * 分页查询采购单 —— 前端期望<b>裸数组</b>
     */
    @GetMapping("/orders")
    public List<PurchaseOrderVO> list(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId) {
        Page<PurchaseOrder> page = new Page<>(current, size);
        IPage<PurchaseOrder> result = purchaseOrderService.pageOrders(page, keyword, supplierId, status, null);
        return result.getRecords().stream().map(PurchaseOrderVO::from).toList();
    }

    /**
     * 采购单详情（含明细）
     */
    @GetMapping("/orders/{id}")
    public PurchaseOrderVO detail(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            throw new BizException("采购单不存在: " + id);
        }
        return PurchaseOrderVO.from(order, purchaseOrderService.getItemsByOrderId(id));
    }

    /**
     * 创建采购单
     *
     * 📌 <b>返回字符串而不是 Long</b>：采购单主键是雪花 ID（19 位），
     * JS 的 {@code Number} 只能精确表示 16 位 → 前端拿到的 id 会被截断
     * → 回传后端时匹配不到任何行 → <b>HTTP 200 但删除/更新静默失败</b>。
     *
     * <p>📌 服务层仍然用 {@code Long}，只在 HTTP 出口转成字符串。</p>
     */
    @PostMapping("/orders")
    public Result<String> create(@RequestBody CreateOrderRequest request) {
        if (request == null || request.getSupplierId() == null) {
            throw new BizException("供应商不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BizException("采购明细不能为空");
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierId(request.getSupplierId());
        order.setOrderNo(request.getPoNo());
        order.setStatus(PurchaseOrderVO.denormalizeStatus(request.getStatus()));
        order.setCurrency(request.getCurrencyCode());
        order.setExpectedArrivalAt(parseDate(request.getExpectedDate()));
        order.setPaymentMethod(request.getPaymentMethod());
        order.setRemark(request.getRemark());

        List<PurchaseOrderItem> items = request.getItems().stream().map(item -> {
            PurchaseOrderItem po = new PurchaseOrderItem();
            po.setSku(item.getSku());
            po.setProductName(item.getProductName());
            po.setQuantity(item.getQuantity());
            po.setUnitPrice(item.getUnitPrice());
            po.setUnit(item.getUnit());
            po.setRemark(item.getRemark());
            // 小计由后端算，不信前端传值
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            po.setSubtotal(price.multiply(BigDecimal.valueOf(qty)));
            return po;
        }).toList();

        Long orderId = purchaseOrderService.createOrder(order, items);
        return Result.successId(orderId);
    }

    /**
     * 审核通过
     */
    @PostMapping("/orders/{id}/approve")
    public void approve(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            throw new BizException("采购单不存在: " + id);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("只有待审核的采购单可以审核通过，当前状态: " + order.getStatus());
        }
        purchaseOrderService.approveOrder(id);
    }

    /**
     * 取消采购单
     */
    @PostMapping("/orders/{id}/cancel")
    public void cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            throw new BizException("采购单不存在: " + id);
        }
        if ("RECEIVED".equals(order.getStatus())) {
            throw new BizException("已收货的采购单不能取消");
        }
        purchaseOrderService.cancelOrder(id, request == null ? null : request.getReason());
    }

    /**
     * 确认收货（入库）
     */
    @PostMapping("/orders/{id}/receive")
    public void receive(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getById(id);
        if (order == null) {
            throw new BizException("采购单不存在: " + id);
        }
        if (!"APPROVED".equals(order.getStatus())
                && !"PURCHASING".equals(order.getStatus())
                && !"IN_TRANSIT".equals(order.getStatus())) {
            throw new BizException("只有已审核或已发货的采购单可以收货，当前状态: " + order.getStatus());
        }
        purchaseOrderService.markReceived(id);
    }

    /**
     * 可选供应商列表（新建采购单时下拉用）
     */
    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() {
        return supplierService.list().stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "name", s.getName(),
                        "code", s.getType() == null ? "" : s.getType()))
                .toList();
    }

    private static LocalDateTime parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(date.length() <= 10 ? date + "T00:00:00" : date.replace(' ', 'T'));
        } catch (Exception e) {
            log.warn("解析预计到货日期失败: {}", date);
            return null;
        }
    }

    /**
     * 创建采购单请求
     */
    public static class CreateOrderRequest {
        private String poNo;
        private Long supplierId;
        private String currencyCode;
        private String status;
        private String expectedDate;
        private String paymentMethod;
        private String remark;
        private List<ItemRequest> items;

        public String getPoNo() { return poNo; }
        public void setPoNo(String poNo) { this.poNo = poNo; }
        public Long getSupplierId() { return supplierId; }
        public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getExpectedDate() { return expectedDate; }
        public void setExpectedDate(String expectedDate) { this.expectedDate = expectedDate; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<ItemRequest> getItems() { return items; }
        public void setItems(List<ItemRequest> items) { this.items = items; }
    }

    /**
     * 明细请求
     */
    public static class ItemRequest {
        private String sku;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private String unit;
        private String remark;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    /**
     * 取消原因
     */
    public static class CancelRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
