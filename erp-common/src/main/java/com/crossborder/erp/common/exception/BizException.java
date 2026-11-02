package com.crossborder.erp.common.exception;

import lombok.Data;

/**
 * 业务异常
 *
 * <p>⚠️ 默认错误码是 <b>400</b> 而不是 500：业务异常表达的是「请求本身不合法」
 * （库存不足、仓库不存在、密码错误……），属于客户端错误。
 * 若默认给 500，{@code GlobalExceptionHandler} 会把它映射成 HTTP 500，
 * 监控里全是「服务端故障」告警，实际上服务本身没有任何问题。
 * 真正的服务端故障走兜底的 {@code Exception} 分支，那里才返回 500。</p>
 *
 * <p>需要 404 / 403 / 409 等语义时，用带 code 的构造器显式指定。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 业务异常的默认错误码：客户端错误
     */
    public static final int DEFAULT_CODE = 400;

    private Integer code;

    public BizException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = DEFAULT_CODE;
    }
}
