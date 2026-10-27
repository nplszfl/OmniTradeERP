package com.crossborder.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.inventory.entity.InventoryCheck;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存盘点单 Mapper
 */
@Mapper
public interface InventoryCheckMapper extends BaseMapper<InventoryCheck> {
}
