package com.crossborder.productdescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.productdescription.entity.DescriptionTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 描述模板 Mapper（表 {@code description_template}）
 *
 * <p>📌 <b>这个 Mapper 的存在意义</b>：在它出现之前，
 * {@code ProductDescriptionServiceImpl} 的模板列表来自
 * <b>一个内存 Map + 写死的元数据</b>，而库里那张
 * {@code description_template} 表<b>从来没人读过</b> →
 * 运营改模板对生成结果零影响（模板正文压根没进 prompt）。
 */
@Mapper
public interface DescriptionTemplateMapper extends BaseMapper<DescriptionTemplate> {
}
