package com.crossborder.erp.platform.schedule;

import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.feign.OrderServiceClient;
import com.crossborder.erp.platform.service.PlatformConfigService;
import com.crossborder.erp.platform.support.PlatformSyncRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单同步调度器单元测试
 *
 * 📌 锁的是 2026-11-03 修的那三层契约错误（路径前缀 / 请求体字段 / Result 拆包）。
 *    回归点：**后端返回 Result&lt;Order&gt; 时，必须拆开取 data** ——
 *    不拆的话 `existingOrder != null` 恒成立，每一单都被当成「已存在」跳过。
 */
class OrderSyncScheduleTest {

    private OrderServiceClient orderServiceClient;
    private OrderSyncSchedule schedule;

    @BeforeEach
    void setUp() {
        orderServiceClient = mock(OrderServiceClient.class);
        schedule = new OrderSyncSchedule(
                mock(PlatformConfigService.class),
                new PlatformSyncRegistry(Collections.emptyList()),
                orderServiceClient);
    }

    private Order sampleOrder() {
        Order o = new Order();
        o.setId(1L);
        o.setPlatform("amazon");
        o.setPlatformOrderNo("AMZ-001");
        return o;
    }

    @Test
    @DisplayName("后端返回 Result<Order> 且 data 为 null → 判定为不存在，继续创建")
    void createsOrderWhenLookupMisses() {
        when(orderServiceClient.getOrderByPlatformOrderNo(any(), any()))
                .thenReturn(Result.success(null));
        when(orderServiceClient.createOrder(any()))
                .thenReturn(Result.success(123L));

        List<OrderItem> items = Collections.emptyList();
        schedule.saveOrderToOrderService(sampleOrder(), items);

        verify(orderServiceClient, times(1)).createOrder(any());
    }

    @Test
    @DisplayName("后端返回 Result<Order> 且 data 非 null → 判定为已存在，跳过创建")
    void skipsWhenOrderAlreadyExists() {
        when(orderServiceClient.getOrderByPlatformOrderNo(any(), any()))
                .thenReturn(Result.success(sampleOrder()));

        schedule.saveOrderToOrderService(sampleOrder(), Collections.emptyList());

        verify(orderServiceClient, never()).createOrder(any());
    }

    @Test
    @DisplayName("整个 Result 为 null（服务异常返回空）→ 也要继续创建，不能误判为已存在")
    void treatsNullResultAsMissing() {
        when(orderServiceClient.getOrderByPlatformOrderNo(any(), any())).thenReturn(null);
        when(orderServiceClient.createOrder(any())).thenReturn(Result.success(123L));

        schedule.saveOrderToOrderService(sampleOrder(), Collections.emptyList());

        verify(orderServiceClient, times(1)).createOrder(any());
    }
}
