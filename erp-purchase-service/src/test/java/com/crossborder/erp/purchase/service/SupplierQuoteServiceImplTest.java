package com.crossborder.erp.purchase.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.purchase.dto.SupplierCompareDTO;
import com.crossborder.erp.purchase.entity.SupplierQuote;
import com.crossborder.erp.purchase.mapper.SupplierQuoteMapper;
import com.crossborder.erp.purchase.service.impl.SupplierQuoteServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 供应商比价服务单元测试
 *
 * 覆盖 SupplierQuoteServiceImpl 的报价查询、比价最优供应商选取、
 * 潜在节省金额计算与报价落库逻辑。
 */
@SuppressWarnings("unchecked")
public class SupplierQuoteServiceImplTest {

    private SupplierQuoteServiceImpl supplierQuoteService;
    private SupplierQuoteMapper supplierQuoteMapper;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析字段名依赖 MyBatis-Plus 的 TableInfo / Lambda 缓存
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.purchase.mapper.SupplierQuoteMapper");
        TableInfoHelper.initTableInfo(builderAssistant, SupplierQuote.class);
    }

    @BeforeEach
    void setUp() {
        supplierQuoteMapper = mock(SupplierQuoteMapper.class);
        supplierQuoteService = new SupplierQuoteServiceImpl();
        injectBaseMapper(supplierQuoteService, supplierQuoteMapper);
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     */
    private void injectBaseMapper(ServiceImpl<?, ?> service, SupplierQuoteMapper mapper) {
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

    private SupplierQuote buildQuote(Long supplierId, String supplierName,
                                     String unitPrice, Integer moq, Integer leadTimeDays) {
        SupplierQuote quote = new SupplierQuote();
        quote.setSupplierId(supplierId);
        quote.setSupplierName(supplierName);
        quote.setUnitPrice(new BigDecimal(unitPrice));
        quote.setMoq(moq);
        quote.setLeadTimeDays(leadTimeDays);
        return quote;
    }

    // ==================== getQuotesByProduct ====================

    @Test
    @DisplayName("测试按商品查询报价 - 带商品ID过滤")
    void testGetQuotesByProduct_FiltersByProductId() {
        supplierQuoteService.getQuotesByProduct(2001L);

        ArgumentCaptor<Wrapper<SupplierQuote>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(supplierQuoteMapper, times(1)).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("product_id"), "应包含商品ID过滤: " + sqlSegment);
    }

    // ==================== compareSuppliers ====================

    @Test
    @DisplayName("测试供应商比价 - 选出最低单价为最优供应商")
    void testCompareSuppliers_SelectsLowestPriceAsBest() {
        when(supplierQuoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildQuote(101L, "供应商A", "10.00", 100, 7),
                buildQuote(102L, "供应商B", "8.50", 50, 3),
                buildQuote(103L, "供应商C", "12.00", 200, 15)
        ));

        SupplierCompareDTO result = supplierQuoteService.compareSuppliers(2001L, "SKU-001", 100);

        assertNotNull(result);
        assertEquals(2001L, result.getProductId());
        assertEquals("SKU-001", result.getSku());
        assertEquals(3, result.getQuotes().size());
        assertEquals(102L, result.getBestSupplierId());
        assertEquals("供应商B", result.getBestSupplierName());
        assertEquals(new BigDecimal("8.50"), result.getBestPrice());
    }

    @Test
    @DisplayName("测试供应商比价 - 报价列表按单价升序")
    void testCompareSuppliers_SortsQuotesByUnitPrice() {
        when(supplierQuoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildQuote(101L, "供应商A", "10.00", 100, 7),
                buildQuote(102L, "供应商B", "8.50", 50, 3),
                buildQuote(103L, "供应商C", "12.00", 200, 15)
        ));

        SupplierCompareDTO result = supplierQuoteService.compareSuppliers(2001L, "SKU-001", 100);

        List<SupplierCompareDTO.SupplierQuoteItem> items = result.getQuotes();
        assertEquals(new BigDecimal("8.50"), items.get(0).getUnitPrice());
        assertEquals(new BigDecimal("10.00"), items.get(1).getUnitPrice());
        assertEquals(new BigDecimal("12.00"), items.get(2).getUnitPrice());
    }

    @Test
    @DisplayName("测试供应商比价 - 总价按数量计算")
    void testCompareSuppliers_CalculatesTotalCost() {
        when(supplierQuoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildQuote(101L, "供应商A", "10.00", 100, 7),
                buildQuote(102L, "供应商B", "8.50", 50, 3)
        ));

        SupplierCompareDTO result = supplierQuoteService.compareSuppliers(2001L, "SKU-001", 100);

        for (SupplierCompareDTO.SupplierQuoteItem item : result.getQuotes()) {
            assertEquals(item.getUnitPrice().multiply(BigDecimal.valueOf(100)), item.getTotalCost());
        }
    }

    @Test
    @DisplayName("测试供应商比价 - 潜在节省金额按最高价与最低价之差计算")
    void testCompareSuppliers_CalculatesPotentialSavings() {
        when(supplierQuoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildQuote(101L, "供应商A", "10.00", 100, 7),
                buildQuote(102L, "供应商B", "8.50", 50, 3),
                buildQuote(103L, "供应商C", "12.00", 200, 15)
        ));

        SupplierCompareDTO result = supplierQuoteService.compareSuppliers(2001L, "SKU-001", 100);

        // (12.00 - 8.50) * 100 = 350.00
        assertEquals(new BigDecimal("350.00"), result.getPotentialSavings());
    }

    @Test
    @DisplayName("测试供应商比价 - 只有一个供应商时不计算潜在节省")
    void testCompareSuppliers_SingleSupplier_NoPotentialSavings() {
        when(supplierQuoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildQuote(101L, "供应商A", "10.00", 100, 7)
        ));

        SupplierCompareDTO result = supplierQuoteService.compareSuppliers(2001L, "SKU-001", 100);

        assertEquals(1, result.getQuotes().size());
        assertEquals(101L, result.getBestSupplierId());
        assertEquals(new BigDecimal("10.00"), result.getBestPrice());
        assertNull(result.getPotentialSavings());
    }

    @Test
    @DisplayName("测试供应商比价 - 无报价时返回空结果且不抛异常")
    void testCompareSuppliers_NoQuotes_ReturnsEmptyResult() {
        when(supplierQuoteMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        SupplierCompareDTO result = supplierQuoteService.compareSuppliers(2001L, "SKU-001", 100);

        assertNotNull(result);
        assertEquals(2001L, result.getProductId());
        assertEquals("SKU-001", result.getSku());
        assertNotNull(result.getQuotes());
        assertTrue(result.getQuotes().isEmpty());
        assertNull(result.getBestSupplierId());
        assertNull(result.getBestPrice());
        assertNull(result.getPotentialSavings());
    }

    // ==================== addQuote ====================

    @Test
    @DisplayName("测试新增报价 - 落库并返回原对象")
    void testAddQuote_PersistsAndReturns() {
        when(supplierQuoteMapper.insert(any(SupplierQuote.class))).thenReturn(1);

        SupplierQuote quote = buildQuote(101L, "供应商A", "10.00", 100, 7);
        SupplierQuote result = supplierQuoteService.addQuote(quote);

        assertSame(quote, result);
        verify(supplierQuoteMapper, times(1)).insert(any(SupplierQuote.class));
    }
}
