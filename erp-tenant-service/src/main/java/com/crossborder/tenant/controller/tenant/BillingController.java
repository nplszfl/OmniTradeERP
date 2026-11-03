package com.crossborder.tenant.controller.tenant;

import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.billing.BillDTO;
import com.crossborder.tenant.dto.billing.BillingRuleDTO;
import com.crossborder.tenant.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 计费管理控制器
 */
@Tag(name = "计费管理", description = "账单查询、支付、计费规则")
@RestController
@RequestMapping("/api/tenant/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @Operation(summary = "获取账单列表")
    @GetMapping("/bills")
    public ResponseEntity<List<BillDTO>> getBills(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(billingService.getBills());
        }
    }

    @Operation(summary = "获取当前月份账单")
    @GetMapping("/bills/current")
    public ResponseEntity<BillDTO> getCurrentBill(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            BillDTO bill = billingService.getCurrentBill();
            if (bill == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(bill);
        }
    }

    @Operation(summary = "获取待支付账单")
    @GetMapping("/bills/pending")
    public ResponseEntity<List<BillDTO>> getPendingBills(
            @RequestParam(required = false) String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.resolve(tenantId))) {
            return ResponseEntity.ok(billingService.getPendingBills());
        }
    }

    @Operation(summary = "支付账单")
    @PostMapping("/bills/{billId}/pay")
    public ResponseEntity<BillDTO> payBill(
            @PathVariable String billId,
            @RequestParam String paymentMethod) {
        return ResponseEntity.ok(billingService.payBill(billId, paymentMethod));
    }

    @Operation(summary = "获取计费规则")
    @GetMapping("/rules")
    public ResponseEntity<List<BillingRuleDTO>> getBillingRules() {
        return ResponseEntity.ok(billingService.getBillingRules());
    }

    @Operation(summary = "创建计费规则（管理员）")
    @PostMapping("/rules")
    public ResponseEntity<BillingRuleDTO> createBillingRule(@RequestBody BillingRuleDTO dto) {
        return ResponseEntity.ok(billingService.createBillingRule(dto));
    }
}