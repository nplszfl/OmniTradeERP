package com.crossborder.pricing.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采纳定价建议的请求体
 *
 * <p>前端 erp-web/src/api/ai.ts 传的是 {@code request.post('/pricing/apply/${sku}', { price })}，
 * 所以这里用 JSON body 接，而不是 query 参数。</p>
 */
@Data
public class ApplyPriceRequest {

    /** 最终成交价 */
    private BigDecimal price;
}
