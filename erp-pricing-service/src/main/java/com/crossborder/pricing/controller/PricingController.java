package com.crossborder.pricing.controller;

import com.crossborder.pricing.dto.ApplyPriceRequest;
import com.crossborder.pricing.dto.PricingRequest;
import com.crossborder.pricing.dto.PricingResponse;
import com.crossborder.pricing.dto.PricingSuggestionVO;
import com.crossborder.pricing.service.PricingService;
import com.crossborder.pricing.service.PricingSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能定价控制器
 *
 * 提供定价相关API接口
 */
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "智能定价", description = "智能定价相关接口")
public class PricingController {

    private final PricingService pricingService;
    private final PricingSuggestionService pricingSuggestionService;

    @Operation(summary = "计算最优价格")
    @PostMapping("/calculate")
    public PricingResponse calculateOptimalPrice(@RequestBody PricingRequest request) {
        return pricingService.calculateOptimalPrice(request);
    }

    @Operation(summary = "批量计算最优价格")
    @PostMapping("/batch-calculate")
    public List<PricingResponse> batchCalculateOptimalPrice(@RequestBody List<PricingRequest> requests) {
        return pricingService.batchCalculateOptimalPrice(requests);
    }

    @Operation(summary = "根据竞品调整价格")
    @PostMapping("/adjust-by-competitors/{productId}")
    public PricingResponse adjustPriceByCompetitors(@PathVariable Long productId) {
        return pricingService.adjustPriceByCompetitors(productId);
    }

    @Operation(summary = "手动调整价格")
    @PostMapping("/manual-adjust")
    public PricingResponse manualPriceAdjustment(
            @RequestParam Long productId,
            @RequestParam java.math.BigDecimal targetPrice,
            @RequestParam String reason) {
        return pricingService.manualPriceAdjustment(productId, targetPrice, reason);
    }

    @Operation(summary = "获取产品定价信息")
    @GetMapping("/info/{productId}")
    public PricingResponse getProductPricingInfo(@PathVariable Long productId) {
        return pricingService.getProductPricingInfo(productId);
    }

    // ========== 前端对齐接口 ==========
    // 前端 erp-web/src/api/ai.ts 的 pricingApi 调的 4 个接口此前后端完全没有 → 404，
    // 定价页面靠 mock 兜底撑着。这里按前端消费方式补齐。

    /**
     * 建议列表（定价任务列表）
     * GET /api/v1/pricing/tasks
     */
    @Operation(summary = "查询定价建议列表")
    @GetMapping("/tasks")
    public List<PricingSuggestionVO> listTasks(
            @RequestParam(defaultValue = "50") Integer limit) {
        return pricingSuggestionService.listSuggestions(limit == null ? 50 : limit);
    }

    /**
     * 按 SKU 查询定价建议
     * GET /api/v1/pricing/suggest/{sku}
     */
    @Operation(summary = "按 SKU 查询定价建议")
    @GetMapping("/suggest/{sku}")
    public PricingSuggestionVO suggest(@PathVariable String sku) {
        return pricingSuggestionService.getSuggestionBySku(sku);
    }

    /**
     * 按 SKU 查询价格变更历史
     * GET /api/v1/pricing/history/{sku}?days=30
     */
    @Operation(summary = "按 SKU 查询价格变更历史")
    @GetMapping("/history/{sku}")
    public List<PricingSuggestionVO> history(
            @PathVariable String sku,
            @RequestParam(defaultValue = "30") Integer days) {
        return pricingSuggestionService.getHistoryBySku(sku, days == null ? 30 : days);
    }

    /**
     * 采纳定价建议
     * POST /api/v1/pricing/apply/{sku}
     *
     * <p>请求体：{@code {"price": 315.00}}（对齐前端 pricingApi.apply）</p>
     */
    @Operation(summary = "采纳定价建议")
    @PostMapping("/apply/{sku}")
    public PricingSuggestionVO apply(
            @PathVariable String sku,
            @RequestBody ApplyPriceRequest request) {
        return pricingSuggestionService.applySuggestion(sku, request.getPrice());
    }

    // ⚠️ 健康检查不在这里放：本类的 @RequestMapping("/api/v1/pricing") 与
    // HealthController 完全相同，两者都声明 @GetMapping("/health") 会导致
    // 启动期 Ambiguous mapping 异常 —— **整个 pricing 服务起不来**。
    // 单测测不出来（不启动 Spring 上下文），只有真起服务才暴露。
    // 健康检查统一由 HealthController 提供。
}
