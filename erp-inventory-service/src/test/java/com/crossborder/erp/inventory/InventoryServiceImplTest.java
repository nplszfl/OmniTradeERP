package com.crossborder.erp.inventory;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.inventory.entity.Inventory;
import com.crossborder.erp.inventory.entity.InventoryAlert;
import com.crossborder.erp.inventory.entity.InventoryCheck;
import com.crossborder.erp.inventory.entity.InventoryCheckDetail;
import com.crossborder.erp.inventory.entity.InventoryLog;
import com.crossborder.erp.inventory.enums.AlertStatus;
import com.crossborder.erp.inventory.enums.AlertType;
import com.crossborder.erp.inventory.enums.CheckStatus;
import com.crossborder.erp.inventory.enums.InventoryStatus;
import com.crossborder.erp.inventory.enums.LockType;
import com.crossborder.erp.inventory.enums.OperationType;
import com.crossborder.erp.inventory.service.InventoryService;
import com.crossborder.erp.inventory.vo.BatchOperationResult;
import com.crossborder.erp.inventory.vo.InventoryAlertStats;
import com.crossborder.erp.inventory.vo.InventoryBatchOperation;
import com.crossborder.erp.inventory.vo.InventoryQuery;
import com.crossborder.erp.inventory.vo.InventoryStatistics;
import com.crossborder.erp.inventory.vo.SafetyStockUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存服务集成单元测试
 *
 * <p>基于 H2 内存库 + MyBatis-Plus，真实执行出入库、锁定、调拨、预警与盘点逻辑。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public class InventoryServiceImplTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM erp_inventory_log");
        jdbcTemplate.update("DELETE FROM erp_inventory_alert");
        jdbcTemplate.update("DELETE FROM erp_inventory_lock");
        jdbcTemplate.update("DELETE FROM erp_inventory_check_detail");
        jdbcTemplate.update("DELETE FROM erp_inventory_check");
        jdbcTemplate.update("DELETE FROM erp_inventory");
        jdbcTemplate.update("ALTER TABLE erp_inventory ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.update("ALTER TABLE erp_inventory_log ALTER COLUMN id RESTART WITH 1");
    }

    // ==================== 基础出入库 ====================

    @Test
    @DisplayName("入库：新建库存记录并写入流水")
    public void testIncreaseInventoryCreatesRecord() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");

        Inventory inventory = inventoryService.getInventory(1L, 10L);
        assertNotNull(inventory);
        assertEquals(100, inventory.getQuantity());
        assertEquals(100, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getLockedQuantity());
        assertEquals(InventoryStatus.NORMAL, inventory.getStatus());
        assertEquals("SKU-1", inventory.getSkuCode());
    }

    @Test
    @DisplayName("入库：数量非法被拒绝")
    public void testIncreaseInventoryRejectsNonPositive() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.increaseInventory(1L, 10L, 0, "非法"));
        assertTrue(ex.getMessage().contains("入库数量必须大于0"));
    }

    @Test
    @DisplayName("出库：可用库存与总数量同步减少")
    public void testDecreaseInventory() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        inventoryService.decreaseInventory(1L, 10L, 30, "销售出库");

        Inventory inventory = inventoryService.getInventory(1L, 10L);
        assertEquals(70, inventory.getQuantity());
        assertEquals(70, inventory.getAvailableQuantity());
    }

    @Test
    @DisplayName("出库：可用库存不足被拒绝")
    public void testDecreaseInventoryInsufficient() {
        inventoryService.increaseInventory(1L, 10L, 10, "首批入库");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.decreaseInventory(1L, 10L, 50, "超量出库"));
        assertTrue(ex.getMessage().contains("可用库存不足"));
    }

    @Test
    @DisplayName("出库：库存不存在被拒绝")
    public void testDecreaseInventoryNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.decreaseInventory(999L, 10L, 1, "无库存"));
        assertTrue(ex.getMessage().contains("库存不存在"));
    }

    @Test
    @DisplayName("出入库流水：记录操作前后数量")
    public void testInventoryLogRecorded() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        inventoryService.decreaseInventory(1L, 10L, 30, "销售出库");

        List<InventoryLog> logs = inventoryService.getInventoryLogsByProduct(1L, null, null);
        assertEquals(2, logs.size());

        InventoryLog inbound = logs.stream()
                .filter(l -> l.getOperationType() == OperationType.INBOUND)
                .findFirst().orElseThrow();
        assertEquals(100, inbound.getQuantity());
        assertEquals(0, inbound.getBeforeQuantity());
        assertEquals(100, inbound.getAfterQuantity());

        InventoryLog outbound = logs.stream()
                .filter(l -> l.getOperationType() == OperationType.OUTBOUND)
                .findFirst().orElseThrow();
        assertEquals(30, outbound.getQuantity());
        assertEquals(100, outbound.getBeforeQuantity());
        assertEquals(70, outbound.getAfterQuantity());
    }

    // ==================== 调拨 ====================

    @Test
    @DisplayName("调拨：源仓减少、目标仓增加，且源仓锁定数归零")
    public void testTransferInventoryKeepsLockConsistent() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        inventoryService.increaseInventory(1L, 20L, 20, "目标仓备货");

        inventoryService.transferInventory(1L, 10L, 20L, 30, "调拨补货");

        Inventory from = inventoryService.getInventory(1L, 10L);
        Inventory to = inventoryService.getInventory(1L, 20L);

        assertEquals(70, from.getQuantity());
        assertEquals(70, from.getAvailableQuantity());
        // 调拨完成后预留必须释放，否则可用库存被永久吞掉
        assertEquals(0, from.getLockedQuantity());

        assertEquals(50, to.getQuantity());
        assertEquals(50, to.getAvailableQuantity());
    }

    @Test
    @DisplayName("调拨：源仓库存不足被拒绝")
    public void testTransferInsufficient() {
        inventoryService.increaseInventory(1L, 10L, 10, "首批入库");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.transferInventory(1L, 10L, 20L, 50, "超量调拨"));
        assertTrue(ex.getMessage().contains("可用库存不足"));
    }

    // ==================== 锁定与释放 ====================

    @Test
    @DisplayName("锁定：可用数量减少、锁定数量增加")
    public void testLockInventory() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        inventoryService.lockInventory(1L, 10L, 40, LockType.ORDER, "SO-001", "订单占用");

        Inventory inventory = inventoryService.getInventory(1L, 10L);
        assertEquals(60, inventory.getAvailableQuantity());
        assertEquals(40, inventory.getLockedQuantity());
        assertEquals(100, inventory.getQuantity());
    }

    @Test
    @DisplayName("释放：按实际锁定量回补可用数量")
    public void testReleaseInventory() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        inventoryService.lockInventory(1L, 10L, 40, LockType.ORDER, "SO-001", "订单占用");
        inventoryService.releaseInventory(1L, 10L, 40, "订单取消");

        Inventory inventory = inventoryService.getInventory(1L, 10L);
        assertEquals(100, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getLockedQuantity());
    }

    @Test
    @DisplayName("释放：没有锁定记录时不得凭空增加可用数量")
    public void testReleaseWithoutLockDoesNotInflateAvailable() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");

        inventoryService.releaseInventory(1L, 10L, 40, "无锁释放");

        Inventory inventory = inventoryService.getInventory(1L, 10L);
        assertEquals(100, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getLockedQuantity());
    }

    // ==================== 预警 ====================

    @Test
    @DisplayName("预警：低于安全库存时生成低库存预警并置为预警状态")
    public void testLowStockAlertCreated() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        // 调低安全库存后立即低于阈值
        SafetyStockUpdate update = new SafetyStockUpdate();
        update.setInventoryId(inventoryService.getInventory(1L, 10L).getId());
        update.setSafetyStock(200);
        inventoryService.batchUpdateSafetyStock(List.of(update));

        List<InventoryAlert> alerts = inventoryService.getAlerts(10L, AlertStatus.PENDING);
        assertEquals(1, alerts.size());
        assertEquals(AlertType.LOW_STOCK, alerts.get(0).getAlertType());

        // 库存状态必须同步刷新，否则统计里的预警/危险数量永远是 0
        assertEquals(InventoryStatus.WARNING, inventoryService.getInventory(1L, 10L).getStatus());
    }

    @Test
    @DisplayName("预警：缺货时生成缺货预警并置为危险状态")
    public void testOutOfStockAlert() {
        inventoryService.increaseInventory(1L, 10L, 5, "首批入库");
        inventoryService.decreaseInventory(1L, 10L, 5, "全部出库");

        List<InventoryAlert> alerts = inventoryService.getAlerts(10L, AlertStatus.PENDING);
        assertEquals(1, alerts.size(), "库存降到 0 后，早先的低库存预警必须自动关闭，不能堆积");
        assertEquals(AlertType.OUT_OF_STOCK, alerts.get(0).getAlertType());
        assertEquals(InventoryStatus.DANGER, inventoryService.getInventory(1L, 10L).getStatus());
    }

    @Test
    @DisplayName("预警：处理后状态变更为已处理")
    public void testProcessAlert() {
        inventoryService.increaseInventory(1L, 10L, 5, "首批入库");
        inventoryService.decreaseInventory(1L, 10L, 5, "全部出库");

        List<InventoryAlert> alerts = inventoryService.getAlerts(10L, AlertStatus.PENDING);
        assertEquals(1, alerts.size());

        inventoryService.processAlert(alerts.get(0).getId(), "张三", "已补货");

        InventoryAlertStats stats = inventoryService.getAlertStats(10L);
        assertEquals(0L, stats.getPendingCount());
        assertTrue(stats.getProcessedCount() >= 1L, "处理过的预警应计入已处理数");
        assertEquals(1L, stats.getOutOfStockCount());
    }

    @Test
    @DisplayName("滞销：期间没有出库记录的商品也算滞销")
    public void testSlowMovingIncludesNeverSoldItems() {
        // 商品 1 在窗口内有出库（销量 30，阈值 50 → 仍属滞销）
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        inventoryService.decreaseInventory(1L, 10L, 30, "销售出库");
        // 商品 2 完全没有出库记录
        inventoryService.increaseInventory(2L, 10L, 20, "首批入库");

        List<Inventory> slow = inventoryService.getSlowMovingInventory(30, 50);

        assertEquals(2, slow.size(), "完全没有出库记录的商品同样属于滞销，不能被漏掉");
    }

    @Test
    @DisplayName("滞销：销量达到阈值的商品不算滞销")
    public void testSlowMovingExcludesFastMovingItems() {
        inventoryService.increaseInventory(1L, 10L, 200, "首批入库");
        inventoryService.decreaseInventory(1L, 10L, 100, "销售出库");

        List<Inventory> slow = inventoryService.getSlowMovingInventory(30, 50);
        assertTrue(slow.isEmpty());
    }

    // ==================== 盘点 ====================

    @Test
    @DisplayName("盘点：实盘数量回写库存并计算盈亏")
    public void testExecuteCheck() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");
        Inventory inventory = inventoryService.getInventory(1L, 10L);
        inventory.setUnitCost(new BigDecimal("5.00"));
        inventoryService.batchUpdateSafetyStock(List.of());

        InventoryCheck check = new InventoryCheck();
        check.setWarehouseId(10L);
        Long checkId = inventoryService.createCheck(check);
        assertNotNull(checkId);

        InventoryCheckDetail detail = new InventoryCheckDetail();
        detail.setInventoryId(inventory.getId());
        detail.setCheckQty(90);

        inventoryService.executeCheck(checkId, List.of(detail));

        Inventory after = inventoryService.getInventoryById(inventory.getId());
        assertEquals(90, after.getQuantity());
        assertEquals(90, after.getAvailableQuantity());
    }

    @Test
    @DisplayName("盘点：盘点单状态推进为已完成")
    public void testCheckStatusFlow() {
        inventoryService.increaseInventory(1L, 10L, 100, "首批入库");

        InventoryCheck check = new InventoryCheck();
        check.setWarehouseId(10L);
        Long checkId = inventoryService.createCheck(check);

        InventoryCheckDetail detail = new InventoryCheckDetail();
        detail.setInventoryId(inventoryService.getInventory(1L, 10L).getId());
        detail.setCheckQty(100);
        inventoryService.executeCheck(checkId, List.of(detail));

        // 重复执行应被拒绝
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inventoryService.executeCheck(checkId, List.of(detail)));
        assertTrue(ex.getMessage().contains("盘点单状态不正确"));
    }

    // ==================== 查询与统计 ====================

    @Test
    @DisplayName("分页查询：返回带 total 的分页结果")
    public void testPageInventories() {
        for (int i = 1; i <= 5; i++) {
            inventoryService.increaseInventory((long) i, 10L, 10 * i, "入库");
        }

        InventoryQuery query = new InventoryQuery();
        query.setPage(1);
        query.setSize(2);
        Page<Inventory> page = inventoryService.pageInventories(query);

        assertEquals(5L, page.getTotal(), "分页 total 必须正确统计");
        assertEquals(2, page.getRecords().size());
    }

    @Test
    @DisplayName("低库存查询：按可用数量判定")
    public void testGetLowStockInventory() {
        inventoryService.increaseInventory(1L, 10L, 5, "低库存");
        inventoryService.increaseInventory(2L, 10L, 500, "正常库存");

        List<Inventory> low = inventoryService.getLowStockInventory(10);
        assertEquals(1, low.size());
        assertEquals(1L, low.get(0).getProductId());
    }

    @Test
    @DisplayName("库存统计：汇总数量、金额与状态分布")
    public void testGetStatistics() {
        inventoryService.increaseInventory(1L, 10L, 100, "入库");
        inventoryService.increaseInventory(2L, 10L, 50, "入库");
        inventoryService.decreaseInventory(1L, 10L, 20, "出库");

        InventoryStatistics stats = inventoryService.getStatistics(10L, null, null);

        assertEquals(2, stats.getTotalSku());
        assertEquals(130, stats.getTotalQuantity());
        assertEquals(130, stats.getAvailableQuantity());
        assertEquals(2, stats.getNormalCount());
    }

    // ==================== 批量操作 ====================

    @Test
    @DisplayName("批量入库：成功与失败分别计数")
    public void testBatchIncreaseInventory() {
        InventoryBatchOperation ok = new InventoryBatchOperation();
        ok.setProductId(1L);
        ok.setWarehouseId(10L);
        ok.setQuantity(10);

        InventoryBatchOperation bad = new InventoryBatchOperation();
        bad.setProductId(2L);
        bad.setWarehouseId(10L);
        bad.setQuantity(0);

        BatchOperationResult result = inventoryService.batchIncreaseInventory(List.of(ok, bad));

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertFalse(result.getFailedList().isEmpty());
    }

    @Test
    @DisplayName("批量出库：成功与失败分别计数")
    public void testBatchDecreaseInventory() {
        inventoryService.increaseInventory(1L, 10L, 100, "入库");

        InventoryBatchOperation ok = new InventoryBatchOperation();
        ok.setProductId(1L);
        ok.setWarehouseId(10L);
        ok.setQuantity(10);

        InventoryBatchOperation bad = new InventoryBatchOperation();
        bad.setProductId(1L);
        bad.setWarehouseId(10L);
        bad.setQuantity(9999);

        BatchOperationResult result = inventoryService.batchDecreaseInventory(List.of(ok, bad));

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(90, inventoryService.getInventory(1L, 10L).getQuantity());
    }
}
