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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 🔴 <b>币种兜底的回归测试</b> —— 修「平台订单同步一单都存不进去」。
 *
 * <p><b>缺陷本体</b>：{@code t_order.currency_code} 和
 * {@code t_order_item.currency_code} 都是 {@code NOT NULL} 且<b>无默认值</b>，
 * 而 MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这一列 →
 * MySQL 报 {@code Field 'currency_code' doesn't have a default value}
 * → <b>整个订单插入失败</b>。</p>
 *
 * <p>📌 平台适配器（Amazon / Shopee）<b>从来没设过</b>币种
 * → 平台订单同步<b>每一单都存不进去</b>，
 * 而异常被 {@code catch (Exception e) { log.error(...) }} 吞成一行日志，
 * 定时任务看起来像「同步跑过了」。</p>
 *
 * <p>📌 <b>为什么单测查不出来</b>：单测用 mock 的 Mapper，
 * 根本不会真的执行 INSERT，也就没有「NOT NULL 无默认值」这回事。
 * 这个缺陷只在<b>真 MySQL</b> 上才暴露。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单创建：币种兜底（NOT NULL 无默认值 → 插入必失败）")
class OrderCurrencyDefaultTest {

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

    private Order orderWithoutCurrency() {
        Order order = new Order();
        order.setPlatform("amazon");
        order.setShopId("US001");
        order.setPlatformOrderNo("AMZ-E2E-001");
        order.setOrderAmount(new BigDecimal("99.99"));
        order.setStatus("PENDING");
        // 📌 刻意不设 currencyCode —— 这就是适配器过去的行为
        return order;
    }

    @Test
    @DisplayName("🔴 币种缺失 → 兜底成 USD（否则 INSERT 被 MySQL 拒绝）")
    void currencyFallsBackToUsd() {
        Order order = orderWithoutCurrency();

        orderService.createOrder(order, List.of());

        assertThat(order.getCurrencyCode())
                .as("t_order.currency_code 是 NOT NULL 且无默认值，必须有值")
                .isEqualTo("USD");
    }

    @Test
    @DisplayName("🔴 明细行币种缺失 → 继承订单币种（t_order_item.currency_code 同样 NOT NULL）")
    void itemCurrencyInheritsFromOrder() {
        Order order = orderWithoutCurrency();
        OrderItem item = new OrderItem();
        item.setProductSku("SKU-1001");
        item.setUnitPrice(new BigDecimal("49.95"));
        item.setQuantity(2);
        item.setTotalAmount(new BigDecimal("99.90"));
        // 📌 刻意不设币种

        orderService.createOrder(order, List.of(item));

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("✅ 币种已设 → 原样保留，不被兜底覆盖")
    void existingCurrencyIsPreserved() {
        Order order = orderWithoutCurrency();
        order.setCurrencyCode("EUR");

        orderService.createOrder(order, List.of());

        assertThat(order.getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("✅ 明细行已有币种 → 不被订单币种覆盖")
    void itemCurrencyNotOverwritten() {
        Order order = orderWithoutCurrency();
        order.setCurrencyCode("EUR");
        OrderItem item = new OrderItem();
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setQuantity(1);
        item.setTotalAmount(new BigDecimal("10.00"));
        item.setCurrencyCode("GBP");

        orderService.createOrder(order, List.of(item));

        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("✅ 空白字符串也算缺失（不能只判 null）")
    void blankCurrencyAlsoFallsBack() {
        Order order = orderWithoutCurrency();
        order.setCurrencyCode("   ");

        orderService.createOrder(order, List.of());

        assertThat(order.getCurrencyCode()).isEqualTo("USD");
    }
}
