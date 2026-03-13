package com.crossborder.erp.common.constant;

/**
 * 平台类型枚举
 * 覆盖主流跨境电商平台
 */
public enum PlatformType {

    AMAZON("amazon", "亚马逊", 1),
    EBAY("ebay", "eBay", 2),
    SHOPEE("shopee", "Shopee", 3),
    LAZADA("lazada", "Lazada", 4),
    TIKTOK("tiktok", "TikTok Shop", 5),
    TEMU("temu", "Temu", 6),
    ALIEXPRESS("aliexpress", "速卖通", 7),
    SHEIN("shein", "SHEIN", 8),
    SHOPIFY("shopify", "Shopify独立站", 9),
    WOOCOMMERCE("woocommerce", "WooCommerce独立店", 10);

    private final String code;
    private final String name;
    private final Integer sort;

    PlatformType(String code, String name, Integer sort) {
        this.code = code;
        this.name = name;
        this.sort = sort;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getSort() {
        return sort;
    }

    public static PlatformType fromCode(String code) {
        for (PlatformType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown platform code: " + code);
    }
}
