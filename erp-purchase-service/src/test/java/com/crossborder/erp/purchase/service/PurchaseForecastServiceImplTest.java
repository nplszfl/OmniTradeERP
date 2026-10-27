package com.crossborder.erp.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.purchase.entity.PurchaseForecast;
import com.crossborder.erp.purchase.mapper.PurchaseForecastMapper;
import com.crossborder.erp.purchase.service.impl.PurchaseForecastServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 采购预测服务单元测试
 *
 * 覆盖 PurchaseForecastServiceImpl 的单/批量预测落库与分页查询过滤条件。
 * queryForecasts 原实现直接 return null，这里验证已补全为真实分页查询。
 */
@SuppressWarnings("unchecked")
public class PurchaseForecastServiceImplTest {

    private PurchaseForecastServiceImpl purchaseForecastService;
    private PurchaseForecastMapper purchaseForecastMapper;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析字段名依赖 MyBatis-Plus 的 TableInfo / Lambda 缓存
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.purchase.mapper.PurchaseForecastMapper");
        TableInfoHelper.initTableInfo(builderAssistant, PurchaseForecast.class);
    }

    @BeforeEach
    void setUp() {
        purchaseForecastMapper = mock(PurchaseForecastMapper.class);
        purchaseForecastService = new PurchaseForecastServiceImpl();
        injectBaseMapper(purchaseForecastService, purchaseForecastMapper);

        when(purchaseForecastMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<PurchaseForecast> page = invocation.getArgument(0);
            return page;
        });
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     */
    private void injectBaseMapper(ServiceImpl<?, ?> service, PurchaseForecastMapper mapper) {
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

    // ==================== predictPurchaseQuantity ====================

    @Test
    @DisplayName("测试采购量预测 - 落库并返回预测记录")
    void testPredictPurchaseQuantity_PersistsAndReturnsRecord() {
        when(purchaseForecastMapper.insert(any(PurchaseForecast.class))).thenReturn(1);

        PurchaseForecast forecast = purchaseForecastService.predictPurchaseQuantity(
                3001L, "SKU-FC-001", "WH001", 30);

        assertNotNull(forecast);
        assertEquals(3001L, forecast.getProductId());
        assertEquals("SKU-FC-001", forecast.getSku());
        assertEquals("WH001", forecast.getWarehouseId());
        assertEquals("30天", forecast.getForecastPeriod());
        assertEquals("PREDICTED", forecast.getStatus());
        assertEquals(100, forecast.getPredictedQuantity());
        assertEquals(50, forecast.getCurrentStock());
        assertEquals(30, forecast.getSafeStock());
        assertNotNull(forecast.getForecastDate());

        verify(purchaseForecastMapper, times(1)).insert(any(PurchaseForecast.class));
    }

    @Test
    @DisplayName("测试采购量预测 - 预测周期随天数变化")
    void testPredictPurchaseQuantity_ForecastPeriodFollowsDays() {
        PurchaseForecast forecast = purchaseForecastService.predictPurchaseQuantity(
                3002L, "SKU-FC-002", "WH002", 60);

        assertEquals("60天", forecast.getForecastPeriod());
    }

    // ==================== batchPredict ====================

    @Test
    @DisplayName("测试批量预测 - 每个商品生成一条记录并落库")
    void testBatchPredict_GeneratesForecastPerProduct() {
        when(purchaseForecastMapper.insert(any(PurchaseForecast.class))).thenReturn(1);

        List<PurchaseForecast> forecasts = purchaseForecastService.batchPredict(
                List.of(4001L, 4002L, 4003L), "WH001", 30);

        assertNotNull(forecasts);
        assertEquals(3, forecasts.size());
        assertEquals(4001L, forecasts.get(0).getProductId());
        assertEquals(4002L, forecasts.get(1).getProductId());
        assertEquals(4003L, forecasts.get(2).getProductId());
        // 批量时 SKU 按商品ID自动生成
        assertEquals("SKU-4001", forecasts.get(0).getSku());
        assertEquals("SKU-4002", forecasts.get(1).getSku());
        assertEquals("WH001", forecasts.get(0).getWarehouseId());

        verify(purchaseForecastMapper, times(3)).insert(any(PurchaseForecast.class));
    }

    @Test
    @DisplayName("测试批量预测 - 空列表不产生落库")
    void testBatchPredict_EmptyList_NoInsert() {
        List<PurchaseForecast> forecasts = purchaseForecastService.batchPredict(
                List.of(), "WH001", 30);

        assertNotNull(forecasts);
        assertTrue(forecasts.isEmpty());
        verify(purchaseForecastMapper, never()).insert(any(PurchaseForecast.class));
    }

    // ==================== queryForecasts ====================

    @Test
    @DisplayName("测试查询预测记录 - 返回分页结果而非 null")
    void testQueryForecasts_ReturnsPage() {
        PurchaseForecast item = new PurchaseForecast();
        item.setId(1L);
        item.setSku("SKU-FC-001");

        when(purchaseForecastMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<PurchaseForecast> page = invocation.getArgument(0);
            page.setRecords(List.of(item));
            page.setTotal(1);
            return page;
        });

        IPage<PurchaseForecast> result = purchaseForecastService.queryForecasts(
                1, 10, 3001L, "WH001");

        assertNotNull(result, "queryForecasts 不应返回 null");
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("SKU-FC-001", result.getRecords().get(0).getSku());
    }

    @Test
    @DisplayName("测试查询预测记录 - 带商品与仓库过滤")
    void testQueryForecasts_AppliesFilters() {
        purchaseForecastService.queryForecasts(1, 10, 3001L, "WH001");

        ArgumentCaptor<Wrapper<PurchaseForecast>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchaseForecastMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("product_id"), "应包含商品ID过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("warehouse_id"), "应包含仓库过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("forecast_date"), "应按预测日期排序: " + sqlSegment);
    }

    @Test
    @DisplayName("测试查询预测记录 - 无过滤条件时不含额外条件")
    void testQueryForecasts_WithoutFilters() {
        purchaseForecastService.queryForecasts(2, 20, null, null);

        ArgumentCaptor<Wrapper<PurchaseForecast>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(purchaseForecastMapper, times(1)).selectPage(any(), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertFalse(sqlSegment.contains("product_id"), "无过滤条件时不应包含商品条件: " + sqlSegment);
        assertFalse(sqlSegment.contains("warehouse_id"), "无过滤条件时不应包含仓库条件: " + sqlSegment);
    }
}
