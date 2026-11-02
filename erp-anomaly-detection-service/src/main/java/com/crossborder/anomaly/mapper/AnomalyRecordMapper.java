package com.crossborder.anomaly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.anomaly.entity.AnomalyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常检测记录 Mapper
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Mapper
public interface AnomalyRecordMapper extends BaseMapper<AnomalyRecord> {
}
