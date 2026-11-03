package com.crossborder.erp.supplier.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.supplier.entity.PurchaseOrder;
import com.crossborder.erp.supplier.entity.PurchaseOrderItem;
import com.crossborder.erp.supplier.entity.Supplier;
import com.crossborder.erp.supplier.service.PurchaseOrderService;
import com.crossborder.erp.supplier.service.SupplierService;
import com.crossborder.erp.supplier.vo.PurchaseOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购单 REST 控制器测试
 *
 * <p>重点验证：前端契约的字段映射、状态归一化、裸数组响应、状态机校验。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("采购单 REST 控制器测试")
class PurchaseOrderRestControllerTest {

    private PurchaseOrderRestController controller;
    private PurchaseOrderService purchaseOrderService;
    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        purchaseOrderService = mock(PurchaseOrderService.class);
        supplierService = mock(SupplierService.class);
        controller = new PurchaseOrderRestController(purchaseOrderService, supplierService);
    }

    private PurchaseOrder order(Long id, String status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setOrderNo("PO202600001");
        order.setSupplierId(10L);
        order.setSupplierName("AcmeSound");
        order.setTotalAmount(new BigDecimal("38400.00"));
        order.setCurrency("CNY");
        order.setStatus(status);
        order.setExpectedArrivalAt(LocalDateTime.of(2026, 4, 15, 0, 0));
        order.setCreateTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        return order;
    }

    @Test
    @DisplayName("列表返回裸数组（前端直接塞给 el-table）")
    void listReturnsPlainArray() {
        Page<PurchaseOrder> page = new Page<>(1, 20);
        page.setRecords(List.of(order(1L, "PENDING"), order(2L, "RECEIVED")));
        page.setTotal(2);
        when(purchaseOrderService.pageOrders(any(), any(), any(), any(), any())).thenReturn(page);

        List<PurchaseOrderVO> result = controller.list(1L, 20L, null, null, null);

        assertEquals(2, result.size());
        assertEquals("PO202600001", result.get(0).getPoNo());
    }

    @Test
    @DisplayName("前端字段名映射：poNo / currencyCode / expectedDate / createdAt")
    void mapsFrontendFieldNames() {
        PurchaseOrderVO vo = PurchaseOrderVO.from(order(1L, "PENDING"));

        assertEquals("PO202600001", vo.getPoNo());
        assertEquals("CNY", vo.getCurrencyCode());
        assertEquals("2026-04-15", vo.getExpectedDate());
        assertEquals("2026-04-01 10:00", vo.getCreatedAt());
        assertEquals("AcmeSound", vo.getSupplierName());
    }

    @Test
    @DisplayName("状态归一化：后端 PURCHASING/IN_TRANSIT → 前端 SHIPPED，RECEIVED → COMPLETED")
    void normalizesStatus() {
        assertEquals("PENDING", PurchaseOrderVO.normalizeStatus("PENDING"));
        assertEquals("APPROVED", PurchaseOrderVO.normalizeStatus("APPROVED"));
        assertEquals("SHIPPED", PurchaseOrderVO.normalizeStatus("PURCHASING"));
        assertEquals("SHIPPED", PurchaseOrderVO.normalizeStatus("IN_TRANSIT"));
        assertEquals("COMPLETED", PurchaseOrderVO.normalizeStatus("RECEIVED"));
        assertEquals("CANCELLED", PurchaseOrderVO.normalizeStatus("CANCELLED"));
        assertEquals("PENDING", PurchaseOrderVO.normalizeStatus(null));
    }

    @Test
    @DisplayName("详情带明细")
    void detailIncludesItems() {
        when(purchaseOrderService.getById(1L)).thenReturn(order(1L, "PENDING"));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(99L);
        item.setSku("SKU-BLU-001-BLK");
        item.setProductName("蓝牙耳机-黑色");
        item.setQuantity(320);
        item.setUnitPrice(new BigDecimal("120.00"));
        when(purchaseOrderService.getItemsByOrderId(1L)).thenReturn(List.of(item));

        PurchaseOrderVO vo = controller.detail(1L);
        assertEquals(1, vo.getItems().size());
        assertEquals("SKU-BLU-001-BLK", vo.getItems().get(0).getSku());
        assertEquals(320, vo.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("详情查不到 → 明确报错，不返回 null")
    void detailMissingThrows() {
        when(purchaseOrderService.getById(404L)).thenReturn(null);
        assertThrows(BizException.class, () -> controller.detail(404L));
    }

    @Test
    @DisplayName("创建采购单：后端自己算小计，不信前端传值")
    void createRecalculatesSubtotal() {
        PurchaseOrderRestController.CreateOrderRequest req = new PurchaseOrderRestController.CreateOrderRequest();
        req.setSupplierId(10L);
        req.setCurrencyCode("CNY");
        req.setStatus("PENDING");
        req.setExpectedDate("2026-04-15");

        PurchaseOrderRestController.ItemRequest item = new PurchaseOrderRestController.ItemRequest();
        item.setSku("SKU-1");
        item.setQuantity(320);
        item.setUnitPrice(new BigDecimal("120.00"));
        req.setItems(List.of(item));

        when(purchaseOrderService.createOrder(any(), any())).thenReturn(555L);

        // 📌 返回值现在是 Result<String>：data 是字符串
        //    （雪花 ID 19 位，JS Number 承载不了；且裸 String 返回会被
        //     StringHttpMessageConverter 原样输出成裸数字 → JS 解析后又变数字）
        Result<String> resp = controller.create(req);
        assertEquals("555", resp.getData());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PurchaseOrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(purchaseOrderService).createOrder(any(), captor.capture());
        assertEquals(new BigDecimal("38400.00"), captor.getValue().get(0).getSubtotal());
    }

    @Test
    @DisplayName("创建采购单：无明细直接拒绝")
    void createWithoutItemsRejected() {
        PurchaseOrderRestController.CreateOrderRequest req = new PurchaseOrderRestController.CreateOrderRequest();
        req.setSupplierId(10L);
        assertThrows(BizException.class, () -> controller.create(req));
        verify(purchaseOrderService, never()).createOrder(any(), any());
    }

    @Test
    @DisplayName("审核：只有 PENDING 能审")
    void approveOnlyPending() {
        when(purchaseOrderService.getById(1L)).thenReturn(order(1L, "PENDING"));
        controller.approve(1L);
        verify(purchaseOrderService).approveOrder(1L);

        when(purchaseOrderService.getById(2L)).thenReturn(order(2L, "RECEIVED"));
        assertThrows(BizException.class, () -> controller.approve(2L));
    }

    @Test
    @DisplayName("收货：已收货的不能再收")
    void receiveRejectsAlreadyReceived() {
        when(purchaseOrderService.getById(1L)).thenReturn(order(1L, "APPROVED"));
        controller.receive(1L);
        verify(purchaseOrderService).markReceived(1L);

        when(purchaseOrderService.getById(2L)).thenReturn(order(2L, "RECEIVED"));
        assertThrows(BizException.class, () -> controller.receive(2L));
    }

    @Test
    @DisplayName("取消：已收货的不能取消，原因透传")
    void cancelRejectsReceivedAndPassesReason() {
        when(purchaseOrderService.getById(1L)).thenReturn(order(1L, "PENDING"));
        PurchaseOrderRestController.CancelRequest req = new PurchaseOrderRestController.CancelRequest();
        req.setReason("供应商缺货");
        controller.cancel(1L, req);
        verify(purchaseOrderService).cancelOrder(eq(1L), eq("供应商缺货"));

        when(purchaseOrderService.getById(2L)).thenReturn(order(2L, "RECEIVED"));
        assertThrows(BizException.class, () -> controller.cancel(2L, req));
    }

    @Test
    @DisplayName("可选供应商列表")
    void suppliersReturnsList() {
        Supplier supplier = new Supplier();
        supplier.setId(10L);
        supplier.setName("AcmeSound");
        supplier.setType("ELECTRONICS");
        when(supplierService.list()).thenReturn(List.of(supplier));

        List<java.util.Map<String, Object>> list = controller.suppliers();
        assertEquals(1, list.size());
        assertEquals("AcmeSound", list.get(0).get("name"));
        assertTrue(list.get(0).containsKey("id"));
    }

    @Test
    @DisplayName("分页参数传到 service")
    void passesPagingParams() {
        Page<PurchaseOrder> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(purchaseOrderService.pageOrders(any(), any(), any(), any(), any())).thenReturn(page);

        controller.list(3L, 50L, "PO", "PENDING", 10L);

        verify(purchaseOrderService).pageOrders(any(), eq("PO"), eq(10L), eq("PENDING"), any());
        ArgumentCaptor<Page<PurchaseOrder>> captor = ArgumentCaptor.forClass(Page.class);
        verify(purchaseOrderService).pageOrders(captor.capture(), any(), any(), any(), any());
        assertEquals(3L, captor.getValue().getCurrent());
        assertEquals(50L, captor.getValue().getSize());
    }
}
