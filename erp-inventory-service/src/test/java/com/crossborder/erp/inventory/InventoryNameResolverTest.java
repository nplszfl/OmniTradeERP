package com.crossborder.erp.inventory;

import com.crossborder.erp.inventory.service.InventoryNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存展示名解析器测试
 *
 * <p><b>为什么要单独测这个类</b><br>
 * 前端 {@code erp-web/src/api/inventory.ts} 的 {@code Inventory} 接口要求
 * {@code productName} / {@code warehouseName} / {@code locationCode}，
 * 缺了这三个字段，库存列表的「商品名称 / 仓库 / 库位」三列就是空白。
 * 之前 {@code InventoryController.toInventoryVO()} 只吐 ID，这个缺陷不会报任何错，
 * 只表现为「页面空列」—— 单测完全覆盖不到。</p>
 *
 * <p><b>关键设计约束</b>：解析器跨 schema 查 erp_product / erp_warehouse，
 * 而本单测跑在 H2 上（只有 erp_inventory 一个库）。
 * 所以这里锁死的行为是：<b>跨库查不到时必须降级、不能抛异常</b>，
 * 库存主数据（数量、状态）永远优先于展示名。</p>
 */
@SpringBootTest
public class InventoryNameResolverTest {

    @Autowired
    private InventoryNameResolver nameResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM erp_inventory_log");
        jdbcTemplate.update("DELETE FROM erp_inventory");
    }

    // ==================== 跨库查不到时的降级 ====================

    @Test
    @DisplayName("降级：H2 里没有 erp_product 库时，商品名解析返回空 Map 而不是抛异常")
    public void testResolveProductNamesDegradesGracefully() {
        Map<Long, String> names = nameResolver.resolveProductNames(List.of(1001L, 1002L));
        // 不抛异常本身就是断言；再确认没有把脏数据塞进来
        assertNotNull(names);
        assertTrue(names.isEmpty(), "H2 无 erp_product 库时应解析不到任何商品名");
    }

    @Test
    @DisplayName("降级：仓库名 / 库位编码解析同样不抛异常")
    public void testResolveWarehouseAndLocationDegradeGracefully() {
        assertNotNull(nameResolver.resolveWarehouseNames(List.of(1L, 2L)));
        assertNotNull(nameResolver.resolveLocationCodes(List.of(1L)));
    }

    @Test
    @DisplayName("降级：空 ID 集合直接返回空 Map，不打 DB")
    public void testEmptyIdCollectionReturnsEmptyMap() {
        assertTrue(nameResolver.resolveProductNames(List.of()).isEmpty());
        assertTrue(nameResolver.resolveWarehouseNames(List.of()).isEmpty());
        assertTrue(nameResolver.resolveLocationCodes(List.of()).isEmpty());
    }

    @Test
    @DisplayName("降级：null ID 被忽略，不会把 null 塞进 IN 子句")
    public void testNullIdsAreIgnored() {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(null);
        ids.add(1001L);
        Map<Long, String> names = nameResolver.resolveProductNames(ids);
        assertNotNull(names);
    }

    // ==================== enrich 字段填充 ====================

    @Test
    @DisplayName("enrich：无论名字能否解析，productName/warehouseName/locationCode 三个 key 都必须存在")
    public void testEnrichAlwaysSetsTheThreeKeys() {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", 1L);
        vo.put("productId", 1001L);
        vo.put("warehouseId", 1L);
        vo.put("locationId", 1L);

        nameResolver.enrich(vo, Map.of(), Map.of(), Map.of());

        // 前端是 {{ row.productName }}，key 缺失在 Vue 里会渲染成空白且不报错，
        // 所以「key 存在但值为 null」才是可接受的降级；「key 不存在」是契约缺失。
        assertTrue(vo.containsKey("productName"), "缺少 productName key");
        assertTrue(vo.containsKey("warehouseName"), "缺少 warehouseName key");
        assertTrue(vo.containsKey("locationCode"), "缺少 locationCode key");
    }

    @Test
    @DisplayName("enrich：能查到映射时正确回填")
    public void testEnrichFillsFromProvidedMaps() {
        Map<String, Object> vo = new HashMap<>();
        vo.put("productId", 1001L);
        vo.put("warehouseId", 2L);
        vo.put("locationId", 3L);

        nameResolver.enrich(vo,
                Map.of(1001L, "智能手表 X1"),
                Map.of(2L, "上海中心仓"),
                Map.of(3L, "A-01-01-01"));

        assertEquals("智能手表 X1", vo.get("productName"));
        assertEquals("上海中心仓", vo.get("warehouseName"));
        assertEquals("A-01-01-01", vo.get("locationCode"));
    }

    @Test
    @DisplayName("enrichAll：批量补齐多条记录")
    public void testEnrichAllFillsMultipleRecords() {
        Map<String, Object> a = new HashMap<>();
        a.put("productId", 1001L);
        a.put("warehouseId", 1L);
        Map<String, Object> b = new HashMap<>();
        b.put("productId", 1002L);
        b.put("warehouseId", 2L);

        nameResolver.enrichAll(List.of(a, b),
                Map.of(1001L, "商品A", 1002L, "商品B"),
                Map.of(1L, "仓A", 2L, "仓B"),
                Map.of());

        assertEquals("商品A", a.get("productName"));
        assertEquals("仓A", a.get("warehouseName"));
        assertEquals("商品B", b.get("productName"));
        assertEquals("仓B", b.get("warehouseName"));
    }

    @Test
    @DisplayName("enrichAll：空列表不报错")
    public void testEnrichAllWithEmptyList() {
        nameResolver.enrichAll(List.of());
        nameResolver.enrichAll(null);
    }

    // ==================== 流转指标 ====================

    @Test
    @DisplayName("流转指标：无出库流水时日均销量为 0、可售天数为 null（不是 0 也不是 Infinity）")
    public void testFlowMetricsWithNoOutboundLogs() {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", 999L);
        vo.put("availableQty", 100);

        nameResolver.enrichFlowMetrics(vo);

        assertEquals(BigDecimal.ZERO.compareTo((BigDecimal) vo.get("dailySales")), 0,
                "无出库流水时日均销量应为 0");
        assertNull(vo.get("turnoverDays"), "日均销量为 0 时可售天数应为空，不能是 0 或 Infinity");
        assertTrue(vo.containsKey("lastInTime"));
        assertTrue(vo.containsKey("lastOutTime"));
    }

    @Test
    @DisplayName("流转指标：无 id 时也返回完整字段，不抛 NPE")
    public void testFlowMetricsWithoutId() {
        Map<String, Object> vo = new HashMap<>();
        nameResolver.enrichFlowMetrics(vo);
        assertEquals(BigDecimal.ZERO.compareTo((BigDecimal) vo.get("dailySales")), 0);
        assertNull(vo.get("turnoverDays"));
    }

    @Test
    @DisplayName("流转指标：有出库流水时算出日均销量与可售天数")
    public void testFlowMetricsWithOutboundLogs() {
        // 造一条库存记录 + 一条出库流水（近 30 天内）
        jdbcTemplate.update("INSERT INTO erp_inventory "
                + "(id, product_id, warehouse_id, sku_code, quantity, available_quantity, "
                + " locked_quantity, safety_stock, unit_cost, status, create_time, update_time) "
                + "VALUES (1, 1001, 1, 'SKU-1', 100, 100, 0, 10, 5.00, 'NORMAL', NOW(), NOW())");
        jdbcTemplate.update("INSERT INTO erp_inventory_log "
                + "(id, inventory_id, product_id, warehouse_id, quantity, operation_type, "
                + " before_quantity, after_quantity, order_no, remark, create_time) "
                + "VALUES (1, 1, 1001, 1, -300, 'OUTBOUND', 400, 100, 'OUT-1', '出库', NOW())");

        Map<String, Object> vo = new HashMap<>();
        vo.put("id", 1L);
        vo.put("availableQty", 100);

        nameResolver.enrichFlowMetrics(vo);

        BigDecimal dailySales = (BigDecimal) vo.get("dailySales");
        assertNotNull(dailySales);
        // 近 30 天出库 300 件 → 日均 10 件
        assertEquals(BigDecimal.TEN.compareTo(dailySales), 0,
                "日均销量应为 300/30 = 10，实际 " + dailySales);
        // 可用 100 / 日均 10 = 10 天
        assertEquals(10, ((Number) vo.get("turnoverDays")).intValue());
        assertNotNull(vo.get("lastOutTime"), "应有最后出库时间");
    }

    @Test
    @DisplayName("流转指标：入库流水产生 lastInTime")
    public void testFlowMetricsLastInTime() {
        jdbcTemplate.update("INSERT INTO erp_inventory "
                + "(id, product_id, warehouse_id, sku_code, quantity, available_quantity, "
                + " locked_quantity, safety_stock, unit_cost, status, create_time, update_time) "
                + "VALUES (1, 1001, 1, 'SKU-1', 100, 100, 0, 10, 5.00, 'NORMAL', NOW(), NOW())");
        jdbcTemplate.update("INSERT INTO erp_inventory_log "
                + "(id, inventory_id, product_id, warehouse_id, quantity, operation_type, "
                + " before_quantity, after_quantity, order_no, remark, create_time) "
                + "VALUES (1, 1, 1001, 1, 400, 'INBOUND', 0, 400, 'IN-1', '入库', NOW())");

        Map<String, Object> vo = new HashMap<>();
        vo.put("id", 1L);
        vo.put("availableQty", 100);

        nameResolver.enrichFlowMetrics(vo);

        assertNotNull(vo.get("lastInTime"), "应有最后入库时间");
        assertNull(vo.get("lastOutTime"), "没有出库流水时 lastOutTime 应为空");
    }
}
