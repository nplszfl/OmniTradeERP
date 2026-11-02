package com.crossborder.erp.supplier.vo;

import com.crossborder.erp.supplier.entity.PurchaseOrder;
import com.crossborder.erp.supplier.entity.PurchaseOrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 采购订单 VO —— 对齐前端 {@code erp-web/src/api/purchase.ts} 的 {@code PurchaseOrder}
 *
 * <p>⚠️ 前端字段名跟实体<b>不一致</b>，必须走 VO 映射，不能直接返回实体：</p>
 * <table border="1">
 *   <tr><th>前端</th><th>实体</th></tr>
 *   <tr><td>poNo</td><td>orderNo</td></tr>
 *   <tr><td>currencyCode</td><td>currency</td></tr>
 *   <tr><td>expectedDate</td><td>expectedArrivalAt</td></tr>
 *   <tr><td>createdAt</td><td>createTime</td></tr>
 * </table>
 *
 * <p>状态枚举也不同：前端是 DRAFT/PENDING/APPROVED/SHIPPED/COMPLETED/CANCELLED，
 * 实体是 PENDING/APPROVED/PURCHASING/IN_TRANSIT/RECEIVED/CANCELLED → 需要归一化。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class PurchaseOrderVO {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long id;
    private String poNo;
    private Long supplierId;
    private String supplierName;
    private BigDecimal totalAmount;
    private String currencyCode;
    private String status;
    private String expectedDate;
    private String createdAt;
    private List<ItemVO> items;

    /**
     * 实体 → VO（列表，不带明细）
     */
    public static PurchaseOrderVO from(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setId(order.getId());
        vo.setPoNo(order.getOrderNo());
        vo.setSupplierId(order.getSupplierId());
        vo.setSupplierName(order.getSupplierName());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setCurrencyCode(order.getCurrency());
        vo.setStatus(normalizeStatus(order.getStatus()));
        vo.setExpectedDate(formatDate(order.getExpectedArrivalAt()));
        vo.setCreatedAt(formatDateTime(order.getCreateTime()));
        return vo;
    }

    /**
     * 实体 → VO（详情，带明细）
     */
    public static PurchaseOrderVO from(PurchaseOrder order, List<PurchaseOrderItem> items) {
        PurchaseOrderVO vo = from(order);
        if (vo == null) {
            return null;
        }
        vo.setItems(items == null ? List.of() : items.stream().map(ItemVO::from).toList());
        return vo;
    }

    /**
     * 实体状态 → 前端状态
     *
     * <p>前端 el-tag 的 type 映射只认这 6 个值，
     * 传 PURCHASING / IN_TRANSIT / RECEIVED 会渲染成没有样式的纯文本。</p>
     */
    public static String normalizeStatus(String backendStatus) {
        if (backendStatus == null || backendStatus.isBlank()) {
            return "PENDING";
        }
        return switch (backendStatus.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "APPROVED";
            case "PURCHASING", "IN_TRANSIT" -> "SHIPPED";
            case "RECEIVED" -> "COMPLETED";
            case "CANCELLED", "CANCELED" -> "CANCELLED";
            case "DRAFT" -> "DRAFT";
            default -> "PENDING";
        };
    }

    /**
     * 前端状态 → 实体状态（创建时用）
     */
    public static String denormalizeStatus(String frontendStatus) {
        if (frontendStatus == null || frontendStatus.isBlank()) {
            return "PENDING";
        }
        return switch (frontendStatus.trim().toUpperCase(Locale.ROOT)) {
            case "DRAFT" -> "PENDING";
            case "SHIPPED" -> "IN_TRANSIT";
            case "COMPLETED" -> "RECEIVED";
            case "APPROVED" -> "APPROVED";
            case "CANCELLED", "CANCELED" -> "CANCELLED";
            default -> "PENDING";
        };
    }

    private static String formatDate(LocalDateTime time) {
        return time == null ? null : time.format(DATE_FMT);
    }

    private static String formatDateTime(LocalDateTime time) {
        return time == null ? null : time.format(DATETIME_FMT);
    }

    /**
     * 采购单明细 VO
     */
    @Data
    public static class ItemVO {
        private Long id;
        private String sku;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;

        static ItemVO from(PurchaseOrderItem item) {
            ItemVO vo = new ItemVO();
            vo.setId(item.getId());
            vo.setSku(item.getSku());
            vo.setProductName(item.getProductName());
            vo.setQuantity(item.getQuantity());
            vo.setUnitPrice(item.getUnitPrice());
            vo.setSubtotal(item.getSubtotal());
            return vo;
        }
    }
}
