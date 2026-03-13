package com.crossborder.erp.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign全局配置
 */
@Configuration
public class FeignConfig {

    /**
     * Feign请求拦截器
     * 用于添加通用请求头（如认证信息）
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // TODO: 添加认证token等通用请求头
                // template.header("Authorization", "Bearer " + getToken());

                // 添加追踪ID
                String traceId = cn.hutool.core.lang.UUID.fastUUID().toString(true);
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}
