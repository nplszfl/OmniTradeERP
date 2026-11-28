package com.crossborder.productdescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.productdescription.entity.AbTestResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * A/B 测试结果 Mapper（表 {@code ab_test_result}）
 *
 * <p>📌 <b>2026-11-28 新增</b>：这张表有 DDL、有索引，<b>但从来没人往里写</b> →
 * {@code generateABTestVersions()} 生成的多版本只活在一次 HTTP 响应里。</p>
 */
@Mapper
public interface AbTestResultMapper extends BaseMapper<AbTestResult> {
}
