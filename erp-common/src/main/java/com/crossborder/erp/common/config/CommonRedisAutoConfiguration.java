package com.crossborder.erp.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis 自动装配
 *
 * <p>注册 erp-common 的 {@link RedisConfig}：key 用 String 序列化、
 * value 用 FastJSON2 序列化，避免 JDK 序列化产生的二进制乱码。</p>
 *
 * <p>注意：Boot 自带的 {@code RedisAutoConfiguration} 也定义了名为 {@code redisTemplate}
 * 的 Bean（{@code @ConditionalOnMissingBean(name = "redisTemplate")}）。
 * 自动装配按类名排序，{@code com.crossborder.*} 排在 {@code org.springframework.*} 之前，
 * 因此本配置优先生效，Boot 的默认实现被跳过 —— 这正是 erp-common 想要的 FastJSON2 版本。
 * 若某个应用想用 Boot 默认实现，自行定义名为 {@code redisTemplate} 的 Bean 即可覆盖。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
@Import(RedisConfig.class)
public class CommonRedisAutoConfiguration {
}
