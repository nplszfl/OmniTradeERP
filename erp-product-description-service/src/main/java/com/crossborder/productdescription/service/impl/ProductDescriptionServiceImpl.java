package com.crossborder.productdescription.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.productdescription.config.LLMConfig;
import com.crossborder.productdescription.dto.AbTestOutcomeRequest;
import com.crossborder.productdescription.dto.AbTestStartRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveResponse;
import com.crossborder.productdescription.entity.AbTestResult;
import com.crossborder.productdescription.entity.DescriptionTemplate;
import com.crossborder.productdescription.entity.ProductBrief;
import com.crossborder.productdescription.entity.ProductDescription;
import com.crossborder.productdescription.service.DescriptionAnalysisRecordService;
import com.crossborder.productdescription.service.DescriptionGenerationTaskService;
import com.crossborder.productdescription.service.DescriptionTemplateRenderer;
import com.crossborder.productdescription.service.DescriptionTemplateService;
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

    /**
     * 从真实商品描述派生的「特性 / 关键词」上限。
     *
     * <p>📌 这些是<b>上限</b>，不是固定值 ——
     * 内容全部来自 {@code t_product.description}，
     * 商品没写描述就是<b>空</b>，不会编。</p>
     */
    private static final int MAX_FEATURES = 10;
    private static final int MAX_KEYWORDS = 8;
    private static final int MAX_PHRASE_WORDS = 6;
    private static final int MAX_PHRASE_LENGTH = 60;

    /**
     * 英文停用词表（2026-11-19 新增）。
     *
     * <p>📌 **只收功能词，不收商品属性词** —— portable / durable / compact 这类
     * **恰恰要留下**当 SEO 关键词（它们是买家真正会搜的词）。</p>
     */
    private static final Set<String> EN_STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "your", "our", "its", "you",
            "are", "was", "were", "has", "have", "had", "can", "will", "not", "but",
            "from", "into", "out", "all", "any", "each", "more", "most", "such",
            "they", "them", "their", "his", "her", "she", "him", "who", "what",
            "when", "where", "which", "while", "about", "after", "before",
            "than", "then", "also", "just", "only", "very", "well", "use", "used",
            "using", "make", "makes", "made", "get", "got", "one", "two", "three",
            "we", "it", "is", "as", "at", "by", "on", "in", "to", "of", "or",
            "be", "do", "does", "did", "so", "up", "if",
            "product", "products", "item", "items", "description",
            // 📌 **模板占位与风格词**（2026-11-19 实测加的）：真 LLM 生成的英文描述里
            //   必然出现 "[Product Name]" / "### Product Title" / 复述请求里的 tone
            //   → 这些词**高频但不是买家会搜的词** → 当 SEO 关键词是纯噪音。
            //   实测：修之前前 5 个里有 'name'（模板占位）和 'professional'（tone 泄漏）。
            "name", "title", "heading", "professional", "premium", "generic",
            // 📌 **markdown 小标题词**（实测：LLM 输出带 "Bullet Points:" / "Search:" 等小标题
            //   → 这些词本身会被当成 SEO 关键词 → 但它们是**结构标记**不是商品词）
            "bullet", "bullets", "points", "search", "features", "benefits",
            "highlights", "specifications", "specs", "overview", "details", "keywords");

    // 📌 **2026-11-24 移除内存 templates Map** —— 原来这里是
    //    `private final Map<String, String> templates = new HashMap<>();`，
    //    由 `initTemplates()` 硬编码 4 条：
    //        amazon → "amazon_product_template"
    //        ebay   → "ebay_product_template"
    //        shopee → "shopee_product_template"
    //        general→ "general_product_template"
    //    🔴 **这三个 ID 库里一个都不存在** —— 表 `description_template` 里种的是
    //    `amazon_template` / `ebay_template` / `shopee_template` / `general_template`。
    //    → 落库到 `product_description.template_id` 的值**永远是一条悬空引用**，
    //      任何「这条描述用了哪个模板」的追溯都查不出来。
    //    → 现在模板来自**数据库**（见 `DescriptionTemplateService`），
    //      内存里**不再有第二套真相**。

    // 📌 **2026-11-14 移除内存 history** —— 原来这里是
    //    `private final CopyOnWriteArrayList<DescriptionGenerationResponse> history`，
    //    生成时 `history.add(response)`，查询时 `history.stream()...`。
    //    而真正的落库历史在 {@code product_description} 表
    //    （由 {@link ProductDescriptionRecordService} 负责读写）。
    //    → **同一份历史有两个来源**：服务重启后内存那份没了（前端历史页变空），
    //      在运行期间两个来源的数据又可能对不上 → **两套真相**。
    //    → 现在统一以落库表为唯一真相（见 {@link #listHistory}）。

    private final LLMConfig llmConfig;

    /** 描述落库（upsert / 查询） */
    private final ProductDescriptionRecordService productDescriptionRecordService;

    /** 商品主数据（sku ↔ productId 解析） */
    private final ProductCatalogService productCatalogService;

    /**
     * 描述生成任务审计（表 {@code description_generation_task}）
     *
     * <p>📌 **2026-11-19 新增**：之前这张表有 DDL、没代码 → 永远 0 行 →
     * 「这批生成跑没跑 / 成没成 / 成几条 / 失败在哪」没有事实来源。</p>
     */
    private final DescriptionGenerationTaskService descriptionGenerationTaskService;

    /**
     * 描述模板（表 {@code description_template}）
     *
     * <p>📌 **2026-11-24 新增**：之前模板是**内存硬编码**，
     * 表 {@code description_template} 从来没人读过 → 运营改模板零效果。</p>
     */
    private final DescriptionTemplateService descriptionTemplateService;

    /**
     * 模板渲染（把 {@code {占位符}} 换成真实商品事实）
     *
     * <p>📌 <b>2026-11-24 新增</b>：之前模板正文**压根没进过 prompt**，
     * {@code useTemplate} / {@code templateId} 是两个死参数。</p>
     */
    private final DescriptionTemplateRenderer descriptionTemplateRenderer;

    /**
     * 描述分析结果落库（表 {@code seo_optimization} / {@code product_highlights} / {@code ab_test_result}）
     *
     * <p>📌 <b>2026-11-28 新增</b>：这三张表<b>早有 DDL、有索引</b>，
     * 但<b>全仓没有任何一行代码往里写</b> →
     * {@code optimizeSEO()} / {@code extractHighlights()} 每次都<b>现算一遍就丢掉</b> →
     * 运营事后回看「当时打了几分」「当时提炼了什么」→ <b>一个都查不到</b>。</p>
     */
    private final DescriptionAnalysisRecordService descriptionAnalysisRecordService;

    private final RestTemplate restTemplate = new RestTemplate();

    public ProductDescriptionServiceImpl(
            LLMConfig llmConfig,
            ProductDescriptionRecordService productDescriptionRecordService,
            ProductCatalogService productCatalogService,
            DescriptionGenerationTaskService descriptionGenerationTaskService,
            DescriptionTemplateService descriptionTemplateService,
            DescriptionTemplateRenderer descriptionTemplateRenderer,
            DescriptionAnalysisRecordService descriptionAnalysisRecordService) {
        this.llmConfig = llmConfig;
        this.productDescriptionRecordService = productDescriptionRecordService;
        this.productCatalogService = productCatalogService;
        this.descriptionGenerationTaskService = descriptionGenerationTaskService;
        this.descriptionTemplateService = descriptionTemplateService;
        this.descriptionTemplateRenderer = descriptionTemplateRenderer;
        this.descriptionAnalysisRecordService = descriptionAnalysisRecordService;
    }

    @Override
    public DescriptionGenerationResponse generateDescription(DescriptionGenerationRequest request) {
        // 📌 **单条入口 = 记一条 SINGLE 任务**
        //    （批量/多语言/A-B 走各自的入口记一条，不会在下面重复记）
        Long taskId = descriptionGenerationTaskService.startTask(
                DescriptionGenerationTaskService.TASK_TYPE_SINGLE,
                request == null ? null : request.getProductId(),
                request == null ? null : request.getTargetLanguage(),
                request == null ? null : request.getTargetPlatform(),
                1);
        long begin = System.currentTimeMillis();
        try {
            DescriptionGenerationResponse response = doGenerateDescription(request);
            if (response.getError() != null && !response.getError().isBlank()) {
                descriptionGenerationTaskService.failTask(taskId, 0, 1,
                        System.currentTimeMillis() - begin, response.getError());
            } else {
                descriptionGenerationTaskService.completeTask(taskId, 1, 0,
                        System.currentTimeMillis() - begin);
            }
            return response;
        } catch (Exception e) {
            descriptionGenerationTaskService.failTask(taskId, 0, 1,
                    System.currentTimeMillis() - begin, e.getMessage());
            throw e;
        }
    }

    /**
     * **真正的生成逻辑**（不记任务）
     *
     * <p>📌 **为什么要拆开**：批量生成会调用 N 次「生成」——
     * 如果记账写在生成里，就会写 **N + 1** 行（1 条批量 + N 条单条）→ 语义乱、统计对不上。
     * → 现在记账只发生在**最外层入口**（单条 / 批量 / 多语言 / A-B），各记一条。</p>
     */
    private DescriptionGenerationResponse doGenerateDescription(DescriptionGenerationRequest request) {
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
            // 📌 **2026-11-24 新增：解析「这次实际用哪个模板」**
            //    🔴 修它之前，`product_description.template_id` 存的是
            //    `amazon_product_template` 这种**库里根本不存在的值** → 悬空引用。
            //    → 现在写**真实用到的模板标识**（没有模板就留 null，不编）。
            // 📌 **只解析一次**：模板既进 prompt，也写进响应 → 不重复查库。
            //    📌 **2026-11-25 修**：原来 `buildPrompt(request)` 内部又调了一次
            //    `resolveTemplate(request)` → **同一个请求查了两遍模板表**
            //    （而且两次可能查到不同的行 —— 模板表在两次查询之间被改的话，
            //    「写进响应的 templateId」和「真正进 prompt 的模板」会**对不上**）。
            DescriptionTemplate usedTemplate = resolveTemplate(request);
            if (usedTemplate != null) {
                response.setTemplateId(usedTemplate.getTemplateId());
            }

            // 1. 构建提示词
            String prompt = buildPrompt(request, usedTemplate);

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
            // 📌 2026-11-18：**不再用 `长度/4` 估算** —— API 响应里本来就有真实 usage
            //    → 拿不到真实 usage 时**置 null**（不知道），不编一个像真的数字（规则 18/19/391）
            if (!answer.mock() && answer.usage() != null) {
                DescriptionGenerationResponse.TokenUsage usage = new DescriptionGenerationResponse.TokenUsage();
                usage.setPromptTokens(answer.usage().promptTokens());
                usage.setCompletionTokens(answer.usage().completionTokens());
                usage.setTotalTokens(answer.usage().totalTokens());
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

        // 📌 **2026-11-14 移除**：`history.add(response)` —— 内存副本。
        //    落库由下面的 {@code productDescriptionRecordService.upsert(...)} 负责，
        //    历史查询统一以落库表为唯一真相。

        return response;
    }

    @Override
    public List<DescriptionGenerationResponse> batchGenerateDescription(
            List<DescriptionGenerationRequest> requests) {
        log.info("批量生成产品描述 - 数量: {}", requests.size());

        // 📌 批量 = **记一条** BATCH 任务（不是 N 条单条）
        Long taskId = descriptionGenerationTaskService.startTask(
                DescriptionGenerationTaskService.TASK_TYPE_BATCH,
                null, null, null, requests == null ? 0 : requests.size());
        long begin = System.currentTimeMillis();
        try {
            List<DescriptionGenerationResponse> responses = (requests == null ? List.<DescriptionGenerationRequest>of() : requests)
                    .stream()
                    .map(this::doGenerateDescription)
                    .toList();
            int success = descriptionGenerationTaskService.countSuccess(responses);
            int total = responses.size();
            descriptionGenerationTaskService.completeTask(taskId, success, total - success,
                    System.currentTimeMillis() - begin);
            return responses;
        } catch (Exception e) {
            descriptionGenerationTaskService.failTask(taskId, 0, 0,
                    System.currentTimeMillis() - begin, e.getMessage());
            throw e;
        }
    }

    @Override
    public List<DescriptionGenerationResponse> generateMultiLanguageDescription(
            Long productId,
            List<String> languages) {
        log.info("生成多语言描述 - 产品ID: {}, 语言: {}", productId, languages);

        // 📌 多语言 = **记一条** MULTI_LANGUAGE 任务
        Long taskId = descriptionGenerationTaskService.startTask(
                DescriptionGenerationTaskService.TASK_TYPE_MULTI_LANGUAGE,
                productId, null, null, languages == null ? 0 : languages.size());
        long begin = System.currentTimeMillis();
        try {
            List<DescriptionGenerationResponse> responses = new ArrayList<>();

            for (String language : languages) {
                DescriptionGenerationRequest request = new DescriptionGenerationRequest();
                request.setProductId(productId);
                request.setProductName(getProductNameById(productId));
                request.setTargetLanguage(language);
                request.setFeatures(getProductFeatures(productId));
                request.setKeywords(getProductKeywords(productId));

                responses.add(doGenerateDescription(request));
            }

            int success = descriptionGenerationTaskService.countSuccess(responses);
            int total = responses.size();
            descriptionGenerationTaskService.completeTask(taskId, success, total - success,
                    System.currentTimeMillis() - begin);
            return responses;
        } catch (Exception e) {
            descriptionGenerationTaskService.failTask(taskId, 0, 0,
                    System.currentTimeMillis() - begin, e.getMessage());
            throw e;
        }
    }

    @Override
    public DescriptionGenerationResponse.SEOResult optimizeSEO(
            Long productId,
            String platform) {
        log.info("SEO优化 - 产品ID: {}, 平台: {}", productId, platform);

        String description = getProductDescription(productId);
        String productName = getProductNameById(productId);

        DescriptionGenerationResponse.SEOResult result =
                optimizeSEO(description, productName, platform);

        // 📌 **2026-11-28 新增：把 SEO 结果落库**（表 {@code seo_optimization}）
        //    🔴 修它之前：这张表**有 DDL、有索引**，但**全仓没有任何一行代码往里写**
        //    → 算出来的分数只活在这一次 HTTP 响应里 → 运营事后问
        //    「上周那条文案当时打了几分？」→ **表里是空的，查不到**。
        //    ✅ 现在：算完就落库 → 有事实来源。
        //    📌 **落不到就不算失败**：SEO 优化是「只读分析」，
        //    分析结果已经返回给调用方了 → 落库失败只 log.error 留痕，
        //    绝不让「写不进去」把整个分析接口打成 500（那才是真的帮倒忙）。
        //    ⚠️ 但**绝不静默**：log.error 里写明表名和原因。
        persistSeoOptimization(productId, platform, result);

        return result;
    }

    /**
     * 把 SEO 优化结果落库（失败只留痕，不影响分析结果返回）
     *
     * <p>📌 <b>为什么用「找不到描述就跳过」而不是报错</b>：
     * {@code seo_optimization.description_id} 是 NOT NULL 外键指向
     * {@code product_description.id} —— 而 SEO 优化**可以针对一个还没保存过描述的商品**做
     * （接口是「拿商品主数据里的描述」去算的）。这时<b>没有可关联的描述行</b> →
     * 强行写一条悬空引用 = 制造一条<b>指向不存在行的数据</b>（规则 72）。
     * → 所以：没有描述行 → <b>不写</b>（并 log.warn 说明），不编一个 descriptionId。</p>
     */
    private void persistSeoOptimization(Long productId,
                                        String platform,
                                        DescriptionGenerationResponse.SEOResult result) {
        try {
            Long descriptionId = resolveDescriptionId(productId, platform);
            if (descriptionId == null) {
                log.warn("商品 {} 还没有落库的描述记录 —— 本次 SEO 结果不落库"
                        + "（seo_optimization.description_id 必填，不能写悬空引用）", productId);
                return;
            }
            descriptionAnalysisRecordService.saveSeoOptimization(
                    descriptionId, productId, result, null);
        } catch (Exception e) {
            // 📌 落库失败**不影响**已经算出来的分析结果（它已经返回给调用方了）
            //    → log.error 留痕（表名 + 原因），**不静默**。
            log.error("SEO优化结果落库失败（表 seo_optimization，productId={}）：{}",
                    productId, e.getMessage(), e);
        }
    }

    /**
     * 解析「这条描述在 {@code product_description} 里的 id」
     *
     * <p>📌 <b>按 (商品, 语言, 平台) 定位</b>：唯一键就是这三列
     * （{@code uk_pd_product_lang_platform}）→ 一条描述唯一确定。</p>
     *
     * <p>📌 <b>平台匹配忽略大小写</b>：库里种的是 {@code Amazon}，
     * 请求传的是 {@code amazon} → 必须显式忽略大小写
     * （与 {@code DescriptionTemplateServiceImpl} 同一个坑、同一套解法）。</p>
     *
     * @return 描述行 id；<b>没有对应描述行就返回 null</b>（= 没有可关联的描述）
     */
    private Long resolveDescriptionId(Long productId, String platform) {
        if (productId == null) {
            return null;
        }
        List<ProductDescription> rows =
                productDescriptionRecordService.listByProductId(productId);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (ProductDescription row : rows) {
            if (platform == null || platform.isBlank()) {
                return row.getId();
            }
            if (row.getPlatform() != null
                    && row.getPlatform().trim().equalsIgnoreCase(platform.trim())) {
                return row.getId();
            }
        }
        return null;
    }

    @Override
    public List<DescriptionGenerationResponse.Highlight> extractHighlights(
            Long productId,
            Integer maxHighlights) {
        log.info("提炼产品亮点 - 产品ID: {}, 最大数量: {}", productId, maxHighlights);

        List<String> features = getProductFeatures(productId);
        List<DescriptionGenerationResponse.Highlight> highlights =
                extractHighlights(features, maxHighlights);

        // 📌 **2026-11-28 新增：把亮点落库**（表 {@code product_highlights}）
        //    🔴 修它之前：这张表**有 DDL、有索引**，但**全仓没有任何一行代码往里写**
        //    → 提炼出的亮点只活在这一次 HTTP 响应里 → 运营事后问
        //    「这条商品上次提炼的亮点是什么？」→ **表里是空的，查不到**。
        //    ✅ 现在：提炼完就落库 → 有事实来源。
        persistHighlights(productId, highlights);

        return highlights;
    }

    /**
     * 把产品亮点落库（失败只留痕，不影响提炼结果返回）
     *
     * <p>📌 与 {@link #persistSeoOptimization} 同理：
     * 提炼结果已经返回给调用方了 → 落库失败只 log.error 留痕，
     * <b>不让「写不进去」把整个接口打成 500</b>。</p>
     */
    private void persistHighlights(Long productId,
                                   List<DescriptionGenerationResponse.Highlight> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            // 📌 没有真实特性 → 提炼不出亮点 → **不写库**（不编 5 条假亮点）
            return;
        }
        try {
            Long descriptionId = resolveDescriptionId(productId, null);
            if (descriptionId == null) {
                log.warn("商品 {} 还没有落库的描述记录 —— 本次亮点不落库"
                        + "（product_highlights.description_id 必填，不能写悬空引用）", productId);
                return;
            }
            descriptionAnalysisRecordService.saveHighlights(descriptionId, productId, highlights);
        } catch (Exception e) {
            log.error("产品亮点落库失败（表 product_highlights，productId={}）：{}",
                    productId, e.getMessage(), e);
        }
    }

    @Override
    public List<DescriptionGenerationResponse> generateABTestVersions(
            Long productId,
            Integer versionCount) {
        log.info("生成A/B测试版本 - 产品ID: {}, 版本数: {}", productId, versionCount);

        // 📌 A/B = **记一条** AB_TEST 任务
        Long taskId = descriptionGenerationTaskService.startTask(
                DescriptionGenerationTaskService.TASK_TYPE_AB_TEST,
                productId, null, null, versionCount == null ? 0 : versionCount);
        long begin = System.currentTimeMillis();
        try {
            List<DescriptionGenerationResponse> versions = new ArrayList<>();

            for (int i = 0; i < versionCount; i++) {
                DescriptionGenerationRequest request = new DescriptionGenerationRequest();
                request.setProductId(productId);
                request.setProductName(getProductNameById(productId));
                request.setFeatures(getProductFeatures(productId));
                request.setKeywords(getProductKeywords(productId));
                request.setTemperature(0.5 + i * 0.2); // 不同温度

                DescriptionGenerationResponse response = doGenerateDescription(request);
                response.setVersionNumber(i + 1);

                versions.add(response);
            }

            int success = descriptionGenerationTaskService.countSuccess(versions);
            int total = versions.size();
            descriptionGenerationTaskService.completeTask(taskId, success, total - success,
                    System.currentTimeMillis() - begin);
            return versions;
        } catch (Exception e) {
            descriptionGenerationTaskService.failTask(taskId, 0, 0,
                    System.currentTimeMillis() - begin, e.getMessage());
            throw e;
        }
    }

    /**
     * 开启一个 A/B 测试（表 {@code ab_test_result}）
     *
     * <p>📌 <b>2026-11-28 新增</b>：{@link #generateABTestVersions} 每次都<b>现生成几个版本就丢掉</b>
     * → 运营事后想比「A 版和 B 版哪个 CTR 高」→ <b>没有任何地方存过这件事</b>。
     * 现在开测就落一条记录，指标值<b>留空</b>（CTR 要等真实流量跑出来才有 → <b>不编</b>）。</p>
     */
    @Override
    public AbTestResult startAbTest(AbTestStartRequest request) {
        log.info("开启A/B测试 - 商品ID: {}, 测试名称: {}, 指标: {}",
                request == null ? null : request.getProductId(),
                request == null ? null : request.getTestName(),
                request == null ? null : request.getMetricName());
        return descriptionAnalysisRecordService.startAbTest(request);
    }

    /**
     * 回填 A/B 测试结果（指标实测值 → 胜者由系统判定）
     *
     * <p>📌 <b>胜者是「算出来的」不是「传进来的」</b>：请求体里只给
     * A/B 两版的<b>实测指标值</b>，谁赢由系统比较得出 → 避免调用方
     * 自己填一个和指标矛盾的 {@code winner} 写进库里。</p>
     */
    @Override
    public AbTestResult recordAbTestOutcome(Long abTestId, AbTestOutcomeRequest request) {
        log.info("回填A/B测试结果 - 测试ID: {}", abTestId);
        return descriptionAnalysisRecordService.recordAbTestOutcome(abTestId, request);
    }

    /**
     * 查某个商品的 A/B 测试记录（按开始时间倒序）
     *
     * <p>📌 查不到就是<b>空列表</b>，不是 null、也不是报错 ——
     * 「这个商品还没开过测」是一个正常状态，不是异常。</p>
     */
    @Override
    public List<AbTestResult> listAbTestResults(Long productId, int limit) {
        log.info("查询A/B测试记录 - 商品ID: {}, 条数上限: {}", productId, limit);
        return descriptionAnalysisRecordService.listAbTestResults(productId, limit);
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

        // 📌 批量导入 = **记一条** BATCH 任务（DDL 的 task_type 列举里没有 IMPORT，
        //    导入归 BATCH；内部调 doGenerateDescription → 不会多写 N 条 SINGLE）
        Long taskId = descriptionGenerationTaskService.startTask(
                DescriptionGenerationTaskService.TASK_TYPE_BATCH,
                null, null, null, productIds == null ? 0 : productIds.size());
        long begin = System.currentTimeMillis();

        int successCount = 0;
        int failCount = 0;

        for (Long productId : productIds) {
            try {
                DescriptionGenerationRequest request = new DescriptionGenerationRequest();
                request.setProductId(productId);
                request.setProductName(getProductNameById(productId));
                request.setFeatures(getProductFeatures(productId));
                request.setKeywords(getProductKeywords(productId));

                DescriptionGenerationResponse response = doGenerateDescription(request);
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
        descriptionGenerationTaskService.completeTask(taskId, successCount, failCount,
                System.currentTimeMillis() - begin);
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
     * 列出可用描述模板
     *
     * <p>🔴 <b>2026-11-24 修：以前这里返回的是「写死的假数据」</b> ——
     * 4 条硬编码的模板元数据（{@code initTemplates()} + {@code template(...)}），
     * <b>从来没查过库</b> → 表 {@code description_template} 里运营维护的模板
     * <b>在这个接口里一个都看不到</b>。</p>
     *
     * <p>📌 <b>现在真查库</b>：模板 ID 取自 {@code description_template.template_id}
     * （{@code amazon_template} 这种<b>库里真实存在</b>的值），
     * 不再是 {@code amazon_product_template} 那种<b>悬空 ID</b>。</p>
     */
    @Override
    public List<Map<String, Object>> listTemplates() {
        List<DescriptionTemplate> rows =
                descriptionTemplateService.listEnabled(null, null);
        if (rows.isEmpty()) {
            // 📌 库里没有启用中的模板 → **返回空列表**（不编 4 条假的）
            log.info("描述模板表里没有启用中的模板 —— 返回空列表");
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (DescriptionTemplate row : rows) {
            list.add(template(row));
        }
        return list;
    }

    /**
     * 落库模板 → 前端视图
     *
     * <p>📌 <b>字段名保持和前端契约一致</b>（前端 {@code erp-web/src/api/ai.ts}
     * 的 {@code descriptionApi.templates()} 拿到的就是这个形状）。</p>
     */
    private Map<String, Object> template(DescriptionTemplate row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.getTemplateId());
        map.put("templateId", row.getTemplateId());
        map.put("name", row.getName());
        map.put("platform", row.getPlatform());
        map.put("language", row.getLanguage());
        // 📌 **2026-11-25 修：以前这里写的是 `map.put("tone", row.getLanguage())`**
        //    → **把「语言」当成「语气」返回**（zh 被当成 tone）。
        //    🔴 后果：前端拿到 `tone: "zh"` → 语气下拉框里是个语言代码，
        //    而模板本身**根本没有 tone 这一列**（DDL 里只有 language）。
        //    → 正确做法：**不编**（规则 72）—— 表里没有 tone 就别返回 tone，
        //      让调用方知道「这个模板没有语气属性」，而不是给一个假值。
        //      前端 `tone` 字段有自己的来源（请求里的 style 语气），与模板无关。
        map.put("sample", row.getTemplateContent());
        map.put("maxLength", row.getMaxLength());
        map.put("variables", descriptionTemplateRenderer.parseVariables(row));
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

        // 🔴 **2026-11-19 修：前端入口把「真实商品数据」丢了 → LLM 只能吐占位符模板**
        //    📌 实测（真起容器 + 真 LLM + 真 MySQL）：
        //       前端请求 `{sku: "SKU-...", language: "en"}` → 返回
        //       `### Amazon Product Listing Template` + `[Brand] [Product Name]` 占位符
        //       → 页面上的「AI 生成商品描述」= **一份空模板**。
        //    📌 **根因**：`generateForFrontend` **从不查商品主数据** → 即使有 sku，
        //          也只把 sku 字符串当商品名，**真实 description/特征一个都没进 prompt**
        //          → LLM 拿不到任何商品事实 → 只能输出带 `[Product Name]` 的模板。
        //    ✅ 修法：**按 productId / sku 查真实商品主数据** → 把真实商品名 / 描述 /
        //       派生特征填进 prompt。查不到 → 保留原行为（**不编数据**），
        //       但明确告诉 LLM「不要输出占位符」。
        ProductBrief brief = lookupBrief(req);
        if (brief != null) {
            internal.setProductId(brief.getId());
            // 📌 **按目标语言选商品名**（2026-11-19 实测）：
            //    英文场景原来用的是**中文名** → `title='无线蓝牙耳机 Pro'` 而文案是英文
            //    → **中英混搭**。现在 en → 优先 `product_name_en`，没维护则回退中文名。
            String localizedName = pickLocalizedName(brief, req.getLanguage());
            if (localizedName != null && !localizedName.isBlank()) {
                internal.setProductName(localizedName);
            }
            if (brief.getDescription() != null && !brief.getDescription().isBlank()) {
                internal.setFeatures(splitIntoPhrases(brief.getDescription()));
                // 📌 关键词：真实描述的短句 + 真实商品名（与 `getProductKeywords` 同一口径）
                LinkedHashSet<String> derived = new LinkedHashSet<>(internal.getFeatures());
                derived.add(localizedName == null ? brief.getProductName().trim() : localizedName.trim());
                internal.setKeywords(derived.stream().limit(MAX_KEYWORDS).toList());
            }
        } else {
            // 📌 **没有真实商品数据** → 明确告诉 LLM「不要输出占位符模板」
            //    （否则它会自作主张地吐 `[Product Name]` 这种空模板）
            String noTemplateHint = "请基于给定信息撰写；若信息不足，请用真实通用的描述，"
                    + "**不要输出 [Product Name] 之类的占位符**。";
            internal.setCustomPrompt(internal.getCustomPrompt() == null
                    ? noTemplateHint
                    : internal.getCustomPrompt() + " " + noTemplateHint);
        }

        DescriptionGenerationResponse response = generateDescription(internal);

        FrontendDescriptionResponse out = new FrontendDescriptionResponse();
        out.setTemplateId(response.getTemplateId());
        out.setGenerationTime(response.getGenerationTime());
        out.setError(response.getError());
        // 📌 透传「是不是模拟生成」—— 前端要能告诉用户「这是模板，不是真 AI」
        out.setMock(response.getMock());
        out.setMockReason(response.getMockReason());

        // 📌 2026-11-18：把**真实** token 用量也透传给前端
        //    （之前这个 DTO 根本没有 usage 字段 → 前端永远拿不到用量）
        if (response.getUsage() != null) {
            FrontendDescriptionResponse.TokenUsage frontUsage = new FrontendDescriptionResponse.TokenUsage();
            frontUsage.setPromptTokens(response.getUsage().getPromptTokens());
            frontUsage.setCompletionTokens(response.getUsage().getCompletionTokens());
            frontUsage.setTotalTokens(response.getUsage().getTotalTokens());
            out.setUsage(frontUsage);
        }

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
        // 📌 **2026-11-14 改为真查库** —— 原来查的是内存 List
        //    （服务重启即丢，且与落库表形成两套真相）。
        //    现在统一以 {@code product_description} 表为唯一真相。
        List<com.crossborder.productdescription.entity.ProductDescription> rows =
                productDescriptionRecordService.listHistory(productId, max);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toGenerationResponse).toList();
    }

    /**
     * 落库记录 → 前端视图。
     *
     * <p>📌 落库表里没有 SEO 明细（seoResult / usage 那些是生成时的临时结果），
     * 所以这里**只填表里真有的字段**，其余留空 ——
     * 不编造（规则 72：数字必须来自真正用的那张表）。</p>
     */
    private DescriptionGenerationResponse toGenerationResponse(
            com.crossborder.productdescription.entity.ProductDescription row) {
        DescriptionGenerationResponse out = new DescriptionGenerationResponse();
        out.setDescriptionId(row.getId() == null ? null : String.valueOf(row.getId()));
        out.setProductId(row.getProductId());
        out.setProductName(row.getProductName());
        out.setContent(row.getContent());
        out.setShortTitle(row.getShortTitle());
        out.setLanguage(row.getLanguage());
        out.setPlatform(row.getPlatform());
        out.setVersionNumber(row.getVersionNumber());
        out.setModel(row.getModel());
        out.setTemplateId(row.getTemplateId());
        out.setGenerationTime(row.getGenerationTimeMs());
        out.setCreateTime(row.getCreateTime());
        // 📌 model == "mock" → 这次是本地模板拼的（降级），如实标出来
        if (MOCK_MODEL_NAME.equals(row.getModel())) {
            out.setMock(Boolean.TRUE);
            out.setMockReason("未配置 LLM API Key，本次由本地模板生成");
        }
        return out;
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
    private String buildPrompt(DescriptionGenerationRequest request, DescriptionTemplate template) {
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
            prompt.append("\n");
        }

        // 📌 **2026-11-24 新增：模板正文真正进 prompt**
        //    🔴 **修它之前，模板是一个「装饰品」**：
        //    `DescriptionGenerationRequest.useTemplate` / `templateId`
        //    两个入参**从头到尾没人读过** → 运营在 `description_template`
        //    表里改模板，对生成结果**零影响**（表本身也没人读）。
        //    → 现在按「请求指定 → 平台默认」的顺序取模板，
        //      把渲染后的正文作为**结构要求**交给 LLM。
        // 📌 **2026-11-25**：模板由调用方**传入**（不再在这里重新解析）
        //    → 保证「进 prompt 的模板」与「写进响应 templateId 的模板」**永远是同一个**。
        String templateSection = buildTemplateSection(request, template);
        if (!templateSection.isBlank()) {
            prompt.append("\n").append(templateSection);
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
     * 构建「模板要求」段落（{@code 2026-11-24 新增}）
     *
     * <p>🔴 <b>这个方法存在的理由</b>：模板正文<b>从来没进过 prompt</b> ——
     * {@code useTemplate} / {@code templateId} 是两个死参数，
     * 表 {@code description_template} 里的模板内容是「装饰品」。</p>
     *
     * <p>📌 <b>取模板的顺序</b>：
     * <ol>
     *   <li>{@code useTemplate == false} → <b>不用模板</b>（尊重调用方）；</li>
     *   <li>请求指定了 {@code templateId} → 按它取；<b>取不到就明确记 warn</b>
     *       （不静默换模板 —— 否则调用方以为自己用了 A 模板，实际用了 B）；</li>
     *   <li>没指定 → 按<b>目标平台</b>取默认模板。</li>
     * </ol>
     *
     * <p>📌 <b>取不到模板 → 返回空字符串</b>（不编模板正文），
     * 这一次的生成就按「无模板」走 —— 但**留痕**，因为
     * 「模板没生效」是需要有人知道的事。</p>
     */
    private String buildTemplateSection(DescriptionGenerationRequest request, DescriptionTemplate template) {
        if (Boolean.FALSE.equals(request.getUseTemplate())) {
            return "";
        }
        if (template == null) {
            return "";
        }
        String rendered = descriptionTemplateRenderer.render(template, templateVariables(request));
        if (rendered == null || rendered.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("请严格按下面的模板结构撰写描述（保留各段落标题，填入真实商品信息）：\n");
        sb.append(rendered);
        return sb.toString();
    }

    /**
     * 按「请求指定 → 平台默认」的顺序取模板。
     */
    private DescriptionTemplate resolveTemplate(DescriptionGenerationRequest request) {
        String requestedId = request.getTemplateId();
        if (requestedId != null && !requestedId.isBlank()) {
            try {
                return descriptionTemplateService.findByTemplateId(requestedId).orElse(null);
            } catch (Exception e) {
                // 📌 模板表不可用 → 记 error，**不静默**（模板表挂了要有人知道）
                log.error("读取描述模板失败（templateId={}）：{}", requestedId, e.getMessage());
                return null;
            }
        }
        try {
            return descriptionTemplateService
                    .findDefault(request.getTargetPlatform(), request.getTargetLanguage())
                    .orElse(null);
        } catch (Exception e) {
            log.error("读取默认描述模板失败（platform={}）：{}",
                    request.getTargetPlatform(), e.getMessage());
            return null;
        }
    }

    /**
     * 模板变量取值（把占位符换成<b>真实商品事实</b>）
     *
     * <p>📌 取不到的值<b>不放进 map</b> → 渲染器会<b>原样保留占位符</b>，
     * 而不是编一个假值填进去（规则 72）。</p>
     */
    private Map<String, String> templateVariables(DescriptionGenerationRequest request) {
        Map<String, String> vars = new LinkedHashMap<>();
        putIfPresent(vars, "产品名称", request.getProductName());
        putIfPresent(vars, "产品介绍", request.getProductName());
        putIfPresent(vars, "核心卖点", joinFeatures(request.getFeatures()));
        putIfPresent(vars, "产品特性", joinFeatures(request.getFeatures()));
        putIfPresent(vars, "亮点列表", joinFeatures(request.getFeatures()));
        putIfPresent(vars, "适用场景", joinKeywords(request.getKeywords()));
        putIfPresent(vars, "SEO关键词", joinKeywords(request.getKeywords()));
        putIfPresent(vars, "产品特点", joinFeatures(request.getFeatures()));
        putIfPresent(vars, "核心优势", joinFeatures(request.getFeatures()));
        return vars;
    }

    private static void putIfPresent(Map<String, String> vars, String key, String value) {
        if (value != null && !value.isBlank()) {
            vars.put(key, value);
        }
    }

    private static String joinFeatures(List<String> features) {
        return features == null || features.isEmpty() ? null : String.join("、", features);
    }

    private static String joinKeywords(List<String> keywords) {
        return keywords == null || keywords.isEmpty() ? null : String.join("、", keywords);
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
                LLMCall call = callRealLLMAPI(prompt);
                return LLMAnswer.real(call.content(), call.usage());
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
    public record LLMAnswer(String content, boolean mock, String reason,
                             com.crossborder.erp.llm.LLMTokenUsage usage) {

        public static LLMAnswer real(String content) {
            return new LLMAnswer(content, false, null, null);
        }

        public static LLMAnswer real(String content, com.crossborder.erp.llm.LLMTokenUsage usage) {
            return new LLMAnswer(content, false, null, usage);
        }

        public static LLMAnswer mock(String content, String reason) {
            return new LLMAnswer(content, true, reason, null);
        }
    }

    /**
     * 一次真实 LLM 调用的结果：**内容 + 真实 token 用量**。
     *
     * <p>📌 usage 必须是**响应体里的真值**，不能本地估算（规则 18/19/391）。
     */
    private record LLMCall(String content, com.crossborder.erp.llm.LLMTokenUsage usage) {
    }

    /**
     * 调用真实LLM API
     */
    private LLMCall callRealLLMAPI(String prompt) {
        // 📌 **端点拼接的唯一事实来源 = LLMEndpointResolver**
        // 📌 之前这里写死 `baseUrl + "/v1/chat/completions"`，
        //    而 application.yml 的 base-url 默认带 /v1 → 拼出 **双 /v1** → 必然 404
        //    → catch 之后静默降级成模拟生成 → **AI 从来没真的调通过**
        String apiUrl = LLMEndpointResolver.resolveApiUrl(
                llmConfig.getBaseUrl(),
                llmConfig.getProvider(),
                llmConfig.getDeployment(),
                llmConfig.getApiVersion());

        // 📌 2026-11-18：**Anthropic 协议**（路径/头/请求体/响应体全和 OpenAI 兼容不一样）
        boolean anthropic = LLMEndpointResolver.isAnthropic(llmConfig.getProvider());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());

        final String systemPrompt =
                "你是一个专业的电商产品描述撰写专家，擅长生成吸引人的产品描述和SEO优化的内容。";
        if (anthropic) {
            // 📌 Anthropic：`system` 是**顶层字段**，messages 里只有 user
            requestBody.put("system", systemPrompt);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        } else {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", llmConfig.getTemperature());
        params.put("max_tokens", llmConfig.getMaxTokens());
        requestBody.putAll(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 📌 Azure 用 `api-key` 头，Anthropic 用 `x-api-key` + `anthropic-version`，
        //    其他家（OpenAI 兼容协议）用 `Authorization: Bearer`
        if (anthropic) {
            headers.set(LLMEndpointResolver.ANTHROPIC_API_KEY_HEADER, llmConfig.getApiKey());
            headers.set("anthropic-version", LLMEndpointResolver.ANTHROPIC_VERSION);
        } else if (LLMEndpointResolver.isAzure(llmConfig.getProvider())) {
            headers.set("api-key", llmConfig.getApiKey());
        } else {
            headers.setBearerAuth(llmConfig.getApiKey());
        }
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, Map.class);
            
            if (response.getBody() != null) {
                // 📌 **真实 token 用量**（从响应体的 usage 字段透传；拿不到 = null，不估算）
                com.crossborder.erp.llm.LLMTokenUsage usage =
                        LLMEndpointResolver.extractTokenUsage(response.getBody(), anthropic);
                if (anthropic) {
                    // 📌 Anthropic 响应：`content[]` 是内容块数组（text 块取 `text`）
                    String text = extractAnthropicText(response.getBody());
                    if (text != null) {
                        return new LLMCall(text, usage);
                    }
                } else {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        return new LLMCall((String) message.get("content"), usage);
                    }
                }
            }
            throw new RuntimeException("LLM API响应为空");
        } catch (Exception e) {
            log.error("LLM API调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM API调用失败", e);
        }
    }

    /**
     * 从 Anthropic 协议的响应体里提取文本。
     *
     * <p>📌 形状：{@code {"content":[{"type":"text","text":"..."}]}}
     * → 和 OpenAI 的 {@code choices[0].message.content} <b>完全不同</b>。
     *
     * @param body 响应体
     * @return 拼接后的文本；没有可用文本时返回 null
     */
    @SuppressWarnings("unchecked")
    private String extractAnthropicText(Map<String, Object> body) {
        // 📌 协议形状的唯一事实来源在 erp-llm-common（三个 AI 调用点共用，别各写一份）
        return LLMEndpointResolver.extractAnthropicText(body);
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

        // ⚠️ null 安全（2026-11-15 修）：description 现在可能真的为 null
        // （商品没写描述 → 不编内容）。原来这里直接 text.contains(word)
        // → 商品没描述时 SEO 接口 **NPE 500**。
        if (text == null || text.isBlank()) {
            return keywords;
        }

        // 简化版：提取一些常见词
        String[] commonWords = {"产品", "性能", "质量", "可靠", "技术",
                "工艺", "稳定", "耐用", "理想", "选择"};

        for (String word : commonWords) {
            if (text.contains(word)) {
                keywords.add(word);
            }
        }

        // 🔴 **2026-11-19 修：英文（跨境电商主力语言）原本一个关键词都抽不出来**
        //    📌 实测（真起容器 + 真 LLM + 真查库）：
        //       `language=en` → 描述 1,180~1,300 字符的英文营销文案
        //       → `seoKeywords` = **[]**（一个都没有）→ 页面上的「SEO 优化」在英文场景**完全不可用**
        //       → 原因：上面那张 `commonWords` 是**硬编码的 11 个中文词**
        //          → 英文描述里一个都匹配不上 → 恒返回空
        //       → 后果：`seoKeywords` 只能靠「请求参数 fallback」撑着
        //          （不传 keywords 就真的是空）→ **请求里没传 keywords 时 SEO 关键词 = 空**
        //    ✅ 修法（**零额外 LLM 调用、零 prompt 变更**）：加英文词频抽取
        //       · 按词频降序（频次相同按首次出现顺序）→ 天然偏向「被反复强调的特性词」
        //       · 停用词表只放**功能词**（the/and/with…），**不放商品属性词**
        //         （portable / durable 这类恰恰是要当 SEO 关键词的）
        keywords.addAll(extractEnglishKeywords(text));

        return keywords;
    }

    /**
     * 英文关键词抽取（词频 + 停用词 + 长度过滤）。
     *
     * <p>📌 **2026-11-19 新增**：原 {@code extractKeywords} 只认 11 个硬编码中文词
     * → 英文描述（跨境电商主力）恒空 → SEO 关键词功能在英文场景**实际不可用**。</p>
     *
     * <p>📌 **只取单词、不取短语**：SEO 关键词表要的是「能被搜到」的词，
     * 短语（n-gram）会把 {@code fast charging} 这类拆成两个词，反而降低召回。</p>
     */
    private List<String> extractEnglishKeywords(String text) {
        java.util.Map<String, Integer> freq = new java.util.LinkedHashMap<>();
        // 📌 **先剥掉 markdown 结构**（2026-11-19 实测加的）：
        //   真 LLM 每次输出的**结构不一样**（有时有 "### Product Title"、有时有
        //   "**Bullet Points:**"、有时带 markdown 链接）→ 不剥离就会把
        //   **结构标记词**（bullet / points / search / title）当成 SEO 关键词
        //   （实测某一轮的前 5 个关键词里有 3 个是这种噪音）。
        //   → 剥掉后剩下的才是**真正的描述正文** → 词频法才准。
        String plain = stripMarkdownStructure(text);

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z][A-Za-z\\-]{2,}")
                .matcher(plain);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(java.util.Locale.ROOT);
            if (word.length() < 3 || word.length() > 24) {
                continue;
            }
            if (EN_STOP_WORDS.contains(word)) {
                continue;
            }
            freq.merge(word, 1, Integer::sum);
        }
        if (freq.isEmpty()) {
            return List.of();
        }
        // 📌 频次降序；频次相同 → 按**首次出现顺序**（LinkedHashMap 保序）→ 稳定输出
        return freq.entrySet().stream()
                .sorted((a, b) -> {
                    int byCount = Integer.compare(b.getValue(), a.getValue());
                    return byCount != 0 ? byCount
                            : Integer.compare(indexOfFirst(text, a.getKey()), indexOfFirst(text, b.getKey()));
                })
                .limit(12)
                .map(java.util.Map.Entry::getKey)
                .toList();
    }

    /** 词在文本里首次出现的下标（用于同频次时保持稳定顺序）。 */
    private int indexOfFirst(String text, String word) {
        int idx = text.toLowerCase(java.util.Locale.ROOT).indexOf(word);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    /**
     * 剥掉 markdown 结构标记，只留描述正文。
     *
     * <p>📌 **2026-11-19 新增**：LLM 输出的结构每次都不一样（标题行、粗体、列表小标题、
     * markdown 链接）→ 这些**结构标记词**（bullet / points / search / title）会被词频法
     * 当成 SEO 关键词 → 必须先剥掉。</p>
     */
    private String stripMarkdownStructure(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String s = text;
        // ⚠️ **必须用 `[ \\t]*` 而不是 `\\s*`** —— `\s` **包含换行**（规则 227 的量词位置坑）
        //    → `^\\s*` 在 MULTILINE 下会跨行吃掉下一行 → 匹配范围错位。
        // ① 标题行（### Product Title）→ 整行删掉
        s = java.util.regex.Pattern.compile("(?m)^[ \\t]*#{1,6}[ \\t]+[^\\n]*\\n?")
                .matcher(s).replaceAll("");
        // ② markdown 链接 [text](url) → 只留 text
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        // ⚠️ **方括号占位符要先删**（2026-11-19 实测加的）：
        //   真实 LLM 在**没有真实商品数据**时会返回**带占位符的模板**：
        //   `[Product Name]` / `[material/technology]` / `[key feature 1]` / `[Size/Capacity]`
        //   → 占位符里的词（material / key / size / feature / capacity）会被词频法
        //      当成 SEO 关键词 → **全是假的**（实测抽出来的前 5 个 **5/5 都是占位符词**）。
        //   → 占位符**永远不是**买家会搜的词 → 必须整个删掉，而不是只删方括号。
        s = s.replaceAll("\\[[^\\]\\n]*\\]", " ");
        // ③ 列表符号（保留列表项内容——那是有价值的正文）
        s = java.util.regex.Pattern.compile("(?m)^[ \\t]*[-*+][ \\t]+").matcher(s).replaceAll("");
        // ③' **规格表的字段标签**（2026-11-19 实测加的）：
        //   LLM 模板里常有 "Size/Capacity: **[Size/Capacity]**" / "Material: **[Material]**"
        //   → 冒号**前**的标签词（size / capacity / material / color / brand）
        //   → 这些是**字段名**，不是商品属性 → 同样不进 SEO 关键词。
        //   （占位符里的已由 ② 删除；这里是**标签本身**）
        s = java.util.regex.Pattern.compile("(?m)^[ \\t]*[A-Za-z][A-Za-z /]{0,30}:[ \\t*]*$")
                .matcher(s).replaceAll("");
        // ④ 粗体/斜体/行内代码标记
        s = s.replaceAll("[*_`]+", " ");
        return s;
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
            // 🔴 **2026-11-28 修**：标题原来写死成 "核心优势 1" / "核心优势 2" ——
            //    那是**占位符，不是真实特性**。
            //    后果：`product_highlights.highlight_title` 是 NOT NULL 列，
            //    亮点落库后表里全是「核心优势 1/2/3」→ 运营翻历史记录看到的是
            //    一堆无意义的编号，而不是「这个商品的卖点到底是什么」。
            //    ✅ 现在标题直接用**真实特性文本**（与 description/feature 同一个来源）。
            highlight.setTitle(features.get(i));
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
     *
     * <p>📌 <b>以前这里是硬编码的</b> {@code Arrays.asList("高性能", "稳定可靠", "易于使用",
     * "节能环保", "设计精巧")} —— <b>完全忽略 productId</b>，
     * 于是任何商品（连库里不存在的 id）拿到的都是同一批文案。
     * 实测：{@code GET /api/description/highlights/1} 与 {@code /999} 返回<b>一字不差</b>。
     * 这正是「去假数据」重构（{@code 646dc06}）要消灭的东西，但描述模块漏了。</p>
     *
     * <p>📌 <b>现在从真实商品描述派生</b>：商品表没有独立的 features 列
     * （{@code t_product} 只有 {@code description} / {@code description_en}），
     * 所以特性 = 把<b>真实描述</b>按常见分隔符切开后的短语列表。</p>
     *
     * <p>📌 <b>取不到就返回空列表，不编</b>：
     * 下游 {@code extractHighlights(features, max)} 遇到空列表会<b>返回空列表</b>，
     * 而不是编 5 条亮点 —— <b>「没有数据」比「假数据」诚实</b>。</p>
     */
    private List<String> getProductFeatures(Long productId) {
        return splitIntoPhrases(getProductDescription(productId));
    }

    /**
     * 按「productId 优先 → sku 兼底」查真实商品主数据。
     *
     * <p>📌 **2026-11-19 新增**：前端入口（{@code generateForFrontend}）之前
     * **从不查商品主数据** → LLM 拿不到商品事实 → 只能吐 {@code [Product Name]}
     * 这种占位符空模板（实测）。</p>
     *
     * <p>📌 **两种 SKU 口径都要认**（同 {@code ProductCatalogServiceImpl}）→ 这里直接
     * 复用 {@code findBySku}，不自己拼 SQL。</p>
     *
     * <p>📌 **查不到就返回 {@code null}**（**不编数据**），由调用方决定降级行为。</p>
     */
    /**
     * 按目标语言挑商品名（2026-11-19 新增）。
     *
     * <p>📌 **实测坑**：英文场景下用中文商品名 → 输出「中英混搭」
     * （实测 {@code title='无线蓝牙耳机 Pro'} 而文案全英文）。</p>
     *
     * <p>📌 **没维护英文名就回退中文名**（不编名字）。</p>
     */
    private String pickLocalizedName(ProductBrief brief, String language) {
        if (brief == null) {
            return null;
        }
        boolean english = language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en");
        if (english) {
            if (brief.getProductNameEn() != null && !brief.getProductNameEn().isBlank()) {
                return brief.getProductNameEn().trim();
            }
        }
        return brief.getProductName();
    }

    private ProductBrief lookupBrief(FrontendDescriptionRequest req) {
        if (req == null) {
            return null;
        }
        if (req.getProductId() != null) {
            try {
                ProductBrief byId = productCatalogService.findById(req.getProductId()).orElse(null);
                if (byId != null) {
                    return byId;
                }
            } catch (BizException e) {
                log.warn("按 productId 查商品失败（id={}），降级按 sku 查：{}",
                        req.getProductId(), e.getMessage());
            }
        }
        if (req.getSku() != null && !req.getSku().isBlank()) {
            try {
                return productCatalogService.findBySku(req.getSku().trim()).orElse(null);
            } catch (BizException e) {
                log.warn("按 SKU 查商品失败（sku={}），本次不编商品数据：{}",
                        req.getSku(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * 获取产品关键词     *
     * <p>📌 <b>以前这里是硬编码的</b> {@code Arrays.asList("高性能", "节能", "环保", "智能")}
     * —— 同样完全忽略 productId。</p>
     *
     * <p>📌 <b>现在从真实商品名 + 真实描述派生</b>：
     * 关键词 = 描述短句 + 商品名（去重、限长）。
     * 取不到 → 返回空列表（不编）。</p>
     */
    private List<String> getProductKeywords(Long productId) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.addAll(splitIntoPhrases(getProductDescription(productId)));

        String productName = getProductNameById(productId);
        if (productName != null && !productName.isBlank() && !productName.startsWith("产品-")) {
            keywords.add(productName.trim());
        }

        return keywords.stream().limit(MAX_KEYWORDS).toList();
    }

    /**
     * 获取产品描述
     *
     * <p>📌 <b>以前这里是硬编码的</b>
     * {@code "这是一个高质量的产品，具有优秀的性能和可靠性..."} ——
     * 一句<b>跟任何商品都无关</b>的套话。</p>
     *
     * <p>📌 <b>现在读真实商品主数据</b>（{@code erp_product.t_product.description}）。
     * 查不到 → 返回 {@code null}（<b>不编</b>），由调用方决定怎么处理。</p>
     */
    private String getProductDescription(Long productId) {
        try {
            ProductBrief product = productCatalogService.findById(productId).orElse(null);
            if (product != null && product.getDescription() != null
                    && !product.getDescription().isBlank()) {
                return product.getDescription().trim();
            }
            log.warn("商品主数据里没有 id={} 的描述 —— 本次不编内容（仅用于提示词，不落库）", productId);
        } catch (BizException e) {
            log.warn("查询商品描述失败（id={}），本次不编内容（仅用于提示词，不落库）：{}", productId, e.getMessage());
        }
        return null;
    }

    /**
     * 把一段文本切成短语列表（特性/关键词的来源）。
     *
     * <p>📌 分隔符：换行 / 中英文逗号 / 顿号 / 分号 / 句号 / 竖线。
     * 切出来的每一段再按句内分隔符（空格）取前几个词，避免整句话当一条特性。</p>
     */
    private List<String> splitIntoPhrases(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = text.split("[\\r\\n,，、;；。|｜]+");
        List<String> phrases = new ArrayList<>();
        for (String part : parts) {
            String phrase = part.trim();
            if (phrase.isEmpty()) {
                continue;
            }
            // 整句太长时取前 N 个词作为一条短语（避免把一整段当「一个特性」）
            String[] words = phrase.split("\\s+");
            if (words.length > MAX_PHRASE_WORDS) {
                phrase = String.join(" ", java.util.Arrays.copyOfRange(words, 0, MAX_PHRASE_WORDS));
            }
            if (phrase.length() > MAX_PHRASE_LENGTH) {
                phrase = phrase.substring(0, MAX_PHRASE_LENGTH);
            }
            phrases.add(phrase);
            if (phrases.size() >= MAX_FEATURES) {
                break;
            }
        }
        return phrases;
    }
}
