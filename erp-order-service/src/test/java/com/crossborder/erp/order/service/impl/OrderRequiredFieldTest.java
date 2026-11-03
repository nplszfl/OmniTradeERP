package com.crossborder.erp.order.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.order.mapper.OrderItemMapper;
import com.crossborder.erp.order.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 🔴 <b>订单必填字段校验的回归测试</b>。
 *
 * <p><b>缺陷本体</b>：{@code t_order.shop_id} / {@code platform} /
 * {@code platform_order_no} 都是 {@code NOT NULL} 且<b>无默认值</b>，
 * 而 MyBatis-Plus 的 insert 默认 {@code FieldStrategy.NOT_NULL}
 * 会<b>跳过 null 字段</b> → INSERT 语句里根本没有这些列 →
 * MySQL 报 {@code Field 'shop_id' doesn't have a default value}
 * → <b>整个订单插入失败</b>。</p>
 *
 * <p>📌 <b>实测泄露</b>：修复前前端收到的响应体是<b>整段 SQL 栈</b> ——
 * 含完整 INSERT 语句、所有列名、表名 {@code t_order}、
 * 甚至 mapper 类名 {@code OrderMapper.insert-Inline}。
 * → 一个请求就能拿到整个表结构，是实打实的**数据库结构泄露**。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单创建：NOT NULL 无默认值列的必填校验")
class OrderRequiredFieldTest {

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

    private Order validOrder() {
        Order order = new Order();
        order.setPlatform("amazon");
        order.setShopId("US001");
        order.setPlatformOrderNo("AMZ-REQ-001");
        order.setOrderAmount(new BigDecimal("99.90"));
        order.setCurrencyCode("USD");
        order.setStatus("PENDING");
        return order;
    }

    private OrderItem validItem() {
        OrderItem item = new OrderItem();
        item.setUnitPrice(new BigDecimal("99.90"));
        item.setQuantity(1);
        item.setTotalAmount(new BigDecimal("99.90"));
        item.setCurrencyCode("USD");
        return item;
    }

    @Test
    @DisplayName("🔴 缺 shopId → 报业务异常（不是 SQL 500，更不泄露 SQL）")
    void shopIdRequired() {
        Order order = validOrder();
        order.setShopId(null);

        assertThatThrownBy(() -> orderService.createOrder(order, List.of(validItem())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("店铺不能为空");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 缺 platform → 报业务异常")
    void platformRequired() {
        Order order = validOrder();
        order.setPlatform("  ");

        assertThatThrownBy(() -> orderService.createOrder(order, List.of(validItem())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单平台不能为空");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 缺 platformOrderNo → 报业务异常")
    void platformOrderNoRequired() {
        Order order = validOrder();
        order.setPlatformOrderNo(null);

        assertThatThrownBy(() -> orderService.createOrder(order, List.of(validItem())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("平台订单号不能为空");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 order 为 null → 报业务异常（不是 NPE）")
    void orderNullHandled() {
        assertThatThrownBy(() -> orderService.createOrder(null, List.of(validItem())))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单不能为空");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("🔴 明细为 null → 报业务异常（不是 NPE）")
    void itemsNullHandled() {
        assertThatThrownBy(() -> orderService.createOrder(validOrder(), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单明细不能为空");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("✅ 空明细列表仍允许（不拦空，只拦 null —— 平台同步可能有 0 明细的订单）")
    void emptyItemsStillAllowed() {
        assertThatCode(() -> orderService.createOrder(validOrder(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("✅ 字段齐全 → 正常插入")
    void validOrderInserts() {
        Order order = validOrder();

        orderService.createOrder(order, List.of(validItem()));

        verify(orderMapper).insert(any(Order.class));
    }
}
