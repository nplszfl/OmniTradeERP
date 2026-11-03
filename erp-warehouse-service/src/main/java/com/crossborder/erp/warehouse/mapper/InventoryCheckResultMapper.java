package com.crossborder.erp.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.warehouse.entity.InventoryCheckResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存盘点结果 Mapper
 *
 * <p>📌 以前**没有这个 Mapper** —— 盘点明细表建了，但服务层根本没读过/写过它，
 * 所以「盘点」这个流程从头到尾是空的。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Mapper
public interface InventoryCheckResultMapper extends BaseMapper<InventoryCheckResult> {
}
