package com.crossborder.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.inventory.entity.InventoryLock;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存锁定 Mapper
 */
@Mapper
public interface InventoryLockMapper extends BaseMapper<InventoryLock> {
}
