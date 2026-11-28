package com.crossborder.productdescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.productdescription.entity.SeoOptimization;
import org.apache.ibatis.annotations.Mapper;

/**
 * SEO 优化结果 Mapper（表 {@code seo_optimization}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这张表有 DDL、有索引，<b>但从来没人往里写</b> →
 * {@code optimizeSEO()} 算出来的分数只活在一次 HTTP 响应里。</p>
 */
@Mapper
public interface SeoOptimizationMapper extends BaseMapper<SeoOptimization> {
}
