package com.crossborder.erp.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.purchase.entity.PurchasePlan;
import com.crossborder.erp.purchase.mapper.PurchasePlanMapper;
import com.crossborder.erp.purchase.service.impl.PurchasePlanServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 采购计划服务单元测试
 *
 * 覆盖 PurchasePlanServiceImpl 的计划生成、批量生成、确认/取消流转、
 * 待执行计划查询与分页查询过滤条件。
 * queryPlans 原实现直接 return null，这里验证已补全为真实分页查询。
 */
@SuppressWarnings("unchecked")
public class PurchasePlanServiceImplTest {

    private PurchasePlanServiceImpl purchasePlanService;
    private PurchasePlanMapper purchasePlanMapper;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析字段名依赖 MyBatis-Plus 的 TableInfo / Lambda 缓存
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.purchase.mapper.PurchasePlanMapper");
        TableInfoHelper.initTableInfo(builderAssistant, PurchasePlan.class);
    }

    @BeforeEach
    void setUp() {
        purchasePlanMapper = mock(PurchasePlanMapper.class);
        purchasePlanService = new PurchasePlanServiceImpl();
        injectBaseMapper(purchasePlanService, purchasePlanMapper);

        when(purchasePlanMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<PurchasePlan> page = invocation.getArgument(0);
            return page;
        });
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     */
    private void injectBaseMapper(ServiceImpl<?, ?> service, PurchasePlanMapper mapper) {
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

    // ==================== generatePlan ====================

    @Test
    @DisplayName("测试生成采购计划 - 落库并返回计划")
    void testGeneratePlan_PersistsAndReturnsPlan() {
        when(purchasePlanMapper.insert(any(PurchasePlan.class))).thenReturn(1);

        PurchasePlan plan = purchasePlanService.generatePlan(
                5001L, "SKU-PP-001", "WH001", 200, 900L);

        assertNotNull(plan);
        assertEquals(5001L, plan.getProductId());
        assertEquals("SKU-PP-001", plan.getSku());
        assertEquals("WH001", plan.getWarehouseId());
        assertEquals(200, plan.getPlanQuantity());
        assertEquals(900L, plan.getSupplierId());
        assertEquals("AUTO", plan.getPlanType());
        assertEquals("NORMAL", plan.getPriority());
        assertEquals("PENDING", plan.getStatus());
        assertEquals(BigDecimal.ZERO, plan.getTotalAmount());

        verify(purchasePlanMapper, times(1)).insert(any(PurchasePlan.class));
    }

    @Test
    @DisplayName("测试生成采购计划 - 计划编号以 PP 开头且需求日期为7天后")
    void testGeneratePlan_PlanNoAndRequiredDate() {
        PurchasePlan plan = purchasePlanService.generatePlan(
                5002L, "SKU-PP-002", "WH002", 100, null);

        assertNotNull(plan.getPlanNo());
        assertTrue(plan.getPlanNo().startsWith("PP"), "计划编号应以 PP 开头: " + plan.getPlanNo());
        assertEquals(LocalDate.now().plusDays(7), plan.getRequiredDate());
    }

    // ==================== batchGeneratePlans ====================

    @Test
    @DisplayName("测试批量生成采购计划 - 每个商品生成一条计划")
    void testBatchGeneratePlans_GeneratesPlanPerProduct() {
        when(purchasePlanMapper.insert(any(PurchasePlan.class))).thenReturn(1);

        List<PurchasePlan> plans = purchasePlanService.batchGeneratePlans(
                List.of(6001L, 6002L), "WH001");

        assertNotNull(plans);
        assertEquals(2, plans.size());
        assertEquals(6001L, plans.get(0).getProductId());
        assertEquals(6002L, plans.get(1).getProductId());
        assertEquals("SKU-6001", plans.get(0).getSku());
        assertEquals(100, plans.get(0).getPlanQuantity());

        verify(purchasePlanMapper, times(2)).insert(any(PurchasePlan.class));
    }

    // ==================== confirmPlan / cancelPlan ====================

    @Test
    @DisplayName("测试确认采购计划 - 状态置为 CONFIRMED")
    void testConfirmPlan_UpdatesStatus() {
        PurchasePlan plan = new PurchasePlan();
        plan.setId(7001L);
        plan.setStatus("PENDING");
        when(purchasePlanMapper.selectById(7001L)).thenReturn(plan);
        when(purchasePlanMapper.updateById(plan)).thenReturn(1);

        purchasePlanService.confirmPlan(7001L, 88L, "采购员");

        assertEquals("CONFIRMED", plan.getStatus());
        verify(purchasePlanMapper, times(1)).updateById(plan);
    }

    @Test
    @DisplayName("测试确认采购计划 - 记录不存在时不报错也不更新")
    void testConfirmPlan_NotFound_NoUpdate() {
        when(purchasePlanMapper.selectById(9999L)).thenReturn(null);

        assertDoesNotThrow(() -> purchasePlanService.confirmPlan(9999L, 88L, "采购员"));

        verify(purchasePlanMapper, never()).updateById(any(PurchasePlan.class));
    }

    @Test
    @DisplayName("测试取消采购计划 - 状态置为 CANCELLED 并记录原因")
    void testCancelPlan_UpdatesStatusAndRemark() {
        PurchasePlan plan = new PurchasePlan();
        plan.setId(7002L);
        plan.setStatus("PENDING");
        when(purchasePlanMapper.selectById(7002L)).thenReturn(plan);
        when(purchasePlanMapper.updateById(plan)).thenReturn(1);

        purchasePlanService.cancelPlan(7002L, "库存充足");

        assertEquals("CANCELLED", plan.getStatus());
        assertEquals("库存充足", plan.getRemark());
        verify(purchasePlanMapper, times(1)).updateById(plan);
    }

    @Test
    @DisplayName("测试取消采购计划 - 记录不存在时不报错也不更新")
    void testCancelPlan_NotFound_NoUpdate() {
        when(purchasePlanMapper.selectById(9998L)).thenReturn(null);

        assertDoesNotThrow(() -> purchasePlanService.cancelPlan(9998L, "原因"));

        verify(purchasePlanMapper, never()).updateById(any(PurchasePlan.class));
    }

    // ==================== getPendingPlans ====================

    @Test
    @DisplayName("测试获取待执行计划 - 按状态与仓库过滤")
    void testGetPendingPlans_FiltersByStatusAndWarehouse() {
        purchasePlanService.getPendingPlans("WH001");

        ArgumentCaptor<Wrapper<PurchasePlan>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchasePlanMapper, times(1)).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("status"), "应包含状态过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("warehouse_id"), "应包含仓库过滤: " + sqlSegment);
    }

    @Test
    @DisplayName("测试获取待执行计划 - 仓库为空时只按状态过滤")
    void testGetPendingPlans_WithoutWarehouse() {
        purchasePlanService.getPendingPlans(null);

        ArgumentCaptor<Wrapper<PurchasePlan>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchasePlanMapper, times(1)).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("status"), "应包含状态过滤: " + sqlSegment);
        assertFalse(sqlSegment.contains("warehouse_id"), "仓库为空时不应包含仓库条件: " + sqlSegment);
    }

    // ==================== queryPlans ====================

    @Test
    @DisplayName("测试查询采购计划 - 返回分页结果而非 null")
    void testQueryPlans_ReturnsPage() {
        PurchasePlan item = new PurchasePlan();
        item.setId(1L);
        item.setPlanNo("PP001");

        when(purchasePlanMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<PurchasePlan> page = invocation.getArgument(0);
            page.setRecords(List.of(item));
            page.setTotal(1);
            return page;
        });

        IPage<PurchasePlan> result = purchasePlanService.queryPlans(1, 10, "PENDING", "WH001");

        assertNotNull(result, "queryPlans 不应返回 null");
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("PP001", result.getRecords().get(0).getPlanNo());
    }

    @Test
    @DisplayName("测试查询采购计划 - 带状态与仓库过滤")
    void testQueryPlans_AppliesFilters() {
        purchasePlanService.queryPlans(1, 10, "PENDING", "WH001");

        ArgumentCaptor<Wrapper<PurchasePlan>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchasePlanMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("status"), "应包含状态过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("warehouse_id"), "应包含仓库过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("required_date"), "应按需求日期排序: " + sqlSegment);
    }

    @Test
    @DisplayName("测试查询采购计划 - 空白状态视为无过滤")
    void testQueryPlans_BlankStatusTreatedAsNoFilter() {
        purchasePlanService.queryPlans(1, 10, "  ", null);

        ArgumentCaptor<Wrapper<PurchasePlan>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchasePlanMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertFalse(sqlSegment.contains("status"), "空白状态不应产生状态条件: " + sqlSegment);
        assertFalse(sqlSegment.contains("warehouse_id"), "空仓库不应产生仓库条件: " + sqlSegment);
    }
}
