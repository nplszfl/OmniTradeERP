package com.crossborder.erp.platform.service.impl;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.service.PlatformOrderSyncService;
import com.crossborder.erp.platform.support.PlatformSyncRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 平台订单同步服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformOrderSyncServiceImpl implements PlatformOrderSyncService {

    private final PlatformSyncRegistry platformSyncRegistry;

    @Override
    public List<Order> syncOrders(String platform, String shopId, LocalDateTime startTime, LocalDateTime endTime) {
        PlatformOrderSync sync = platformSyncRegistry.require(platform);

        log.info("开始同步平台订单: platform={}, shopId={}", platform, shopId);
        return sync.syncOrders(startTime, endTime, shopId);
    }

    @Override
    public Order getOrderByNo(String platform, String platformOrderNo, String shopId) {
        PlatformOrderSync sync = platformSyncRegistry.require(platform);

        return sync.getOrderByNo(platformOrderNo, shopId);
    }

    @Override
    public boolean validateConfig(String platform, String shopId) {
        PlatformOrderSync sync = platformSyncRegistry.require(platform);

        return sync.validateConfig(shopId);
    }

    @Override
    public List<PlatformType> getSupportedPlatforms() {
        return List.of(
                PlatformType.AMAZON,
                PlatformType.EBAY,
                PlatformType.SHOPEE,
                PlatformType.LAZADA,
                PlatformType.TIKTOK
        );
    }
}
