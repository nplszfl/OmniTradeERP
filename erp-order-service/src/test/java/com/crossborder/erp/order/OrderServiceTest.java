package com.crossborder.erp.order;

import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.enums.OrderStatus;
import com.crossborder.erp.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单服务单元测试
 */
@SpringBootTest
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    public void testCreateOrder() {
        Order order = new Order();
        order.setPlatformOrderId("TEST-001");
        order.setPlatform("amazon");
        order.setShopId("shop001");
        order.setBuyerName("Test Buyer");
        order.setTotalAmount(new BigDecimal("99.99"));
        order.setCurrency("USD");
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderTime(LocalDateTime.now());
        order.setCreateTime(LocalDateTime.now());

        Long orderId = orderService.createOrder(order);
        assertNotNull(orderId);
        assertTrue(orderId > 0);
    }

    @Test
    public void testGetOrderByOrderNo() {
        Order order = orderService.getOrderByOrderNo("TEST-001");
        assertNotNull(order);
        assertEquals("TEST-001", order.getPlatformOrderId());
    }

    @Test
    public void testUpdateOrderStatus() {
        boolean success = orderService.updateOrderStatus("TEST-001", OrderStatus.SHIPPED);
        assertTrue(success);

        Order order = orderService.getOrderByOrderNo("TEST-001");
        assertEquals(OrderStatus.SHIPPED, order.getOrderStatus());
    }

    @Test
    public void testGetOrdersByShop() {
        var orders = orderService.getOrdersByShop("shop001", 1, 10);
        assertNotNull(orders);
        assertFalse(orders.isEmpty());
    }
}
