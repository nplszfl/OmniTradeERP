package com.crossborder.erp.platform.impl;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shopee订单同步实现
 * 使用Shopee Open Platform API
 */
@Slf4j
@Component
public class ShopeeOrderSync implements PlatformOrderSync {

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SHOPEE;
    }

    @Override
    public List<Order> syncOrders(LocalDateTime startTime, LocalDateTime endTime, String shopId) {
        log.info("开始同步Shopee订单, 店铺: {}, 时间范围: {} - {}", shopId, startTime, endTime);

        // TODO: 调用Shopee Open API获取订单列表
        // 接口：https://open.shopee.com/documents/v2/v2.order-api.order.get_order_list?module=order-service

        List<Order> orders = new ArrayList<>();
        log.info("Shopee订单同步完成, 共获取 {} 条订单", orders.size());
        return orders;
    }

    @Override
    public Order getOrderByNo(String platformOrderNo, String shopId) {
        log.info("获取Shopee订单详情, 订单号: {}", platformOrderNo);

        // TODO: 调用Shopee API获取订单详情

        return null;
    }

    @Override
    public List<OrderItem> syncOrderItems(String platformOrderNo, String shopId) {
        log.info("同步Shopee订单商品明细, 订单号: {}", platformOrderNo);

        // TODO: 调用Shopee API获取订单商品明细

        return new ArrayList<>();
    }

    @Override
    public boolean validateConfig(String shopId) {
        log.info("验证Shopee API配置, 店铺: {}", shopId);

        // TODO: 调用Shopee API验证配置有效性

        return true;
    }
}
