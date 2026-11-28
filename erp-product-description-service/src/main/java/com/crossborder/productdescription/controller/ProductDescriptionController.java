package com.crossborder.productdescription.controller;

import com.crossborder.productdescription.dto.AbTestOutcomeRequest;
import com.crossborder.productdescription.dto.AbTestStartRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveResponse;
import com.crossborder.productdescription.entity.AbTestResult;
import com.crossborder.productdescription.entity.ProductHighlight;
import com.crossborder.productdescription.entity.SeoOptimization;
import com.crossborder.productdescription.service.DescriptionAnalysisRecordService;
import com.crossborder.productdescription.service.ProductDescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产品描述生成控制器
 */
@RestController
@RequestMapping("/api/v1/product-description")
@RequiredArgsConstructor
@Tag(name = "产品描述生成", description = "AI驱动的产品描述生成")
public class ProductDescriptionController {

    private final ProductDescriptionService productDescriptionService;

    /**
     * 描述分析结果落库服务（历史 SEO / 亮点 / A-B 查询）
     *
     * <p>📌 <b>2026-11-28 新增</b>：以前「查历史」这件事<b>没有任何地方存过</b>
     * → 现在补上三个查询端点，让运营能翻回当时的评分与结果。</p>
     */
    private final DescriptionAnalysisRecordService descriptionAnalysisRecordService;

    @Operation(summary = "生成产品描述（前端接口）")
    @PostMapping("/generate")
    public FrontendDescriptionResponse generateDescription(
            @RequestBody FrontendDescriptionRequest request) {
        return productDescriptionService.generateForFrontend(request);
    }

    @Operation(summary = "生成产品描述（内部接口，完整参数）")
    @PostMapping("/generate/internal")
    public DescriptionGenerationResponse generateDescriptionInternal(
            @RequestBody DescriptionGenerationRequest request) {
        return productDescriptionService.generateDescription(request);
    }

    @Operation(summary = "批量生成产品描述")
    @PostMapping("/batch-generate")
    public List<DescriptionGenerationResponse> batchGenerateDescription(
            @RequestBody List<DescriptionGenerationRequest> requests) {
        return productDescriptionService.batchGenerateDescription(requests);
    }

    @Operation(summary = "生成多语言描述")
    @PostMapping("/multi-language")
    public List<DescriptionGenerationResponse> generateMultiLanguageDescription(
            @RequestParam Long productId,
            @RequestParam List<String> languages) {
        return productDescriptionService.generateMultiLanguageDescription(productId, languages);
    }

    @Operation(summary = "SEO优化")
    @PostMapping("/seo-optimize")
    public DescriptionGenerationResponse.SEOResult optimizeSEO(
            @RequestParam Long productId,
            @RequestParam String platform) {
        return productDescriptionService.optimizeSEO(productId, platform);
    }

    @Operation(summary = "提炼产品亮点")
    @GetMapping("/highlights/{productId}")
    public List<DescriptionGenerationResponse.Highlight> extractHighlights(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "5") Integer maxHighlights) {
        return productDescriptionService.extractHighlights(productId, maxHighlights);
    }

    @Operation(summary = "生成A/B测试版本")
    @PostMapping("/ab-test")
    public List<DescriptionGenerationResponse> generateABTestVersions(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "2") Integer versionCount) {
        return productDescriptionService.generateABTestVersions(productId, versionCount);
    }

    /**
     * 保存产品描述（<b>前端契约</b>，按 SKU 定位商品）
     *
     * <p>前端 {@code erp-web/src/api/ai.ts} 的
     * {@code descriptionApi.save()} → {@code POST /api/description/save}
     * （网关改写成 {@code /api/v1/product-description/save}）。</p>
     *
     * <p>📌 <b>为什么这个路径归前端契约</b>：前端只有 SKU，
     * 而雪花 ID 是 19 位（JS Number 装不下）→ 让前端传 productId 必然出精度问题。
     * 由后端解析 SKU → productId，解析不到就<b>明确报错</b>。</p>
     */
    @Operation(summary = "保存产品描述（前端接口，按 SKU）")
    @PostMapping("/save")
    public FrontendDescriptionSaveResponse saveDescription(
            @Valid @RequestBody FrontendDescriptionSaveRequest request) {
        return productDescriptionService.saveFrontendDescription(request);
    }

    /**
     * 保存描述（<b>内部接口</b>，完整参数）
     *
     * <p>📌 <b>为什么从 {@code /save} 改成 {@code /save/internal}</b>：
     * {@code /save} 这个路径现在归「前端契约（按 SKU 存）」——
     * 两个接口的请求体形状完全不同（一个收 sku，一个收 productId），
     * 同路径只能靠内容区分，Spring 会直接抛
     * {@code Ambiguous mapping} → <b>整个服务起不来</b>。
     * 改法参照本 Controller 已有的 {@code /generate}（前端）与
     * {@code /generate/internal}（内部）的命名惯例。</p>
     *
     * <p>⚠️ 全仓已核对：除前端（它打的是前端契约那个 /save）外，
     * 没有其它地方调用旧的 {@code POST /api/v1/product-description/save}。</p>
     */
    @Operation(summary = "保存描述（内部接口，完整参数）")
    @PostMapping("/save/internal")
    public String saveDescriptionInternal(@RequestBody DescriptionGenerationResponse response) {
        Boolean saved = productDescriptionService.saveDescription(response);
        return saved ? "描述保存成功！" : "描述保存失败！";
    }

    @Operation(summary = "获取产品所有描述")
    @GetMapping("/list/{productId}")
    public List<DescriptionGenerationResponse> getProductDescriptions(@PathVariable Long productId) {
        return productDescriptionService.getProductDescriptions(productId);
    }

    @Operation(summary = "批量导入产品")
    @PostMapping("/batch-import")
    public String batchImportProducts(@RequestBody List<Long> productIds) {
        return productDescriptionService.batchImportProducts(productIds);
    }

    /**
     * 获取可用描述模板
     *
     * <p>前端 erp-web/src/api/ai.ts 的 {@code descriptionApi.templates()}
     * → {@code GET /api/description/templates}，之前后端没这个接口 → 404。</p>
     */
    @Operation(summary = "获取可用描述模板")
    @GetMapping("/templates")
    public List<Map<String, Object>> templates() {
        return productDescriptionService.listTemplates();
    }

    /**
     * 获取描述生成历史
     *
     * <p>前端 {@code descriptionApi.history(params)} → {@code GET /api/description/history}。</p>
     */
    @Operation(summary = "获取描述生成历史")
    @GetMapping("/history")
    public List<DescriptionGenerationResponse> history(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "20") Integer limit) {
        return productDescriptionService.listHistory(productId, limit == null ? 20 : limit);
    }

    /**
     * 查询某条描述的历史 SEO 优化结果（表 {@code seo_optimization}）
     *
     * <p>📌 <b>2026-11-28 新增</b>：在这之前，{@code POST /seo-optimize} 算出的分数
     * <b>只活在一次 HTTP 响应里</b> → 运营事后问「上周那条文案当时打了几分？」
     * → <b>无处可查</b>。现在算一次存一次，这里能翻历史。</p>
     *
     * <p>查不到就是<b>空列表</b>（不是 null、不报错）——
     * 「还没优化过」是正常状态。</p>
     */
    @Operation(summary = "查询历史SEO优化结果")
    @GetMapping("/seo-history/{descriptionId}")
    public List<SeoOptimization> seoHistory(
            @PathVariable Long descriptionId,
            @RequestParam(defaultValue = "20") Integer limit) {
        return descriptionAnalysisRecordService.listSeoOptimizations(
                descriptionId, limit == null ? 20 : limit);
    }

    /**
     * 查询某条描述的历史产品亮点（表 {@code product_highlights}）
     *
     * <p>📌 提炼出的亮点之前也是算完就丢，现在能翻回去看。</p>
     */
    @Operation(summary = "查询历史产品亮点")
    @GetMapping("/highlight-history/{descriptionId}")
    public List<ProductHighlight> highlightHistory(@PathVariable Long descriptionId) {
        return descriptionAnalysisRecordService.listHighlights(descriptionId);
    }

    /**
     * 开启一个 A/B 测试（表 {@code ab_test_result}）
     *
     * <p>📌 <b>2026-11-28 新增</b>：{@code POST /ab-test} 之前只是「生成几个版本给你看看」，
     * 生成完<b>什么都不留</b> → 没法真正跑一次 A/B（没有起点、没有版本 ID、没有指标记录）。
     * 现在开测会落一条记录：<b>指标值留空</b>（要等真实流量才有 → 不编）。</p>
     */
    @Operation(summary = "开启A/B测试")
    @PostMapping("/ab-test/start")
    public AbTestResult startAbTest(@Valid @RequestBody AbTestStartRequest request) {
        return productDescriptionService.startAbTest(request);
    }

    /**
     * 回填 A/B 测试结果（指标实测值 → 系统判定胜者）
     */
    @Operation(summary = "回填A/B测试结果")
    @PostMapping("/ab-test/{abTestId}/outcome")
    public AbTestResult recordAbTestOutcome(
            @PathVariable Long abTestId,
            @Valid @RequestBody AbTestOutcomeRequest request) {
        return productDescriptionService.recordAbTestOutcome(abTestId, request);
    }

    /**
     * 查询某商品的 A/B 测试历史（表 {@code ab_test_result}）
     */
    @Operation(summary = "查询A/B测试历史")
    @GetMapping("/ab-test/list/{productId}")
    public List<AbTestResult> listAbTestResults(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "20") Integer limit) {
        return productDescriptionService.listAbTestResults(productId, limit == null ? 20 : limit);
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public String health() {
        return "产品描述生成服务运行中！🔥";
    }
}
