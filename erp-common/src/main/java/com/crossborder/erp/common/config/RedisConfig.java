package com.crossborder.erp.common.config;

import com.alibaba.fastjson2.support.spring6.data.redis.GenericFastJsonRedisSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置
 * 使用FastJSON2作为序列化器
 */
@Configuration
public class RedisConfig {

    /**
     * erp-common 的 FastJSON2 版 RedisTemplate。
     *
     * <p>📌 必须加 {@code @ConditionalOnMissingBean(name = "redisTemplate")}：
     * Boot 自带的 {@code RedisAutoConfiguration#redisTemplate} 也叫这个名字。
     * 没有这个条件时，谁先被处理谁先注册；后注册的那个在
     * {@code allow-bean-definition-overriding=false}（默认值）下会抛
     * {@code BeanDefinitionOverrideException} → **整个服务起不来**。
     * 加上之后「先到先得」，无论自动装配顺序如何都不会冲突。</p>
     *
     * <p>📌 谁先到由 {@link CommonRedisAutoConfiguration} 上的
     * {@code @AutoConfigureBefore(RedisAutoConfiguration.class)} 决定 —— 靠类名字母序
     * 「碰巧」让本配置先跑是**不可靠**的（erp-gateway 上已被实测证伪）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key使用String序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value使用FastJSON2序列化
        GenericFastJsonRedisSerializer fastJsonSerializer = new GenericFastJsonRedisSerializer();
        template.setValueSerializer(fastJsonSerializer);
        template.setHashValueSerializer(fastJsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
