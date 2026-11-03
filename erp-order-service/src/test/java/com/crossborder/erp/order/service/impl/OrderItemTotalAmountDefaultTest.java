package com.crossborder.erp.order.service.impl;

import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.order.mapper.OrderItemMapper;
import com.crossborder.erp.order.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>明细行 total_amount 缺失导致订单插入 100% 失败的回归测试</b>。
 *
 * <p><b>缺陷本体</b>：{@code t_order_item.total_amount} 是
 * {@code DECIMAL(18,2) NOT NULL} 且<b>无默认值</b>，
 * MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里没有这一列 →
 * MySQL 报 {@code Field 'total_amount' doesn't have a default value}
 * → <b>整个订单插入失败</b>（实测 code=500，库里 0 行）。</p>
 *
 * <p>📌 <b>为什么之前没发现</b>：平台适配器（Amazon / Shopee）
 * 都会 {@code setTotalAmount(unitPrice × quantity)}，
 * 所以走平台同步的路径看起来是好的；
 * 但<b>订单服务自己</b>没有兜底 ——
 * 前端 / 第三方直接调 {@code POST /order/create} 时，
 * 只要没传 {@code totalAmount} 就 100% 失败。</p>
 *
 * <p>📌 金额本身就是「单价 × 数量」可推导的，
 * 不应该让调用方负责 → 服务层直接算出来。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("明细行 total_amount 兜底（NOT NULL 无默认值 → 插入必失败）")
class OrderItemTotalAmountDefaultTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() throws Exception {
        orderService = new OrderServiceImpl(orderItemMapper);
        Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(orderService, orderMapper);
    }

    private Order newOrder() {
        Order order = new Order();
        order.setPlatform("amazon");
        order.setShopId("US001");
        order.setPlatformOrderNo("AMZ-TOTAL-001");
        order.setOrderAmount(new BigDecimal("99.90"));
        order.setCurrencyCode("USD");
        order.setStatus("PENDING");
        return order;
    }

    private OrderItem newItem() {
        OrderItem item = new OrderItem();
        item.setProductSku("SKU-1001");
        item.setUnitPrice(new BigDecimal("49.95"));
        item.setQuantity(2);
        // 📌 刻意不设 totalAmount
        return item;
    }

    @Test
    @DisplayName("🔴 明细行缺 totalAmount → 自动算 单价×数量（否则 INSERT 被 MySQL 拒绝）")
    void totalAmountDerivedFromUnitPriceTimesQuantity() {
        Order order = newOrder();
        OrderItem item = newItem();

        orderService.createOrder(order, List.of(item));

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getTotalAmount())
                .as("t_order_item.total_amount 是 NOT NULL 且无默认值")
                .isEqualByComparingTo(new BigDecimal("99.90"));
    }

    @Test
    @DisplayName("✅ 调用方已传 totalAmount → 原样保留，不被覆盖")
    void existingTotalAmountPreserved() {
        Order order = newOrder();
        OrderItem item = newItem();
        item.setTotalAmount(new BigDecimal("88.88"));

        orderService.createOrder(order, List.of(item));

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("88.88"));
    }

    @Test
    @DisplayName("✅ 单价或数量缺失 → 不乱算，保持 null（宁可让上层报错也不要算错钱）")
    void noDerivationWhenInputsMissing() {
        Order order = newOrder();
        OrderItem item = newItem();
        item.setUnitPrice(null);

        orderService.createOrder(order, List.of(item));

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getTotalAmount()).isNull();
    }
}
