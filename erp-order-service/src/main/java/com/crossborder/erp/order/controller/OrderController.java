package com.crossborder.erp.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单Controller
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/create")
    public Result<Long> createOrder(@RequestBody CreateOrderRequest request) {
        Long orderId = orderService.createOrder(request.getOrder(), request.getOrderItems());
        return Result.success(orderId);
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

    /**
     * 创建订单请求
     */
    @lombok.Data
    public static class CreateOrderRequest {
        private Order order;
        private List<OrderItem> orderItems;
    }
}
