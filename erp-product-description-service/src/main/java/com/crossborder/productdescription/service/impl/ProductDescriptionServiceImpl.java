package com.crossborder.productdescription.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveResponse;
import com.crossborder.productdescription.entity.ProductBrief;
import com.crossborder.productdescription.entity.ProductDescription;
import com.crossborder.productdescription.service.ProductCatalogService;
import com.crossborder.productdescription.service.ProductDescriptionRecordService;
import com.crossborder.productdescription.service.ProductDescriptionService;
import com.crossborder.erp.llm.LLMEndpointResolver;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 模拟生成时写在 {@code model} 字段里的标记值
     *
     * <p>📌 <b>不能继续写 "deepseek-chat"</b> —— 那是在<b>谎报事实</b>
     * （说这段文案是 deepseek-chat 写的，实际是本地模板拼的）。
     */
    private static final String MOCK_MODEL_NAME = "mock";

    /** 描述状态：刚生成（未审批、未发布） */
    private static final String STATUS_GENERATED = "GENERATED";

    // 模拟描述模板
    private final Map<String, String> templates = new HashMap<>();

    /**
     * 生成历史（内存）
     *
     * <p>⚠️ 这里只用于 {@code GET /api/description/history} 的<b>本次进程内</b>回显，
     * <b>不是</b>落库历史：真正的历史在 {@code product_description} 表里，
     * 由 {@link ProductDescriptionRecordService} 负责读写。
     * （服务重启后内存历史会丢，这是已知边界，不影响落库数据。）</p>
     */
    private final java.util.concurrent.CopyOnWriteArrayList<DescriptionGenerationResponse> history =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private final LLMConfig llmConfig;

    /** 描述落库（upsert / 查询） */
    private final ProductDescriptionRecordService productDescriptionRecordService;

    /** 商品主数据（sku ↔ productId 解析） */
    private final ProductCatalogService productCatalogService;

    private final RestTemplate restTemplate = new RestTemplate();

    public ProductDescriptionServiceImpl(
            LLMConfig llmConfig,
            ProductDescriptionRecordService productDescriptionRecordService,
            ProductCatalogService productCatalogService) {
        this.llmConfig = llmConfig;
        this.productDescriptionRecordService = productDescriptionRecordService;
        this.productCatalogService = productCatalogService;
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
        response.setModel(MOCK_MODEL_NAME);  // 📌 真实调用成功后会覆盖成真实模型名
        response.setCreateTime(LocalDateTime.now());

        try {
            // 1. 构建提示词
            String prompt = buildPrompt(request);

            // 2. 调用LLM API
            LLMAnswer answer = callLLMAPI(prompt, request);
            String description = answer.content();
            response.setContent(description);
            response.setMock(answer.mock());
            if (answer.mock()) {
                // 📌 **降级必须可被看见** —— 之前这里静默返回模板文本，
                //    而 model 仍写着 deepseek-chat → 调用方完全无法分辨真假
                response.setMockReason(answer.reason());
                response.setModel(MOCK_MODEL_NAME);
                log.warn("产品描述返回的是**模拟生成**（mock=true）：{}", answer.reason());
            } else {
                // 📌 真实调用成功 → model 写**真实**模型名
                response.setModel(llmConfig != null ? llmConfig.getModel() : MOCK_MODEL_NAME);
            }

            // 3. SEO优化
            if (Boolean.TRUE.equals(request.getEnableSEO())) {
                DescriptionGenerationResponse.SEOResult seoResult = optimizeSEO(description, request);
                response.setSeoResult(seoResult);
            }

            // 4. 提炼亮点
            if (Boolean.TRUE.equals(request.getExtractHighlights())) {
                List<DescriptionGenerationResponse.Highlight> highlights = 
                        extractHighlights(request.getFeatures(), 5);
                response.setHighlights(highlights);
            }

            // 5. 生成短标题
            if (Boolean.TRUE.equals(request.getGenerateShortTitle())) {
                String shortTitle = generateShortTitle(request.getProductName());
                response.setShortTitle(shortTitle);
            }

            // 6. Token使用统计
            // 📌 **只对真实调用统计** —— 之前对模板文本也按 `长度/4` 编造 token 数
            //    → 那是个**假事实**
            if (!answer.mock()) {
                DescriptionGenerationResponse.TokenUsage usage = new DescriptionGenerationResponse.TokenUsage();
                usage.setPromptTokens(prompt.length() / 4);
                usage.setCompletionTokens(description.length() / 4);
                usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
                response.setUsage(usage);
            }

            // 7. 生成时长
            response.setGenerationTime(System.currentTimeMillis() - startTime);

            log.info("产品描述生成完成 - 耗时: {}ms", response.getGenerationTime());

        } catch (Exception e) {
            log.error("产品描述生成失败", e);
            response.setContent("生成失败，请稍后重试。");
            response.setError(e.getMessage());
        }

        // 记录生成历史（前端 GET /api/description/history 要查）
        history.add(response);

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
    public DescriptionGenerationResponse.SEOResult optimizeSEO(
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

    /**
     * 保存描述（内部接口，完整参数）
     *
     * <p>📌 <b>以前这是个假接口</b>：只打了一行 {@code log.info} 就 {@code return true}，
     * 什么都没写进库，却告诉调用方「保存成功」——
     * 这类「静默失败」比报错更贵：页面提示成功，数据永远不存在。</p>
     *
     * <p>现在真的落库：按 {@code (product_id, language, platform)} upsert，
     * 重复保存版本号递增；<b>任何失败都抛异常</b>。</p>
     */
    @Override
    public Boolean saveDescription(DescriptionGenerationResponse response) {
        if (response == null) {
            throw new BizException("待保存的产品描述不能为空");
        }
        Long productId = response.getProductId();
        if (productId == null) {
            throw new BizException("产品ID不能为空，无法保存产品描述");
        }

        // 📌 product_name 是 NOT NULL 列；调用方没给名字就去商品主数据里查，
        //    查不到就明确报错 —— 绝不写一个编出来的名字进库。
        String productName = response.getProductName();
        if (productName == null || productName.isBlank()) {
            productName = productCatalogService.findById(productId)
                    .map(ProductBrief::getProductName)
                    .orElseThrow(() -> new BizException("未找到商品: " + productId + "，无法保存产品描述"));
        }

        ProductDescription record = new ProductDescription();
        record.setProductId(productId);
        record.setProductName(truncate(productName, 200));
        record.setContent(response.getContent());
        record.setShortTitle(truncate(response.getShortTitle(), 100));
        record.setLanguage(response.getLanguage());
        record.setPlatform(response.getPlatform());
        record.setTemplateId(truncate(response.getTemplateId(), 50));
        record.setModel(truncate(response.getModel(), 50));
        record.setStatus(STATUS_GENERATED);
        record.setTokensUsed(response.getUsage() == null ? null : response.getUsage().getTotalTokens());
        record.setGenerationTimeMs(response.getGenerationTime());

        ProductDescription saved = productDescriptionRecordService.upsert(record);

        log.info("产品描述保存成功 - productId: {}, language: {}, platform: {}, version: {}",
                saved.getProductId(), saved.getLanguage(), saved.getPlatform(), saved.getVersionNumber());
        return true;
    }

    /**
     * 按前端契约保存描述（SKU → 商品）
     *
     * <p>前端 {@code erp-web/src/api/ai.ts} 的 {@code descriptionApi.save()}
     * → {@code POST /api/description/save}。</p>
     *
     * <p>📌 SKU 解析不到商品 → <b>抛 BizException</b>，
     * 不能「随便找个 productId 存进去」—— 那是凭空造数据，
     * 而且前端会看到「保存成功」，下次回来查却是别的商品。</p>
     */
    @Override
    public FrontendDescriptionSaveResponse saveFrontendDescription(FrontendDescriptionSaveRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        String sku = request.getSku() == null ? null : request.getSku().trim();
        if (sku == null || sku.isEmpty()) {
            throw new BizException("商品SKU不能为空");
        }
        String body = request.getBody();
        if (body == null || body.isBlank()) {
            throw new BizException("描述正文不能为空");
        }

        ProductBrief product = productCatalogService.findBySku(sku)
                .orElseThrow(() -> new BizException("未找到 SKU 对应的商品: " + sku));

        ProductDescription record = new ProductDescription();
        record.setProductId(product.getId());
        record.setProductName(truncate(product.getProductName(), 200));
        record.setContent(buildContent(body, request.getBulletPoints()));
        record.setShortTitle(truncate(firstNonBlank(request.getSubtitle(), request.getTitle()), 100));
        record.setLanguage(request.getLanguage());
        record.setPlatform(request.getPlatform());
        record.setTemplateId(truncate(request.getTemplateId(), 50));
        record.setStatus(STATUS_GENERATED);
        record.setGenerationTimeMs(request.getGenerationTime());

        ProductDescription saved = productDescriptionRecordService.upsert(record);

        FrontendDescriptionSaveResponse out = new FrontendDescriptionSaveResponse();
        // 📌 productId 用字符串承载：雪花 ID 19 位，JS Number 装不下 → 丢精度
        out.setProductId(String.valueOf(saved.getProductId()));
        out.setProductName(saved.getProductName());
        out.setSaved(Boolean.TRUE);
        out.setVersionNumber(saved.getVersionNumber());
        return out;
    }

    /**
     * 获取产品所有描述（真的查库）
     *
     * <p>📌 <b>以前返回的是一条写死的假数据</b>
     * （"这是一个高质量的产品，具有优秀的性能和可靠性..."）——
     * 无论库里有没有数据都返回它。现在查不到就返回<b>空列表</b>。</p>
     */
    @Override
    public List<DescriptionGenerationResponse> getProductDescriptions(Long productId) {
        if (productId == null) {
            throw new BizException("产品ID不能为空");
        }
        log.info("获取产品所有描述 - 产品ID: {}", productId);

        List<ProductDescription> records = productDescriptionRecordService.listByProductId(productId);
        if (records.isEmpty()) {
            log.info("商品 {} 还没有任何描述记录（返回空列表）", productId);
            return List.of();
        }

        // 📌 一个 (language, platform) 组合对应库里的一行（唯一键保证），
        //    所以列表本身就是「按语言/平台分组」的结果。
        return records.stream().map(this::toDescriptionResponse).toList();
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
     * 库记录 → 对外 DTO
     *
     * <p>📌 {@code descriptionId} 用字符串（库里是自增 BIGINT，序列化给前端时统一成字符串，
     * 避免前端把它当数字用）。</p>
     */
    private DescriptionGenerationResponse toDescriptionResponse(ProductDescription record) {
        DescriptionGenerationResponse response = new DescriptionGenerationResponse();
        response.setDescriptionId(record.getId() == null ? null : String.valueOf(record.getId()));
        response.setProductId(record.getProductId());
        response.setProductName(record.getProductName());
        response.setContent(record.getContent());
        response.setShortTitle(record.getShortTitle());
        response.setLanguage(record.getLanguage());
        response.setPlatform(record.getPlatform());
        response.setVersionNumber(record.getVersionNumber());
        response.setTemplateId(record.getTemplateId());
        response.setModel(record.getModel());
        response.setGenerationTime(record.getGenerationTimeMs());
        response.setCreateTime(record.getCreateTime());
        return response;
    }

    /**
     * 正文 + 核心卖点 拼成 content
     *
     * <p>📌 <b>为什么拼在一起</b>：{@code product_description} 表里没有「卖点」列，
     * 但前端契约有 {@code bulletPoints} —— 直接丢掉就等于丢数据，
     * 而丢了数据用户还以为存下来了。所以按「- xxx」行拼进正文。</p>
     */
    private String buildContent(String body, List<String> bulletPoints) {
        if (bulletPoints == null || bulletPoints.isEmpty()) {
            return body;
        }
        StringBuilder sb = new StringBuilder(body);
        for (String point : bulletPoints) {
            if (point == null || point.isBlank()) {
                continue;
            }
            sb.append("\n- ").append(point.trim());
        }
        return sb.toString();
    }

    /**
     * 按列宽截断
     *
     * <p>📌 MySQL 严格模式下超长会直接报错（{@code Data too long} → 500），
     * 而短标题只有 100 字符 —— 前端随手写一段长文案就能把接口打挂。
     * 这里先截断再入库，不静默丢内容（正文 content 是 TEXT，不截断）。</p>
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /** 取第一个非空值 */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    /**
     * 初始化模板
     */
    private void initTemplates() {
        templates.put("amazon", "amazon_product_template");
        templates.put("ebay", "ebay_product_template");
        templates.put("shopee", "shopee_product_template");
        templates.put("general", "general_product_template");
    }

    @Override
    public List<Map<String, Object>> listTemplates() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(template("amazon", "Amazon Listing", "zh", "professional",
                "标题 + 五点描述 + 描述段落，符合 Amazon A10 规范"));
        list.add(template("ebay", "eBay Listing", "en", "concise",
                "标题 + 简短描述 + 商品属性，适合快速浏览"));
        list.add(template("shopee", "Shopee Listing", "en", "friendly",
                "强调卖点与促销信息，贴合东南亚买家的短文案习惯"));
        list.add(template("general", "通用描述", "auto", "balanced",
                "跨平台通用描述，按目标语言自动适配"));
        return list;
    }

    private Map<String, Object> template(String id, String name, String language, String tone, String sample) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("templateId", templates.getOrDefault(id, id + "_product_template"));
        map.put("name", name);
        map.put("platform", id);
        map.put("language", language);
        map.put("tone", tone);
        map.put("sample", sample);
        return map;
    }

    @Override
    public FrontendDescriptionResponse generateForFrontend(FrontendDescriptionRequest request) {
        FrontendDescriptionRequest req = request == null
                ? new FrontendDescriptionRequest()
                : request;

        DescriptionGenerationRequest internal = new DescriptionGenerationRequest();
        internal.setProductId(null);
        internal.setProductName(req.getSku() == null ? "商品" : req.getSku());
        internal.setKeywords(req.getKeywords());
        internal.setFeatures(req.getKeywords());
        internal.setTargetLanguage(req.getLanguage());
        internal.setTargetPlatform(req.getTargetPlatform());
        internal.setLength(req.getLength());
        internal.setEnableSEO(true);
        internal.setExtractHighlights(true);
        internal.setGenerateShortTitle(true);
        if (req.getTone() != null && !req.getTone().isBlank()) {
            internal.setCustomPrompt("风格语气: " + req.getTone());
        }

        DescriptionGenerationResponse response = generateDescription(internal);

        FrontendDescriptionResponse out = new FrontendDescriptionResponse();
        out.setTemplateId(response.getTemplateId());
        out.setGenerationTime(response.getGenerationTime());
        out.setError(response.getError());
        // 📌 透传「是不是模拟生成」—— 前端要能告诉用户「这是模板，不是真 AI」
        out.setMock(response.getMock());
        out.setMockReason(response.getMockReason());

        // SEO 结果 → title / seoKeywords
        DescriptionGenerationResponse.SEOResult seo = response.getSeoResult();
        if (seo != null) {
            out.setTitle(seo.getTitle());
            out.setSeoKeywords(seo.getPrimaryKeywords() == null
                    ? List.of() : seo.getPrimaryKeywords());
            if (seo.getDescription() != null && !seo.getDescription().isBlank()) {
                out.setBody(seo.getDescription());
            }
        }
        if (out.getTitle() == null || out.getTitle().isBlank()) {
            out.setTitle(response.getShortTitle() != null && !response.getShortTitle().isBlank()
                    ? response.getShortTitle()
                    : internal.getProductName());
        }
        if (out.getBody() == null || out.getBody().isBlank()) {
            out.setBody(response.getContent());
        }
        out.setSubtitle(response.getShortTitle());

        // 亮点 → bulletPoints
        if (response.getHighlights() != null && !response.getHighlights().isEmpty()) {
            List<String> points = response.getHighlights().stream()
                    .map(h -> h.getFeature() != null && !h.getFeature().isBlank()
                            ? h.getFeature()
                            : h.getDescription())
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            out.setBulletPoints(points);
        }
        if (out.getBulletPoints() == null || out.getBulletPoints().isEmpty()) {
            out.setBulletPoints(req.getKeywords() == null ? List.of() : req.getKeywords());
        }
        if (out.getSeoKeywords() == null || out.getSeoKeywords().isEmpty()) {
            out.setSeoKeywords(req.getKeywords() == null ? List.of() : req.getKeywords());
        }
        return out;
    }

    @Override
    public List<DescriptionGenerationResponse> listHistory(Long productId, int limit) {
        int max = limit <= 0 ? 20 : Math.min(limit, 200);
        return history.stream()
                .filter(h -> productId == null || productId.equals(h.getProductId()))
                .sorted(Comparator.comparing(
                        (DescriptionGenerationResponse h) -> h.getCreateTime() == null
                                ? LocalDateTime.MIN : h.getCreateTime()).reversed())
                .limit(max)
                .toList();
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
    private LLMAnswer callLLMAPI(String prompt, DescriptionGenerationRequest request) {
        if (llmConfig != null && llmConfig.isConfigured()) {
            try {
                log.info("调用LLM API生成产品描述 - Provider: {}", llmConfig.getProvider());
                return LLMAnswer.real(callRealLLMAPI(prompt));
            } catch (Exception e) {
                // 📌 降级不是「静默失败」—— 原因要能传到调用方
                log.error("LLM API调用失败，使用模拟生成", e);
                return LLMAnswer.mock(generateMockDescription(request),
                        "真实 API 调用失败：" + e.getMessage());
            }
        }
        return LLMAnswer.mock(generateMockDescription(request),
                "未配置 LLM API Key 或 llm.enabled=false");
    }

    /**
     * 一次 LLM 调用的结果：**内容 + 是不是模拟的 + 降级原因**
     *
     * <p>📌 之前 {@code callLLMAPI} 直接返回 {@code String}，
     * 「这是不是模拟的」这个信息<b>在方法签名里就丢了</b>。
     */
    public record LLMAnswer(String content, boolean mock, String reason) {

        public static LLMAnswer real(String content) {
            return new LLMAnswer(content, false, null);
        }

        public static LLMAnswer mock(String content, String reason) {
            return new LLMAnswer(content, true, reason);
        }
    }

    /**
     * 调用真实LLM API
     */
    private String callRealLLMAPI(String prompt) {
        // 📌 **端点拼接的唯一事实来源 = LLMEndpointResolver**
        // 📌 之前这里写死 `baseUrl + "/v1/chat/completions"`，
        //    而 application.yml 的 base-url 默认带 /v1 → 拼出 **双 /v1** → 必然 404
        //    → catch 之后静默降级成模拟生成 → **AI 从来没真的调通过**
        String apiUrl = LLMEndpointResolver.chatCompletionsUrl(
                llmConfig.getBaseUrl(),
                llmConfig.getProvider(),
                llmConfig.getDeployment(),
                llmConfig.getApiVersion());
        
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
        // 📌 Azure 用 `api-key` 头，其他家（OpenAI 兼容协议）用 `Authorization: Bearer`
        if (LLMEndpointResolver.isAzure(llmConfig.getProvider())) {
            headers.set("api-key", llmConfig.getApiKey());
        } else {
            headers.setBearerAuth(llmConfig.getApiKey());
        }
        
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
    /**
     * 生成模拟描述（用于测试或无LLM API时）
     */
    private String generateMockDescription(DescriptionGenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getProductName()).append(" - ");
        if (request.getFeatures() != null) {
            sb.append(String.join(", ", request.getFeatures()));
        }
        return sb.toString();
    }

    /**
     * SEO优化
     */
    private DescriptionGenerationResponse.SEOResult optimizeSEO(
            String description,
            DescriptionGenerationRequest request) {
        return optimizeSEO(description, request.getProductName(), request.getTargetPlatform());
    }

    /**
     * SEO优化
     */
    private DescriptionGenerationResponse.SEOResult optimizeSEO(
            String description,
            String productName,
            String platform) {
        DescriptionGenerationResponse.SEOResult result = new DescriptionGenerationResponse.SEOResult();

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
        result.setSeoscore(calculateSEOScore(result));

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
    private Double calculateSEOScore(DescriptionGenerationResponse.SEOResult result) {
        double score = 0;

        // 关键词密度 (30分)
        double avgDensity = (double) result.getKeywordDensity().values().stream()
                .mapToDouble(d -> d)
                .sum() / result.getKeywordDensity().size();
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
    private List<String> generateSEOSuggestions(DescriptionGenerationResponse.SEOResult result) {
        List<String> suggestions = new ArrayList<>();

        if (result.getSeoscore() < 50) {
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
            highlight.setImportance(5 - i); // 重要性递减
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
     * 获取产品名称（商品主数据）
     *
     * <p>📌 <b>以前这里是硬编码的</b> {@code "产品-" + productId} ——
     * 也就是说不管库里是什么商品，调用的都是同一个假名字，
     * 生成出来的文案里商品名字也是假的。现在查商品主数据（{@code t_product}）。</p>
     *
     * <p>⚠️ <b>为什么这里容错、而保存路径不容错</b>：
     * 本方法只用于拼 AI 提示词（<b>不是落库字段</b>），
     * 商品主数据暂时不可用时不应该让「AI 生成」整个挂掉；
     * 但落库时商品必须真实存在 —— 那里查不到会直接抛 BizException。
     * 两种路径都打 warn，不静默。</p>
     */
    private String getProductNameById(Long productId) {
        try {
            ProductBrief product = productCatalogService.findById(productId).orElse(null);
            if (product != null && product.getProductName() != null
                    && !product.getProductName().isBlank()) {
                return product.getProductName();
            }
            log.warn("商品主数据里没有 id={} —— 本次用占位名（仅用于提示词，不落库）", productId);
        } catch (BizException e) {
            log.warn("查询商品名称失败（id={}），本次用占位名（仅用于提示词，不落库）：{}", productId, e.getMessage());
        }
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
