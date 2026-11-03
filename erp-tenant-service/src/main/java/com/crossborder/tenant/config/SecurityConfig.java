package com.crossborder.tenant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 租户服务安全配置
 *
 * <p>🔴 <b>修复前的严重缺陷</b>：规则是 {@code anyRequest().authenticated()}，
 * 但项目里<b>没有任何认证 provider / JWT 过滤器</b>往 SecurityContext 放 Authentication
 * （TenantFilter 只填 TenantContext，不是 Security 的认证入口）。
 * → 结果：除 /api/v1/auth/** 和 /actuator/** 外的<b>所有接口一律 403 空 body</b>，
 * 连管理端的 {@code GET /api/tenant/list} 都进不去 → 前端租户页面整页空白。</p>
 *
 * <p>现在：</p>
 * <ul>
 *   <li>{@code /api/tenant/**} —— 管理端租户管理，permitAll
 *       （与项目其它 ERP 服务一致：erp-user-service 是 anyRequest().permitAll()，
 *        网关也不鉴权；多租户 SaaS 自助那套是另一个认证域）</li>
 *   <li>{@code /api/v1/auth/**} —— 租户自己的登录/注册，permitAll</li>
 *   <li>{@code /api/v1/tenant/**}、{@code /api/v1/platforms/**} —— 租户自助，
 *       需要 tenant JWT（由 {@link TenantJwtAuthenticationFilter} 校验）</li>
 * </ul>
 *
 * <p>📌 显式配 {@link AuthenticationEntryPoint}：Spring Security 默认在未认证时返回
 * <b>403</b>（因为没配 httpBasic/formLogin），前端拿到的是<b>空 body</b>，
 * 完全不知道发生了什么。改成 401 + JSON 报文后前端能提示「请先登录」。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantJwtAuthenticationFilter tenantJwtAuthenticationFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                // 租户自助的登录/注册
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // 管理端租户管理（列表/看板/账单/用量/套餐）—— 与其它 ERP 服务一致
                .requestMatchers("/api/tenant/**").permitAll()
                // 租户自助接口：必须带 tenant JWT
                .requestMatchers("/api/v1/tenant/**").authenticated()
                .requestMatchers("/api/v1/platforms/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(tenantJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 未认证 → 401 + JSON（默认是 403 空 body，前端拿不到任何信息）
     */
    private AuthenticationEntryPoint authEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("code", 401);
                put("message", "未登录或登录已过期，请重新登录");
                put("data", null);
            }}));
        };
    }

    /**
     * 已认证但权限不足 → 403 + JSON
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, deniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("code", 403);
                put("message", "没有访问权限");
                put("data", null);
            }}));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
