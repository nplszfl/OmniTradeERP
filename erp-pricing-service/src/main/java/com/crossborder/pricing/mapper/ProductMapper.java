package com.crossborder.pricing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.pricing.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品 Mapper
 *
 * <p>⚠️ 之前 pricing-service 完全没有持久层 —— Product 实体建好了、SQL 表也建好了，
 * 但没有任何 Mapper，Service 里的数据全是内存 mock / 随机数。
 * 前端 {@code GET /api/pricing/tasks} 要拿真实建议列表，没有 Mapper 根本无从谈起。</p>
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
