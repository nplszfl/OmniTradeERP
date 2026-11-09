package com.crossborder.erp.order.metrics;

import com.crossborder.erp.common.constant.OrderStatus;
import com.crossborder.erp.order.mapper.OrderMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 订单状态分布指标 —— {@code order.status{status="..."}}。
 *
 * <p>🔴 <b>这个指标之前是假的。</b>它在 {@code erp-common} 的 {@code MetricsConfig} 里声明成
 * 一个孤零零的 Gauge（描述写着「各状态订单数量」，实际只挂了 {@code module=orders} 一个 tag），
 * 而它的 supplier 是：</p>
 * <pre>
 *     private double getOrderStatusCount() {
 *         // TODO: 从数据库查询各状态订单数量
 *         return 0;
 *     }
 * </pre>
 * <p>→ <b>永远返回 0</b>。而它又被 {@code AutoConfiguration.imports} 注册进<b>所有</b>带 Actuator 的服务
 * → 每个服务的 {@code /actuator/prometheus} 里都有一行 {@code order_status 0}，
 * 「订单状态分布」看上去有监控，实际上是<b>假的</b>。</p>
 *
 * <p>✅ 现在：指标归到<b>真正拥有订单表的 erp-order-service</b>，值来自
 * {@link OrderMapper#countByStatusAll()} 的真实 GROUP BY。</p>
 *
 * <p>📌 三条不能省的细节：</p>
 * <ul>
 *   <li><b>每个状态一个 gauge + {@code status} tag</b> —— 原来「各状态」却只有一个数，
 *       形状根本表达不了一份分布（描述与形状不符）。</li>
 *   <li><b>取不到数时返回 NaN，不是 0</b> —— 0 是「确实没有订单」，
 *       NaN 是「不知道」。数据库挂了就报 0，等于<b>撒谎</b>。</li>
 *   <li><b>刷新失败时保留上一次的值</b>，并递增 {@code order.status.refresh.errors}
 *       —— 既不假装知道、也不把故障伪装成「订单都清空了」。</li>
 * </ul>
 *
 * <p>📌 防复发：{@code scripts/check-metrics-truthfulness.py}（第 36 道闸门）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusMetrics implements MeterBinder {

    /** 缓存有效期 —— 每次 Prometheus 抓取都查库会把库打满 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final OrderMapper orderMapper;

    /** 最近一次**成功**取到的分布；空 = 从没成功过 */
    private final AtomicReference<Map<String, Double>> snapshot = new AtomicReference<>(Map.of());
    private final AtomicLong lastRefreshAt = new AtomicLong(0L);
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private volatile Counter refreshErrorCounter;

    @Override
    public void bindTo(MeterRegistry registry) {
        this.refreshErrorCounter = Counter.builder("order.status.refresh.errors")
                .description("订单状态分布刷新失败次数（失败时指标保留上一次的值，而不是变成 0）")
                .register(registry);

        for (OrderStatus status : OrderStatus.values()) {
            Gauge.builder("order.status", () -> countOf(status.getCode()))
                    .tag("status", status.getCode())
                    .description("当前状态为「" + status.getName() + "」的订单数量")
                    .register(registry);
        }
    }

    /**
     * 取某个状态的订单数。
     *
     * <p>⚠️ <b>关键：没有数据时返回 NaN，而不是 0。</b>
     * Prometheus 里 {@code order_status{...} NaN} 表示「无数据」——
     * 不会触发 {@code > 100} 这类告警，也不会在看板上显示成「0 单」；
     * 而返回 0 会被当成「这个状态真的没有订单」→ <b>假事实</b>。</p>
     */
    double countOf(String statusCode) {
        refreshIfStale();
        Map<String, Double> snap = snapshot.get();
        if (snap.isEmpty()) {
            return Double.NaN;
        }
        Double v = snap.get(statusCode);
        // 状态确实为 0 → 0 是真的
        return v == null ? 0d : v;
    }

    /**
     * 缓存过期时刷新一次（多个线程同时触发时只有一个真的去查库）。
     */
    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAt.get() < CACHE_TTL.toMillis()) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            List<Map<String, Object>> rows = orderMapper.countByStatusAll();
            Map<String, Double> dist = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Object status = row.get("status");
                Object cnt = row.get("cnt");
                if (status == null || cnt == null) {
                    continue;
                }
                dist.put(status.toString(), toDouble(cnt));
            }
            snapshot.set(Map.copyOf(dist));
            lastRefreshAt.set(System.currentTimeMillis());
        } catch (Exception e) {
            // 🔴 失败时**保留上一次的值**（而不是变成 0 / NaN）：
            //    上一次的值至少是真的，0 是在撒谎。
            if (refreshErrorCounter != null) {
                refreshErrorCounter.increment();
            }
            log.warn("刷新订单状态分布失败，指标保留上一次的值", e);
        } finally {
            refreshing.set(false);
        }
    }

    /**
     * MyBatis 返回的 COUNT(*) 在不同驱动下可能是 Long / BigDecimal / Integer。
     */
    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
