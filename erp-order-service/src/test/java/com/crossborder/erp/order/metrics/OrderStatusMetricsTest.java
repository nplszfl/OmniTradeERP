package com.crossborder.erp.order.metrics;

import com.crossborder.erp.order.mapper.OrderMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 订单状态分布指标测试。
 *
 * <p>🔴 这个指标之前是<b>假的</b>（{@code erp-common} 里的 supplier 直接 {@code return 0;}），
 * 而老的测试只断言「bean 存在」→ 给的是假信心。</p>
 *
 * <p>这里测的是<b>指标自己的逻辑</b>，三件事必须成立：</p>
 * <ol>
 *   <li>值来自真实的 GROUP BY 结果，<b>不是写死的 0</b>；</li>
 *   <li><b>没取到数时是 NaN，不是 0</b> —— 0 是「确实没有订单」，NaN 才是「不知道」；</li>
 *   <li><b>刷新失败时保留上一次的值</b>，不是变成 0（那是在撒谎）。</li>
 * </ol>
 *
 * @author 火球鼠
 * @since 2026-11-10
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusMetricsTest {

    @Mock
    private OrderMapper orderMapper;

    private OrderStatusMetrics metrics;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        metrics = new OrderStatusMetrics(orderMapper);
        registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);
    }

    private static Map<String, Object> row(String status, Object cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("cnt", cnt);
        return m;
    }

    @Test
    @DisplayName("值来自真实 GROUP BY 结果，不是写死的 0")
    void reportsRealDistribution() {
        when(orderMapper.countByStatusAll()).thenReturn(List.of(
                row("pending_payment", 3L),
                row("shipped", 7L),
                row("cancelled", 1L)));

        assertThat(metrics.countOf("pending_payment")).isEqualTo(3.0);
        assertThat(metrics.countOf("shipped")).isEqualTo(7.0);
        assertThat(metrics.countOf("cancelled")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("某状态确实没有订单时是 0（这是真的 0）")
    void zeroForStatusWithNoRows() {
        when(orderMapper.countByStatusAll()).thenReturn(List.of(row("shipped", 2L)));

        // 该状态一行都没有 → 真的 0
        assertThat(metrics.countOf("refunded")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("从数据库取过一次之后、状态缺失 → 0；从未成功取过 → NaN（不是 0）")
    void nanWhenNeverLoadedAndZeroWhenLoaded() {
        when(orderMapper.countByStatusAll()).thenReturn(List.of(row("shipped", 2L)));

        // 取过一次 → 缺失状态是 0
        assertThat(metrics.countOf("delivered")).isEqualTo(0.0);

        // 全新实例、且查询直接失败 → **NaN**，不是 0
        OrderStatusMetrics fresh = new OrderStatusMetrics(orderMapper);
        fresh.bindTo(new SimpleMeterRegistry());
        when(orderMapper.countByStatusAll()).thenThrow(new RuntimeException("db down"));
        assertThat(fresh.countOf("shipped")).isNaN();
    }

    @Test
    @DisplayName("刷新失败时保留上一次的值（不是变成 0），并记录失败次数")
    void keepsLastValueOnRefreshFailure() throws Exception {
        when(orderMapper.countByStatusAll()).thenReturn(List.of(row("shipped", 9L)));
        assertThat(metrics.countOf("shipped")).isEqualTo(9.0);

        // 让缓存过期，模拟下一次抓取时数据库挂了
        expireCache(metrics);
        when(orderMapper.countByStatusAll()).thenThrow(new RuntimeException("db down"));

        // 🔴 关键：失败后**保留 9**，而不是变成 0 / NaN —— 0 是在撒谎
        assertThat(metrics.countOf("shipped")).isEqualTo(9.0);

        // 而且失败要**可见**
        double errors = registry.get("order.status.refresh.errors").counter().count();
        assertThat(errors).isEqualTo(1.0);
    }

    @Test
    @DisplayName("每个状态一个 gauge，带 status tag（一个数表达不了一份分布）")
    void oneGaugePerStatusWithTag() {
        when(orderMapper.countByStatusAll()).thenReturn(List.of(row("shipped", 4L)));
        metrics.countOf("shipped");

        assertThat(registry.find("order.status").tag("status", "shipped").gauge()).isNotNull();
        assertThat(registry.find("order.status").tag("status", "pending_payment").gauge()).isNotNull();
        assertThat(registry.get("order.status").tag("status", "shipped").gauge().value()).isEqualTo(4.0);
    }

    /**
     * 把缓存打过期（缓存有效期 30s，测试里直接改时间戳）。
     */
    private static void expireCache(OrderStatusMetrics metrics) throws Exception {
        var field = OrderStatusMetrics.class.getDeclaredField("lastRefreshAt");
        field.setAccessible(true);
        var atomic = (java.util.concurrent.atomic.AtomicLong) field.get(metrics);
        atomic.set(0L);
    }
}
