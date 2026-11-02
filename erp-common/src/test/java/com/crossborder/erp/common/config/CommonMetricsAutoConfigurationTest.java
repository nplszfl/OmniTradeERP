package com.crossborder.erp.common.config;

import com.crossborder.erp.common.aspect.PerformanceMonitorAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标 / 切面自动装配测试
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("指标与切面自动装配")
class CommonMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonMetricsAutoConfiguration.class));

    @Test
    @DisplayName("有 MeterRegistry 时注册计数器与性能切面")
    void registersWhenMeterRegistryPresent() {
        runner.withUserConfiguration(MeterRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PerformanceMonitorAspect.class);
            assertThat(context).hasBean("orderCreateCounter");
            assertThat(context).hasBean("platformApiCallCounter");

            Counter counter = context.getBean("orderCreateCounter", Counter.class);
            assertThat(counter.getId().getName()).isEqualTo("order.create.count");
        });
    }

    @Test
    @DisplayName("没有 MeterRegistry（非 Actuator 应用）时不注册，避免启动直接失败")
    void doesNotRegisterWithoutMeterRegistry() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(PerformanceMonitorAspect.class);
            assertThat(context).doesNotHaveBean("orderCreateCounter");
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
