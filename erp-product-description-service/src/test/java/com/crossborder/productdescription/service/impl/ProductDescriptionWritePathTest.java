package com.crossborder.productdescription.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.dto.AbTestOutcomeRequest;
import com.crossborder.productdescription.dto.AbTestStartRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.AbTestResult;
import com.crossborder.productdescription.entity.ProductBrief;
import com.crossborder.productdescription.entity.ProductDescription;
import com.crossborder.productdescription.entity.SeoOptimization;
import com.crossborder.productdescription.service.DescriptionAnalysisRecordService;
import com.crossborder.productdescription.service.DescriptionGenerationTaskService;
import com.crossborder.productdescription.service.DescriptionTemplateRenderer;
import com.crossborder.productdescription.service.DescriptionTemplateService;
import com.crossborder.productdescription.service.ProductCatalogService;
import com.crossborder.productdescription.service.ProductDescriptionRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写路径接线测试 —— 证明「算完就丢」这个缺陷真的被修掉了
 *
 * <p>🔴 <b>这个测试守护的是什么缺陷</b>（2026-11-28 实测）：
 * <pre>
 * optimizeSEO() / extractHighlights() / generateABTestVersions()
 * 每次都<b>现算一遍就丢掉</b> → 结果只活在一次 HTTP 响应里
 * → 三张表（seo_optimization / product_highlights / ab_test_result）
 *    <b>有 DDL、有索引，但永远是空的</b>
 * </pre>
 *
 * <p>📌 <b>为什么不能只测 Service 层</b>：光测
 * {@code DescriptionAnalysisRecordServiceImpl} 只能证明「它会写库」，
 * <b>不能证明「真的有人调它」</b> —— 而「方法写了但没有运行期调用点」
 * 正是本仓库反复出现过的真实缺陷形态（见
 * {@code LLMConfig.isSupportedProvider()}：被单测调用过，运行期从没执行过）。
 * → 所以这里<b>从 Service 的公开入口打进去</b>，断言落库调用真的发生。</p>
 *
 * <p>📌 <b>为什么用 {@code MockitoSettings} 的 LENIENT</b>：
 * 被测类内部会调很多协作者（LLM、任务记录、商品目录），
 * 本测试只关心「有没有真的落库」，其余调用不逐一 stub。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("写路径接线：算出来的结果不再只活在一次响应里")
class ProductDescriptionWritePathTest {

    @Mock
    private ProductDescriptionRecordService productDescriptionRecordService;

    @Mock
    private ProductCatalogService productCatalogService;

    @Mock
    private DescriptionGenerationTaskService descriptionGenerationTaskService;

    @Mock
    private DescriptionTemplateService descriptionTemplateService;

    @Mock
    private DescriptionTemplateRenderer descriptionTemplateRenderer;

    @Mock
    private DescriptionAnalysisRecordService descriptionAnalysisRecordService;

    @InjectMocks
    private ProductDescriptionServiceImpl service;

    // ---------------------------------------------------------------
    // 公共装配
    // ---------------------------------------------------------------

    private ProductDescription savedDescription(long id) {
        ProductDescription d = new ProductDescription();
        d.setId(id);
        d.setProductId(200L);
        d.setProductName("Test Phone");
        d.setContent("Original description content");
        d.setLanguage("en");
        d.setPlatform("Amazon");
        return d;
    }

    private void givenExistingDescription() {
        when(productDescriptionRecordService.listByProductId(200L))
                .thenReturn(List.of(savedDescription(100L)));
    }

    private void givenNoDescription() {
        when(productDescriptionRecordService.listByProductId(200L)).thenReturn(List.of());
    }

    /**
     * 让商品主数据里有（/没有）真实描述
     *
     * <p>📌 亮点是从**商品真实描述**里切出来的（getProductFeatures → splitIntoPhrases），
     * 所以想验「亮点会落库」，必须先给一段真实描述。</p>
     */
    private void givenProductDescription(String description) {
        ProductBrief brief = new ProductBrief();
        brief.setId(200L);
        brief.setProductName("Test Phone");
        brief.setDescription(description);
        when(productCatalogService.findById(200L)).thenReturn(Optional.of(brief));
    }

    // ---------------------------------------------------------------
    // SEO 写路径
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SEO 优化：算完之后要真的落库（表 seo_optimization）")
    void seoOptimizationResultIsPersisted() {
        givenExistingDescription();

        DescriptionGenerationResponse.SEOResult seoResult = new DescriptionGenerationResponse.SEOResult();
        seoResult.setTitle("Optimized");
        seoResult.setSeoscore(90.0);

        when(descriptionAnalysisRecordService.saveSeoOptimization(
                any(), any(), any(), any())).thenReturn(new SeoOptimization());

        service.optimizeSEO(200L, "Amazon");

        // 📌 核心断言：写路径被调用了，且带的是真实的 descriptionId
        verify(descriptionAnalysisRecordService).saveSeoOptimization(
                eq(100L), eq(200L), any(DescriptionGenerationResponse.SEOResult.class), any());
    }

    @Test
    @DisplayName("SEO 优化：没有可关联的描述行时不写库（不制造悬空引用）")
    void seoOptimizationSkippedWhenNoDescriptionRow() {
        givenNoDescription();

        service.optimizeSEO(200L, "Amazon");

        // 📌 没有 descriptionId → 不写（seo_optimization.description_id 是 NOT NULL）
        verify(descriptionAnalysisRecordService, never())
                .saveSeoOptimization(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SEO 优化：落库失败不能把分析接口打成 500（结果已经返回给调用方了）")
    void seoOptimizationPersistenceFailureDoesNotBreakTheApi() {
        givenExistingDescription();

        DescriptionGenerationResponse.SEOResult seoResult = new DescriptionGenerationResponse.SEOResult();
        seoResult.setTitle("Optimized");
        seoResult.setSeoscore(90.0);
        when(descriptionAnalysisRecordService.saveSeoOptimization(any(), any(), any(), any()))
                .thenThrow(new BizException("数据库炸了"));

        // 📌 关键：分析结果照样返回，不能 500
        DescriptionGenerationResponse.SEOResult result = service.optimizeSEO(200L, "Amazon");
        assertNotNull(result, "落库失败也不能让已经算出来的结果丢掉");
    }

    // ---------------------------------------------------------------
    // 亮点写路径
    // ---------------------------------------------------------------

    @Test
    @DisplayName("提炼亮点：算完之后要真的落库（表 product_highlights）")
    void highlightsArePersisted() {
        givenExistingDescription();
        // 📌 亮点来自**商品真实描述**（getProductFeatures → splitIntoPhrases），
        //    没有真实描述就提炼不出亮点 → 也就没什么可落库。
        givenProductDescription("5000mAh battery, 108MP camera, fast charging");
        when(descriptionAnalysisRecordService.saveHighlights(any(), any(), any()))
                .thenReturn(List.of());

        service.extractHighlights(200L, 3);

        verify(descriptionAnalysisRecordService).saveHighlights(eq(100L), eq(200L), any());
    }

    @Test
    @DisplayName("提炼亮点：标题必须是真实特性，不能是「核心优势 N」这种占位符")
    void highlightTitleIsRealFeatureNotPlaceholder() {
        givenExistingDescription();
        givenProductDescription("5000mAh battery, 108MP camera");
        when(descriptionAnalysisRecordService.saveHighlights(any(), any(), any()))
                .thenReturn(List.of());

        List<DescriptionGenerationResponse.Highlight> highlights =
                service.extractHighlights(200L, 3);

        assertNotNull(highlights);
        assertTrue(highlights.size() > 0, "有真实描述就该提炼出亮点");
        for (DescriptionGenerationResponse.Highlight h : highlights) {
            assertTrue(h.getTitle() == null || !h.getTitle().startsWith("核心优势"),
                    "亮点标题不能是占位符「核心优势 N」，真实值是：" + h.getTitle());
        }
    }

    @Test
    @DisplayName("提炼亮点：没有真实描述时什么都不写（不编亮点）")
    void highlightsNotWrittenWithoutRealDescription() {
        givenExistingDescription();
        givenProductDescription(null);

        service.extractHighlights(200L, 3);

        verify(descriptionAnalysisRecordService, never()).saveHighlights(any(), any(), any());
    }

    // ---------------------------------------------------------------
    // A/B 测试写路径
    // ---------------------------------------------------------------

    @Test
    @DisplayName("开启 A/B 测试：必须真的落库（表 ab_test_result）")
    void startAbTestIsPersisted() {
        AbTestStartRequest request = new AbTestStartRequest();
        request.setProductId(200L);
        request.setTestName("Title A vs B");
        request.setMetricName("CTR");
        request.setStartTime(LocalDateTime.now());

        AbTestResult expected = new AbTestResult();
        expected.setId(1L);
        when(descriptionAnalysisRecordService.startAbTest(request)).thenReturn(expected);

        AbTestResult result = service.startAbTest(request);

        assertEquals(1L, result.getId());
        verify(descriptionAnalysisRecordService).startAbTest(request);
    }

    @Test
    @DisplayName("回填 A/B 结果：必须真的落库")
    void recordAbTestOutcomeIsPersisted() {
        AbTestOutcomeRequest request = new AbTestOutcomeRequest();
        request.setMetricAValue(new BigDecimal("0.01"));
        request.setMetricBValue(new BigDecimal("0.02"));

        AbTestResult expected = new AbTestResult();
        expected.setId(1L);
        expected.setWinner("B");
        when(descriptionAnalysisRecordService.recordAbTestOutcome(1L, request)).thenReturn(expected);

        AbTestResult result = service.recordAbTestOutcome(1L, request);

        assertEquals("B", result.getWinner());
        verify(descriptionAnalysisRecordService).recordAbTestOutcome(1L, request);
    }

    @Test
    @DisplayName("查 A/B 历史：委托给落库服务查，查不到就是空列表")
    void listAbTestResultsDelegatesToRecordService() {
        when(descriptionAnalysisRecordService.listAbTestResults(200L, 20)).thenReturn(List.of());

        List<AbTestResult> result = service.listAbTestResults(200L, 20);

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(descriptionAnalysisRecordService).listAbTestResults(200L, 20);
    }
}
