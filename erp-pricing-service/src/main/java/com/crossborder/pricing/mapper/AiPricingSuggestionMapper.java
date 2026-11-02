package com.crossborder.pricing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.pricing.entity.AiPricingSuggestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 定价建议 Mapper
 */
@Mapper
public interface AiPricingSuggestionMapper extends BaseMapper<AiPricingSuggestion> {
}
