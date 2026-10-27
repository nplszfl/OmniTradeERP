package com.crossborder.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.inventory.alert.entity.SlowMovingInventory;
import com.crossborder.erp.inventory.alert.mapper.SlowMovingInventoryMapper;
import com.crossborder.erp.inventory.alert.service.impl.SlowMovingInventoryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 滞销商品服务单元测试
 *
 * 覆盖 SlowMovingInventoryServiceImpl 的检测、分页筛选、待推送查询与通知标记。
 * ServiceImpl 内部的 baseMapper 通过反射注入 mock，避免依赖真实数据库。
 */
@SuppressWarnings("unchecked")
public class SlowMovingInventoryServiceImplTest {

    private SlowMovingInventoryServiceImpl slowMovingInventoryService;
    private SlowMovingInventoryMapper slowMovingInventoryMapper;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析字段名依赖 MyBatis-Plus 的 TableInfo / Lambda 缓存，
        // 纯单元测试环境下需要手动初始化一次。
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.inventory.alert.mapper.SlowMovingInventoryMapper");
        TableInfoHelper.initTableInfo(builderAssistant, SlowMovingInventory.class);
    }

    @BeforeEach
    void setUp() {
        slowMovingInventoryMapper = mock(SlowMovingInventoryMapper.class);
        slowMovingInventoryService = new SlowMovingInventoryServiceImpl();
        injectBaseMapper(slowMovingInventoryService, slowMovingInventoryMapper);

        // selectPage 默认回传入参 page，便于断言返回内容
        when(slowMovingInventoryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<SlowMovingInventory> page = invocation.getArgument(0);
            return page;
        });
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     * （ServiceImpl 未提供公开 setter）
     */
    private void injectBaseMapper(ServiceImpl<?, ?> service, SlowMovingInventoryMapper mapper) {
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

    // ==================== detectSlowMoving ====================

    @Test
    @DisplayName("测试检测滞销商品 - 落库并返回记录")
    void testDetectSlowMoving_PersistsAndReturnsRecord() {
        when(slowMovingInventoryMapper.insert(any(SlowMovingInventory.class))).thenReturn(1);

        SlowMovingInventory record = slowMovingInventoryService.detectSlowMoving(
                1001L, "SKU-SLOW-001", "WH001");

        assertNotNull(record);
        assertEquals(1001L, record.getProductId());
        assertEquals("SKU-SLOW-001", record.getSku());
        assertEquals("WH001", record.getWarehouseId());

        // 落库校验
        verify(slowMovingInventoryMapper, times(1)).insert(any(SlowMovingInventory.class));
    }

    @Test
    @DisplayName("测试检测滞销商品 - 默认指标与预警级别")
    void testDetectSlowMoving_SetsDefaultMetrics() {
        SlowMovingInventory record = slowMovingInventoryService.detectSlowMoving(
                1002L, "SKU-SLOW-002", "WH002");

        assertNotNull(record);
        assertEquals(100, record.getCurrentStock());
        assertEquals(5, record.getSalesLast30Days());
        assertEquals(12, record.getSalesLast60Days());
        assertEquals(20, record.getSalesLast90Days());
        assertEquals(0.3, record.getTurnoverRate());
        assertEquals("MEDIUM", record.getAlertLevel());
        assertEquals("DETECTED", record.getStatus());
        assertEquals(Boolean.FALSE, record.getNotified());
    }

    @Test
    @DisplayName("测试检测滞销商品 - 停滞天数与最后销售日期一致")
    void testDetectSlowMoving_StagnantDaysMatchesLastSaleDate() {
        SlowMovingInventory record = slowMovingInventoryService.detectSlowMoving(
                1003L, "SKU-SLOW-003", "WH003");

        assertNotNull(record);
        assertNotNull(record.getLastSaleDate());
        assertEquals(45, record.getStagnantDays());
        assertEquals(LocalDate.now().minusDays(45), record.getLastSaleDate());
    }

    // ==================== markAsNotified ====================

    @Test
    @DisplayName("测试标记已通知 - 记录存在时更新")
    void testMarkAsNotified_ExistingRecord_Updates() {
        SlowMovingInventory existing = new SlowMovingInventory();
        existing.setId(500L);
        existing.setNotified(false);
        when(slowMovingInventoryMapper.selectById(500L)).thenReturn(existing);
        when(slowMovingInventoryMapper.updateById(existing)).thenReturn(1);

        slowMovingInventoryService.markAsNotified(500L);

        assertTrue(existing.getNotified());
        verify(slowMovingInventoryMapper, times(1)).updateById(existing);
    }

    @Test
    @DisplayName("测试标记已通知 - 记录不存在时不报错也不更新")
    void testMarkAsNotified_RecordNotFound_NoUpdate() {
        when(slowMovingInventoryMapper.selectById(999L)).thenReturn(null);

        assertDoesNotThrow(() -> slowMovingInventoryService.markAsNotified(999L));

        verify(slowMovingInventoryMapper, never()).updateById(any(SlowMovingInventory.class));
    }

    // ==================== querySlowMoving ====================

    @Test
    @DisplayName("测试分页查询滞销商品 - 命中仓库与预警级别过滤")
    void testQuerySlowMoving_AppliesFilters() {
        slowMovingInventoryService.querySlowMoving(1, 10, "WH001", "HIGH");

        ArgumentCaptor<Wrapper<SlowMovingInventory>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(slowMovingInventoryMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("warehouse_id"), "应包含仓库过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("alert_level"), "应包含预警级别过滤: " + sqlSegment);
    }

    @Test
    @DisplayName("测试分页查询滞销商品 - 无过滤条件时只按周转率升序")
    void testQuerySlowMoving_WithoutFilters() {
        slowMovingInventoryService.querySlowMoving(2, 20, null, null);

        ArgumentCaptor<Wrapper<SlowMovingInventory>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(slowMovingInventoryMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertFalse(sqlSegment.contains("warehouse_id"), "无过滤条件时不应包含仓库条件: " + sqlSegment);
        assertFalse(sqlSegment.contains("alert_level"), "无过滤条件时不应包含预警级别条件: " + sqlSegment);
        assertTrue(sqlSegment.contains("turnover_rate"), "应按周转率排序: " + sqlSegment);
    }

    @Test
    @DisplayName("测试分页查询滞销商品 - 返回分页结果")
    void testQuerySlowMoving_ReturnsPage() {
        SlowMovingInventory item = new SlowMovingInventory();
        item.setId(1L);
        item.setSku("SKU-001");

        when(slowMovingInventoryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<SlowMovingInventory> page = invocation.getArgument(0);
            page.setRecords(java.util.Collections.singletonList(item));
            page.setTotal(1);
            return page;
        });

        IPage<SlowMovingInventory> result =
                slowMovingInventoryService.querySlowMoving(1, 10, "WH001", "HIGH");

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("SKU-001", result.getRecords().get(0).getSku());
    }

    // ==================== getPendingAlerts ====================

    @Test
    @DisplayName("测试获取待推送预警 - 只查询未通知记录")
    void testGetPendingAlerts_FiltersNotNotified() {
        slowMovingInventoryService.getPendingAlerts(1, 10);

        ArgumentCaptor<Wrapper<SlowMovingInventory>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(slowMovingInventoryMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("notified"), "应包含未通知过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("alert_level"), "应按预警级别排序: " + sqlSegment);
    }
}
