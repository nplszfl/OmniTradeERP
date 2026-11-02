package com.crossborder.erp.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 自动装配测试
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@DisplayName("Redis 自动装配")
class CommonRedisAutoConfigurationTest {

    @Test
    @DisplayName("注册 FastJSON2 序列化的 redisTemplate（覆盖 Boot 默认实现）")
    void registersFastJsonRedisTemplate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonRedisAutoConfiguration.class))
                .withUserConfiguration(ConnectionFactoryStub.class)
                .run(context -> {
                    assertThat(context).hasBean("redisTemplate");
                    RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
                    assertThat(template.getValueSerializer().getClass().getSimpleName())
                            .contains("FastJson");
                    assertThat(template.getKeySerializer().getClass().getSimpleName())
                            .isEqualTo("StringRedisSerializer");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConnectionFactoryStub {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }
    }
}
