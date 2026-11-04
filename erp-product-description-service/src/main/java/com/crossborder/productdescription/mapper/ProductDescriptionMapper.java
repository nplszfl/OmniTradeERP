package com.crossborder.productdescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.productdescription.entity.ProductDescription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品描述 Mapper
 *
 * <p>📌 <b>这个 Mapper 是本模块「真的会落库」的唯一凭据</b>：
 * 在它出现之前，{@code saveDescription} 只是一行 {@code log.info} +
 * {@code return true}（接口在骗人），{@code getProductDescriptions} 返回的是写死的假数据。</p>
 *
 * <p>表 {@code product_description} 的自然唯一键
 * {@code uk_pd_product_lang_platform (product_id, language, platform)}
 * 决定了「同一商品 + 同一语言 + 同一平台」只有一行 → 保存必须走 upsert。</p>
 */
@Mapper
public interface ProductDescriptionMapper extends BaseMapper<ProductDescription> {
}
