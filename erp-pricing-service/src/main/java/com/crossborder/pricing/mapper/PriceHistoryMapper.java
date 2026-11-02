package com.crossborder.pricing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.pricing.entity.PriceHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 价格历史 Mapper
 */
@Mapper
public interface PriceHistoryMapper extends BaseMapper<PriceHistory> {
}
