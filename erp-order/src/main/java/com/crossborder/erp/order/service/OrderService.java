package com.crossborder.erp.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单
     * @param order 订单信息
     * @param orderItems 订单商品列表
     * @return 订单ID
     */
    Long createOrder(Order order, List<OrderItem> orderItems);

    /**
     * 根据ID查询订单
     * @param orderId 订单ID
     * @return 订单信息
     */
    Order getOrderById(Long orderId);

    /**
     * 根据平台订单号查询
     * @param platform 平台类型
     * @param platformOrderNo 平台订单号
     * @return 订单信息
     */
    Order getOrderByPlatformOrderNo(String platform, String platformOrderNo);

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 新状态
     */
    void updateOrderStatus(Long orderId, String status);

    /**
     * 更新物流信息
     * @param orderId 订单ID
     * @param trackingNumber 物流单号
     * @param logisticsCompany 物流公司
     */
    void updateShippingInfo(Long orderId, String trackingNumber, String logisticsCompany);

    /**
     * 分页查询订单
     * @param page 分页参数
     * @param platform 平台类型（可选）
     * @param status 订单状态（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 订单列表
     */
    IPage<Order> pageOrders(Page<Order> page, String platform, String status,
                           java.time.LocalDateTime startTime,
                           java.time.LocalDateTime endTime);

    /**
     * 查询订单商品列表
     * @param orderId 订单ID
     * @return 商品列表
     */
    List<OrderItem> getOrderItems(Long orderId);
}
