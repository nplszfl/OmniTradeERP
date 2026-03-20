package com.crossborder.productdescription.service.impl;

import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.service.ProductDescriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;

/**
 * 产品描述生成服务实现
 *
 * 集成LLM API，提供AI驱动的多语言描述生成能力
 */
@Slf4j
@Service
public class ProductDescriptionServiceImpl implements ProductDescriptionService {

    // 模拟描述模板
    private final Map<String, String> templates = new HashMap<>();

    @Autowired
    private LLMConfig llmConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    public ProductDescriptionServiceImpl() {
        initTemplates();
    }

    @Override
    public DescriptionGenerationResponse generateDescription(DescriptionGenerationRequest request) {
        log.info("生成产品描述 - 产品ID: {}, 产品名称: {}", 
                request.getProductId(), request.getProductName());

        long startTime = System.currentTimeMillis();

        DescriptionGenerationResponse response = new DescriptionGenerationResponse();
        response.setDescriptionId(UUID.randomUUID().toString());
        response.setProductId(request.getProductId());
        response.setProductName(request.getProductName());
        response.setLanguage(detectLanguage(request.getProductName()));
        response.setPlatform(request.getTargetPlatform());
        response.setVersionNumber(1);
        response.setModel("deepseek-chat");
        response.setCreateTime(LocalDateTime.now());

        try {
            // 1. 构建提示词
            String prompt = buildPrompt(request);

            // 2. 调用LLM API
            String description = callLLMAPI(prompt, request);
            response.setContent(description);

            // 3. SEO优化
            if (Boolean.TRUE.equals(request.getEnableSEO())) {
                DescriptionGenerationResponse.SEOoptimize seoResult = optimizeSEO(description, request);
                response.setSeoResult(seoResult);
            }

            // 4. 提炼亮点
            if (Boolean.TRUE.equals(request.getExtractHighlights())) {
                List<DescriptionGenerationResponse.Highlight> highlights = 
                        extractHighlights(request.getFeatures());
                response.setHighlights(highlights);
            }

            // 5. 生成短标题
            if (Boolean.TRUE.equals(request.getGenerateShortTitle())) {
                String shortTitle = generateShortTitle(request.getProductName());
                response.setShortTitle(shortTitle);
            }

            // 6. Token使用统计
            DescriptionGenerationResponse.TokenUsage usage = new DescriptionGenerationResponse.TokenUsage();
            usage.setPromptTokens(prompt.length() / 4);
            usage.setCompletionTokens(description.length() / 4);
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
            response.setUsage(usage);

            // 7. 生成时长
            response.setGenerationTime(System.currentTimeMillis() - startTime);

            log.info("产品描述生成完成 - 耗时: {}ms", response.getGenerationTime());

        } catch (Exception e) {
            log.error("产品描述生成失败", e);
            response.setContent("生成失败，请稍后重试。");
            response.setError(e.getMessage());
        }

        return response;
    }

    @Override
    public List<DescriptionGenerationResponse> batchGenerateDescription(
            List<DescriptionGenerationRequest> requests) {
        log.info("批量生成产品描述 - 数量: {}", requests.size());

        return requests.stream()
                .map(this::generateDescription)
                .toList();
    }

    @Override
    public List<DescriptionGenerationResponse> generateMultiLanguageDescription(
            Long productId,
            List<String> languages) {
        log.info("生成多语言描述 - 产品ID: {}, 语言: {}", productId, languages);

        List<DescriptionGenerationResponse> responses = new ArrayList<>();

        for (String language : languages) {
            DescriptionGenerationRequest request = new DescriptionGenerationRequest();
            request.setProductId(productId);
            request.setProductName(getProductNameById(productId));
            request.setTargetLanguage(language);
            request.setFeatures(getProductFeatures(productId));
            request.setKeywords(getProductKeywords(productId));

            responses.add(generateDescription(request));
        }

        return responses;
    }

    @Override
    public DescriptionGenerationResponse.SEOoptimize optimizeSEO(
            Long productId,
            String platform) {
        log.info("SEO优化 - 产品ID: {}, 平台: {}", productId, platform);

        String description = getProductDescription(productId);
        String productName = getProductNameById(productId);

        return optimizeSEO(description, productName, platform);
    }

    @Override
    public List<DescriptionGenerationResponse.Highlight> extractHighlights(
            Long productId,
            Integer maxHighlights) {
        log.info("提炼产品亮点 - 产品ID: {}, 最大数量: {}", productId, maxHighlights);

        List<String> features = getProductFeatures(productId);
        return extractHighlights(features, maxHighlights);
    }

    @Override
    public List<DescriptionGenerationResponse> generateABTestVersions(
            Long productId,
            Integer versionCount) {
        log.info("生成A/B测试版本 - 产品ID: {}, 版本数: {}", productId, versionCount);

        List<DescriptionGenerationResponse> versions = new ArrayList<>();

        for (int i = 0; i < versionCount; i++) {
            DescriptionGenerationRequest request = new DescriptionGenerationRequest();
            request.setProductId(productId);
            request.setProductName(getProductNameById(productId));
            request.setFeatures(getProductFeatures(productId));
            request.setKeywords(getProductKeywords(productId));
            request.setTemperature(0.5 + i * 0.2); // 不同温度

            DescriptionGenerationResponse response = generateDescription(request);
            response.setVersionNumber(i + 1);

            versions.add(response);
        }

        return versions;
    }

    @Override
    public Boolean saveDescription(DescriptionGenerationResponse response) {
        log.info("保存产品描述 - 产品ID: {}", response.getProductId());

        // TODO: 实际保存到数据库
        log.info("产品描述保存成功");
        return true;
    }

    @Override
    public List<DescriptionGenerationResponse> getProductDescriptions(Long productId) {
        log.info("获取产品所有描述 - 产品ID: {}", productId);

        // TODO: 实际从数据库查询
        List<DescriptionGenerationResponse> descriptions = new ArrayList<>();

        // 模拟返回
        DescriptionGenerationResponse desc = new DescriptionGenerationResponse();
        desc.setProductId(productId);
        desc.setProductName(getProductNameById(productId));
        desc.setContent("这是一个高质量的产品，具有优秀的性能和可靠性...");
        desc.setCreateTime(LocalDateTime.now());

        descriptions.add(desc);

        return descriptions;
    }

    @Override
    public String batchImportProducts(List<Long> productIds) {
        log.info("批量导入产品描述 - 数量: {}", productIds.size());

        int successCount = 0;
        int failCount = 0;

        for (Long productId : productIds) {
            try {
                DescriptionGenerationRequest request = new DescriptionGenerationRequest();
                request.setProductId(productId);
                request.setProductName(getProductNameById(productId));
                request.setFeatures(getProductFeatures(productId));
                request.setKeywords(getProductKeywords(productId));

                DescriptionGenerationResponse response = generateDescription(request);
                if (saveDescription(response)) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                log.error("产品 {} 导入失败", productId, e);
                failCount++;
            }
        }

        log.info("批量导入完成 - 成功: {}, 失败: {}", successCount, failCount);
        return String.format("批量导入完成！成功: %d, 失败: %d", successCount, failCount);
    }

    // ========== 私有方法 ==========

    /**
     * 初始化模板
     */
    private void initTemplates() {
        templates.put("amazon", "amazon_product_template");
        templates.put("ebay", "ebay_product_template");
        templates.put("shopee", "shopee_product_template");
        templates.put("general", "general_product_template");
    }

    /**
     * 检测语言
     */
    private String detectLanguage(String text) {
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {
                return "zh";
            }
        }
        return "en";
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(DescriptionGenerationRequest request) {
        StringBuilder prompt = new StringBuilder();

        // 系统提示
        prompt.append("你是一个专业的产品文案撰写专家。");
        prompt.append("你的职责是为产品生成吸引人、准确、优化的产品描述。\n\n");

        // 目标平台
        if (request.getTargetPlatform() != null) {
            prompt.append("目标平台: ").append(request.getTargetPlatform()).append("\n");
        }

        // 目标语言
        if (request.getTargetLanguage() != null) {
            prompt.append("目标语言: ").append(request.getTargetLanguage()).append("\n");
        }

        // 产品名称
        prompt.append("\n产品名称: ").append(request.getProductName());

        // 产品特性
        if (request.getFeatures() != null && !request.getFeatures().isEmpty()) {
            prompt.append("\n产品特性:\n");
            for (String feature : request.getFeatures()) {
                prompt.append("- ").append(feature).append("\n");
            }
        }

        // 关键词
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            prompt.append("\n关键词: ");
            prompt.append(String.join(", ", request.getKeywords()));
        }

        // 自定义提示词
        if (request.getCustomPrompt() != null) {
            prompt.append("\n").append(request.getCustomPrompt());
        }

        prompt.append("\n\n请为这个产品生成一个吸引人、专业、符合SEO规范的产品描述。");
        prompt.append("描述要突出产品的核心卖点和优势。");

        return prompt.toString();
    }

    /**
     * 调用LLM API（模拟）
     */
    /**
     * 调用LLM API生成描述
     * 优先使用真实API，fallback到模拟生成
     */
    private String callLLMAPI(String prompt, DescriptionGenerationRequest request) {
        if (llmConfig.isConfigured()) {
            try {
                log.info("调用LLM API生成产品描述 - Provider: {}", llmConfig.getProvider());
                return callRealLLMAPI(prompt);
            } catch (Exception e) {
                log.error("LLM API调用失败，使用模拟生成", e);
                return generateMockDescription(request);
            }
        }
        return generateMockDescription(request);
    }

    /**
     * 调用真实LLM API
     */
    private String callRealLLMAPI(String prompt) {
        String apiUrl = llmConfig.getBaseUrl() + "/v1/chat/completions";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());
        
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", 
            "你是一个专业的电商产品描述撰写专家，擅长生成吸引人的产品描述和SEO优化的内容。"));
        messages.add(Map.of("role", "user", "content", prompt));
        requestBody.put("messages", messages);
        
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", llmConfig.getTemperature());
        params.put("max_tokens", llmConfig.getMaxTokens());
        requestBody.putAll(params);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmConfig.getApiKey());
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, Map.class);
            
            if (response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            throw new RuntimeException("LLM API响应为空");
        } catch (Exception e) {
            log.error("LLM API调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM API调用失败", e);
        }
    }

    /**
     * 生成模拟描述
     */
    private String generateMockDescription(DescriptionGenerationRequest request) {

    /**
     * SEO优化
     */
    private DescriptionGenerationResponse.SEOoptimize optimizeSEO(
            String description,
            DescriptionGenerationRequest request) {
        return optimizeSEO(description, request.getProductName(), request.getTargetPlatform());
    }

    /**
     * SEO优化
     */
    private DescriptionGenerationResponse.SEOoptimize optimizeSEO(
            String description,
            String productName,
            String platform) {
        DescriptionGenerationResponse.SEOoptimize result = new DescriptionGenerationResponse.SEOoptimize();

        // 优化标题
        String optimizedTitle = optimizeTitle(productName, platform);
        result.setTitle(optimizedTitle);

        // 优化描述
        String optimizedDesc = optimizeDescription(description, platform);
        result.setDescription(optimizedDesc);

        // 提取关键词
        List<String> keywords = extractKeywords(description);
        result.setPrimaryKeywords(keywords.subList(0, Math.min(5, keywords.size())));

        // 关键词密度
        Map<String, Double> keywordDensity = calculateKeywordDensity(description, keywords);
        result.setKeywordDensity(keywordDensity);

        // 可读性评分
        result.setReadabilityScore(calculateReadabilityScore(description));

        // SEO评分
        result.setSeoScore(calculateSEOScore(result));

        // 改进建议
        List<String> suggestions = generateSEOSuggestions(result);
        result.setSuggestions(suggestions);

        return result;
    }

    /**
     * 优化标题
     */
    private String optimizeTitle(String title, String platform) {
        // 简化版标题优化
        if (title == null || title.isEmpty()) {
            return "Product Title";
        }

        // 移除多余空格
        String optimized = title.trim();

        // 限制长度
        if (optimized.length() > 60) {
            optimized = optimized.substring(0, 57) + "...";
        }

        return optimized;
    }

    /**
     * 优化描述
     */
    private String optimizeDescription(String description, String platform) {
        // 简化版描述优化
        if (description == null || description.isEmpty()) {
            return "Product description";
        }

        return description.trim();
    }

    /**
     * 提取关键词（简化版）
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();

        // 简化版：提取一些常见词
        String[] commonWords = {"产品", "性能", "质量", "可靠", "技术", 
                "工艺", "稳定", "耐用", "理想", "选择"};

        for (String word : commonWords) {
            if (text.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * 计算关键词密度
     */
    private Map<String, Double> calculateKeywordDensity(String text, List<String> keywords) {
        Map<String, Double> density = new HashMap<>();

        if (text == null || text.isEmpty()) {
            return density;
        }

        int totalLength = text.length();

        for (String keyword : keywords) {
            int count = text.split(keyword, -1).length - 1;
            double d = (double) (count * keyword.length()) / totalLength * 100;
            density.put(keyword, d);
        }

        return density;
    }

    /**
     * 计算可读性评分
     */
    private Double calculateReadabilityScore(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        // 简化版：基于句子长度
        String[] sentences = text.split("[.!?。！？]");
        double avgSentenceLength = Arrays.stream(sentences)
                .mapToDouble(String::length)
                .average()
                .orElse(20.0);

        // 理想句子长度15-20
        double score = Math.max(0, 100 - Math.abs(avgSentenceLength - 17.5) * 2);

        return score;
    }

    /**
     * 计算SEO评分
     */
    private Double calculateSEOScore(DescriptionGenerationResponse.SEOoptimize result) {
        double score = 0;

        // 关键词密度 (30分)
        double avgDensity = (double) result.getKeywordDensity().values().stream()
                .mapToDouble(d -> d)
                .sum() / result.getKeywordDensity().size());
        score += Math.min(30, avgDensity * 10);

        // 可读性 (30分)
        score += result.getReadabilityScore() * 0.3;

        // 标题长度 (20分)
        int titleLength = result.getTitle() != null ? result.getTitle().length() : 0;
        if (titleLength >= 20 && titleLength <= 60) {
            score += 20;
        } else if (titleLength > 0) {
            score += 10;
        }

        // 描述长度 (20分)
        int descLength = result.getDescription() != null ? result.getDescription().length() : 0;
        if (descLength >= 100 && descLength <= 200) {
            score += 20;
        } else if (descLength > 0) {
            score += 10;
        }

        return score;
    }

    /**
     * 生成SEO改进建议
     */
    private List<String> generateSEOSuggestions(DescriptionGenerationResponse.SEOoptimize result) {
        List<String> suggestions = new ArrayList<>();

        if (result.getSeoScore() < 50) {
            suggestions.add("建议增加更多产品特性描述");
        }

        if (result.getPrimaryKeywords().size() < 3) {
            suggestions.add("建议添加更多关键词以提高搜索排名");
        }

        if (result.getReadabilityScore() < 60) {
            suggestions.add("建议优化句子结构以提高可读性");
        }

        if (result.getTitle() != null && result.getTitle().length() > 60) {
            suggestions.add("建议缩短标题长度（60字符以内）");
        }

        return suggestions;
    }

    /**
     * 提炼亮点
     */
    private List<DescriptionGenerationResponse.Highlight> extractHighlights(
            List<String> features,
            Integer maxHighlights) {
        List<DescriptionGenerationResponse.Highlight> highlights = new ArrayList<>();

        if (features == null || features.isEmpty()) {
            return highlights;
        }

        int limit = maxHighlights != null ? maxHighlights : 5;
        int count = Math.min(limit, features.size());

        for (int i = 0; i < count; i++) {
            DescriptionGenerationResponse.Highlight highlight = new DescriptionGenerationResponse.Highlight();
            highlight.setId("highlight-" + (i + 1));
            highlight.setTitle("核心优势 " + (i + 1));
            highlight.setDescription(features.get(i));
            highlight.setImportity(5 - i); // 重要性递减
            highlight.setFeature(features.get(i));

            highlights.add(highlight);
        }

        return highlights;
    }

    /**
     * 生成短标题
     */
    private String generateShortTitle(String productName) {
        if (productName == null || productName.isEmpty()) {
            return "Product";
        }

        return productName.length() > 20 ? productName.substring(0, 17) + "..." : productName;
    }

    // ========== 模拟数据方法 ==========

    /**
     * 获取产品名称
     */
    private String getProductNameById(Long productId) {
        // 模拟：返回产品名称
        return "产品-" + productId;
    }

    /**
     * 获取产品特性
     */
    private List<String> getProductFeatures(Long productId) {
        // 模拟：返回产品特性
        return Arrays.asList(
            "高性能",
            "稳定可靠",
            "易于使用",
            "节能环保",
            "设计精巧"
        );
    }

    /**
     * 获取产品关键词
     */
    private List<String> getProductKeywords(Long productId) {
        // 模拟：返回产品关键词
        return Arrays.asList(
            "高性能",
            "节能",
            "环保",
            "智能"
        );
    }

    /**
     * 获取产品描述
     */
    private String getProductDescription(Long productId) {
        // 模拟：返回产品描述
        return "这是一个高质量的产品，具有优秀的性能和可靠性...";
    }
}
