package com.crossborder.tenant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 租户 JWT 认证过滤器
 *
 * <p>🔴 <b>修复前的严重缺陷</b>：本服务原先只有一个 {@code SecurityFilterChain}，
 * 规则是 {@code anyRequest().authenticated()}，但**全项目没有任何地方往
 * SecurityContext 里放 Authentication**（既没有认证 provider，也没有 JWT 过滤器），
 * TenantFilter 只负责填 TenantContext，不是 Security 的认证入口。
 * → 结果：<b>除 /api/v1/auth/** 和 /actuator/** 外的所有接口一律 403 空 body</b>，
 * 管理端的 {@code GET /api/tenant/list} 也进不去 → 前端租户页面整页空白。</p>
 *
 * <p>⚠️ 另一个坑：Spring Security 没配 AuthenticationEntryPoint 时，
 * 未认证请求返回的是 <b>403</b> 而不是 401（因为没有 httpBasic/formLogin 触发 401），
 * 前端拿不到任何错误信息，只看到一个空 body。</p>
 *
 * <p>现在本过滤器把 tenant 自己的 JWT（{@link JwtTokenProvider} 签发，
 * 带 tenantId/userId claim）转换成 SecurityContext 里的 Authentication，
 * 让 /api/v1/tenant/**、/api/v1/platforms/** 这类「租户自助」接口能真正鉴权。</p>
 *
 * <p>📌 注意：管理端 {@code /api/tenant/**} 走 permitAll —— 与项目其它 ERP 服务
 * 保持一致（erp-user-service 用的是 anyRequest().permitAll()，网关也不鉴权）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String userId = jwtTokenProvider.getUserIdFromToken(token);
                String tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                String username = jwtTokenProvider.getUsernameFromToken(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, List.of(
                                new SimpleGrantedAuthority("ROLE_TENANT")));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 认证信息里带上租户/用户 id，下游不必再解一次 JWT
                request.setAttribute("tenantId", tenantId);
                request.setAttribute("userId", userId);
            } catch (RuntimeException e) {
                // token 非法/过期 —— 不放行，交给后续的授权判定（401）
                log.debug("租户 JWT 校验失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
