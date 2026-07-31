package com.crossborder.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crossborder.erp.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单商品Mapper
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}