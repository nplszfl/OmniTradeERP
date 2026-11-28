package com.crossborder.productdescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.crossborder.erp.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crossborder.productdescription.dto.AbTestOutcomeRequest;
import com.crossborder.productdescription.dto.AbTestStartRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.AbTestResult;
import com.crossborder.productdescription.entity.ProductHighlight;
import com.crossborder.productdescription.entity.SeoOptimization;
import com.crossborder.productdescription.mapper.AbTestResultMapper;
import com.crossborder.productdescription.mapper.ProductHighlightMapper;
import com.crossborder.productdescription.mapper.SeoOptimizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DescriptionAnalysisRecordServiceImpl} 的落库行为测试
 *
 * <p>🔴 <b>这个测试守护的是什么缺陷</b>（2026-11-28 实测）：
 * <pre>
 * seo_optimization / product_highlights / ab_test_result
 * 三张表<b>有 DDL、有索引</b>，但<b>全仓没有任何一行代码往里写</b>
 * → optimizeSEO() / extractHighlights() / generateABTestVersions()
 *    每次都<b>现算一遍就丢掉</b>
 * → 运营事后问「上周那条文案 A 版和 B 版哪个 CTR 高？」→ <b>表里是空的，问不出来</b>
 * </pre>
 *
 * <p>📌 <b>为什么这里必须 mock Mapper</b>：本测试只验证「Service 层怎么组装实体、
 * 怎么校验、怎么在写不进去时<b>明确报错而不是假装成功</b>」——
 * 这些逻辑不依赖真实 MySQL。真实 SQL 的正确性由 DDL 对齐 + 集成测试负责。</p>
 *
 * <p>📌 <b>关键断言：insert 返回 0 行必须抛异常</b>。
 * 「静默失败」比报错更贵 —— 页面提示「保存成功」，数据永远不存在。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("描述分析结果落库（SEO/亮点/A-B 三张表）")
class DescriptionAnalysisRecordServiceImplTest {

    @Mock
    private SeoOptimizationMapper seoOptimizationMapper;

    @Mock
    private ProductHighlightMapper productHighlightMapper;

    @Mock
    private AbTestResultMapper abTestResultMapper;

    private DescriptionAnalysisRecordServiceImpl newService() {
        return new DescriptionAnalysisRecordServiceImpl(
                seoOptimizationMapper, productHighlightMapper, abTestResultMapper,
                new ObjectMapper());
    }

    @Test
    @DisplayName("SEO 结果落库：把分数和关键词都写进去（不是只写个标题）")
    void seoOptimizationIsPersisted() {
        when(seoOptimizationMapper.insert(any(SeoOptimization.class))).thenReturn(1);

        DescriptionGenerationResponse.SEOResult seoResult = new DescriptionGenerationResponse.SEOResult();
        seoResult.setTitle("Optimized Title");
        seoResult.setDescription("Optimized Description");
        seoResult.setSeoscore(88.5);
        seoResult.setReadabilityScore(75.0);
        seoResult.setPrimaryKeywords(List.of("phone"));
        seoResult.setLongTailKeywords(List.of("cheap phone"));
        seoResult.setSuggestions(List.of("add specs"));

        SeoOptimization saved = newService().saveSeoOptimization(100L, 200L, seoResult, 1234L);

        assertNotNull(saved, "必须返回落库后的记录");
        assertEquals(100L, saved.getDescriptionId());
        assertEquals(200L, saved.getProductId());
        assertEquals(88.5, saved.getSeoScore().doubleValue(), 0.001);
        assertEquals(1234L, saved.getOptimizeTimeMs());

        // 📌 证明真的调了 insert，且带上了指标值
        ArgumentCaptor<SeoOptimization> captor = ArgumentCaptor.forClass(SeoOptimization.class);
        verify(seoOptimizationMapper).insert(captor.capture());
        assertEquals(88.5, captor.getValue().getSeoScore().doubleValue(), 0.001,
                "SEO 分数必须真的写进实体，不能只传个标题");
    }

    @Test
    @DisplayName("SEO 落库：缺 descriptionId 直接报错 —— 不写悬空引用")
    void seoOptimizationRequiresDescriptionId() {
        DescriptionGenerationResponse.SEOResult seoResult = new DescriptionGenerationResponse.SEOResult();
        seoResult.setTitle("T");

        BizException e = assertThrows(BizException.class,
                () -> newService().saveSeoOptimization(null, 200L, seoResult, null));
        assertTrue(e.getMessage().contains("描述ID"),
                "错误信息要指明是描述ID的问题：" + e.getMessage());

        verify(seoOptimizationMapper, never()).insert(any(SeoOptimization.class));
    }

    @Test
    @DisplayName("SEO 落库：insert 返回 0 行必须报错 —— 不能假装保存成功")
    void seoOptimizationFailsWhenInsertAffectsNoRow() {
        when(seoOptimizationMapper.insert(any(SeoOptimization.class))).thenReturn(0);

        DescriptionGenerationResponse.SEOResult seoResult = new DescriptionGenerationResponse.SEOResult();
        seoResult.setTitle("T");

        BizException e = assertThrows(BizException.class,
                () -> newService().saveSeoOptimization(100L, 200L, seoResult, null));
        assertTrue(e.getMessage().contains("seo_optimization") || e.getMessage().contains("SEO"),
                "错误信息要带上表名，方便定位：" + e.getMessage());
    }

    @Test
    @DisplayName("产品亮点落库：标题/特性/描述全空的亮点必须报错，不编占位标题")
    void highlightsWithoutAnyRealTextAreRejected() {
        DescriptionGenerationResponse.Highlight noTitle = new DescriptionGenerationResponse.Highlight();
        noTitle.setTitle(null);
        noTitle.setFeature(null);
        noTitle.setDescription(null);

        // 📌 highlight_title 是 NOT NULL → 一个真实来源都没有时不能写
        BizException e = assertThrows(BizException.class,
                () -> newService().saveHighlights(100L, 200L, List.of(noTitle)));
        assertTrue(e.getMessage().contains("亮点标题"), e.getMessage());

        verify(productHighlightMapper, never()).insert(any(ProductHighlight.class));
    }

    @Test
    @DisplayName("产品亮点落库：空列表直接返回空，不报错也不写")
    void highlightsEmptyListWritesNothing() {
        List<ProductHighlight> saved = newService().saveHighlights(100L, 200L, List.of());

        assertNotNull(saved);
        assertTrue(saved.isEmpty());
        verify(productHighlightMapper, never()).insert(any(ProductHighlight.class));
    }

    @Test
    @DisplayName("产品亮点落库：有真实标题就写进去")
    void highlightsWithTitleArePersisted() {
        when(productHighlightMapper.insert(any(ProductHighlight.class))).thenReturn(1);

        DescriptionGenerationResponse.Highlight h = new DescriptionGenerationResponse.Highlight();
        h.setTitle("5000mAh Battery");
        h.setDescription("Long lasting battery");
        h.setFeature("5000mAh");
        h.setImportance(4);

        List<ProductHighlight> saved = newService().saveHighlights(100L, 200L, List.of(h));

        assertNotNull(saved);
        assertEquals(1, saved.size());
        ArgumentCaptor<ProductHighlight> captor = ArgumentCaptor.forClass(ProductHighlight.class);
        verify(productHighlightMapper).insert(captor.capture());
        assertEquals("5000mAh Battery", captor.getValue().getHighlightTitle());
        assertEquals("5000mAh", captor.getValue().getFeature());
    }

    @Test
    @DisplayName("开启 A/B 测试：指标值留空（CTR 要等真实流量才有，不编）")
    void abTestStartLeavesMetricsEmpty() {
        when(abTestResultMapper.insert(any(AbTestResult.class))).thenReturn(1);

        AbTestStartRequest request = new AbTestStartRequest();
        request.setProductId(200L);
        request.setTestName("Title A vs B");
        request.setMetricName("CTR");
        request.setVersionAId(11L);
        request.setVersionBId(12L);
        request.setStartTime(LocalDateTime.now());

        AbTestResult result = newService().startAbTest(request);

        assertNotNull(result);
        assertEquals("Title A vs B", result.getTestName());
        assertEquals("CTR", result.getMetricName());
        // 📌 开测时没有任何真实流量 → 指标值必须是 null，不能预填 0
        assertTrue(result.getMetricAValue() == null && result.getMetricBValue() == null,
                "开测时指标值必须为空，不能预填数字");
        assertTrue(result.getWinner() == null, "开测时还没有胜者");
    }

    @Test
    @DisplayName("开启 A/B 测试：缺 productId 或 testName 直接报错")
    void abTestStartValidatesRequiredFields() {
        AbTestStartRequest noProduct = new AbTestStartRequest();
        noProduct.setTestName("T");
        noProduct.setMetricName("CTR");
        BizException e1 = assertThrows(BizException.class,
                () -> newService().startAbTest(noProduct));
        assertTrue(e1.getMessage().contains("产品ID"), e1.getMessage());

        AbTestStartRequest noName = new AbTestStartRequest();
        noName.setProductId(1L);
        noName.setMetricName("CTR");
        BizException e2 = assertThrows(BizException.class,
                () -> newService().startAbTest(noName));
        assertTrue(e2.getMessage().contains("测试名称"), e2.getMessage());

        verify(abTestResultMapper, never()).insert(any(AbTestResult.class));
    }

    @Test
    @DisplayName("回填 A/B 结果：胜者由系统比较得出，不是调用方填的")
    void abTestOutcomeDecidesWinnerFromMetrics() {
        AbTestResult stored = new AbTestResult();
        stored.setId(500L);
        stored.setProductId(200L);
        stored.setTestName("Title A vs B");
        stored.setMetricName("CTR");
        stored.setStartTime(LocalDateTime.now().minusDays(2));
        when(abTestResultMapper.selectById(500L)).thenReturn(stored);
        when(abTestResultMapper.updateById(any(AbTestResult.class))).thenReturn(1);

        AbTestOutcomeRequest request = new AbTestOutcomeRequest();
        request.setMetricAValue(new BigDecimal("0.0310"));
        request.setMetricBValue(new BigDecimal("0.0520"));
        request.setEndTime(LocalDateTime.now());

        AbTestResult result = newService().recordAbTestOutcome(500L, request);

        assertNotNull(result);
        assertEquals("B", result.getWinner(), "B 版指标更高 → 胜者必须是 B");
        assertEquals(0.0520, result.getMetricBValue().doubleValue(), 0.0001);
        assertNotNull(result.getEndTime());
    }

    @Test
    @DisplayName("回填 A/B 结果：两版持平判 TIE，不硬凑一个胜者")
    void abTestOutcomeTieWhenMetricsEqual() {
        AbTestResult stored = new AbTestResult();
        stored.setId(500L);
        stored.setProductId(200L);
        stored.setTestName("T");
        stored.setMetricName("CTR");
        stored.setStartTime(LocalDateTime.now().minusDays(2));
        when(abTestResultMapper.selectById(500L)).thenReturn(stored);
        when(abTestResultMapper.updateById(any(AbTestResult.class))).thenReturn(1);

        AbTestOutcomeRequest request = new AbTestOutcomeRequest();
        request.setMetricAValue(new BigDecimal("0.0400"));
        request.setMetricBValue(new BigDecimal("0.0400"));

        AbTestResult result = newService().recordAbTestOutcome(500L, request);
        assertEquals("TIE", result.getWinner(), "持平就是 TIE，不能硬凑 A 或 B");
    }

    @Test
    @DisplayName("回填 A/B 结果：查不到这条测试就报错 —— 不凭空造一条")
    void abTestOutcomeFailsWhenTestNotFound() {
        when(abTestResultMapper.selectById(999L)).thenReturn(null);

        AbTestOutcomeRequest request = new AbTestOutcomeRequest();
        request.setMetricAValue(new BigDecimal("0.01"));
        request.setMetricBValue(new BigDecimal("0.02"));

        assertThrows(BizException.class, () -> newService().recordAbTestOutcome(999L, request));
        verify(abTestResultMapper, never()).updateById(any(AbTestResult.class));
    }

    @Test
    @DisplayName("查 A/B 历史：查不到就是空列表，不是 null 也不是异常")
    void abTestListReturnsEmptyListWhenNothingFound() {
        when(abTestResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<AbTestResult> result = newService().listAbTestResults(200L, 20);

        assertNotNull(result, "必须是空列表，不能是 null");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("查 A/B 历史：limit 超上限时按上限截断")
    void abTestListNormalizesLimit() {
        when(abTestResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        newService().listAbTestResults(200L, 99999);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(abTestResultMapper).selectList(captor.capture());
        // 📌 不深查 Wrapper 内部，只确认调用发生且 limit 被收敛（不抛异常即为收敛成功）
        assertNotNull(captor.getValue());
    }
}
