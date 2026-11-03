package com.crossborder.erp.common.exception;

import com.crossborder.erp.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>响应体统一为 {@link Result}（{@code code/message/data}），
 * 同时<b>保留正确的 HTTP 状态码</b>（400/500），这样前端 axios 的 error 拦截器
 * 依然能捕获错误并弹出提示——如果只返回 200 + code:500，
 * 前端会走 success 分支，错误被静默吞掉。</p>
 *
 * <p>⚠️ 本类通过 {@code CommonWebAutoConfiguration} 自动装配到所有 Servlet 服务
 * （各服务的组件扫描范围不包含 {@code com.crossborder.erp.common}，
 * 直接标注 {@code @Component} 是无效的）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        int status = resolveStatus(e.getCode());
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 参数校验异常（@RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, message));
    }

    /**
     * 参数绑定异常（@RequestParam）
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<?>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, message));
    }

    /**
     * 缺少必填请求参数（{@code @RequestParam} 必填项没传）
     *
     * <p>⚠️ 之前没有单独处理，会落到兜底的 {@code Exception} 分支 →
     * HTTP 500「系统繁忙，请稍后重试」，把一个纯客户端错误报成服务端故障，
     * 监控上会看到一堆假的 500 告警。</p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("参数缺失: {}", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, message));
    }

    /**
     * 请求体解析失败（JSON 格式错误 / 字段类型不匹配）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, "请求体格式错误，无法解析为合法 JSON"));
    }

    /**
     * 方法级参数校验失败（{@code @Validated} + {@code @NotBlank} 等，作用于 {@code @RequestParam}/{@code @PathVariable}）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, message));
    }

    /**
     * 请求方法不支持（GET 打到只接受 POST 的接口）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        String message = "不支持的请求方法: " + e.getMethod();
        log.warn("请求方法不支持: {}", e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Result.fail(405, message));
    }

    /**
     * 第三方平台接口异常（Amazon / eBay / Shopee / Lazada / TikTok）
     *
     * <p>映射为 502 Bad Gateway：请求本身没问题，是下游平台挂了或返回了错误。
     * 不单独处理的话会落到兜底分支，真实的上游错误信息被「系统繁忙」吞掉，
     * 排查时完全看不到平台返回了什么。</p>
     */
    @ExceptionHandler(PlatformApiException.class)
    public ResponseEntity<Result<?>> handlePlatformApiException(PlatformApiException e) {
        log.error("平台接口异常: platform={}, errorCode={}", e.getPlatform(), e.getErrorCode(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Result.fail(502, "平台接口调用失败: " + e.getMessage()));
    }

    /**
     * 参数类型不匹配（{@code ?id=abc} 传给了 {@code Long id}）
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Result<?>> handleTypeMismatch(TypeMismatchException e) {
        String name = e.getPropertyName() == null ? "参数" : e.getPropertyName();
        String message = "参数类型不正确: " + name;
        log.warn("参数类型不匹配: {}", name);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(400, message));
    }

    /**
     * 不支持的 Content-Type
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的 Content-Type: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.fail(415, "不支持的 Content-Type: " + e.getContentType()));
    }

    /**
     * 无法满足 Accept 头的响应类型
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<?>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.warn("无法满足的 Accept: {}", e.getSupportedMediaTypes());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(Result.fail(406, "无法提供可接受的响应类型"));
    }

    /**
     * 非法参数（{@code IllegalArgumentException}）
     *
     * <p>项目里还有大量直接 {@code throw new IllegalArgumentException("...")} 的写法，
     * 语义上就是「请求参数不合法」→ 400 + 原始提示。</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数非法: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, e.getMessage() == null ? "参数不合法" : e.getMessage()));
    }

    /**
     * 未知异常
     *
     * <p>默认返回 500 + 「系统繁忙」，但如果异常是一个<b>带业务提示的运行时异常</b>
     * （即各服务里 {@code throw new RuntimeException("账户不存在")} 这类写法），
     * 会把原始提示原样返回 —— 否则真正的业务原因被一句「系统繁忙」盖掉，
     * 前后端都拿不到有用信息。</p>
     *
     * <p>NPE / ClassCastException 这类「代码有 bug」的异常不在白名单内，
     * 仍然只回「系统繁忙」，避免把内部细节暴露给前端。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统异常", e);
        if (isBusinessRuntimeException(e)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail(500, e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "系统繁忙，请稍后重试"));
    }

    /**
     * 是否是「带业务提示的运行时异常」
     *
     * <p>白名单：常见的、由业务代码主动抛出的运行时异常；
     * 黑名单：空指针 / 类型转换等真正的代码缺陷，不把内部细节返回给前端。</p>
     */
    private boolean isBusinessRuntimeException(Exception e) {
        if (e instanceof NullPointerException
                || e instanceof ClassCastException
                || e instanceof IllegalAccessException
                || e instanceof IndexOutOfBoundsException
                || e instanceof ArithmeticException
                || e instanceof java.io.IOException) {
            return false;
        }
        if (e instanceof BizException || e instanceof IllegalArgumentException) {
            return false; // 上面已有专门的分支处理
        }
        // 🔴 **数据完整性异常**（MyBatis 包装的 SQL 失败）绝不能原样返回
        //    —— 它的 message 里含**完整的 SQL 语句、列名、表名**，
        //    例如："Field 'total_amount' doesn't have a default value ...
        //             SQL: INSERT INTO t_order_item ..."
        //    → 直接透给前端 = **数据库结构泄露**（还能把内部表名/字段名告诉攻击者）。
        //    📌 实测：漏传 totalAmount 的下单请求，前端收到了整段 SQL 栈。
        if (e instanceof org.springframework.dao.DataIntegrityViolationException
                || e instanceof org.springframework.dao.DataAccessException
                || e instanceof java.sql.SQLException) {
            return false;
        }
        return e instanceof RuntimeException
                && e.getMessage() != null
                && !e.getMessage().isBlank();
    }

    /**
     * 业务异常码 → HTTP 状态码
     *
     * <p>业务码本身落在 400~599 区间时直接作为 HTTP 状态码使用；
     * 其余自定义业务码（如 1001、20001）统一映射为 400，
     * 表达「请求本身不合法」而不是「服务器内部错误」。</p>
     */
    private int resolveStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        if (code >= 400 && code < 600) {
            return code;
        }
        return HttpStatus.BAD_REQUEST.value();
    }
}
