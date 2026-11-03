package com.crossborder.erp.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.order.dto.OrderBatchRequest;
import com.crossborder.erp.order.dto.OrderExportResponse;
import com.crossborder.erp.order.dto.OrderQueryRequest;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单Controller
 */
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/create")
    public Result<String> createOrder(@RequestBody CreateOrderRequest request) {
        Long orderId = orderService.createOrder(request.getOrder(), request.getOrderItems());
        // 📌 data 用字符串：orderId 是雪花 ID（19 位），JS Number 承载不了
        return Result.successId(orderId);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    public Result<Order> getOrder(@PathVariable Long orderId) {
        Order order = orderService.getOrderById(orderId);
        return Result.success(order);
    }

    /**
     * 根据平台订单号查询
     */
    @GetMapping("/platform/{platform}/{platformOrderNo}")
    public Result<Order> getOrderByPlatformOrderNo(@PathVariable String platform,
                                                     @PathVariable String platformOrderNo) {
        Order order = orderService.getOrderByPlatformOrderNo(platform, platformOrderNo);
        return Result.success(order);
    }

    /**
     * 更新订单状态
     */
    @PutMapping("/{orderId}/status")
    public Result<Void> updateOrderStatus(@PathVariable Long orderId,
                                          @RequestParam String status) {
        orderService.updateOrderStatus(orderId, status);
        return Result.success();
    }

    /**
     * 更新物流信息
     */
    @PutMapping("/{orderId}/shipping")
    public Result<Void> updateShippingInfo(@PathVariable Long orderId,
                                           @RequestParam String trackingNumber,
                                           @RequestParam String logisticsCompany) {
        orderService.updateShippingInfo(orderId, trackingNumber, logisticsCompany);
        return Result.success();
    }

    /**
     * 分页查询订单列表
     */
    @GetMapping("/list")
    public Result<PageResult<Order>> pageOrders(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        Page<Order> page = new Page<>(current, size);
        IPage<Order> result = orderService.pageOrders(page, platform, status, startTime, endTime);
        return Result.success(PageResult.of(result));
    }

    /**
     * 查询订单商品明细
     */
    @GetMapping("/{orderId}/items")
    public Result<List<OrderItem>> getOrderItems(@PathVariable Long orderId) {
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return Result.success(items);
    }

    // ========== 高级查询API ==========

    /**
     * 高级分页查询订单（支持多维度筛选）
     */
    @PostMapping("/advanced/list")
    public Result<PageResult<Order>> advancedQuery(@RequestBody OrderQueryRequest request) {
        log.info("高级订单查询请求: {}", request);
        
        Page<Order> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<Order> result = orderService.advancedQuery(page, request);
        return Result.success(PageResult.of(result));
    }

    // ========== 批量操作API ==========

    /**
     * 批量更新订单状态
     */
    @PostMapping("/batch/update-status")
    public Result<Integer> batchUpdateStatus(@RequestBody OrderBatchRequest request) {
        log.info("批量更新订单状态, 订单数量: {}, 目标状态: {}", 
                request.getOrderIds().size(), request.getTargetStatus());
        
        int count = orderService.batchUpdateStatus(request.getOrderIds(), request.getTargetStatus());
        return Result.success(count);
    }

    /**
     * 批量标记发货
     */
    @PostMapping("/batch/mark-shipped")
    public Result<Integer> batchMarkShipped(@RequestBody OrderBatchRequest request) {
        log.info("批量标记发货, 订单数量: {}", request.getOrderIds().size());
        
        int count = orderService.batchMarkShipped(
                request.getOrderIds(), 
                request.getTrackingNumber(), 
                request.getLogisticsCompany()
        );
        return Result.success(count);
    }

    /**
     * 批量导出订单
     */
    @PostMapping("/batch/export")
    public Result<OrderExportResponse> exportOrders(@RequestBody OrderBatchRequest request) {
        log.info("批量导出订单, 订单数量: {}", 
                request.getOrderIds() != null ? request.getOrderIds().size() : 0);
        
        OrderQueryRequest queryRequest = new OrderQueryRequest();
        OrderExportResponse response = orderService.exportOrders(request.getOrderIds(), queryRequest);
        return Result.success(response);
    }

    // ========== 订单统计API ==========

    /**
     * 按国家统计订单数量
     */
    @PostMapping("/statistics/by-country")
    public Result<Map<String, Long>> countByCountry(@RequestBody OrderQueryRequest request) {
        Map<String, Long> stats = orderService.countByCountry(request);
        return Result.success(stats);
    }

    /**
     * 按平台统计订单金额
     */
    @PostMapping("/statistics/by-platform")
    public Result<Map<String, BigDecimal>> sumAmountByPlatform(@RequestBody OrderQueryRequest request) {
        Map<String, BigDecimal> stats = orderService.sumAmountByPlatform(request);
        return Result.success(stats);
    }

    /**
     * 创建订单请求
     */
    @lombok.Data
    public static class CreateOrderRequest {
        private Order order;
        private List<OrderItem> orderItems;
    }
}
