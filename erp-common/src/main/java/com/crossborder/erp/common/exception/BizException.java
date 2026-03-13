package com.crossborder.erp.common.exception;

import lombok.Data;

/**
 * 业务异常
 */
@Data
public class BizException extends RuntimeException {

    private Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }
}
