package com.crossborder.erp.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * 账户系统的安全配置
 *
 * <p>🔴 <b>修复前的严重缺陷</b>：规则是 {@code anyRequest().permitAll()} →
 * <b>整个账户系统一个请求都不用带 token</b>。配合
 * {@code POST /api/user/{id}/reset-password?defaultPassword=xxx}
 * （<b>密码由调用方指定</b>）和 permitAll 的 {@code POST /api/v1/auth/register}
 * → <b>未认证即可接管任意账号</b>。见 {@code docs/SECURITY.md}。</p>
 *
 * <p>✅ 现在：除「登录/注册/刷新」和 actuator 外，<b>一律要认证</b>；
 * 管理类操作（改人、建角色、删人、重置密码）额外要求 {@code ROLE_ADMIN}
 * （由 {@code UserJwtAuthenticationFilter} 按库里的真实角色给出）。</p>
 *
 * <p>⚠️ **必须配 {@link AuthenticationEntryPoint}**：Spring Security 没配它时，
 * 未认证请求返回的是 <b>403 + 空 body</b>（没有 httpBasic/formLogin 触发 401），
 * 前端拿不到任何错误信息，只看到一个空 body —— 排障时完全看不出发生了什么。
 * 抄 {@code erp-tenant-service} 的教训。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 让 @PreAuthorize("hasRole('ADMIN')") 生效
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserJwtAuthenticationFilter userJwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 📌 只有「登录 / 注册 / 刷新 token」和健康检查可以匿名访问
                // 📌 **路径必须是 `/auth/**`，不是 `/api/v1/auth/**`**（实测踩过，差一个前缀就全线登不上）：
                //    · 网关路由 `Path=/api/auth/**` + `StripPrefix=1` → 服务侧真实路径是 **`/auth/login`**
                //    · `/api/v1/auth/**` 是 **erp-tenant-service** 的路径（那条路由 StripPrefix=0），
                //      **两个服务不是一回事** → 照抄租户那边的写法 → 白名单**一个都匹配不上**
                //      → 登录被拦成 401 → **没人能登录 = 全线不可用**。
                //    📌 判据：白名单路径 = **controller 的 @RequestMapping**，不是网关路径（规则 5 / 76）
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // 📌 其余一律要认证 —— **写操作不是例外**
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
            .addFilterBefore(userJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 未认证 → **401 + 有意义的 JSON body**（不是 403 + 空 body）。
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of(
                    "code", 401,
                    "message", "未登录或登录已过期，请重新登录"
            )));
        };
    }
}
