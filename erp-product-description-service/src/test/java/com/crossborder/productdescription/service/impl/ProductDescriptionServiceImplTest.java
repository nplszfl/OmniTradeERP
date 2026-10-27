package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.service.ProductDescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 产品描述生成服务测试
 */
class ProductDescriptionServiceImplTest {

    private ProductDescriptionService productDescriptionService;

    @BeforeEach
    void setUp() {
        // LLM 未配置时走模拟生成（原先字段注入在非 Spring 环境下为 null，
        // 导致 callLLMAPI 抛 NPE 被 catch 吞掉，响应变成「生成失败」）
        productDescriptionService = new ProductDescriptionServiceImpl(new LLMConfig());
    }

    @Test
    @DisplayName("测试生成产品描述 - 基本场景")
    void testGenerateDescription_BasicScenario() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(1L);
        request.setProductName("无线蓝牙耳机");
        request.setTargetPlatform("amazon");
        request.setTargetLanguage("zh");
        request.setFeatures(Arrays.asList("主动降噪", "30小时续航", "Hi-Fi音质"));
        request.setKeywords(Arrays.asList("蓝牙耳机", "降噪", "Hi-Fi"));

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getDescriptionId());
        assertEquals(1L, response.getProductId());
        assertEquals("无线蓝牙耳机", response.getProductName());
        assertNotNull(response.getContent());
        assertTrue(response.getContent().contains("无线蓝牙耳机"));
    }

    @Test
    @DisplayName("测试生成产品描述 - 启用SEO优化")
    void testGenerateDescription_WithSEO() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(2L);
        request.setProductName("智能手表");
        request.setTargetPlatform("shopee");
        request.setFeatures(Arrays.asList("心率监测", "睡眠追踪", "防水"));
        request.setKeywords(Arrays.asList("智能手表", "健康"));
        request.setEnableSEO(true);

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getSeoResult());
        assertNotNull(response.getSeoResult().getTitle());
        assertNotNull(response.getSeoResult().getDescription());
        assertNotNull(response.getSeoResult().getSeoscore());
    }

    @Test
    @DisplayName("测试生成产品描述 - 提取亮点")
    void testGenerateDescription_ExtractHighlights() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(3L);
        request.setProductName("便携音箱");
        request.setFeatures(Arrays.asList("360度环绕声", "防水防尘", "12小时续航", "内置麦克风"));
        request.setExtractHighlights(true);

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getHighlights());
        assertTrue(response.getHighlights().size() > 0);
    }

    @Test
    @DisplayName("测试生成产品描述 - 生成短标题")
    void testGenerateDescription_ShortTitle() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(4L);
        request.setProductName("超长待机的无线蓝牙降噪耳机");
        request.setGenerateShortTitle(true);

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getShortTitle());
        assertTrue(response.getShortTitle().length() <= 20);
    }

    @Test
    @DisplayName("测试生成产品描述 - Token使用统计")
    void testGenerateDescription_TokenUsage() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(5L);
        request.setProductName("测试产品");
        request.setFeatures(Arrays.asList("特性1", "特性2"));

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getUsage());
        assertTrue(response.getUsage().getPromptTokens() > 0);
        assertTrue(response.getUsage().getCompletionTokens() > 0);
        assertTrue(response.getUsage().getTotalTokens() > 0);
    }

    @Test
    @DisplayName("测试批量生成产品描述")
    void testBatchGenerateDescription() {
        // 准备测试数据
        DescriptionGenerationRequest request1 = new DescriptionGenerationRequest();
        request1.setProductId(1L);
        request1.setProductName("产品A");
        request1.setFeatures(Arrays.asList("特性A1", "特性A2"));

        DescriptionGenerationRequest request2 = new DescriptionGenerationRequest();
        request2.setProductId(2L);
        request2.setProductName("产品B");
        request2.setFeatures(Arrays.asList("特性B1", "特性B2"));

        // 执行测试
        List<DescriptionGenerationResponse> responses = 
                productDescriptionService.batchGenerateDescription(Arrays.asList(request1, request2));

        // 验证结果
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertNotNull(responses.get(0).getContent());
        assertNotNull(responses.get(1).getContent());
    }

    @Test
    @DisplayName("测试多语言描述生成")
    void testGenerateMultiLanguageDescription() {
        // 执行测试
        List<DescriptionGenerationResponse> responses = 
                productDescriptionService.generateMultiLanguageDescription(
                        1L, Arrays.asList("zh", "en", "ja"));

        // 验证结果
        assertNotNull(responses);
        assertEquals(3, responses.size());
    }

    @Test
    @DisplayName("测试SEO优化")
    void testOptimizeSEO() {
        // 执行测试
        DescriptionGenerationResponse.SEOResult seoResult = 
                productDescriptionService.optimizeSEO(1L, "amazon");

        // 验证结果
        assertNotNull(seoResult);
        assertNotNull(seoResult.getTitle());
        assertNotNull(seoResult.getDescription());
        assertNotNull(seoResult.getPrimaryKeywords());
        assertNotNull(seoResult.getKeywordDensity());
        assertNotNull(seoResult.getReadabilityScore());
        assertNotNull(seoResult.getSeoscore());
        assertNotNull(seoResult.getSuggestions());
    }

    @Test
    @DisplayName("测试提炼产品亮点")
    void testExtractHighlights() {
        // 执行测试
        List<DescriptionGenerationResponse.Highlight> highlights = 
                productDescriptionService.extractHighlights(1L, 3);

        // 验证结果
        assertNotNull(highlights);
        assertTrue(highlights.size() <= 3);
    }

    @Test
    @DisplayName("测试生成A/B测试版本")
    void testGenerateABTestVersions() {
        // 执行测试
        List<DescriptionGenerationResponse> versions = 
                productDescriptionService.generateABTestVersions(1L, 3);

        // 验证结果
        assertNotNull(versions);
        assertEquals(3, versions.size());
        
        // 验证版本号不同
        for (int i = 0; i < versions.size(); i++) {
            assertEquals(i + 1, versions.get(i).getVersionNumber());
        }
    }

    @Test
    @DisplayName("测试保存产品描述")
    void testSaveDescription() {
        // 准备测试数据
        DescriptionGenerationResponse response = new DescriptionGenerationResponse();
        response.setProductId(1L);
        response.setProductName("测试产品");
        response.setContent("测试内容");

        // 执行测试
        Boolean result = productDescriptionService.saveDescription(response);

        // 验证结果
        assertNotNull(result);
        assertTrue(result);
    }

    @Test
    @DisplayName("测试获取产品所有描述")
    void testGetProductDescriptions() {
        // 执行测试
        List<DescriptionGenerationResponse> descriptions = 
                productDescriptionService.getProductDescriptions(1L);

        // 验证结果
        assertNotNull(descriptions);
        assertTrue(descriptions.size() > 0);
    }

    @Test
    @DisplayName("测试批量导入产品")
    void testBatchImportProducts() {
        // 准备测试数据
        List<Long> productIds = Arrays.asList(1L, 2L, 3L);

        // 执行测试
        String result = productDescriptionService.batchImportProducts(productIds);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains("成功"));
        assertTrue(result.contains("失败"));
    }

    @Test
    @DisplayName("测试语言检测 - 中文")
    void testLanguageDetection_Chinese() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductName("无线蓝牙耳机");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertEquals("zh", response.getLanguage());
    }

    @Test
    @DisplayName("测试语言检测 - 英文")
    void testLanguageDetection_English() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductName("Wireless Bluetooth Headphones");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertEquals("en", response.getLanguage());
    }

    @Test
    @DisplayName("测试多平台支持 - Amazon")
    void testPlatform_Amazon() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(1L);
        request.setProductName("测试产品");
        request.setTargetPlatform("amazon");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertEquals("amazon", response.getPlatform());
    }

    @Test
    @DisplayName("测试多平台支持 - eBay")
    void testPlatform_Ebay() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(1L);
        request.setProductName("测试产品");
        request.setTargetPlatform("ebay");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertEquals("ebay", response.getPlatform());
    }

    @Test
    @DisplayName("测试多平台支持 - Shopee")
    void testPlatform_Shopee() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(1L);
        request.setProductName("测试产品");
        request.setTargetPlatform("shopee");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertEquals("shopee", response.getPlatform());
    }

    @Test
    @DisplayName("测试生成时长记录")
    void testGenerationTime() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(1L);
        request.setProductName("测试产品");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        // 模拟生成可能在 1ms 内完成，耗时为 0 属于正常，只校验已记录且非负
        assertNotNull(response.getGenerationTime());
        assertTrue(response.getGenerationTime() >= 0);
    }

    @Test
    @DisplayName("测试版本号递增")
    void testVersionNumber() {
        // 准备测试数据
        DescriptionGenerationRequest request = new DescriptionGenerationRequest();
        request.setProductId(1L);
        request.setProductName("测试产品");

        // 执行测试
        DescriptionGenerationResponse response = productDescriptionService.generateDescription(request);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getVersionNumber() >= 1);
    }
}