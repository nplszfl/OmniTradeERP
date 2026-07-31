package com.crossborder.erp.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.order.dto.OrderExportResponse;
import com.crossborder.erp.order.dto.OrderQueryRequest;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.order.mapper.OrderMapper;
import com.crossborder.erp.order.mapper.OrderItemMapper;
import com.crossborder.erp.order.service.OrderService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务实现（带监控）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderItemMapper orderItemMapper;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public long count(LambdaQueryWrapper<Order> wrapper) {
        return super.count(wrapper);
    }

    @Override
    @Timed(value = "order.create", description = "创建订单", histogram = true)
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Order order, List<OrderItem> orderItems) {
        log.info("创建订单, 平台订单号: {}", order.getPlatformOrderNo());

        // 生成内部订单号
        String internalOrderNo = generateInternalOrderNo(order.getPlatform());
        order.setInternalOrderNo(internalOrderNo);

        // 保存订单
        save(order);

        // 保存订单商品明细
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        log.info("订单创建成功, 内部订单号: {}, 商品数量: {}", internalOrderNo, orderItems.size());
        return order.getId();
    }

    @Override
    @Timed(value = "order.getById", description = "根据ID查询订单")
    public Order getOrderById(Long orderId) {
        return getById(orderId);
    }

    @Override
    @Timed(value = "order.getByPlatformOrderNo", description = "根据平台订单号查询")
    public Order getOrderByPlatformOrderNo(String platform, String platformOrderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getPlatform, platform)
                .eq(Order::getPlatformOrderNo, platformOrderNo);
        return getOne(wrapper);
    }

    @Override
    @Timed(value = "order.updateStatus", description = "更新订单状态")
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(Long orderId, String status) {
        log.info("更新订单状态, orderId: {}, status: {}", orderId, status);

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(status);
        updateById(order);
    }

    @Override
    @Timed(value = "order.updateShippingInfo", description = "更新物流信息")
    @Transactional(rollbackFor = Exception.class)
    public void updateShippingInfo(Long orderId, String trackingNumber, String logisticsCompany) {
        log.info("更新订单物流信息, orderId: {}, trackingNumber: {}", orderId, trackingNumber);

        Order order = new Order();
        order.setId(orderId);
        order.setTrackingNumber(trackingNumber);
        order.setLogisticsCompany(logisticsCompany);
        order.setShippingTime(LocalDateTime.now());
        order.setStatus("shipped");
        updateById(order);
    }

    @Override
    @Timed(value = "order.pageOrders", description = "分页查询订单")
    public IPage<Order> pageOrders(Page<Order> page, String platform, String status,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

        if (platform != null) {
            wrapper.eq(Order::getPlatform, platform);
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (startTime != null) {
            wrapper.ge(Order::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(Order::getCreateTime, endTime);
        }

        wrapper.orderByDesc(Order::getCreateTime);

        return page(page, wrapper);
    }

    @Override
    @Timed(value = "order.getOrderItems", description = "查询订单商品")
    public List<OrderItem> getOrderItems(Long orderId) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        return orderItemMapper.selectList(wrapper);
    }

    // ========== 高级查询功能实现 ==========

    @Override
    @Timed(value = "order.advancedQuery", description = "高级分页查询订单")
    public IPage<Order> advancedQuery(Page<Order> page, OrderQueryRequest request) {
        LambdaQueryWrapper<Order> wrapper = buildQueryWrapper(request);
        wrapper.orderByDesc(Order::getCreateTime);
        return page(page, wrapper);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<Order> buildQueryWrapper(OrderQueryRequest request) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

        // 平台筛选
        if (StringUtils.hasText(request.getPlatform())) {
            wrapper.eq(Order::getPlatform, request.getPlatform());
        }
        // 订单状态筛选
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Order::getStatus, request.getStatus());
        }
        // 支付状态筛选
        if (StringUtils.hasText(request.getPaymentStatus())) {
            wrapper.eq(Order::getPaymentStatus, request.getPaymentStatus());
        }
        // 日期范围筛选
        if (request.getStartTime() != null) {
            wrapper.ge(Order::getCreateTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(Order::getCreateTime, request.getEndTime());
        }
        // 金额范围筛选
        if (request.getMinAmount() != null) {
            wrapper.ge(Order::getOrderAmount, request.getMinAmount());
        }
        if (request.getMaxAmount() != null) {
            wrapper.le(Order::getOrderAmount, request.getMaxAmount());
        }
        // 买家国家筛选
        if (StringUtils.hasText(request.getBuyerCountry())) {
            wrapper.eq(Order::getRecipientCountry, request.getBuyerCountry());
        }
        // 买家姓名模糊匹配
        if (StringUtils.hasText(request.getBuyerName())) {
            wrapper.like(Order::getBuyerName, request.getBuyerName());
        }
        // 买家邮箱模糊匹配
        if (StringUtils.hasText(request.getBuyerEmail())) {
            wrapper.like(Order::getBuyerEmail, request.getBuyerEmail());
        }
        // 物流单号模糊匹配
        if (StringUtils.hasText(request.getTrackingNumber())) {
            wrapper.like(Order::getTrackingNumber, request.getTrackingNumber());
        }
        // 内部订单号模糊匹配
        if (StringUtils.hasText(request.getInternalOrderNo())) {
            wrapper.like(Order::getInternalOrderNo, request.getInternalOrderNo());
        }
        // 平台订单号模糊匹配
        if (StringUtils.hasText(request.getPlatformOrderNo())) {
            wrapper.like(Order::getPlatformOrderNo, request.getPlatformOrderNo());
        }

        // 商品SKU筛选（需要关联查询OrderItem）
        if (StringUtils.hasText(request.getProductSku())) {
            List<Long> orderIdsWithSku = getOrderIdsBySku(request.getProductSku());
            if (orderIdsWithSku.isEmpty()) {
                // 如果没有匹配的商品，返回空结果
                wrapper.eq(Order::getId, -1L);
            } else {
                wrapper.in(Order::getId, orderIdsWithSku);
            }
        }

        return wrapper;
    }

    /**
     * 根据SKU查询订单ID列表
     */
    private List<Long> getOrderIdsBySku(String sku) {
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.like(OrderItem::getProductSku, sku);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);
        return items.stream().map(OrderItem::getOrderId).distinct().collect(Collectors.toList());
    }

    // ========== 批量操作功能实现 ==========

    @Override
    @Timed(value = "order.batchUpdateStatus", description = "批量更新订单状态")
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateStatus(List<Long> orderIds, String status) {
        log.info("批量更新订单状态, 订单数量: {}, 目标状态: {}", orderIds.size(), status);

        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }

        // 批量更新
        List<Order> orders = new ArrayList<>();
        for (Long orderId : orderIds) {
            Order order = new Order();
            order.setId(orderId);
            order.setStatus(status);
            order.setUpdateTime(LocalDateTime.now());
            orders.add(order);
        }

        boolean success = updateBatchById(orders);
        log.info("批量更新订单状态完成, 成功: {}", success);
        return success ? orderIds.size() : 0;
    }

    @Override
    @Timed(value = "order.batchMarkShipped", description = "批量标记发货")
    @Transactional(rollbackFor = Exception.class)
    public int batchMarkShipped(List<Long> orderIds, String trackingNumber, String logisticsCompany) {
        log.info("批量标记发货, 订单数量: {}, 物流单号: {}", orderIds.size(), trackingNumber);

        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = new ArrayList<>();
        for (Long orderId : orderIds) {
            Order order = new Order();
            order.setId(orderId);
            order.setTrackingNumber(trackingNumber);
            order.setLogisticsCompany(logisticsCompany);
            order.setShippingTime(now);
            order.setStatus("shipped");
            order.setUpdateTime(now);
            orders.add(order);
        }

        boolean success = updateBatchById(orders);
        log.info("批量标记发货完成, 成功: {}", success);
        return success ? orderIds.size() : 0;
    }

    @Override
    @Timed(value = "order.exportOrders", description = "批量导出订单")
    public OrderExportResponse exportOrders(List<Long> orderIds, OrderQueryRequest request) {
        log.info("导出订单, 指定ID数量: {}, 查询条件: {}", 
                orderIds != null ? orderIds.size() : 0, request);

        OrderExportResponse response = new OrderExportResponse();
        
        // 构建查询条件
        LambdaQueryWrapper<Order> wrapper;
        if (orderIds != null && !orderIds.isEmpty()) {
            // 如果指定了ID列表，只导出这些订单
            wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Order::getId, orderIds);
        } else if (request != null) {
            // 使用高级查询条件
            wrapper = buildQueryWrapper(request);
        } else {
            wrapper = new LambdaQueryWrapper<>();
        }

        wrapper.orderByDesc(Order::getCreateTime);
        
        // 查询所有符合条件的订单（不分页，导出全部）
        List<Order> orders = list(wrapper);

        // 转换为导出格式
        List<OrderExportResponse.OrderExportData> exportDataList = new ArrayList<>();
        for (Order order : orders) {
            OrderExportResponse.OrderExportData data = new OrderExportResponse.OrderExportData();
            data.setPlatform(order.getPlatform());
            data.setPlatformOrderNo(order.getPlatformOrderNo());
            data.setInternalOrderNo(order.getInternalOrderNo());
            data.setBuyerName(order.getBuyerName());
            data.setBuyerEmail(order.getBuyerEmail());
            data.setBuyerCountry(order.getRecipientCountry());
            data.setOrderAmount(order.getOrderAmount() != null ? order.getOrderAmount().toString() : "0.00");
            data.setCurrencyCode(order.getCurrencyCode());
            data.setStatus(order.getStatus());
            data.setPaymentStatus(order.getPaymentStatus());
            data.setTrackingNumber(order.getTrackingNumber());
            data.setLogisticsCompany(order.getLogisticsCompany());
            data.setRecipientCountry(order.getRecipientCountry());
            data.setRecipientState(order.getRecipientState());
            data.setRecipientCity(order.getRecipientCity());
            data.setRecipientAddress(order.getRecipientAddress());
            data.setCreateTime(order.getCreateTime() != null ? order.getCreateTime().format(DATETIME_FORMATTER) : "");
            data.setPaymentTime(order.getPaymentTime() != null ? order.getPaymentTime().format(DATETIME_FORMATTER) : "");
            data.setShippingTime(order.getShippingTime() != null ? order.getShippingTime().format(DATETIME_FORMATTER) : "");
            exportDataList.add(data);
        }

        // 生成文件名
        String fileName = "orders_export_" + System.currentTimeMillis() + ".csv";
        response.setFileName(fileName);
        response.setData(exportDataList);
        response.setTotal(orders.size());

        log.info("订单导出完成, 总数: {}", orders.size());
        return response;
    }

    // ========== 订单统计功能实现 ==========

    @Override
    @Timed(value = "order.countByCountry", description = "按国家统计订单数量")
    public Map<String, Long> countByCountry(OrderQueryRequest request) {
        LambdaQueryWrapper<Order> wrapper = buildQueryWrapper(request);
        List<Order> orders = list(wrapper);

        return orders.stream()
                .filter(o -> o.getRecipientCountry() != null)
                .collect(Collectors.groupingBy(Order::getRecipientCountry, Collectors.counting()));
    }

    @Override
    @Timed(value = "order.sumAmountByPlatform", description = "按平台统计订单金额")
    public Map<String, BigDecimal> sumAmountByPlatform(OrderQueryRequest request) {
        LambdaQueryWrapper<Order> wrapper = buildQueryWrapper(request);
        List<Order> orders = list(wrapper);

        return orders.stream()
                .filter(o -> o.getPlatform() != null && o.getOrderAmount() != null)
                .collect(Collectors.groupingBy(Order::getPlatform, 
                        Collectors.reducing(BigDecimal.ZERO, Order::getOrderAmount, BigDecimal::add)));
    }

    /**
     * 生成内部订单号
     * 格式：平台代码 + 时间戳 + 后缀
     */
    private String generateInternalOrderNo(String platform) {
        long timestamp = System.currentTimeMillis();
        String suffix = String.format("%04d", (int)(Math.random() * 10000));
        return platform.toUpperCase() + timestamp + suffix;
    }
}