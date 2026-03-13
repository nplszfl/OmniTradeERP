package com.crossborder.erp.platform.schedule;

import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.entity.PlatformConfig;
import com.crossborder.erp.platform.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import;

/**
 * 订单同步调度器
 * 定时从各平台拉取新订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncSchedule {

    private final PlatformConfigService platformConfigService;
    private final Map<String, PlatformOrderSync> platformSyncMap;
    private final List<RemoteCall<OrderService>> orderServiceClients;

    /**
     * 每10分钟同步一次订单
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void syncOrders() {
        log.info("开始定时同步订单...");

        List<PlatformConfig> configs = platformConfigService.getAllConfigs();
        configs.stream()
                .filter(config -> config.getStatus() == 1) // 只同步启用的配置
                .forEach(this::syncPlatformOrders);

        log.info("订单同步完成");
    }

    /**
     * 同步单个平台的订单
     */
    private void syncPlatformOrders(PlatformConfig config) {
        try {
            String platform = config.getPlatform();
            String shopId = config.getShopId();

            PlatformOrderSync sync = platformSyncMap.get(platform.toLowerCase());
            if (sync == null) {
                log.warn("暂不支持平台: {}", platform);
                return;
            }

            // 同步最近1小时的订单
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(1);

            log.info("同步平台订单: platform={}, shopId={}, time={}", platform, shopId, startTime);

            List<Order> orders = sync.syncOrders(startTime, endTime, shopId);

            for (Order order : orders) {
                try {
                    // 查询订单商品明细
                    List<OrderItem> items = sync.syncOrderItems(order.getPlatformOrderNo(), shopId);

                    // 调用订单服务保存订单
                    saveOrderToOrderService(order, items);
                } catch (Exception e) {
                    log.error("保存订单失败: {}", order.getPlatformOrderNo(), e);
                }
            }

            log.info("平台订单同步完成: platform={}, count={}", platform, orders.size());

        } catch (Exception e) {
            log.error("同步平台订单失败: {}", config.getShopName(), e);
        }
    }

    /**
     * 保存订单到订单服务
     */
    private void saveOrderToOrderService(Order order, List<OrderItem> items) {
        // TODO: 调用订单服务API保存订单
        // 可以通过Feign调用，或者通过消息队列异步处理
        log.info("保存订单到订单服务: {}", order.getPlatformOrderNo());
    }
}
