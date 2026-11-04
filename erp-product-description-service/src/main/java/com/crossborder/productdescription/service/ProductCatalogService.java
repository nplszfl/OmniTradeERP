package com.crossborder.productdescription.service;

import com.crossborder.productdescription.entity.ProductBrief;

import java.util.Optional;

/**
 * 商品主数据查询（sku ↔ productId 的解析）
 *
 * <p>📌 前端契约用 sku，描述表用 product_id —— 中间这一步必须有唯一实现，
 * 否则「sku 解析」会在各个接口里各写一遍、各错一遍。</p>
 */
public interface ProductCatalogService {

    /**
     * 按 SKU 查商品
     *
     * @param sku 内部 SKU（前后空格会被忽略）
     * @return 商品；<b>查不到返回 empty</b>（不抛异常 —— 由调用方决定是报错还是跳过）
     */
    Optional<ProductBrief> findBySku(String sku);

    /**
     * 按商品ID查商品
     *
     * @param productId 商品ID
     * @return 商品；查不到返回 empty
     */
    Optional<ProductBrief> findById(Long productId);
}
