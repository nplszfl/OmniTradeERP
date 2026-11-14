package com.crossborder.erp.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
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
 * 本配置用 {@code @AutoConfigureBefore(RedisAutoConfiguration.class)} 显式排在 Boot 之前，
 * 于是 erp-common 的 FastJSON2 版本先注册，Boot 的默认实现被
 * {@code @ConditionalOnMissingBean} 跳过 —— 这正是 erp-common 想要的序列化行为。</p>
 *
 * <p>📌 **不要相信「按类名字母序 com.crossborder.* 排在 org.springframework.* 前面」**：
 * 那是自动装配排序的**默认规则**，但 {@code @AutoConfigureBefore/After} 会重排整个图。
 * 实测（2026-11-14）erp-gateway 上 Boot 的 {@code RedisAutoConfiguration} 反而先跑，
 * 导致 {@code redisTemplate} 重复定义 → {@code BeanDefinitionOverrideException}
 * → **网关完全起不来**（而 erp-user-service 等 servlet 服务正常 → 现象极具迷惑性）。
 * 排序必须用注解**显式声明**，不能靠字母序碰运气。</p>
 *
 * <p>📌 若某个应用想用 Boot 默认实现，自行定义名为 {@code redisTemplate} 的 Bean 即可覆盖。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@AutoConfiguration
@AutoConfigureBefore(RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@Import(RedisConfig.class)
public class CommonRedisAutoConfiguration {
}
