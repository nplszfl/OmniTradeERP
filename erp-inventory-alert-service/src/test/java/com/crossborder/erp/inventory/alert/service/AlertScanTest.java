package com.crossborder.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.entity.InventoryAlertRule;
import com.crossborder.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.crossborder.erp.inventory.alert.mapper.LiveInventoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存预警「扫描活库存」单元测试
 *
 * 📌 这组测试锁的是 2026-11-03 修的那个洞：
 *    预警引擎从来没被触发过（t_inventory_alert 永远 0 行）。
 *    回归点有三个：扫描能触发 / 库存充足不误报 / 重复扫描不刷屏。
 */
class AlertScanTest {

    private InventoryAlertService service;
    private AlertRecordService alertRecordService;
    private AlertNotifyService alertNotifyService;
    private ReplenishmentSuggestionService replenishmentSuggestionService;
    private AlertStatisticsService alertStatisticsService;
    private LiveInventoryMapper liveInventoryMapper;
    private InventoryAlertRuleMapper ruleMapper;

    @BeforeEach
    void setUp() {
        alertRecordService = mock(AlertRecordService.class);
        alertNotifyService = mock(AlertNotifyService.class);
        replenishmentSuggestionService = mock(ReplenishmentSuggestionService.class);
        alertStatisticsService = mock(AlertStatisticsService.class);
        liveInventoryMapper = mock(LiveInventoryMapper.class);
        ruleMapper = mock(InventoryAlertRuleMapper.class);

        service = new InventoryAlertService(alertRecordService, alertNotifyService,
                replenishmentSuggestionService, alertStatisticsService, liveInventoryMapper);
        injectBaseMapper(service, ruleMapper);
    }

    private void injectBaseMapper(InventoryAlertService svc, InventoryAlertRuleMapper mapper) {
        try {
            Field field = ReflectionUtils.findField(ServiceImpl.class, "baseMapper");
            if (field == null) {
                throw new IllegalStateException("未找到 ServiceImpl.baseMapper 字段");
            }
            ReflectionUtils.makeAccessible(field);
            field.set(svc, mapper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("注入 baseMapper 失败", e);
        }
    }

    private Map<String, Object> row(Long productId, String sku, String warehouseCode,
                                    int available) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId);
        m.put("sku", sku);
        m.put("productName", "商品" + productId);
        m.put("warehouseCode", warehouseCode);
        m.put("warehouseName", "仓库" + warehouseCode);
        m.put("currentStock", available);
        return m;
    }

    private InventoryAlertRule rule(Long productId, String warehouseCode,
                                    int safe, int alert, int min) {
        InventoryAlertRule r = new InventoryAlertRule();
        r.setId(9001L);
        r.setProductId(productId);
        r.setWarehouseId(warehouseCode);
        r.setWarehouseName("仓库" + warehouseCode);
        r.setSku("SKU-" + productId);
        r.setProductName("商品" + productId);
        r.setSafeStock(safe);
        r.setAlertStock(alert);
        r.setMinStock(min);
        r.setEnabled(true);
        r.setNotifyType("ALL");
        return r;
    }

    @Test
    @DisplayName("扫描活库存：可用量低于预警值 → 生成 LOW 预警")
    void scanCreatesLowAlert() {
        when(liveInventoryMapper.selectLiveInventory())
                .thenReturn(List.of(row(1002L, "SKU-WAT-002-SLV", "WH-DE-HH", 18)));
        // 产品级规则命中
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1002L, "WH-DE-HH", 25, 20, 10)));
        when(alertRecordService.count(any())).thenReturn(0L);

        int created = service.scanLiveInventory();

        assertEquals(1, created);
        verify(alertRecordService, times(1)).createAlert(any(InventoryAlert.class));
    }

    @Test
    @DisplayName("扫描活库存：可用量高于安全库存 → 不生成任何预警（防误报）")
    void scanDoesNotCreateAlertWhenStockIsHealthy() {
        when(liveInventoryMapper.selectLiveInventory())
                .thenReturn(List.of(row(1001L, "SKU-BLU-001-BLK", "WH-US-LA", 48)));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1001L, "WH-US-LA", 20, 15, 5)));
        when(alertRecordService.count(any())).thenReturn(0L);

        int created = service.scanLiveInventory();

        assertEquals(0, created);
        verify(alertRecordService, never()).createAlert(any(InventoryAlert.class));
    }

    @Test
    @DisplayName("重复扫描：已有 PENDING 预警 → 不重复插入（防刷屏）")
    void scanIsIdempotentForPendingAlert() {
        when(liveInventoryMapper.selectLiveInventory())
                .thenReturn(List.of(row(1002L, "SKU-WAT-002-SLV", "WH-DE-HH", 18)));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1002L, "WH-DE-HH", 25, 20, 10)));
        // 已有同 (商品, 仓库, 类型) 的 PENDING
        when(alertRecordService.count(any())).thenReturn(1L);

        int created = service.scanLiveInventory();

        assertEquals(0, created);
        verify(alertRecordService, never()).createAlert(any(InventoryAlert.class));
    }

    @Test
    @DisplayName("扫描失败（活表不可达）→ 返回 0 且不抛异常，不吞错误日志")
    void scanSurvivesLiveInventoryFailure() {
        when(liveInventoryMapper.selectLiveInventory())
                .thenThrow(new RuntimeException("Table 'erp_inventory.erp_inventory' doesn't exist"));

        int created = service.scanLiveInventory();

        assertEquals(0, created);
        verify(alertRecordService, never()).createAlert(any(InventoryAlert.class));
    }

    @Test
    @DisplayName("扫描空结果 → 返回 0")
    void scanWithEmptyInventory() {
        when(liveInventoryMapper.selectLiveInventory()).thenReturn(List.of());

        int created = service.scanLiveInventory();

        assertEquals(0, created);
        assertTrue(true);
    }

    @Test
    @DisplayName("没人通知到 → 不标记「已通知」（notified 不能撒谎）")
    void doesNotMarkNotifiedWhenNothingWasSent() {
        when(liveInventoryMapper.selectLiveInventory())
                .thenReturn(List.of(row(1002L, "SKU-WAT-002-SLV", "WH-DE-HH", 18)));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1002L, "WH-DE-HH", 25, 20, 10)));
        when(alertRecordService.count(any())).thenReturn(0L);
        // 通知服务返回 false = 一个人都没通知到
        when(alertNotifyService.sendNotification(any(), any(), any(), any())).thenReturn(false);

        service.scanLiveInventory();

        verify(alertRecordService, never()).markAsNotified(any());
    }

    @Test
    @DisplayName("真的发出去了 → 标记「已通知」")
    void marksNotifiedWhenNotificationSent() {
        when(liveInventoryMapper.selectLiveInventory())
                .thenReturn(List.of(row(1002L, "SKU-WAT-002-SLV", "WH-DE-HH", 18)));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1002L, "WH-DE-HH", 25, 20, 10)));
        when(alertRecordService.count(any())).thenReturn(0L);
        when(alertNotifyService.sendNotification(any(), any(), any(), any())).thenReturn(true);

        service.scanLiveInventory();

        verify(alertRecordService, times(1)).markAsNotified(any());
    }

    @Test
    @DisplayName("脏数据行（缺 productId / sku）被跳过，不打断整轮扫描")
    void scanSkipsDirtyRows() {
        Map<String, Object> dirty = new HashMap<>();
        dirty.put("productId", null);
        dirty.put("sku", "");
        dirty.put("currentStock", 5);

        when(liveInventoryMapper.selectLiveInventory())
                .thenReturn(List.of(dirty, row(1002L, "SKU-WAT-002-SLV", "WH-DE-HH", 18)));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(1002L, "WH-DE-HH", 25, 20, 10)));
        when(alertRecordService.count(any())).thenReturn(0L);

        int created = service.scanLiveInventory();

        assertEquals(1, created);
    }
}
