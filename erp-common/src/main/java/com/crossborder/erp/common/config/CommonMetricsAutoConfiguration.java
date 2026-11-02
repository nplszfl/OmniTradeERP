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
 * <p>把 {@link MetricsConfig} 与 {@link PerformanceMonitorAspect} 注册到所有
 * 带 Actuator（{@link MeterRegistry}）的服务。</p>
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
@Import({MetricsConfig.class, PerformanceMonitorAspect.class})
public class CommonMetricsAutoConfiguration {
}
