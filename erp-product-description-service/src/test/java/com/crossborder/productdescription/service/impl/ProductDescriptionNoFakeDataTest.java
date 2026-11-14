package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.entity.ProductBrief;
import com.crossborder.productdescription.service.ProductCatalogService;
import com.crossborder.productdescription.service.ProductDescriptionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「去假数据」回归测试（2026-11-15）。
 *
 * <p>📌 <b>为什么要这道测试</b>：
 * commit {@code 646dc06}「去假数据」重构清了 pricing 的内存存储、
 * AI 知识库的硬编码，但<b>漏了 product-description</b> ——
 * {@code getProductFeatures} / {@code getProductKeywords} / {@code getProductDescription}
 * 三个方法全是<b>硬编码常量</b>，<b>完全忽略 productId</b>。
 * 实测 {@code GET /api/description/highlights/1} 与 {@code /999}
 * 返回<b>一字不差</b>的 5 条「高性能 / 稳定可靠 / …」。</p>
 *
 * <p>📌 <b>不变式</b>（三条）：
 * <ol>
 *   <li><b>不同商品 → 不同结果</b>（特性/关键词来自各自的真实描述）</li>
 *   <li><b>商品没有描述 → 返回空</b>（<b>不是</b>编 5 条亮点）</li>
 *   <li><b>描述为 null → SEO 接口不 NPE</b>（原来 {@code text.contains(word)} 会炸）</li>
 * </ol>
 */
class ProductDescriptionNoFakeDataTest {

    private ProductDescriptionServiceImpl service;
    private ProductCatalogService catalogService;

    @BeforeEach
    void setUp() {
        ProductDescriptionRecordService recordService = mock(ProductDescriptionRecordService.class);
        catalogService = mock(ProductCatalogService.class);

        when(recordService.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recordService.listByProductId(anyLong())).thenReturn(List.of());

        service = new ProductDescriptionServiceImpl(
                new LLMConfig(), recordService, catalogService);
    }

    /** 让 id → (商品名, 描述) 的映射可配 */
    private void catalog(Long id, String name, String description) {
        ProductBrief brief = new ProductBrief();
        brief.setId(id);
        brief.setProductName(name);
        brief.setDescription(description);
        when(catalogService.findById(id)).thenReturn(Optional.of(brief));
    }

    @Test
    @DisplayName("不同商品的亮点必须不同 —— 特性来自各自的真实描述，不是硬编码常量")
    void extractHighlights_differentProducts_returnDifferentResults() {
        catalog(1L, "蓝牙耳机", "降噪深度 45dB，续航 30 小时，佩戴舒适");
        catalog(2L, "充电宝", "20000mAh 大容量，支持 65W 快充，三口输出");

        List<DescriptionGenerationResponse.Highlight> h1 = service.extractHighlights(1L, 5);
        List<DescriptionGenerationResponse.Highlight> h2 = service.extractHighlights(2L, 5);

        assertFalse(h1.isEmpty(), "商品1 有真实描述 → 应能提炼出亮点");
        assertFalse(h2.isEmpty(), "商品2 有真实描述 → 应能提炼出亮点");

        String text1 = h1.toString();
        String text2 = h2.toString();
        assertNotEquals(text1, text2,
                "两个不同商品的亮点内容**必须不同** —— 相同说明还在返回硬编码常量");
        assertTrue(text1.contains("降噪"), "商品1 的亮点应含自己描述里的词，实际=" + text1);
        assertTrue(text2.contains("快充"), "商品2 的亮点应含自己描述里的词，实际=" + text2);
    }

    @Test
    @DisplayName("商品没有描述 → 亮点返回空列表（不编 5 条假亮点）")
    void extractHighlights_noDescription_returnsEmptyNotFake() {
        catalog(999L, "没写描述的商品", null);

        List<DescriptionGenerationResponse.Highlight> highlights = service.extractHighlights(999L, 5);

        assertTrue(highlights == null || highlights.isEmpty(),
                "没有真实描述时必须返回空，不能编 —— 实际=" + highlights);
    }

    @Test
    @DisplayName("商品查不到 → 亮点返回空列表，不抛异常也不编")
    void extractHighlights_productNotFound_returnsEmptyNotFake() {
        when(catalogService.findById(123456L)).thenReturn(Optional.empty());

        List<DescriptionGenerationResponse.Highlight> highlights = service.extractHighlights(123456L, 5);

        assertTrue(highlights == null || highlights.isEmpty(),
                "商品不存在时必须返回空，不能编 —— 实际=" + highlights);
    }

    @Test
    @DisplayName("SEO 优化：描述为 null 也不 NPE（原 text.contains 会炸）")
    void optimizeSEO_nullDescription_doesNotThrowNpe() {
        catalog(7L, "没写描述的商品", null);

        DescriptionGenerationResponse.SEOResult result = assertDoesNotThrow(
                () -> service.optimizeSEO(7L, "amazon"),
                "描述为 null 时 SEO 接口不得抛 NPE");

        assertNotNull(result, "应返回一个结果对象（内容为空，而不是异常）");
    }
}
