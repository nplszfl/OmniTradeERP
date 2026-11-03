package com.crossborder.erp.inventory.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存预警定时扫描
 *
 * 📌 这套预警引擎原先**只有手动入口**（POST /inventory/alert/check，
 *    还要求调用方自带 currentStock），没有任何定时触发。
 *    现在补上定时任务，预警才能真正跑起来。
 *
 * 📌 开关：erp.alert.scan.enabled=false 可关掉（测试环境/只想手工触发的场景）。
 *    默认开启，间隔 5 分钟（erp.alert.scan.interval-ms）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.alert.scan", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertScanScheduler {

    private final InventoryAlertService inventoryAlertService;

    /**
     * 定时扫描活库存并生成预警
     */
    @Scheduled(initialDelay = 60_000, fixedDelayString = "${erp.alert.scan.interval-ms:300000}")
    public void scan() {
        try {
            int created = inventoryAlertService.scanLiveInventory();
            if (created > 0) {
                log.info("⏰ 定时扫描新增 {} 条库存预警", created);
            }
        } catch (Exception e) {
            // 定时任务绝不能因为异常被静默吞掉（Spring 会打日志，但这里再明确记一次）
            log.error("库存预警定时扫描失败", e);
        }
    }
}
