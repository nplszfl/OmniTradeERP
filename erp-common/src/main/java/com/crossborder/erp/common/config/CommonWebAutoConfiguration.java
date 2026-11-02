package com.crossborder.erp.common.config;

import com.crossborder.erp.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * 通用 Web 自动装配
 *
 * <p><b>为什么需要它</b>：各服务的 {@code @SpringBootApplication} 都位于自己的子包
 * （如 {@code com.crossborder.erp.order}），组件扫描范围<b>不包含</b>
 * {@code com.crossborder.erp.common}。这意味着 erp-common 里的
 * {@link GlobalExceptionHandler} 从来没有被任何服务注册过 —— 抛出的业务异常
 * 不会走统一响应体，而是落到 Spring 默认错误页（HTTP 500 +
 * {@code {timestamp,status,error,path}}），与项目统一的 {@code Result{code,message,data}}
 * 契约完全对不上，前端按 {@code code} 取值会拿到 undefined。</p>
 *
 * <p>Spring Boot 3 的自动装配机制不依赖组件扫描，而是读取
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}，
 * 因此把 {@link GlobalExceptionHandler} 放进自动配置即可让所有服务零成本生效。</p>
 *
 * <p>仅对 Servlet 应用生效（{@code @ConditionalOnWebApplication(SERVLET)}）：
 * 网关是 WebFlux 响应式应用，{@code @RestControllerAdvice} 在其中不适用。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GlobalExceptionHandler.class)
public class CommonWebAutoConfiguration {
}
