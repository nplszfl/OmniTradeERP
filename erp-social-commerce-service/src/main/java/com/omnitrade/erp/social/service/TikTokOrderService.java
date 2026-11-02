package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnitrade.erp.social.client.TikTokApiClient;
import com.omnitrade.erp.social.dto.TikTokOrderDTO;
import com.omnitrade.erp.social.model.TikTokOrder;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.TikTokOrderRepository;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TikTok 订单同步服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokOrderService {
    
    private final TikTokOrderRepository orderRepository;
    private final TikTokShopConfigRepository shopConfigRepository;
    private final TikTokApiClient apiClient;
    private final ObjectMapper objectMapper;
    
    /**
     * 同步所有店铺的订单
     */
    public void syncAllOrders() {
        List<TikTokShopConfig> activeShops = shopConfigRepository.findByStatus(TikTokShopConfig.ConfigStatus.ACTIVE);
        for (TikTokShopConfig shop : activeShops) {
            try {
                syncOrdersByShop(shop);
            } catch (Exception e) {
                log.error("同步店铺订单失败, shopId: {}", shop.getShopId(), e);
            }
        }
    }
    
    /**
     * 根据店铺同步订单
     */
    @Transactional
    public void syncOrdersByShop(TikTokShopConfig shop) {
        log.info("开始同步TikTok订单, shopId: {}", shop.getShopId());
        
        // 检查token是否过期
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        String accessToken = shop.getAccessToken();
        String shopId = shop.getShopId();
        
        // 获取最近7天的订单
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(7);
        
        String pageToken = null;
        int pageSize = 100;
        int totalSynced = 0;
        
        do {
            TikTokApiClient.TikTokOrderListResponse response = apiClient.getOrders(
                    accessToken, shopId, 
                    String.valueOf(startTime.atZone(ZoneId.systemDefault()).toEpochSecond()),
                    String.valueOf(endTime.atZone(ZoneId.systemDefault()).toEpochSecond()),
                    pageToken, pageSize);
            
            if (response.getData() != null && response.getData().getOrders() != null) {
                for (Map<String, Object> orderData : response.getData().getOrders()) {
                    try {
                        saveOrUpdateOrder(shopId, orderData);
                        totalSynced++;
                    } catch (Exception e) {
                        log.error("保存订单失败: {}", orderData.get("order_id"), e);
                    }
                }
                pageToken = response.getData().getPageToken();
            } else {
                pageToken = null;
            }
            
        } while (pageToken != null);
        
        log.info("订单同步完成, shopId: {}, 共同步: {} 条", shopId, totalSynced);
    }
    
    /**
     * 同步单个订单详情
     */
    @Transactional
    public TikTokOrderDTO syncOrderDetail(Long shopConfigId, String orderId) {
        TikTokShopConfig shop = shopConfigRepository.findById(shopConfigId)
                .orElseThrow(() -> new RuntimeException("店铺配置不存在: " + shopConfigId));
        
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        TikTokApiClient.TikTokOrderDetailResponse response = apiClient.getOrderDetail(
                shop.getAccessToken(), shop.getShopId(), orderId);
        
        if (response.getData() != null) {
            saveOrUpdateOrder(shop.getShopId(), response.getData());
            return findByOrderId(orderId);
        }
        
        throw new RuntimeException("订单同步失败: " + orderId);
    }
    
    /**
     * 定时同步任务 - 每小时执行一次
     */
    @Scheduled(fixedRate = 3600000)
    public void scheduledSync() {
        log.info("开始定时同步TikTok订单");
        try {
            syncAllOrders();
        } catch (Exception e) {
            log.error("定时同步订单失败", e);
        }
    }
    
    // ========== CRUD 操作 ==========
    
    public List<TikTokOrderDTO> findAll() {
        return orderRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public TikTokOrderDTO findById(Long id) {
        return orderRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + id));
    }
    
    public TikTokOrderDTO findByOrderId(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderId));
    }
    
    public List<TikTokOrderDTO> findByShopId(String shopId) {
        return orderRepository.findByShopId(shopId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokOrderDTO> findByStatus(String status) {
        return orderRepository.findByStatus(TikTokOrder.OrderStatus.valueOf(status)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokOrderDTO> findByDateRange(String startTime, String endTime) {
        LocalDateTime start = LocalDateTime.parse(startTime);
        LocalDateTime end = LocalDateTime.parse(endTime);
        return orderRepository.findByCreateTimeBetween(start, end).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokOrderDTO> findByLiveStreamId(Long liveStreamId) {
        return orderRepository.findByLiveStreamId(liveStreamId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<TikTokOrderDTO> findByInfluencerId(Long influencerId) {
        return orderRepository.findByInfluencerId(influencerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 关联订单与直播
     */
    @Transactional
    public TikTokOrderDTO associateWithLiveStream(String orderId, Long liveStreamId) {
        TikTokOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderId));
        order.setLiveStreamId(liveStreamId);
        return toDTO(orderRepository.save(order));
    }
    
    /**
     * 关联订单与达人
     */
    @Transactional
    public TikTokOrderDTO associateWithInfluencer(String orderId, Long influencerId) {
        TikTokOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderId));
        order.setInfluencerId(influencerId);
        return toDTO(orderRepository.save(order));
    }
    
    /**
     * 统计店铺订单
     */
    public Map<String, Object> getShopOrderStats(String shopId) {
        long orderCount = orderRepository.countByShopId(shopId);
        BigDecimal totalSales = orderRepository.sumSalesByShopId(shopId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("orderCount", orderCount);
        stats.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
        
        return stats;
    }
    
    /**
     * 统计直播订单
     */
    public Map<String, Object> getLiveStreamOrderStats(Long liveStreamId) {
        long orderCount = orderRepository.countByLiveStreamId(liveStreamId);
        BigDecimal totalSales = orderRepository.sumSalesByLiveStreamId(liveStreamId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("orderCount", orderCount);
        stats.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
        
        return stats;
    }
    
    // ========== 私有方法 ==========
    
    private void saveOrUpdateOrder(String shopId, Map<String, Object> orderData) {
        String orderId = (String) orderData.get("order_id");
        
        TikTokOrder order = orderRepository.findByOrderId(orderId)
                .orElse(TikTokOrder.builder().build());
        
        order.setOrderId(orderId);
        order.setShopId(shopId);
        
        // 解析订单状态
        String status = (String) orderData.get("order_status");
        if (status != null) {
            order.setStatus(parseOrderStatus(status));
        }
        
        // 解析金额
        if (orderData.get("order_amount") != null) {
            order.setOrderAmount(new BigDecimal(orderData.get("order_amount").toString()));
        }
        if (orderData.get("product_amount") != null) {
            order.setProductAmount(new BigDecimal(orderData.get("product_amount").toString()));
        }
        if (orderData.get("shipping_fee") != null) {
            order.setShippingFee(new BigDecimal(orderData.get("shipping_fee").toString()));
        }
        
        // 解析时间
        if (orderData.get("create_time") != null) {
            long createTime = Long.parseLong(orderData.get("create_time").toString());
            order.setCreateTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(createTime), ZoneId.systemDefault()));
        }
        
        // 买家信息
        if (orderData.get("buyer") != null) {
            Map<String, Object> buyer = (Map<String, Object>) orderData.get("buyer");
            order.setBuyerId((String) buyer.get("buyer_id"));
            order.setBuyerUsername((String) buyer.get("username"));
        }
        
        // 物流信息
        if (orderData.get("shipping_info") != null) {
            Map<String, Object> shipping = (Map<String, Object>) orderData.get("shipping_info");
            order.setTrackingNumber((String) shipping.get("tracking_number"));
            order.setShippingCarrier((String) shipping.get("carrier"));
        }
        
        // 商品信息
        if (orderData.get("line_items") != null) {
            try {
                order.setItems(objectMapper.writeValueAsString(orderData.get("line_items")));
            } catch (Exception e) {
                log.error("序列化商品信息失败", e);
            }
        }
        
        orderRepository.save(order);
    }
    
    private TikTokOrder.OrderStatus parseOrderStatus(String status) {
        switch (status.toUpperCase()) {
            case "PENDING": return TikTokOrder.OrderStatus.PENDING;
            case "PAID": return TikTokOrder.OrderStatus.PAID;
            case "CONFIRMED": return TikTokOrder.OrderStatus.CONFIRMED;
            case "PROCESSING": return TikTokOrder.OrderStatus.PROCESSING;
            case "SHIPPED": return TikTokOrder.OrderStatus.SHIPPED;
            case "DELIVERED": return TikTokOrder.OrderStatus.DELIVERED;
            case "COMPLETED": return TikTokOrder.OrderStatus.COMPLETED;
            case "CANCELLED": return TikTokOrder.OrderStatus.CANCELLED;
            case "REFUNDED": return TikTokOrder.OrderStatus.REFUNDED;
            default: return TikTokOrder.OrderStatus.PENDING;
        }
    }
    
    private boolean isTokenExpired(TikTokShopConfig shop) {
        if (shop.getTokenExpireTime() == null) {
            return true;
        }
        // 提前1小时认为token即将过期
        return shop.getTokenExpireTime().isBefore(LocalDateTime.now().plusHours(1));
    }
    
    private void refreshToken(TikTokShopConfig shop) {
        if (shop.getRefreshToken() == null) {
            throw new BizException("Refresh Token不存在，无法刷新");
        }
        
        try {
            TikTokApiClient.TokenResponse tokenResponse = apiClient.refreshAccessToken(shop.getRefreshToken());
            shop.setAccessToken(tokenResponse.getAccessToken());
            shop.setRefreshToken(tokenResponse.getRefreshToken());
            shop.setTokenExpireTime(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
            shopConfigRepository.save(shop);
            log.info("Token刷新成功, shopId: {}", shop.getShopId());
        } catch (Exception e) {
            log.error("Token刷新失败, shopId: {}", shop.getShopId(), e);
            shop.setStatus(TikTokShopConfig.ConfigStatus.ERROR);
            shopConfigRepository.save(shop);
        }
    }
    
    private TikTokOrderDTO toDTO(TikTokOrder entity) {
        return TikTokOrderDTO.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .shopId(entity.getShopId())
                .status(entity.getStatus().name())
                .orderAmount(entity.getOrderAmount())
                .productAmount(entity.getProductAmount())
                .shippingFee(entity.getShippingFee())
                .taxAmount(entity.getTaxAmount())
                .discountAmount(entity.getDiscountAmount())
                .buyerId(entity.getBuyerId())
                .buyerUsername(entity.getBuyerUsername())
                .recipientName(entity.getRecipientName())
                .recipientPhone(entity.getRecipientPhone())
                .shippingAddress(entity.getShippingAddress())
                .country(entity.getCountry())
                .state(entity.getState())
                .city(entity.getCity())
                .postalCode(entity.getPostalCode())
                .itemQuantity(entity.getItemQuantity())
                .items(entity.getItems())
                .trackingNumber(entity.getTrackingNumber())
                .shippingCarrier(entity.getShippingCarrier())
                .shippedTime(entity.getShippedTime())
                .deliveredTime(entity.getDeliveredTime())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .payTime(entity.getPayTime())
                .liveStreamId(entity.getLiveStreamId())
                .influencerId(entity.getInfluencerId())
                .commissionAmount(entity.getCommissionAmount())
                .commissionRate(entity.getCommissionRate())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}