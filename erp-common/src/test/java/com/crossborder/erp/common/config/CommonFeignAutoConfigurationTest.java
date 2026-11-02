package com.crossborder.erp.common.config;

import feign.RequestInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 自动装配测试
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("Feign 自动装配")
class CommonFeignAutoConfigurationTest {

    @Test
    @DisplayName("注册了全局 RequestInterceptor（Feign 调用自动带 X-Trace-Id）")
    void registersRequestInterceptor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonFeignAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RequestInterceptor.class);
                    assertThat(context.getBean("requestInterceptor", RequestInterceptor.class)).isNotNull();
                });
    }
}
