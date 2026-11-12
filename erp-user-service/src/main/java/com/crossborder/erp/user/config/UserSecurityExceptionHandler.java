package com.crossborder.erp.user.config;

import com.crossborder.erp.common.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账户系统的安全异常 → 正确的 HTTP 状态码
 *
 * <p>🔴 <b>为什么需要它</b>：{@code erp-common} 的 {@code GlobalExceptionHandler} 里有个
 * <b>兜底的 {@code @ExceptionHandler(Exception.class)}</b> → 返回 500「系统繁忙」。
 * 而 Spring Security 的 {@code AccessDeniedException}（{@code @PreAuthorize} 拒绝）
 * 是在 DispatcherServlet <b>里面</b>抛的 → <b>先被 ControllerAdvice 接住</b>，
 * 根本走不到上游的 {@code ExceptionTranslationFilter}
 * → 「没有权限」被报成 <b>500 服务端故障</b>。</p>
 *
 * <p>后果：前端把「你没有权限」显示成「系统繁忙」；
 * 监控里一片 500（全是假的「服务端故障」）；
 * 真实的服务端故障反而被淹没 —— 告警疲劳（同族：规则 118「业务错误 → 500 = 假的故障」）。</p>
 *
 * <p>📌 **必须带 {@code @Order(HIGHEST_PRECEDENCE)}**：多个 {@code @ControllerAdvice} 并存时，
 * Spring 按 order 依次找**第一个**能处理该异常的方法 → 不加 order 就会被 erp-common
 * 那个兜底分支抢走（它匹配 {@code Exception.class}，什么都能匹配）。
 * 📌 **不能把这两个异常处理器加到 erp-common**：其余 23 个模块**没有** spring-security 依赖，
 * 加了会让它们编译不过（而且一旦给它们引上 security starter，
 * Boot 的默认安全配置会要求所有接口都带密码 → 全线 401 —— 绝不能这么做）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-12
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserSecurityExceptionHandler {

    /** 没有权限 → **403**（不是 500） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(403, "没有权限执行该操作"));
    }

    /** 认证失败 → **401** */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<?>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, "未登录或登录已过期，请重新登录"));
    }
}
