package com.crossborder.erp.user.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.crossborder.erp.user.entity.Role;
import com.crossborder.erp.user.entity.UserRole;
import com.crossborder.erp.user.mapper.RoleMapper;
import com.crossborder.erp.user.mapper.UserRoleMapper;
import com.crossborder.erp.user.util.JwtUtil;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 用户 JWT 认证过滤器
 *
 * <p>🔴 <b>修复前的严重缺陷</b>：{@code SecurityConfig} 写的是
 * {@code anyRequest().permitAll()} → <b>整个账户系统一个请求都不用带 token</b>。
 * 而 {@code POST /api/user/{id}/reset-password} 还接受
 * {@code defaultPassword} 请求参数 → <b>任何人都能把任意用户的密码改成自己知道的值</b>
 * （再配合 {@code POST /api/v1/auth/register} 是 permitAll → 注册一个账号就能改管理员密码）=
 * <b>未认证即可完成账号接管</b>。见 {@code docs/SECURITY.md}。</p>
 *
 * <p>本过滤器把 {@link JwtUtil} 签发的 token 转成 SecurityContext 里的 Authentication，
 * 并**按数据库里的真实角色**（{@code t_user_role} → {@code t_role.role_code}）给出权限，
 * 这样「角色」不是写在 token 里的快照 —— <b>管理员在界面上改掉某个人的角色，立刻生效</b>，
 * 不需要等对方重新登录。</p>
 *
 * <p>📌 范式抄自本仓已有的 {@code erp-tenant-service} 的
 * {@code TenantJwtAuthenticationFilter}（同一套「过滤器 + SecurityContext」做法），
 * 📌 **过滤器必须放在本服务的包下**（{@code com.crossborder.erp.user.*}）：
 * 各服务的 {@code @SpringBootApplication} 只扫自己的子包 → 放 erp-common 里不会被加载（规则 21）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /** 请求属性名：当前登录用户 id */
    public static final String ATTR_USER_ID = "currentUserId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId != null && jwtUtil.validateToken(token)) {
                List<SimpleGrantedAuthority> authorities = loadAuthorities(userId);
                String username = jwtUtil.getUsernameFromToken(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(ATTR_USER_ID, userId);
            } else {
                // token 非法/过期 —— 不放行，交给后续授权判定（401）
                log.debug("用户 JWT 校验失败");
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 读出该用户的角色 → 权限。
     *
     * 📌 **查库而不是信 token**：token 里只有 userId/username，没有角色；
     * 角色存在 {@code t_user_role} → {@code t_role} 里，实时查才不会「改了角色还得重新登录」。
     * 📌 查不到任何角色 → 返回**空列表**（不是默认 ADMIN）：拿不到角色就等于没有权限。
     */
    private List<SimpleGrantedAuthority> loadAuthorities(Long userId) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        try {
            List<UserRole> relations = userRoleMapper.selectList(
                    Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
            if (relations.isEmpty()) {
                return authorities;
            }
            List<Long> roleIds = relations.stream().map(UserRole::getRoleId).toList();
            for (Role role : roleMapper.selectBatchIds(roleIds)) {
                if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
                    continue;
                }
                // 禁用状态（status=0）的角色不生效
                if (role.getStatus() != null && role.getStatus() == 0) {
                    continue;
                }
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode().toUpperCase()));
            }
        } catch (Exception e) {
            // 📌 **查角色失败 = 拿不到权限**，绝不能退化成「放行」
            log.error("加载用户角色失败, userId={}", userId, e);
            return List.of();
        }
        return authorities;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
