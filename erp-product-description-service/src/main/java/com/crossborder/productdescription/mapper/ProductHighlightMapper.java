package com.crossborder.productdescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.productdescription.entity.ProductHighlight;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品亮点 Mapper（表 {@code product_highlights}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这张表有 DDL、有索引，<b>但从来没人往里写</b> →
 * {@code extractHighlights()} 提炼出的亮点只活在一次 HTTP 响应里。</p>
 */
@Mapper
public interface ProductHighlightMapper extends BaseMapper<ProductHighlight> {
}
