package com.crossborder.erp.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.mapper.OrderMapper;
import com.crossborder.erp.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link OrderStatisticsController} 单元测试
 *
 * <p>⚠️ 这个 Controller 的方法之前**全是假实现**（金额恒 0、平台分布写死、
 * 趋势直接返回空列表），而当时**一个测试都没有**，所以没人发现。
 * 这个测试类的作用就是把「真实数据必须真的被用上」锁死。</p>
 */
@DisplayName("OrderStatisticsController 订单统计")
class OrderStatisticsControllerTest {

    private OrderService orderService;
    private OrderMapper orderMapper;
    private OrderStatisticsController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        orderMapper = mock(OrderMapper.class);
        controller = new OrderStatisticsController(orderService, orderMapper);
    }

    // ---------------------------------------------------------------- 今日统计

    @Test
    @DisplayName("getTodayStatistics - 用真实聚合查询，不是写死的 0")
    void getTodayStatistics_usesRealAggregations() {
        when(orderService.count(any())).thenReturn(30L);
        when(orderMapper.sumOrderAmount(any(), any())).thenReturn(new BigDecimal("6301.00"));

        Map<String, Object> row = new HashMap<>();
        row.put("platform", "amazon");
        row.put("cnt", 13L);
        when(orderMapper.countByPlatform(any(), any())).thenReturn(List.of(row));

        Map<String, Object> srow = new HashMap<>();
        srow.put("status", "SHIPPED");
        srow.put("cnt", 14L);
        when(orderMapper.countByStatus(any(), any())).thenReturn(List.of(srow));

        Map<String, Object> stats = controller.getTodayStatistics().getData();

        assertEquals(30L, stats.get("orderCount"));
        // 关键断言：金额必须来自 mapper，不能是写死的 0
        assertEquals(0, ((BigDecimal) stats.get("totalAmount")).compareTo(new BigDecimal("6301.00")));

        @SuppressWarnings("unchecked")
        Map<String, Long> platform = (Map<String, Long>) stats.get("platformDistribution");
        assertEquals(13L, platform.get("amazon"));

        @SuppressWarnings("unchecked")
        Map<String, Long> status = (Map<String, Long>) stats.get("statusDistribution");
        assertEquals(14L, status.get("SHIPPED"));
    }

    @Test
    @DisplayName("getTodayStatistics - 数据库没数据时是 0，不是 null")
    void getTodayStatistics_emptyDbReturnsZero() {
        when(orderService.count(any())).thenReturn(0L);
        when(orderMapper.sumOrderAmount(any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.countByPlatform(any(), any())).thenReturn(List.of());
        when(orderMapper.countByStatus(any(), any())).thenReturn(List.of());

        Map<String, Object> stats = controller.getTodayStatistics().getData();

        assertEquals(0L, stats.get("orderCount"));
        assertNotNull(stats.get("totalAmount"));
        assertEquals(0, ((BigDecimal) stats.get("totalAmount")).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("getTodayStatistics - platform 为 null 的订单归到 unknown，不能丢")
    void getTodayStatistics_nullPlatformGoesToUnknown() {
        when(orderService.count(any())).thenReturn(1L);
        when(orderMapper.sumOrderAmount(any(), any())).thenReturn(BigDecimal.ONE);

        Map<String, Object> row = new HashMap<>();
        row.put("platform", null);
        row.put("cnt", 1L);
        when(orderMapper.countByPlatform(any(), any())).thenReturn(List.of(row));
        when(orderMapper.countByStatus(any(), any())).thenReturn(List.of());

        Map<String, Object> stats = controller.getTodayStatistics().getData();

        @SuppressWarnings("unchecked")
        Map<String, Long> platform = (Map<String, Long>) stats.get("platformDistribution");
        assertEquals(1L, platform.get("unknown"));
    }

    @Test
    @DisplayName("getTodayStatistics - COUNT(*) 返回 Integer 时也能正确转成 long")
    void getTodayStatistics_handlesIntegerCount() {
        when(orderService.count(any())).thenReturn(0L);
        when(orderMapper.sumOrderAmount(any(), any())).thenReturn(BigDecimal.ZERO);

        Map<String, Object> row = new HashMap<>();
        row.put("platform", "amazon");
        // 某些驱动 COUNT(*) 会返回 Integer 或 BigDecimal
        row.put("cnt", 13);
        when(orderMapper.countByPlatform(any(), any())).thenReturn(List.of(row));
        when(orderMapper.countByStatus(any(), any())).thenReturn(List.of());

        Map<String, Object> stats = controller.getTodayStatistics().getData();

        @SuppressWarnings("unchecked")
        Map<String, Long> platform = (Map<String, Long>) stats.get("platformDistribution");
        assertEquals(13L, platform.get("amazon"));
    }

    // ---------------------------------------------------------------- 销售趋势

    @Test
    @DisplayName("getSalesTrend - 返回连续日期，缺失的天补 0")
    void getSalesTrend_fillsMissingDates() {
        // 只有今天有数据
        Map<String, Object> row = new HashMap<>();
        row.put("dt", LocalDate.now().toString());
        row.put("cnt", 29L);
        row.put("amount", new BigDecimal("6301.00"));
        when(orderMapper.aggregateByDay(any(), any())).thenReturn(List.of(row));

        List<Map<String, Object>> trend = controller.getSalesTrend(7).getData();

        assertEquals(7, trend.size());
        // 最后一天 = 今天
        assertEquals(LocalDate.now().toString(), trend.get(6).get("date"));
        assertEquals(29L, trend.get(6).get("count"));
        // 前 6 天没订单 → 补 0，而不是缺项或 null
        for (int i = 0; i < 6; i++) {
            assertEquals(0L, trend.get(i).get("count"));
            assertNotNull(trend.get(i).get("amount"));
            assertEquals(0, ((BigDecimal) trend.get(i).get("amount")).compareTo(BigDecimal.ZERO));
        }
    }

    @Test
    @DisplayName("getSalesTrend - days 非法时被钳到 1，不会算崩时间区间")
    void getSalesTrend_clampsInvalidDays() {
        when(orderMapper.aggregateByDay(any(), any())).thenReturn(List.of());

        assertEquals(1, controller.getSalesTrend(0).getData().size());
        assertEquals(1, controller.getSalesTrend(-5).getData().size());
        assertEquals(365, controller.getSalesTrend(99999).getData().size());
    }

    @Test
    @DisplayName("getSalesTrend - 默认 7 天")
    void getSalesTrend_defaultsTo7Days() {
        when(orderMapper.aggregateByDay(any(), any())).thenReturn(List.of());

        assertEquals(7, controller.getSalesTrend(7).getData().size());
        verify(orderMapper).aggregateByDay(any(), any());
    }

    @Test
    @DisplayName("getSalesTrend - 日期按升序排列")
    void getSalesTrend_datesAreAscending() {
        when(orderMapper.aggregateByDay(any(), any())).thenReturn(List.of());

        List<Map<String, Object>> trend = controller.getSalesTrend(7).getData();

        String prev = "";
        for (Map<String, Object> item : trend) {
            String date = (String) item.get("date");
            assertTrue(date.compareTo(prev) > 0, "日期应升序: " + prev + " → " + date);
            prev = date;
        }
    }
}
