package com.crossborder.erp.common.config;

import com.crossborder.erp.common.aspect.PerformanceMonitorAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * 指标与切面自动装配
 *
 * <p>把 {@link PerformanceMonitorAspect} 注册到所有带 Actuator（{@link MeterRegistry}）的服务。</p>
 *
 * <p>🔴 <b>这里曾经还有一个 {@code MetricsConfig}</b>，它声明了 5 个 Micrometer 指标
 * （{@code order.create.count} / {@code order.sync.count} / {@code order.status} /
 * {@code platform.api.call.count} / {@code platform.api.error.count}），
 * 看起来「监控齐了」，实际上：</p>
 * <ul>
 *   <li>4 个 Counter <b>没有任何地方 {@code .increment()}</b> → <b>永远是 0</b>；</li>
 *   <li>{@code order.status} 的 Gauge 直接 {@code return 0;} → <b>永远是 0</b>；</li>
 *   <li>而且它们声明在 <b>erp-common</b> 里 —— 公共库<b>拿不到</b>订单表、也拿不到平台 API，
 *       所以在 common 里声明业务域指标，<b>从设计上就只能写死</b>。</li>
 * </ul>
 * <p>后果：{@code /actuator/prometheus} 抓到的全是假事实 ——
 * 「订单创建总数 = 0」而系统明明在创建订单；「平台 API 失败次数 = 0」而平台同步失败时监控完全看不见。</p>
 * <p>✅ 修法：<b>指标归「真正拥有这份数据」的服务</b> ——
 * 订单指标放 erp-order-service，平台 API 指标放 erp-platform-service，
 * 并在<b>真实的写入点</b> increment。公共库只放<b>通用能力</b>（比如 {@code @Timed} 切面）。</p>
 * <p>📌 防复发：{@code scripts/check-metrics-truthfulness.py}（第 36 道闸门）会扫这件事。</p>
 *
 * <p>两个前提条件缺一不可：</p>
 * <ul>
 *   <li>{@link MeterRegistry} 必须存在，否则 Bean 创建直接失败（不是 Actuator 应用没有该 Bean）；
 *       用 {@code @ConditionalOnBean} 兜底，且用 {@code afterName} 保证排在
 *       Boot 的 {@code MetricsAutoConfiguration} 之后评估条件。</li>
 *   <li>{@code org.aspectj.lang.annotation.Aspect} 必须在 classpath 上，
 *       否则切面只是普通 Bean，Around 通知不会织入。</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@AutoConfiguration(afterName = "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration")
@ConditionalOnClass({MeterRegistry.class, org.aspectj.lang.annotation.Aspect.class})
@ConditionalOnBean(MeterRegistry.class)
@Import(PerformanceMonitorAspect.class)
public class CommonMetricsAutoConfiguration {
}
