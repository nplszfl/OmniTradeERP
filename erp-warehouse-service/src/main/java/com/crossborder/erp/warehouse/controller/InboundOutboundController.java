package com.crossborder.erp.warehouse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.warehouse.entity.InboundDetail;
import com.crossborder.erp.warehouse.entity.InboundOrder;
import com.crossborder.erp.warehouse.entity.OutboundDetail;
import com.crossborder.erp.warehouse.entity.OutboundOrder;
import com.crossborder.erp.warehouse.service.WarehouseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 入库 / 出库 Controller
 *
 * <p>覆盖 {@link WarehouseService} 中的入库单与出库单相关方法。</p>
 *
 * <p>业务异常统一由 erp-common 的 {@code GlobalExceptionHandler} 处理，Controller 层不做异常包装。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequestMapping("/warehouse")
@RequiredArgsConstructor
public class InboundOutboundController {

    private final WarehouseService warehouseService;

    // ==================== 入库管理 ====================

    /**
     * 创建入库单（含明细）
     */
    @PostMapping("/inbound/orders")
    public Result<Long> createInboundOrder(@RequestBody InboundOrderRequest request) {
        List<InboundDetail> details = request.getDetails() == null ? List.of() : request.getDetails();
        log.info("创建入库单请求: 仓库={}, 明细行数={}",
                request.getOrder() == null ? null : request.getOrder().getWarehouseId(), details.size());
        Long orderId = warehouseService.createInboundOrder(request.getOrder(), details);
        return Result.success(orderId);
    }

    /**
     * 查询入库单详情
     */
    @GetMapping("/inbound/orders/{orderId}")
    public Result<InboundOrder> getInboundOrder(@PathVariable Long orderId) {
        return Result.success(warehouseService.getInboundOrder(orderId));
    }

    /**
     * 分页查询入库单
     */
    @GetMapping("/inbound/orders/page")
    public Result<Page<InboundOrder>> listInboundOrders(@RequestParam(required = false) Long warehouseId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        log.info("分页查询入库单: 仓库={}, 状态={}", warehouseId, status);
        return Result.success(warehouseService.listInboundOrders(warehouseId, status, page, size));
    }

    /**
     * 确认入库（按实际收货明细更新库位库存）
     */
    @PostMapping("/inbound/orders/{orderId}/confirm")
    public Result<Void> confirmInbound(@PathVariable Long orderId,
                                       @RequestBody List<InboundDetail> actualDetails) {
        List<InboundDetail> details = actualDetails == null ? List.of() : actualDetails;
        log.info("确认入库: 单据={}, 明细行数={}", orderId, details.size());
        warehouseService.confirmInbound(orderId, details);
        return Result.success();
    }

    /**
     * 部分入库
     */
    @PostMapping("/inbound/orders/{orderId}/partial")
    public Result<Void> partialInbound(@PathVariable Long orderId,
                                       @RequestBody List<InboundDetail> details) {
        List<InboundDetail> partialDetails = details == null ? List.of() : details;
        log.info("部分入库: 单据={}, 明细行数={}", orderId, partialDetails.size());
        warehouseService.partialInbound(orderId, partialDetails);
        return Result.success();
    }

    /**
     * 取消入库单
     */
    @PostMapping("/inbound/orders/{orderId}/cancel")
    public Result<Void> cancelInbound(@PathVariable Long orderId,
                                      @RequestParam(required = false) String reason) {
        log.info("取消入库单: 单据={}, 原因={}", orderId, reason);
        warehouseService.cancelInbound(orderId, reason);
        return Result.success();
    }

    /**
     * 查询入库单明细
     */
    @GetMapping("/inbound/orders/{orderId}/details")
    public Result<List<InboundDetail>> getInboundDetails(@PathVariable Long orderId) {
        return Result.success(warehouseService.getInboundDetails(orderId));
    }

    // ==================== 出库管理 ====================

    /**
     * 创建出库单（含明细，创建时自动锁定库位库存）
     */
    @PostMapping("/outbound/orders")
    public Result<Long> createOutboundOrder(@RequestBody OutboundOrderRequest request) {
        List<OutboundDetail> details = request.getDetails() == null ? List.of() : request.getDetails();
        log.info("创建出库单请求: 仓库={}, 明细行数={}",
                request.getOrder() == null ? null : request.getOrder().getWarehouseId(), details.size());
        Long orderId = warehouseService.createOutboundOrder(request.getOrder(), details);
        return Result.success(orderId);
    }

    /**
     * 查询出库单详情
     */
    @GetMapping("/outbound/orders/{orderId}")
    public Result<OutboundOrder> getOutboundOrder(@PathVariable Long orderId) {
        return Result.success(warehouseService.getOutboundOrder(orderId));
    }

    /**
     * 分页查询出库单
     */
    @GetMapping("/outbound/orders/page")
    public Result<Page<OutboundOrder>> listOutboundOrders(@RequestParam(required = false) Long warehouseId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        log.info("分页查询出库单: 仓库={}, 状态={}", warehouseId, status);
        return Result.success(warehouseService.listOutboundOrders(warehouseId, status, page, size));
    }

    /**
     * 确认出库（扣减库位库存并释放锁定）
     */
    @PostMapping("/outbound/orders/{orderId}/confirm")
    public Result<Void> confirmOutbound(@PathVariable Long orderId) {
        log.info("确认出库: 单据={}", orderId);
        warehouseService.confirmOutbound(orderId);
        return Result.success();
    }

    /**
     * 取消出库单（释放已锁定的库存）
     */
    @PostMapping("/outbound/orders/{orderId}/cancel")
    public Result<Void> cancelOutbound(@PathVariable Long orderId,
                                       @RequestParam(required = false) String reason) {
        log.info("取消出库单: 单据={}, 原因={}", orderId, reason);
        warehouseService.cancelOutbound(orderId, reason);
        return Result.success();
    }

    /**
     * 查询出库单明细
     */
    @GetMapping("/outbound/orders/{orderId}/details")
    public Result<List<OutboundDetail>> getOutboundDetails(@PathVariable Long orderId) {
        return Result.success(warehouseService.getOutboundDetails(orderId));
    }

    /**
     * 入库单创建请求
     */
    @Data
    public static class InboundOrderRequest {
        private InboundOrder order;
        private List<InboundDetail> details;
    }

    /**
     * 出库单创建请求
     */
    @Data
    public static class OutboundOrderRequest {
        private OutboundOrder order;
        private List<OutboundDetail> details;
    }
}
