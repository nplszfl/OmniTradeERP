package com.crossborder.inventoryprediction.service.impl;

import com.crossborder.inventoryprediction.dto.PredictionRequest;
import com.crossborder.inventoryprediction.dto.PredictionResponse;
import com.crossborder.inventoryprediction.entity.Inventory;
import com.crossborder.inventoryprediction.entity.PredictionTask;
import com.crossborder.inventoryprediction.entity.ReplenishmentSuggestionEntity;
import com.crossborder.inventoryprediction.entity.SalesPrediction;
import com.crossborder.inventoryprediction.repository.InventoryRepository;
import com.crossborder.inventoryprediction.repository.PredictionTaskRepository;
import com.crossborder.inventoryprediction.repository.ReplenishmentSuggestionRepository;
import com.crossborder.inventoryprediction.repository.SalesPredictionRepository;
import com.crossborder.inventoryprediction.service.InventoryPredictionService;
import com.crossborder.inventoryprediction.service.PredictionPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预测结果落库服务实现
 *
 * <p>📌 **2026-11-19 新增**：见 {@link PredictionPersistenceService} 的说明 ——
 * 本模块此前<b>没有写路径</b>，三张预测相关表永远是空的。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PredictionPersistenceServiceImpl implements PredictionPersistenceService {

    private static final String TASK_TYPE = "SALES_PREDICTION";
    private static final String MODEL_TYPE = "RULE_TREND_SEASONAL";
    private static final String STATUS_PENDING = "PENDING";
    /** 与 {@code InventoryPredictionServiceImpl.HISTORY_WINDOW_DAYS} 保持一致（90 天） */
    private static final int HISTORY_WINDOW_DAYS = 90;

    private final InventoryPredictionService predictionService;
    private final InventoryRepository inventoryRepository;
    private final com.crossborder.inventoryprediction.repository.SalesHistoryRepository salesHistoryRepository;
    private final SalesPredictionRepository salesPredictionRepository;
    private final ReplenishmentSuggestionRepository replenishmentRepository;
    private final PredictionTaskRepository predictionTaskRepository;

    @Override
    public List<Long> getMonitoredProductIds() {
        // 📌 **真查库存表** —— 有库存记录的产品才是「需要预测的产品」
        Set<Long> ids = new LinkedHashSet<>();
        for (Inventory inv : inventoryRepository.findAll()) {
            if (inv.getProductId() != null) {
                ids.add(inv.getProductId());
            }
        }
        return new ArrayList<>(ids);
    }

    @Override
    @Transactional
    public Map<String, Object> runPredictionRound(int predictionDays) {
        LocalDateTime startedAt = LocalDateTime.now();
        PredictionTask task = new PredictionTask();
        task.setTaskType(TASK_TYPE);
        task.setStatus("RUNNING");
        task.setStartTime(startedAt);
        task.setCreateTime(startedAt);
        predictionTaskRepository.save(task);

        Map<String, Object> summary = new LinkedHashMap<>();
        int productCount = 0;
        int predictionRows = 0;
        int suggestionRows = 0;
        int productsWithoutHistory = 0;
        String errorMessage = null;

        try {
            List<Long> productIds = getMonitoredProductIds();
            productCount = productIds.size();
            summary.put("monitoredProducts", productCount);

            if (productIds.isEmpty()) {
                // 📌 没有库存记录 → 如实说「没有可预测的产品」，**不编数字**
                summary.put("skippedReason", "库存表没有任何产品记录 → 无可预测对象");
                log.warn("⏭️ 预测任务：库存表没有产品记录 → 本轮不产生预测（不编造数据）");
            }

            for (Long productId : productIds) {
                try {
                    // 📌 **先查销售历史**：没历史的产品，模型会返回「日预测 = 0」——
                    //    直接落库会让读者误以为「预测这个产品会卖 0 件」，
                    //    而真相是「**没数据**」（规则 43/56：WARN 级别的「没查到」= 虚假安全感）。
                    // → 无历史 → **不写行**，只在任务摘要里如实计数。
                    List<com.crossborder.inventoryprediction.entity.SalesHistory> history =
                            salesHistoryRepository.findRecentSales(productId, LocalDate.now().minusDays(HISTORY_WINDOW_DAYS));
                    if (history == null || history.isEmpty()) {
                        productsWithoutHistory++;
                        log.warn("⏭️ 产品 {} 无销售历史 → 本轮不写预测行（不编造「预测为 0」）", productId);
                        continue;
                    }

                    PredictionRequest request = new PredictionRequest();
                    request.setProductId(productId);
                    request.setPredictionDays(predictionDays);
                    PredictionResponse response = predictionService.predictSales(request);

                    if (response == null || response.getDailyPredictions() == null
                            || response.getDailyPredictions().isEmpty()) {
                        // 📌 没有销售历史 → 预测为空 → **如实计数为 0**，不补零行
                        productsWithoutHistory++;
                        continue;
                    }

                    predictionRows += persistDailyPredictions(productId, response, request.getConfidenceLevel());
                    suggestionRows += persistReplenishmentSuggestion(productId, response);
                } catch (Exception e) {
                    // 📌 单个产品失败**不拖垮整轮** —— 记日志、继续下一个（规则 41）
                    log.warn("⚠️ 预测任务：产品 {} 处理失败（已跳过）: {}", productId, e.getMessage());
                }
            }

            summary.put("predictionRows", predictionRows);
            summary.put("suggestionRows", suggestionRows);
            summary.put("productsWithoutHistory", productsWithoutHistory);
            summary.put("predictionDays", predictionDays);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("❌ 预测任务整轮失败", e);
            throw e;
        } finally {
            LocalDateTime endedAt = LocalDateTime.now();
            task.setEndTime(endedAt);
            task.setDurationMs(java.time.Duration.between(startedAt, endedAt).toMillis());
            task.setStatus(errorMessage == null ? "SUCCESS" : "FAILED");
            task.setResultSummary(summarize(summary));
            task.setErrorMessage(errorMessage);
            predictionTaskRepository.save(task);
        }
        return summary;
    }

    /**
     * 落日粒度预测（同产品同日期唯一 → 已存在则更新，不存在则插入）。
     */
    private int persistDailyPredictions(Long productId, PredictionResponse response, Double requestedConfidence) {
        int rows = 0;
        // 📌 置信度取**请求里的** confidenceLevel（区间宽度是按它算的）
        //    —— 响应 DTO 里没有这个字段（只有 accuracy = 历史准确度），别混用。
        BigDecimal confidence = requestedConfidence == null
                ? null : BigDecimal.valueOf(requestedConfidence);
        for (PredictionResponse.DailyPrediction daily : response.getDailyPredictions()) {
            if (daily == null || daily.getDate() == null) {
                continue;
            }
            SalesPrediction row = salesPredictionRepository
                    .findByProductIdAndPredictionDate(productId, daily.getDate())
                    .orElseGet(SalesPrediction::new);
            row.setProductId(productId);
            row.setPredictionDate(daily.getDate());
            row.setPredictedSales(daily.getPredictedSales() == null ? 0 : daily.getPredictedSales().intValue());
            row.setLowerBound(daily.getLowerBound() == null ? null : daily.getLowerBound().intValue());
            row.setUpperBound(daily.getUpperBound() == null ? null : daily.getUpperBound().intValue());
            row.setConfidenceLevel(confidence);
            row.setModelType(response.getModelType() != null && !response.getModelType().isBlank()
                    ? response.getModelType() : MODEL_TYPE);
            if (row.getCreateTime() == null) {
                row.setCreateTime(LocalDateTime.now());
            }
            salesPredictionRepository.save(row);
            rows++;
        }
        return rows;
    }

    /**
     * 落补货建议（同产品的<b>待审批</b>建议更新，不刷成一片）。
     */
    private int persistReplenishmentSuggestion(Long productId, PredictionResponse response) {
        // 📌 **当前库存真查 inventory 表**（各仓库求和）—— 不拿预测值推算（那是编造）
        int currentStock = 0;
        for (Inventory inv : inventoryRepository.findByProductId(productId)) {
            currentStock += inv.getQuantity() == null ? 0 : inv.getQuantity();
        }
        int safetyStock = predictionService.calculateSafetyStock(productId);
        long predictedDemand = response.getTotalPredictedSales() == null ? 0L : response.getTotalPredictedSales();
        int suggested = (int) Math.max(0, predictedDemand - currentStock);

        ReplenishmentSuggestionEntity row = replenishmentRepository
                .findByProductIdAndStatus(productId, STATUS_PENDING)
                .orElseGet(ReplenishmentSuggestionEntity::new);
        row.setProductId(productId);
        row.setCurrentStock(currentStock);
        row.setSafetyStock(safetyStock);
        row.setPredictedDemand(predictedDemand);
        row.setSuggestedQuantity(suggested);
        row.setUrgency(suggested <= 0 ? "LOW" : (currentStock <= safetyStock ? "HIGH" : "MEDIUM"));
        row.setReason(String.format(
                "预测 %d 天需求 %d，当前库存 %d，安全库存 %d → 建议补货 %d",
                response.getPredictionDays(), predictedDemand, currentStock, safetyStock, suggested));
        row.setStatus(STATUS_PENDING);
        if (row.getCreateTime() == null) {
            row.setCreateTime(LocalDateTime.now());
        }
        replenishmentRepository.save(row);
        return 1;
    }

    private String summarize(Map<String, Object> summary) {
        StringBuilder sb = new StringBuilder();
        summary.forEach((k, v) -> sb.append(k).append('=').append(v).append("; "));
        return sb.toString();
    }
}
