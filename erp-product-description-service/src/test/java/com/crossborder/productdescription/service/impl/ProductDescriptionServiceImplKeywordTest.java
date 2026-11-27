package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.service.DescriptionGenerationTaskService;
import com.crossborder.productdescription.service.ProductCatalogService;
import com.crossborder.productdescription.service.ProductDescriptionRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * SEO 关键词抽取的单元测试
 *
 * <p>📌 **这个测试是为了守住一个真缺陷**：原 {@code extractKeywords} 只认
 * <b>11 个硬编码中文词</b> → 英文描述（跨境电商主力语言）**恒返回空**
 * → 页面上的「SEO 优化」在英文场景<b>完全不可用</b>（实测：真起容器 + 真 LLM，
 * {@code language=en} → {@code seoKeywords=[]}）。</p>
 */
@DisplayName("ProductDescriptionServiceImpl SEO 关键词抽取")
class ProductDescriptionServiceImplKeywordTest {

    private List<String> extractKeywords(String text) {
        // 📌 抽取关键词是**纯函数** → 依赖全用 mock（不碰 LLM / 不碰 DB）
        ProductDescriptionServiceImpl service = new ProductDescriptionServiceImpl(
                mock(LLMConfig.class),
                mock(ProductDescriptionRecordService.class),
                mock(ProductCatalogService.class),
                mock(DescriptionGenerationTaskService.class),
                // 📌 2026-11-26：模板服务（2026-11-24 引入）—— 关键词抽取是纯函数，与模板无关。
                mock(com.crossborder.productdescription.service.DescriptionTemplateService.class),
                mock(com.crossborder.productdescription.service.DescriptionTemplateRenderer.class));
        return ReflectionTestUtils.invokeMethod(service, "extractKeywords", text);
    }

    @Test
    @DisplayName("英文描述能抽出关键词（原本恒空 → 英文场景 SEO 不可用）")
    void englishDescriptionYieldsKeywords() {
        String description = """
                ### Product Title
                **Fast Charging Power Bank – Portable Charger for Smartphones**
                Upgrade your everyday experience with this compact power bank.
                The durable design delivers reliable charging for your devices.
                Built with premium materials for long lasting performance.
                """;

        List<String> keywords = extractKeywords(description);

        assertNotNull(keywords);
        assertFalse(keywords.isEmpty(), "英文描述必须能抽出关键词，否则英文场景的 SEO 优化形同虚设");
        // 高频的商品属性词应该在列表里
        assertTrue(keywords.contains("power") || keywords.contains("bank")
                        || keywords.contains("charging") || keywords.contains("charger"),
                "应抽到商品相关词，实际=" + keywords);
        // 频次高的词排前面（power/charging 被反复提到）
        assertTrue(keywords.indexOf("power") >= 0 || keywords.indexOf("charging") >= 0,
                "应抽到 'power'/'charging' 这类被反复强调的词，实际=" + keywords);
    }

    @Test
    @DisplayName("英文停用词被过滤掉（功能词不进 SEO 关键词）")
    void englishStopWordsAreFiltered() {
        List<String> keywords = extractKeywords(
                "This is the product and it is for you with the durable design that you can use every day.");

        assertFalse(keywords.contains("the"));
        assertFalse(keywords.contains("and"));
        assertFalse(keywords.contains("with"));
        assertFalse(keywords.contains("that"));
        assertFalse(keywords.contains("you"));
        // 但商品属性词要留下
        assertTrue(keywords.contains("durable") || keywords.contains("design"),
                "商品属性词应保留，实际=" + keywords);
    }

    @Test
    @DisplayName("中文描述仍走原有的中文词表（不回归）")
    void chineseDescriptionStillMatchesChineseWordList() {
        List<String> keywords = extractKeywords(
                "这是一款品质可靠的产品，性能稳定，工艺耐用，是理想的选择。");

        assertTrue(keywords.contains("产品"), "中文词表应仍然命中，实际=" + keywords);
        assertTrue(keywords.contains("性能"));
        assertTrue(keywords.contains("可靠"));
    }

    @Test
    @DisplayName("null / 空白输入返回空列表（不 NPE）")
    void nullAndBlankReturnEmpty() {
        assertTrue(extractKeywords(null).isEmpty());
        assertTrue(extractKeywords("   ").isEmpty());
        assertTrue(extractKeywords("").isEmpty());
    }

    @Test
    @DisplayName("方括号占位符里的词不进 SEO 关键词（模板占位符不是真实关键词）")
    void placeholderWordsAreNotKeywords() {
        // 📌 真 LLM 在**没有真实商品数据**时返回带占位符的模板：
        //   实测这一轮抽出来的前 5 个关键词（material/key/size/feature/capacity）
        //   **全部来自占位符** → 全是假的。
        String description = """
                ### Amazon Product Description Template
                **Professional [Product Name]**
                Built with **[material/technology]**, it combines **[key feature 1]**,
                **[key feature 2]** to provide reliable performance.
                Size/Capacity: **[Size/Capacity]**
                """;

        List<String> keywords = extractKeywords(description);

        for (String placeholder : List.of("material", "technology", "key", "feature",
                "size", "capacity", "product", "name")) {
            assertFalse(keywords.contains(placeholder),
                    "占位符里的词 '" + placeholder + "' 不该进 SEO 关键词，实际=" + keywords);
        }
    }

    @Test
    @DisplayName("结果去重且稳定（同一输入两次调用结果一致）")
    void resultIsDeduplicatedAndStable() {
        String text = "charging cable for charging phone, durable charging cable";
        List<String> first = extractKeywords(text);
        List<String> second = extractKeywords(text);

        assertEquals(first, second, "同一输入必须得到稳定输出");
        assertEquals(first.size(), first.stream().distinct().count(), "关键词必须去重");
        assertEquals(0, first.indexOf("charging"), "频次最高的词应排第一");
    }

    @Test
    @DisplayName("markdown 结构标记词不进 SEO 关键词（bullet/points/search/title）")
    void markdownStructureWordsAreNotKeywords() {
        // 📌 真 LLM 每次输出的结构不一样 → 结构标记词会污染词频
        String description = """
                ### Product Title
                **Fast Charging Power Bank**
                Bullet Points:
                - durable charging
                - compact design
                Search: portable power bank
                This durable charging design fits every travel bag and daily commute.
                """;

        List<String> keywords = extractKeywords(description);

        for (String noise : List.of("bullet", "points", "search", "title", "heading")) {
            assertFalse(keywords.contains(noise), "结构标记词 '" + noise + "' 不该进 SEO 关键词，实际=" + keywords);
        }
        assertTrue(keywords.contains("durable") || keywords.contains("charging"),
                "正文里的商品词仍应保留，实际=" + keywords);
    }
}
