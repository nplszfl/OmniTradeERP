package com.crossborder.erp.common.config;

import com.crossborder.erp.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用 Web 自动装配测试
 *
 * <p>背景：各服务的 {@code @SpringBootApplication} 都在自己的子包下，
 * 组件扫描覆盖不到 {@code com.crossborder.erp.common}，
 * 所以 erp-common 的 {@link GlobalExceptionHandler} 一直是「写了但没生效」的。
 * 本测试锁死「自动装配确实把它注册进 Servlet 上下文，且不污染非 Web 上下文」。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("通用 Web 自动装配")
class CommonWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

    @Test
    @DisplayName("Servlet 上下文中注册了 GlobalExceptionHandler")
    void registersHandlerInServletContext() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context.getBean(GlobalExceptionHandler.class)).isNotNull();
        });
    }

    @Test
    @DisplayName("非 Web 上下文中不注册 MVC advice")
    void doesNotRegisterInNonWebContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                });
    }

    @Test
    @DisplayName("自动装配清单里登记了该配置类（否则其它模块根本不会加载）")
    void isRegisteredInAutoConfigurationImports() throws Exception {
        String imports;
        try (var in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).as("AutoConfiguration.imports 必须存在").isNotNull();
            imports = new String(in.readAllBytes());
        }
        assertThat(imports).contains(CommonWebAutoConfiguration.class.getName());
    }
}
