package com.crossborder.erp.common.aspect;

import com.crossborder.erp.common.config.CommonMetricsAutoConfiguration;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 性能监控切面测试
 *
 * <p>验证切面真的织入并记录了耗时指标 —— 之前由于缺少 aspectjweaver，
 * {@code @Timed} 注解在 order 服务里完全无效（16 处）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("性能监控切面")
class PerformanceMonitorAspectTest {

    @Test
    @DisplayName("@Timed 方法执行后写入 MeterRegistry，指标名取 @Timed 的 value")
    void recordsTimerForTimedMethod() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonMetricsAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    context.getBean(OrderServiceStub.class).create();

                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.find("order.create").timer()).isNotNull();
                    assertThat(registry.find("order.create").timer().count()).isEqualTo(1L);
                    assertThat(registry.find("order.create").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isGreaterThanOrEqualTo(0L);
                });
    }

    @Service
    static class OrderServiceStub {

        @Timed(value = "order.create", description = "创建订单")
        public String create() {
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        OrderServiceStub orderServiceStub() {
            return new OrderServiceStub();
        }
    }
}
