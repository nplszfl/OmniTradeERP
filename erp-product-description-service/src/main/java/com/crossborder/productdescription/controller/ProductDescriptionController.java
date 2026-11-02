package com.crossborder.productdescription.controller;

import com.crossborder.productdescription.dto.DescriptionGenerationRequest;
import com.crossborder.productdescription.dto.DescriptionGenerationResponse;
import com.crossborder.productdescription.dto.FrontendDescriptionRequest;
import com.crossborder.productdescription.dto.FrontendDescriptionResponse;
import com.crossborder.productdescription.service.ProductDescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "保存描述")
    @PostMapping("/save")
    public String saveDescription(@RequestBody DescriptionGenerationResponse response) {
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
