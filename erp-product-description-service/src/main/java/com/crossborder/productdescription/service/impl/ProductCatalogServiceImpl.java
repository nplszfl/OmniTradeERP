package com.crossborder.productdescription.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.entity.ProductBrief;
import com.crossborder.productdescription.mapper.ProductBriefMapper;
import com.crossborder.productdescription.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 商品主数据查询实现（<b>跨库只读</b> {@code erp_product.*}）
 *
 * <p>📌 <b>只读</b>：商品主数据归 {@code erp-product-service} 管，
 * 这里不写任何一行，避免两个服务各写一份商品数据。</p>
 *
 * <p>📌 <b>SKU 两种口径都要认</b>（实测踩出来的坑）：
 * 先按<b>变体 SKU</b>（{@code t_product_sku.sku_code}，如 {@code SKU-BLU-001-BLK}）查，
 * 查不到再按<b>商品级 SKU</b>（{@code t_product.internal_sku}，如 {@code SKU-BLU-001}）查。
 * 只认一种 → 另一种 SKU 一律解析失败 → 「明明有这个商品却存不进去」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {

    private final ProductBriefMapper productBriefMapper;

    @Override
    public Optional<ProductBrief> findBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        String key = sku.trim();
        try {
            // 1) 变体 SKU（前端 AI 描述页展示的就是这一种）
            ProductBrief brief = productBriefMapper.findByVariantSku(key);
            if (brief != null) {
                return Optional.of(brief);
            }
            // 2) 商品级 SKU（商品列表页展示的那一种）
            brief = productBriefMapper.findByProductSku(key);
            return Optional.ofNullable(brief);
        } catch (BadSqlGrammarException e) {
            // 📌 商品主表不可用（最常见：当前实例没有 erp_product 库）
            //    → 明确告诉调用方「SKU 解析不了」，而不是甩一个 500 + SQL 原文出去。
            //    ⚠️ 绝不 fallback 成「随便找个 productId 存进去」—— 那等于凭空造数据。
            throw new BizException(
                    "商品主数据表 erp_product.t_product 在当前数据库不可用，无法按 SKU 解析商品："
                            + e.getMostSpecificCause().getMessage(), e);
        }
    }

    @Override
    public Optional<ProductBrief> findById(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(productBriefMapper.findById(productId));
        } catch (BadSqlGrammarException e) {
            throw new BizException(
                    "商品主数据表 erp_product.t_product 在当前数据库不可用，无法按商品ID查询商品："
                            + e.getMostSpecificCause().getMessage(), e);
        }
    }
}
