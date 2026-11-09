package com.crossborder.erp.platform.aspect;

import com.crossborder.erp.platform.annotation.ApiMonitor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台 API 监控切面测试 —— <b>端到端</b>：真的起 Spring 上下文、真的走代理、真的写指标。
 *
 * <p>🔴 <b>这个切面之前是「死的」</b>：它拦截 {@code @ApiMonitor}，
 * 而 {@code @ApiMonitor} <b>全仓没人用</b> → 切面永不触发 →
 * {@code api.calls.total} / {@code api.errors.total} / {@code api.call.duration}
 * 三个指标<b>永远停在 0</b>，却看上去「监控已就位」。</p>
 *
 * <p>所以这里不能只测「切面类存在」，必须测「<b>方法被 {@code @ApiMonitor} 标注后，
 * 代理真的被织入、指标真的被写</b>」。</p>
 *
 * @author 火球鼠
 * @since 2026-11-10
 */
@DisplayName("平台 API 监控切面")
class ApiMonitorAspectTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("被 @ApiMonitor 标注的方法：调用成功后真的写进指标")
    void writesMetricsOnSuccess() {
        runner.run(context -> {
            DemoService service = context.getBean(DemoService.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertThat(service.ok()).isEqualTo("ok");

            assertThat(registry.get("api.calls.total")
                    .tag("platform", "demo")
                    .tag("result", "success")
                    .counter().count()).isEqualTo(1.0);

            // 耗时也被记下来了
            assertThat(registry.find("api.call.duration").timer()).isNotNull();
        });
    }

    @Test
    @DisplayName("被 @ApiMonitor 标注的方法：抛异常 → 记失败次数 + 错误数，异常照常抛出")
    void writesMetricsOnFailure() {
        runner.run(context -> {
            DemoService service = context.getBean(DemoService.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertThatThrownBy(service::boom).isInstanceOf(RuntimeException.class);

            assertThat(registry.get("api.calls.total")
                    .tag("platform", "demo")
                    .tag("result", "failure")
                    .counter().count()).isEqualTo(1.0);
            assertThat(registry.get("api.errors.total")
                    .tag("platform", "demo")
                    .counter().count()).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("没标注 @ApiMonitor 的方法：不写指标（切面不该乱织入）")
    void ignoresUnannotatedMethods() {
        runner.run(context -> {
            DemoService service = context.getBean(DemoService.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertThat(service.plain()).isEqualTo("plain");

            assertThat(registry.find("api.calls.total").counter()).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ApiMonitorAspect apiMonitorAspect(MeterRegistry registry) {
            return new ApiMonitorAspect(registry);
        }

        @Bean
        DemoService demoService() {
            return new DemoService();
        }
    }

    static class DemoService {

        @ApiMonitor(platform = "demo")
        public String ok() {
            return "ok";
        }

        @ApiMonitor(platform = "demo")
        public String boom() {
            throw new RuntimeException("boom");
        }

        public String plain() {
            return "plain";
        }
    }
}
