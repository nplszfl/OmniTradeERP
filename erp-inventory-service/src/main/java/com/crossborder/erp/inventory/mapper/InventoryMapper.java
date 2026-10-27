package com.crossborder.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存记录 Mapper
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
}
