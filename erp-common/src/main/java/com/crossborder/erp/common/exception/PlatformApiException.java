package com.cro ssborder.erp.common.exception;

import lombok.Getter;

/**
 * 平台API异常
 */
@Getter
public class PlatformApiException extends RuntimeException {

    private final String platform;
    private final String errorCode;
    private final String errorMessage;

    public PlatformApiException(String platform, String errorCode, String errorMessage) {
        super(String.format("[%s] %s: %s", platform, errorCode, errorMessage));
        this.platform = platform;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public PlatformApiException(String platform, String errorCode, String errorMessage, Throwable cause) {
        super(String.format("[%s] %s: %s", platform, errorCode, errorMessage), cause);
        this.platform = platform;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建Amazon异常
     */
    public static PlatformApiException amazon(String errorCode, String errorMessage) {
        return new PlatformApiException("Amazon", errorCode, errorMessage);
    }

    /**
     * 创建eBay异常
     */
    public static PlatformApiException ebay(String errorCode, String errorMessage) {
        return new PlatformApiException("eBay", errorCode, errorMessage);
    }

    /**
     * 创建Shopee异常
     */
    public static PlatformApiException shopee(String errorCode, String errorMessage) {
        return new PlatformApiException("Shopee", errorCode, errorMessage);
    }

    /**
     * 创建Lazada异常
     */
    public static PlatformApiException lazada(String errorCode, String errorMessage) {
        return new PlatformApiException("Lazada", errorCode, errorMessage);
    }

    /**
     * 创建TikTok异常
     */
    public static PlatformApiException tiktok(String errorCode, String errorMessage) {
        return new PlatformApiException("TikTok", errorCode, errorMessage);
    }
}
