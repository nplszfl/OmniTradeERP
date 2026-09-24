package com.crossborder.erp.platform.api;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台订单同步接口
 * 所有平台API适配器都需要实现此接口
 */
public interface PlatformOrderSync {

    /**
     * 获取平台类型
     * @return 平台类型枚举
     */
    PlatformType getPlatformType();

    /**
     * 同步指定时间范围内的订单
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param shopId 店铺ID
     * @return 订单列表
     */
    List<Order> syncOrders(LocalDateTime startTime, LocalDateTime endTime, String shopId);

    /**
     * 获取单个订单详情（包含商品明细）
     * @param platformOrderNo 平台订单号
     * @param shopId 店铺ID
     * @return 订单及其商品明细
     */
    Order getOrderByNo(String platformOrderNo, String shopId);

    /**
     * 同步订单商品明细
     * @param platformOrderNo 平台订单号
     * @param shopId 店铺ID
     * @return 商品明细列表
     */
    List<OrderItem> syncOrderItems(String platformOrderNo, String shopId);

    /**
     * 验证API配置是否有效
     * @param shopId 店铺ID
     * @return true有效，false无效
     */
    boolean validateConfig(String shopId);

    /**
     * 刷新访问令牌（OAuth类平台需要）
     * @param shopId 店铺ID
     * @return true刷新成功
     */
    default boolean refreshToken(String shopId) {
        // 默认实现：大部分平台不需要刷新token
        return true;
    }
}
