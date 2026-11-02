package com.crossborder.erp.common.config;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * Feign 自动装配
 *
 * <p>注册 erp-common 的 {@link FeignConfig}（全局 {@link RequestInterceptor}），
 * 让所有 Feign 调用自动带上 {@code X-Trace-Id} 链路追踪头。</p>
 *
 * <p>之前 {@code FeignConfig} 标注了 {@code @Configuration} 却从未被扫描到
 * （各服务的组件扫描范围不包含 {@code com.crossborder.erp.common}），属于「写了没生效」。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@Import(FeignConfig.class)
public class CommonFeignAutoConfiguration {
}
