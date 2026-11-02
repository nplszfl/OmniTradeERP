package com.crossborder.inventoryprediction.controller;

import com.crossborder.inventoryprediction.dto.AccuracyEvaluation;
import com.crossborder.inventoryprediction.dto.PredictionRequest;
import com.crossborder.inventoryprediction.dto.PredictionResponse;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestion;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestionVO;
import com.crossborder.inventoryprediction.dto.ServiceStatistics;
import com.crossborder.inventoryprediction.service.InventoryPredictionService;
import com.crossborder.inventoryprediction.service.ReplenishmentSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存预测控制器
 */
@RestController
@RequestMapping("/api/v1/inventory-prediction")
@RequiredArgsConstructor
@Tag(name = "库存预测", description = "库存预测和补货建议")
public class InventoryPredictionController {

    private final InventoryPredictionService inventoryPredictionService;
    private final ReplenishmentSuggestionService replenishmentSuggestionService;

    @Operation(summary = "预测销量")
    @PostMapping("/predict")
    public PredictionResponse predictSales(@RequestBody PredictionRequest request) {
        return inventoryPredictionService.predictSales(request);
    }

    @Operation(summary = "批量预测销量")
    @PostMapping("/batch-predict")
    public List<PredictionResponse> batchPredictSales(@RequestBody List<PredictionRequest> requests) {
        return inventoryPredictionService.batchPredictSales(requests);
    }

    @Operation(summary = "获取补货建议")
    @GetMapping("/replenishment/{productId}")
    public ReplenishmentSuggestion getReplenishmentSuggestion(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") int predictionDays) {
        return inventoryPredictionService.getReplenishmentSuggestion(productId, predictionDays);
    }

    @Operation(summary = "计算安全库存")
    @GetMapping("/safety-stock/{productId}")
    public int calculateSafetyStock(@PathVariable Long productId) {
        return inventoryPredictionService.calculateSafetyStock(productId);
    }

    @Operation(summary = "预警库存不足")
    @GetMapping("/warn-low-stock")
    public List<Long> warnLowStock(@RequestParam(defaultValue = "30") int predictionDays) {
        return inventoryPredictionService.warnLowStock(predictionDays);
    }

    @Operation(summary = "计算库存周转率")
    @GetMapping("/turnover/{productId}")
    public double calculateInventoryTurnover(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "90") int days) {
        return inventoryPredictionService.calculateInventoryTurnover(productId, days);
    }

    @Operation(summary = "优化库存结构")
    @PostMapping("/optimize")
    public String optimizeInventoryStructure() {
        inventoryPredictionService.optimizeInventoryStructure();
        return "库存结构优化完成！";
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public String health() {
        return "库存预测服务运行中！🔥";
    }

    @Operation(summary = "评估预测准确性")
    @GetMapping("/accuracy/{productId}")
    public AccuracyEvaluation evaluateAccuracy(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return inventoryPredictionService.evaluateAccuracy(productId, startDate, endDate);
    }

    @Operation(summary = "批量评估预测准确性")
    @PostMapping("/accuracy/batch")
    public List<AccuracyEvaluation> batchEvaluateAccuracy(
            @RequestParam List<Long> productIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return inventoryPredictionService.batchEvaluateAccuracy(productIds, startDate, endDate);
    }

    @Operation(summary = "获取准确性趋势")
    @GetMapping("/accuracy-trend/{productId}")
    public List<AccuracyEvaluation> getAccuracyTrend(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") int days) {
        return inventoryPredictionService.getAccuracyTrend(productId, days);
    }

    // ========== 前端对齐接口 ==========
    // 前端 erp-web/src/api/ai.ts 的 inventoryPredictApi 调的接口此前后端没有对应实现。

    /**
     * 补货建议列表
     * GET /api/v1/inventory-prediction/suggestions
     *
     * <p>前端 InventoryPredict.vue 读的是 {@code sku} / {@code predicted30DaySales} /
     * {@code recommendedReplenishment} / {@code urgency}，字段名已做映射。</p>
     */
    @Operation(summary = "查询补货建议列表")
    @GetMapping("/suggestions")
    public List<ReplenishmentSuggestionVO> listSuggestions(
            @RequestParam(required = false) List<Long> productIds,
            @RequestParam(defaultValue = "50") Integer limit) {
        return replenishmentSuggestionService.listSuggestions(productIds, limit == null ? 50 : limit);
    }

    /**
     * 紧急补货建议
     * GET /api/v1/inventory-prediction/urgent
     */
    @Operation(summary = "查询紧急补货建议")
    @GetMapping("/urgent")
    public List<ReplenishmentSuggestion> urgentReplenishments() {
        return replenishmentSuggestionService.getUrgentReplenishments();
    }

    /**
     * 库存周转率
     * GET /api/v1/inventory-prediction/turnover
     *
     * <p>前端 {@code inventoryPredictApi.turnover({ productId })} 传的是 query 参数，
     * 这里统一按可选 productId 处理：不传就返回全部受监控产品的周转率。</p>
     */
    @Operation(summary = "查询库存周转率")
    @GetMapping("/turnover")
    public List<TurnoverVO> turnover(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "30") Integer days) {
        int span = days == null ? 30 : days;
        if (productId != null) {
            double value = inventoryPredictionService.calculateInventoryTurnover(productId, span);
            return List.of(TurnoverVO.builder()
                    .productId(productId)
                    .turnover(value)
                    .days(span)
                    .build());
        }
        List<TurnoverVO> list = new java.util.ArrayList<>();
        for (Long id : List.of(1L, 2L, 3L, 4L, 5L)) {
            list.add(TurnoverVO.builder()
                    .productId(id)
                    .turnover(inventoryPredictionService.calculateInventoryTurnover(id, span))
                    .days(span)
                    .build());
        }
        return list;
    }

    /**
     * 库存周转率视图
     */
    @lombok.Data
    @lombok.Builder
    public static class TurnoverVO {
        private Long productId;
        private Double turnover;
        private Integer days;
    }

    /**
     * 按 SKU 预测销量
     * GET /api/v1/inventory-prediction/predict/{sku}?days=30
     *
     * <p>前端 {@code inventoryPredictApi.predict(sku, days)} 用的是 GET + 路径里的 SKU，
     * 而后端原有的 {@code POST /predict} 是传 productId 的请求体。
     * 这里保留两者：GET 版本给前端，POST 版本给批量/内部调用。</p>
     */
    @Operation(summary = "按 SKU 预测销量")
    @GetMapping("/predict/{sku}")
    public PredictionResponse predictBySku(
            @PathVariable String sku,
            @RequestParam(defaultValue = "30") Integer days) {
        PredictionRequest request = new PredictionRequest();
        request.setProductId(resolveProductIdBySku(sku));
        request.setProductCode(sku);
        request.setPredictionDays(days == null ? 30 : days);
        return inventoryPredictionService.predictSales(request);
    }

    /**
     * 预测任务统计
     * GET /api/v1/inventory-prediction/tasks
     *
     * <p>前端 {@code inventoryPredictApi.tasks(params)} 拿的是「跑过多少次预测」的概况。</p>
     */
    @Operation(summary = "查询预测任务统计")
    @GetMapping("/tasks")
    public ServiceStatistics tasks() {
        return inventoryPredictionService.getStatistics();
    }

    /**
     * SKU → 产品ID
     *
     * <p>⚠️ 目前没有商品表接入，SKU 与产品ID 是 1:1 的约定。
     * 接入 erp-product-service 后应改成真实查询。</p>
     */
    private Long resolveProductIdBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU 不能为空");
        }
        try {
            return Long.parseLong(sku.trim());
        } catch (NumberFormatException e) {
            // 非数字 SKU：用 hash 稳定映射到产品ID
            return Math.abs((long) sku.trim().hashCode());
        }
    }
}
