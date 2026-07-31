package com.crossborder.erp.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.order.dto.OrderExportResponse;
import com.crossborder.erp.order.dto.OrderQueryRequest;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;

import java.util.List;
import java.util.Map;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 统计订单数量
     * @param wrapper 查询条件
     * @return 订单数量
     */
    long count(LambdaQueryWrapper<Order> wrapper);

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

    // ========== 高级查询功能 ==========

    /**
     * 高级分页查询订单
     * @param page 分页参数
     * @param request 查询条件
     * @return 订单分页结果
     */
    IPage<Order> advancedQuery(Page<Order> page, OrderQueryRequest request);

    // ========== 批量操作功能 ==========

    /**
     * 批量更新订单状态
     * @param orderIds 订单ID列表
     * @param status 目标状态
     * @return 成功更新的数量
     */
    int batchUpdateStatus(List<Long> orderIds, String status);

    /**
     * 批量标记发货
     * @param orderIds 订单ID列表
     * @param trackingNumber 物流单号
     * @param logisticsCompany 物流公司
     * @return 成功更新的数量
     */
    int batchMarkShipped(List<Long> orderIds, String trackingNumber, String logisticsCompany);

    /**
     * 批量导出订单
     * @param orderIds 订单ID列表（为空则导出全部）
     * @param request 查询条件
     * @return 导出数据
     */
    OrderExportResponse exportOrders(List<Long> orderIds, OrderQueryRequest request);

    // ========== 订单统计功能 ==========

    /**
     * 按国家统计订单数量
     * @param request 查询条件
     * @return 国家订单统计Map
     */
    Map<String, Long> countByCountry(OrderQueryRequest request);

    /**
     * 按平台统计订单金额
     * @param request 查询条件
     * @return 平台订单金额统计Map
     */
    Map<String, java.math.BigDecimal> sumAmountByPlatform(OrderQueryRequest request);
}
