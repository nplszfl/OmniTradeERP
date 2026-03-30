package com.crossborder.erp.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.warehouse.entity.PackingMaterial;
import org.apache.ibatis.annotations.Mapper;

/**
 * 包装材料Mapper
 */
@Mapper
public interface PackingMaterialMapper extends BaseMapper<PackingMaterial> {
}

/**
 * 波次拣货Mapper
 */
@Mapper
interface WavePickingMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<com.crossborder.erp.warehouse.entity.WavePicking> {
}