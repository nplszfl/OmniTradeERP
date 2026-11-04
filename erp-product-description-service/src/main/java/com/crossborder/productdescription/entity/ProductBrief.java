package com.crossborder.productdescription.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品主数据<b>只读投影</b>（{@code erp_product.t_product}）
 *
 * <p>📌 <b>为什么需要它</b>：前端契约的保存接口收的是 <b>sku</b>，
 * 而 {@code product_description.product_id} 存的是<b>商品ID</b> ——
 * 中间必须做一次 sku → id 的解析。</p>
 *
 * <p>📌 <b>为什么放在 erp_product 库而不是本服务库</b>：
 * 商品主数据归 {@code erp-product-service} 管，
 * 描述服务擅自 copy 一份 = <b>两份商品数据必然漂移</b>
 * （商品改名/下架/删除，本服务这边还留着旧值 → 描述挂在一个已下架的商品上）。
 * → 这里只<b>跨库只读</b>，不建本地副本。
 * 跨库 JOIN 的先例：{@code erp-analytics-service} 就是这么查
 * {@code erp_product.t_product} / {@code erp_order.t_order} 的。</p>
 *
 * <p>📌 <b>为什么不是 Feign 调 erp-product-service</b>：
 * 保存描述是<b>写路径</b>，让写路径依赖另一个服务的可用性，
 * 等于把商品服务的抖动直接传导成「用户保存失败」。
 * 同一 MySQL 实例内跨库读只是一条 SQL，没有额外网络跳数。</p>
 *
 * <p>📌 <b>只读</b>：本投影只用于查询，不做 insert/update。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductBrief {

    /** 商品ID（雪花 ID，19 位 —— 传给前端必须用 String 才不丢精度） */
    private Long id;

    /** 商品名称 */
    private String productName;
}
