package com.crossborder.erp.platform.feign;

import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单服务 Feign 客户端
 *
 * ⚠️ 三层契约都必须和 erp-order-service 的 OrderController 对齐
 *    （改任何一边都要重新对一遍）：
 *
 * 1. **路径前缀**：controller 根是 `/order`（`@RequestMapping("/order")`），
 *    原来这里写的是 `/internal/orders` —— 订单服务**根本没有 /internal 这套路径**，
 *    每次调用都是 404，异常又被 saveOrderToOrderService 的 catch 吞成一行日志
 *    → 平台订单同步每 10 分钟跑一次，**一次都没存进订单服务**。
 * 2. **请求体字段名**：后端 `CreateOrderRequest` 的字段是 `order` + **`orderItems`**，
 *    这里原来叫 `items` → JSON 字段对不上，后端收到 null 的 orderItems。
 * 3. **返回值包装**：后端返回的是 `Result<Order>` / `Result<Long>`，
 *    Feign 必须声明成 `Result<...>` 再自己拆 —— 直接声明 `Order` 会把
 *    `{code,message,data}` 反序列化成一个**字段全空**的 Order，
 *    于是 `existingOrder != null` 恒成立 → 永远「订单已存在，跳过」。
 */
@FeignClient(name = "erp-order-service", path = "/order")
public interface OrderServiceClient {

    @PostMapping("/create")
    Result<Long> createOrder(@RequestBody OrderCreateRequest request);

    @GetMapping("/platform/{platform}/{platformOrderNo}")
    Result<Order> getOrderByPlatformOrderNo(@PathVariable("platform") String platform, @PathVariable("platformOrderNo") String platformOrderNo);

    /**
     * 创建订单请求
     *
     * ⚠️ 字段名必须和后端 OrderController.CreateOrderRequest 完全一致：
     *    `order` + `orderItems`（不是 `items`）。
     */
    record OrderCreateRequest(Order order, List<OrderItem> orderItems) {}
}