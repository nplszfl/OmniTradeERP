package com.crossborder.erp.common.exception;

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

    // ============================================================
    // 带原始 cause 的重载（2026-11-03 新增）
    //
    // 📌 为什么要加：平台适配器里把底层异常（IOException / JSONException / 签名错误）
    //    包成业务异常时，**必须保留 cause**，否则堆栈断在包装处，
    //    排查时看不到真正的失败原因（只剩一句「调用失败」）。
    // ============================================================

    public static PlatformApiException amazon(String errorCode, String errorMessage, Throwable cause) {
        return new PlatformApiException("Amazon", errorCode, errorMessage, cause);
    }

    public static PlatformApiException ebay(String errorCode, String errorMessage, Throwable cause) {
        return new PlatformApiException("eBay", errorCode, errorMessage, cause);
    }

    public static PlatformApiException shopee(String errorCode, String errorMessage, Throwable cause) {
        return new PlatformApiException("Shopee", errorCode, errorMessage, cause);
    }

    public static PlatformApiException lazada(String errorCode, String errorMessage, Throwable cause) {
        return new PlatformApiException("Lazada", errorCode, errorMessage, cause);
    }

    public static PlatformApiException tiktok(String errorCode, String errorMessage, Throwable cause) {
        return new PlatformApiException("TikTok", errorCode, errorMessage, cause);
    }
}
