package com.crossborder.productdescription.service;

import com.crossborder.productdescription.entity.ProductDescription;

import java.util.List;

/**
 * 产品描述<b>落库</b>服务
 *
 * <p>📌 <b>为什么单独拆一个 Service</b>：
 * {@code ProductDescriptionServiceImpl} 是「AI 生成 + 编排」层，
 * 落库规则（upsert、version_number 递增、必填校验）应该有一处唯一实现，
 * 否则「保存」这种有唯一键约束的写操作会被多个调用方各写一遍、各错一遍。</p>
 */
public interface ProductDescriptionRecordService {

    /**
     * 按自然唯一键 {@code (product_id, language, platform)} upsert
     *
     * <p>存在 → 更新 content / short_title / version_number（版本号 +1）；
     * 不存在 → 插入，version_number = 1。</p>
     *
     * @param record 待保存的描述（productId / content 必填）
     * @return 落库后的记录（含最终 version_number）
     * @throws com.crossborder.erp.common.exception.BizException 参数非法或落库失败
     */
    ProductDescription upsert(ProductDescription record);

    /**
     * 按商品ID查出全部描述（按 language / platform 排序）
     *
     * @param productId 商品ID
     * @return 描述记录列表；<b>没有就是空列表</b>，绝不返回假数据
     */
    List<ProductDescription> listByProductId(Long productId);

    /**
     * 按唯一键查单条
     *
     * @return 记录；不存在返回 null
     */
    ProductDescription findByProductLangPlatform(Long productId, String language, String platform);
}
