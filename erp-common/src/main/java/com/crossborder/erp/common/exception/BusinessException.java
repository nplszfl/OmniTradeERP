package com.crossborder.erp.common.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * <p>与 erp-common 中的 {@link BizException} 保持一致的语义，
 * 继承自 BizException 以便被 GlobalExceptionHandler 统一捕获处理。</p>
 */
public class BusinessException extends BizException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(Integer code, String message) {
        super(code, message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
