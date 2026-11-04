package com.crossborder.productdescription.mapper;

import com.crossborder.productdescription.entity.ProductBrief;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品主数据（{@code erp_product.t_product} / {@code erp_product.t_product_sku}）只读 Mapper
 *
 * <p>📌 只用于 sku → productId 的解析，<b>不提供任何写操作</b> ——
 * 商品主数据归 {@code erp-product-service} 管，
 * 描述服务擅自改商品数据会造成两个服务各写一份。</p>
 *
 * <p>📌 <b>跨库只读</b>（{@code erp_product.*}）——
 * 商品主数据在 {@code erp_product} 库，不在本服务的 {@code erp_product_description_db}。
 * compose 里所有 schema 在<b>同一个 MySQL 实例</b>上 → 跨库 JOIN 可行
 * （先例：{@code erp-analytics-service}）。</p>
 *
 * <p>📌 <b>SKU 有两种口径，必须都支持</b>（这是实测踩出来的坑）：
 * <ul>
 *   <li><b>变体 SKU</b>：{@code t_product_sku.sku_code}，形如 {@code SKU-BLU-001-BLK}
 *       —— 前端「AI 商品描述」页展示的 SKU 就是这一种（带颜色/规格后缀）</li>
 *   <li><b>商品级 SKU</b>：{@code t_product.internal_sku}，形如 {@code SKU-BLU-001}
 *       —— 商品列表页展示的是这一种</li>
 * </ul>
 * 只认一种 → 另一种 SKU 一律解析失败 → 「明明有这个商品却存不进去」。</p>
 */
@Mapper
public interface ProductBriefMapper {

    /**
     * 按<b>变体 SKU</b>（{@code t_product_sku.sku_code}）解析商品。
     *
     * <p>走 {@code t_product_sku JOIN t_product} —— 直接拿到商品ID和商品名。</p>
     */
    @Select("SELECT p.id AS id, p.product_name AS productName "
            + "FROM erp_product.t_product_sku s "
            + "JOIN erp_product.t_product p ON p.id = s.product_id "
            + "WHERE s.sku_code = #{sku} AND s.deleted = 0 AND p.deleted = 0 "
            + "LIMIT 1")
    ProductBrief findByVariantSku(@Param("sku") String sku);

    /**
     * 按<b>商品级 SKU</b>（{@code t_product.internal_sku}）解析商品。
     */
    @Select("SELECT id AS id, product_name AS productName "
            + "FROM erp_product.t_product "
            + "WHERE internal_sku = #{sku} AND deleted = 0 "
            + "LIMIT 1")
    ProductBrief findByProductSku(@Param("sku") String sku);

    /**
     * 按商品ID取商品（只读，用于回填商品名）。
     */
    @Select("SELECT id AS id, product_name AS productName "
            + "FROM erp_product.t_product "
            + "WHERE id = #{id} AND deleted = 0 "
            + "LIMIT 1")
    ProductBrief findById(@Param("id") Long id);
}
