package com.crossborder.productdescription.controller;

import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionSaveResponse;
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

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public String health() {
        return "产品描述生成服务运行中！🔥";
    }
}
