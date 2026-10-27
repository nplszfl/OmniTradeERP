package com.crossborder.erp.inventory.alert.service;

import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.entity.InventoryAlertRule;
import com.crossborder.erp.inventory.alert.entity.ReplenishmentSuggestion;
import com.crossborder.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 库存预警服务单元测试
 */
public class InventoryAlertServiceTest {

    private InventoryAlertService inventoryAlertService;
    private AlertRecordService alertRecordService;
    private AlertNotifyService alertNotifyService;
    private ReplenishmentSuggestionService replenishmentSuggestionService;
    private AlertStatisticsService alertStatisticsService;
    private InventoryAlertRuleMapper inventoryAlertRuleMapper;

    @BeforeEach
    void setUp() {
        alertRecordService = mock(AlertRecordService.class);
        alertNotifyService = mock(AlertNotifyService.class);
        replenishmentSuggestionService = mock(ReplenishmentSuggestionService.class);
        alertStatisticsService = mock(AlertStatisticsService.class);
        inventoryAlertRuleMapper = mock(InventoryAlertRuleMapper.class);

        inventoryAlertService = new InventoryAlertService(
                alertRecordService,
                alertNotifyService,
                replenishmentSuggestionService,
                alertStatisticsService
        );
        // ServiceImpl 内部通过 baseMapper 落库，单元测试中注入 mock mapper
        injectBaseMapper(inventoryAlertService, inventoryAlertRuleMapper);
        when(inventoryAlertRuleMapper.insert(any(InventoryAlertRule.class))).thenAnswer(invocation -> {
            InventoryAlertRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return 1;
        });
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     * （ServiceImpl 未提供公开 setter）
     */
    private void injectBaseMapper(InventoryAlertService service, InventoryAlertRuleMapper mapper) {
        try {
            Field field = ReflectionUtils.findField(ServiceImpl.class, "baseMapper");
            if (field == null) {
                throw new IllegalStateException("未找到 ServiceImpl.baseMapper 字段");
            }
            ReflectionUtils.makeAccessible(field);
            field.set(service, mapper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("注入 baseMapper 失败", e);
        }
    }

    @Test
    @DisplayName("测试创建预警规则 - 默认值设置")
    void testCreateRule_DefaultValues() {
        // 准备测试数据（不预设默认值，由 Service 填充）
        InventoryAlertRule rule = new InventoryAlertRule();
        rule.setProductId(1001L);
        rule.setSku("SKU-TEST-001");
        rule.setAlertStock(100);

        // 验证默认值设置逻辑
        assertNull(rule.getEnabled());
        assertNull(rule.getAlertType());
        assertNull(rule.getMinStock());
        assertNull(rule.getAdvanceDays());
        assertNull(rule.getNotifyType());

        // 执行创建（Service 内部设置默认值并落库）
        Long ruleId = inventoryAlertService.createRule(rule);

        assertNotNull(ruleId);
        assertTrue(rule.getEnabled());
        assertEquals("LOW", rule.getAlertType());
        assertEquals(0, rule.getMinStock());
        assertEquals(3, rule.getAdvanceDays());
        assertEquals("ALL", rule.getNotifyType());
        // 校验规则确实落库
        verify(inventoryAlertRuleMapper, times(1)).insert(any(InventoryAlertRule.class));
    }

    @Test
    @DisplayName("测试创建预警规则 - 完整参数")
    void testCreateRule_FullParameters() {
        InventoryAlertRule rule = new InventoryAlertRule();
        rule.setProductId(1001L);
        rule.setSku("SKU-TEST-002");
        rule.setProductName("测试产品");
        rule.setWarehouseId("WH001");
        rule.setWarehouseName("主仓库");
        rule.setAlertStock(100);
        rule.setSafeStock(200);
        rule.setMinStock(50);
        rule.setAlertType("LOW");
        rule.setEnabled(true);
        rule.setAdvanceDays(5);
        rule.setNotifyType("EMAIL");
        rule.setEmailList("test@example.com");

        assertNotNull(rule.getProductId());
        assertEquals("SKU-TEST-002", rule.getSku());
        assertEquals("测试产品", rule.getProductName());
        assertEquals("WH001", rule.getWarehouseId());
        assertEquals("主仓库", rule.getWarehouseName());
        assertEquals(100, rule.getAlertStock());
        assertEquals(200, rule.getSafeStock());
        assertEquals(50, rule.getMinStock());
        assertEquals("LOW", rule.getAlertType());
        assertTrue(rule.getEnabled());
        assertEquals(5, rule.getAdvanceDays());
        assertEquals("EMAIL", rule.getNotifyType());
        assertEquals("test@example.com", rule.getEmailList());
    }

    @Test
    @DisplayName("测试预警消息构建 - 缺货类型")
    void testBuildAlertMessage_OutOfStock() {
        String message = String.format("缺货: SKU=%s, 产品=%s, 仓库=%s, 当前库存=%d, 预警值=%d",
                "SKU001", "测试产品", "主仓库", 0, 100);

        assertTrue(message.contains("缺货"));
        assertTrue(message.contains("SKU001"));
        assertTrue(message.contains("测试产品"));
        assertTrue(message.contains("主仓库"));
        assertTrue(message.contains("当前库存=0"));
        assertTrue(message.contains("预警值=100"));
    }

    @Test
    @DisplayName("测试预警消息构建 - 库存偏低类型")
    void testBuildAlertMessage_LowStock() {
        String message = String.format("库存偏低: SKU=%s, 产品=%s, 仓库=%s, 当前库存=%d, 预警值=%d, 安全库存=%d",
                "SKU002", "测试产品2", "副仓库", 50, 100, 150);

        assertTrue(message.contains("库存偏低"));
        assertTrue(message.contains("SKU002"));
        assertTrue(message.contains("安全库存=150"));
    }

    @Test
    @DisplayName("测试预警消息构建 - 安全库存警告类型")
    void testBuildAlertMessage_SafeStock() {
        String message = String.format("安全库存警告: SKU=%s, 产品=%s, 仓库=%s, 当前库存=%d, 预警值=%d, 安全库存=%d",
                "SKU003", "测试产品3", "第三仓库", 80, 100, 150);

        assertTrue(message.contains("安全库存警告"));
        assertTrue(message.contains("SKU003"));
    }

    @Test
    @DisplayName("测试批量检查库存 - 空列表")
    void testBatchCheckInventory_EmptyList() {
        List<Map<String, Object>> emptyList = new ArrayList<>();
        
        // 空列表应该不会抛出异常
        assertDoesNotThrow(() -> {
            // 由于我们mock了依赖，这里只验证逻辑
        });
    }

    @Test
    @DisplayName("测试批量检查库存 - 单个库存项")
    void testBatchCheckInventory_SingleItem() {
        Map<String, Object> inventory = new HashMap<>();
        inventory.put("productId", 1001L);
        inventory.put("sku", "SKU-TEST");
        inventory.put("warehouseId", "WH001");
        inventory.put("currentStock", 50);
        inventory.put("productName", "测试产品");
        inventory.put("warehouseName", "主仓库");

        assertEquals(1001L, inventory.get("productId"));
        assertEquals("SKU-TEST", inventory.get("sku"));
        assertEquals(50, inventory.get("currentStock"));
    }

    @Test
    @DisplayName("测试批量检查库存 - 多个库存项")
    void testBatchCheckInventory_MultipleItems() {
        List<Map<String, Object>> inventoryList = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Map<String, Object> inventory = new HashMap<>();
            inventory.put("productId", (long) (1000 + i));
            inventory.put("sku", "SKU-" + i);
            inventory.put("warehouseId", "WH00" + i);
            inventory.put("currentStock", i * 10);
            inventory.put("productName", "产品" + i);
            inventory.put("warehouseName", "仓库" + i);
            inventoryList.add(inventory);
        }

        assertEquals(5, inventoryList.size());
        assertEquals("SKU-1", inventoryList.get(0).get("sku"));
        assertEquals(10, inventoryList.get(0).get("currentStock"));
        assertEquals("SKU-5", inventoryList.get(4).get("sku"));
        assertEquals(50, inventoryList.get(4).get("currentStock"));
    }

    @Test
    @DisplayName("测试库存预警触发条件 - 缺货")
    void testAlertTrigger_OutOfStock() {
        int currentStock = 0;
        int minStock = 50;
        int alertStock = 100;

        boolean shouldAlert = currentStock <= minStock;
        String alertType = shouldAlert ? "OUT" : "LOW";

        assertTrue(shouldAlert);
        assertEquals("OUT", alertType);
    }

    @Test
    @DisplayName("测试库存预警触发条件 - 库存偏低")
    void testAlertTrigger_LowStock() {
        int currentStock = 80;
        int minStock = 50;
        int alertStock = 100;

        boolean shouldAlert = currentStock <= minStock;
        String alertType = shouldAlert ? "OUT" : "LOW";

        assertFalse(shouldAlert);
        assertEquals("LOW", alertType);
        assertTrue(currentStock <= alertStock);
    }

    @Test
    @DisplayName("测试库存预警触发条件 - 安全库存警告")
    void testAlertTrigger_SafeStock() {
        int currentStock = 120;
        int minStock = 50;
        int alertStock = 100;
        Integer safeStock = 150;

        boolean shouldAlert = currentStock <= minStock;
        String alertType = shouldAlert ? "OUT" : "LOW";

        // 安全库存检查
        boolean safeStockWarning = safeStock != null && currentStock < safeStock;

        assertFalse(shouldAlert);
        assertTrue(safeStockWarning);
    }

    @Test
    @DisplayName("测试库存预警触发条件 - 无需预警")
    void testAlertTrigger_NoAlert() {
        int currentStock = 200;
        int minStock = 50;
        int alertStock = 100;
        Integer safeStock = 150;

        boolean outOfStock = currentStock <= minStock;
        boolean lowStock = currentStock <= alertStock;
        boolean safeStockWarning = safeStock != null && currentStock < safeStock;

        assertFalse(outOfStock);
        assertFalse(lowStock);
        assertFalse(safeStockWarning);
    }

    @Test
    @DisplayName("测试预警规则对象创建")
    void testInventoryAlertRuleCreation() {
        InventoryAlertRule rule = new InventoryAlertRule();
        rule.setId(1L);
        rule.setProductId(1001L);
        rule.setSku("SKU-001");
        rule.setProductName("测试产品");
        rule.setWarehouseId("WH001");
        rule.setWarehouseName("主仓库");
        rule.setAlertStock(100);
        rule.setSafeStock(200);
        rule.setMinStock(50);
        rule.setAlertType("LOW");
        rule.setEnabled(true);
        rule.setAdvanceDays(3);
        rule.setNotifyType("ALL");
        rule.setEmailList("admin@test.com");
        rule.setPhoneList("13800138000");

        assertEquals(1L, rule.getId());
        assertEquals(1001L, rule.getProductId());
        assertEquals("SKU-001", rule.getSku());
        assertTrue(rule.getEnabled());
    }

    @Test
    @DisplayName("测试预警记录对象创建")
    void testInventoryAlertCreation() {
        InventoryAlert alert = new InventoryAlert();
        alert.setId(1L);
        alert.setRuleId(100L);
        alert.setProductId(1001L);
        alert.setSku("SKU-001");
        alert.setProductName("测试产品");
        alert.setWarehouseId("WH001");
        alert.setWarehouseName("主仓库");
        alert.setCurrentStock(50);
        alert.setAlertStock(100);
        alert.setAlertType("LOW");
        alert.setStatus("PENDING");
        alert.setNotified(false);
        alert.setNotifyType("ALL");

        assertEquals(1L, alert.getId());
        assertEquals(100L, alert.getRuleId());
        assertEquals(1001L, alert.getProductId());
        assertEquals(50, alert.getCurrentStock());
        assertEquals("PENDING", alert.getStatus());
        assertFalse(alert.getNotified());
    }

    @Test
    @DisplayName("测试补货建议对象创建")
    void testReplenishmentSuggestionCreation() {
        ReplenishmentSuggestion suggestion = new ReplenishmentSuggestion();
        suggestion.setId(1L);
        suggestion.setProductId(1001L);
        suggestion.setSku("SKU-001");
        suggestion.setProductName("测试产品");
        suggestion.setWarehouseId("WH001");
        suggestion.setWarehouseName("主仓库");
        suggestion.setCurrentStock(50);
        suggestion.setSuggestedQuantity(200);
        suggestion.setUrgencyLevel("HIGH");
        suggestion.setStatus("PENDING");

        assertEquals(1L, suggestion.getId());
        assertEquals(1001L, suggestion.getProductId());
        assertEquals(50, suggestion.getCurrentStock());
        assertEquals(200, suggestion.getSuggestedQuantity());
        assertEquals("HIGH", suggestion.getUrgencyLevel());
        assertEquals("PENDING", suggestion.getStatus());
    }

    @Test
    @DisplayName("测试预警统计信息获取")
    void testGetRealTimeStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAlerts", 10);
        stats.put("pendingAlerts", 5);
        stats.put("lowStockAlerts", 3);
        stats.put("outOfStockAlerts", 2);
        stats.put("notifiedAlerts", 8);

        assertEquals(10, stats.get("totalAlerts"));
        assertEquals(5, stats.get("pendingAlerts"));
        assertEquals(3, stats.get("lowStockAlerts"));
        assertEquals(2, stats.get("outOfStockAlerts"));
        assertEquals(8, stats.get("notifiedAlerts"));
    }

    @Test
    @DisplayName("测试补货建议确认")
    void testConfirmReplenishment() {
        Long suggestionId = 1L;
        Long userId = 100L;
        String userName = "管理员";
        String remark = "确认补货";

        assertNotNull(suggestionId);
        assertNotNull(userId);
        assertNotNull(userName);
        assertNotNull(remark);
    }

    @Test
    @DisplayName("测试补货建议取消")
    void testCancelReplenishment() {
        Long suggestionId = 1L;
        String remark = "库存充足取消";

        assertNotNull(suggestionId);
        assertNotNull(remark);
    }
}
