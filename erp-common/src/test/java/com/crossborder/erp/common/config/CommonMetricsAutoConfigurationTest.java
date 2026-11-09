package com.crossborder.erp.common.config;

import com.crossborder.erp.common.aspect.PerformanceMonitorAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标 / 切面自动装配测试
 *
 * <p>🔴 这个测试以前断言的是「{@code orderCreateCounter} / {@code platformApiCallCounter} 这两个
 * bean 存在」—— 那两个 bean 声明了指标，却<b>没有任何地方 increment</b> →
 * <b>永远是 0</b>（假事实）。断言「bean 存在」给的是<b>假信心</b>：
 * 「指标在」被当成了「指标在说真话」。</p>
 *
 * <p>现在改成断言<b>真正该成立的事</b>：</p>
 * <ul>
 *   <li>切面（通用能力）被注册；</li>
 *   <li><b>公共库里不再有任何业务域指标 bean</b> —— common 拿不到订单表 / 平台 API，
 *       在这里声明业务域指标<b>只能写死</b>，必然是假事实。</li>
 * </ul>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("指标与切面自动装配")
class CommonMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonMetricsAutoConfiguration.class));

    /** 业务域指标前缀 —— 公共库里出现即代表「这里拿不到这份数据」 */
    private static final Set<String> BUSINESS_DOMAIN_PREFIXES =
            Set.of("order.", "platform.", "inventory.", "purchase.", "customer.",
                   "supplier.", "finance.", "warehouse.");

    @Test
    @DisplayName("有 MeterRegistry 时注册性能切面")
    void registersPerformanceAspectWhenMeterRegistryPresent() {
        runner.withUserConfiguration(MeterRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PerformanceMonitorAspect.class);
        });
    }

    @Test
    @DisplayName("公共库里不再声明业务域指标（拿不到数据，只能写死 → 必然是假事实）")
    void commonDoesNotDeclareBusinessDomainMetrics() {
        runner.withUserConfiguration(MeterRegistryConfiguration.class).run(context -> {
            Set<String> businessMetrics = java.util.Arrays.stream(
                            context.getBeanNamesForType(io.micrometer.core.instrument.Meter.class))
                    .map(name -> {
                        try {
                            return ((io.micrometer.core.instrument.Meter) context.getBean(name))
                                    .getId().getName();
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .filter(n -> !n.isBlank())
                    .filter(n -> BUSINESS_DOMAIN_PREFIXES.stream().anyMatch(n::startsWith))
                    .collect(Collectors.toSet());
            assertThat(businessMetrics)
                    .as("erp-common 不该声明业务域指标：它拿不到这些数据，写出来只能是假的")
                    .isEmpty();
        });
    }

    @Test
    @DisplayName("没有 MeterRegistry（非 Actuator 应用）时不注册，避免启动直接失败")
    void doesNotRegisterWithoutMeterRegistry() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(PerformanceMonitorAspect.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
