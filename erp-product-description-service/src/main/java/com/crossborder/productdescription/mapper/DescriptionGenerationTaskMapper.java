package com.crossborder.productdescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.productdescription.entity.DescriptionGenerationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述生成任务 Mapper
 *
 * <p>📌 **这个 Mapper 是「描述生成任务真的有落库」的凭据** ——
 * 在它出现之前，表 {@code description_generation_task} 的 DDL 写得很完整
 * （含 {@code total_count/success_count/failed_count/duration_ms/error_message}），
 * 但**没有任何代码引用它** → 表永远 0 行 → 「这批生成跑没跑、成没成」无人可答（规则 18）。</p>
 */
@Mapper
public interface DescriptionGenerationTaskMapper extends BaseMapper<DescriptionGenerationTask> {
}
