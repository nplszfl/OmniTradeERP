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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预测落库（写路径）单元测试
 *
 * <p>📌 重点验证：<b>这个模块原先根本没有写路径</b> —— 三张预测表永远是空的，
 * 而 API 看上去完全正常。现在补了写路径，就要证明：
 * <ol>
 *   <li>日粒度预测**真的被写进** {@code sales_prediction}；</li>
 *   <li>同产品同日期重跑是<b>更新</b>而不是插第二行（唯一键 {@code uk_product_date}）；</li>
 *   <li>补货建议真的被写进 {@code replenishment_suggestion}，且<b>当前库存取自库存表</b>；</li>
 *   <li>任务审计行 {@code prediction_task} 被写，且<b>状态与摘要如实</b>；</li>
 *   <li>没有销售历史 → <b>不编数字</b>（0 行 + 摘要写明），而不是造一批零行。</li>
 * </ol>
 */
@DisplayName("PredictionPersistenceService 写路径单元测试")
class PredictionPersistenceServiceImplTest {

    private InventoryPredictionService predictionService;
    private InventoryRepository inventoryRepository;
    private com.crossborder.inventoryprediction.repository.SalesHistoryRepository salesHistoryRepository;
    private SalesPredictionRepository salesPredictionRepository;
    private ReplenishmentSuggestionRepository replenishmentRepository;
    private PredictionTaskRepository predictionTaskRepository;
    private PredictionPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        predictionService = mock(InventoryPredictionService.class);
        inventoryRepository = mock(InventoryRepository.class);
        salesHistoryRepository = mock(com.crossborder.inventoryprediction.repository.SalesHistoryRepository.class);
        salesPredictionRepository = mock(SalesPredictionRepository.class);
        replenishmentRepository = mock(ReplenishmentSuggestionRepository.class);
        predictionTaskRepository = mock(PredictionTaskRepository.class);
        service = new PredictionPersistenceServiceImpl(predictionService, inventoryRepository,
                salesHistoryRepository, salesPredictionRepository, replenishmentRepository, predictionTaskRepository);
    }

    private com.crossborder.inventoryprediction.entity.SalesHistory historyOf(Long productId, int qty) {
        com.crossborder.inventoryprediction.entity.SalesHistory h =
                new com.crossborder.inventoryprediction.entity.SalesHistory();
        h.setProductId(productId);
        h.setSalesQuantity(qty);
        return h;
    }

    private Inventory inventoryOf(Long productId, int qty) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setQuantity(qty);
        return inv;
    }

    private PredictionResponse responseOf(Long productId, int days, long total) {
        PredictionResponse r = new PredictionResponse();
        r.setProductId(productId);
        r.setPredictionDays(days);
        r.setTotalPredictedSales(total);
        r.setModelType("RULE_TREND_SEASONAL");
        List<PredictionResponse.DailyPrediction> daily = new ArrayList<>();
        PredictionResponse.DailyPrediction d = new PredictionResponse.DailyPrediction();
        d.setDate(LocalDate.of(2026, 11, 20));
        d.setPredictedSales(10L);
        d.setLowerBound(8L);
        d.setUpperBound(12L);
        daily.add(d);
        r.setDailyPredictions(daily);
        return r;
    }

    @Test
    @DisplayName("监控产品来自库存表，而不是写死的 ID 列表")
    void monitoredProductsComeFromInventoryTable() {
        when(inventoryRepository.findAll()).thenReturn(List.of(inventoryOf(7L, 10), inventoryOf(8L, 5)));
        assertEquals(List.of(7L, 8L), service.getMonitoredProductIds());
    }

    @Test
    @DisplayName("预测结果真的写进 sales_prediction + 补货建议写进 replenishment_suggestion + 任务审计写进 prediction_task")
    void writesPredictionsSuggestionsAndTaskAudit() {
        when(inventoryRepository.findAll()).thenReturn(List.of(inventoryOf(1L, 100)));
        when(inventoryRepository.findByProductId(1L)).thenReturn(List.of(inventoryOf(1L, 100)));
        when(salesHistoryRepository.findRecentSales(any(), any())).thenReturn(List.of(historyOf(1L, 10)));
        when(predictionService.predictSales(any(PredictionRequest.class))).thenReturn(responseOf(1L, 30, 300L));
        when(predictionService.calculateSafetyStock(1L)).thenReturn(20);
        when(salesPredictionRepository.findByProductIdAndPredictionDate(any(), any())).thenReturn(Optional.empty());
        when(replenishmentRepository.findByProductIdAndStatus(any(), any())).thenReturn(Optional.empty());

        Map<String, Object> summary = service.runPredictionRound(30);

        // 预测行
        ArgumentCaptor<SalesPrediction> predCaptor = ArgumentCaptor.forClass(SalesPrediction.class);
        verify(salesPredictionRepository, times(1)).save(predCaptor.capture());
        SalesPrediction saved = predCaptor.getValue();
        assertEquals(1L, saved.getProductId());
        assertEquals(10, saved.getPredictedSales());
        assertEquals(8, saved.getLowerBound());
        assertEquals(12, saved.getUpperBound());
        assertEquals("RULE_TREND_SEASONAL", saved.getModelType());

        // 补货建议（当前库存取自库存表 = 100，不是推算值）
        ArgumentCaptor<ReplenishmentSuggestionEntity> sugCaptor =
                ArgumentCaptor.forClass(ReplenishmentSuggestionEntity.class);
        verify(replenishmentRepository, times(1)).save(sugCaptor.capture());
        ReplenishmentSuggestionEntity sug = sugCaptor.getValue();
        assertEquals(100, sug.getCurrentStock());
        assertEquals(20, sug.getSafetyStock());
        assertEquals(300L, sug.getPredictedDemand());
        assertEquals("PENDING", sug.getStatus());

        // 任务审计：写了 2 次（RUNNING 一次 + 收尾一次），收尾那次是 SUCCESS
        ArgumentCaptor<PredictionTask> taskCaptor = ArgumentCaptor.forClass(PredictionTask.class);
        verify(predictionTaskRepository, times(2)).save(taskCaptor.capture());
        PredictionTask finalTask = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals("SUCCESS", finalTask.getStatus());
        assertNotNull(finalTask.getDurationMs());
        assertTrue(finalTask.getResultSummary().contains("predictionRows=1"), finalTask.getResultSummary());

        assertEquals(1, summary.get("predictionRows"));
        assertEquals(1, summary.get("suggestionRows"));
    }

    @Test
    @DisplayName("同产品同日期重跑 = 更新，不插第二行（唯一键 uk_product_date）")
    void rerunUpdatesExistingRowInsteadOfInserting() {
        when(inventoryRepository.findAll()).thenReturn(List.of(inventoryOf(1L, 100)));
        when(inventoryRepository.findByProductId(1L)).thenReturn(List.of(inventoryOf(1L, 100)));
        when(salesHistoryRepository.findRecentSales(any(), any())).thenReturn(List.of(historyOf(1L, 10)));
        when(predictionService.predictSales(any(PredictionRequest.class))).thenReturn(responseOf(1L, 30, 300L));
        when(predictionService.calculateSafetyStock(1L)).thenReturn(20);

        SalesPrediction existing = new SalesPrediction();
        existing.setId(77L);
        existing.setProductId(1L);
        existing.setPredictionDate(LocalDate.of(2026, 11, 20));
        existing.setPredictedSales(9);
        when(salesPredictionRepository.findByProductIdAndPredictionDate(any(), any())).thenReturn(Optional.of(existing));
        when(replenishmentRepository.findByProductIdAndStatus(any(), any())).thenReturn(Optional.empty());

        service.runPredictionRound(30);

        ArgumentCaptor<SalesPrediction> captor = ArgumentCaptor.forClass(SalesPrediction.class);
        verify(salesPredictionRepository, times(1)).save(captor.capture());
        SalesPrediction saved = captor.getValue();
        assertEquals(77L, saved.getId());          // 复用已有行
        assertEquals(10, saved.getPredictedSales()); // 值被刷新
    }

    @Test
    @DisplayName("没有销售历史 → 不编数字：0 行 + 如实计数（productsWithoutHistory）")
    void noHistoryDoesNotFabricateRows() {
        when(inventoryRepository.findAll()).thenReturn(List.of(inventoryOf(1L, 100)));
        // 📌 没有销售历史 → 模型会返回「日预测 = 0」→ **不能当成真预测落库**
        when(salesHistoryRepository.findRecentSales(any(), any())).thenReturn(List.of());
        PredictionResponse zero = responseOf(1L, 30, 0L);
        when(predictionService.predictSales(any(PredictionRequest.class))).thenReturn(zero);

        Map<String, Object> summary = service.runPredictionRound(30);

        verify(salesPredictionRepository, times(0)).save(any());
        verify(replenishmentRepository, times(0)).save(any());
        assertEquals(0, summary.get("predictionRows"));
        assertEquals(1, summary.get("productsWithoutHistory"));
    }

    @Test
    @DisplayName("库存表没有任何产品 → 如实记「无可预测对象」，不造数据")
    void noInventoryRecordsIsReportedNotSilentlySkipped() {
        when(inventoryRepository.findAll()).thenReturn(List.of());

        Map<String, Object> summary = service.runPredictionRound(30);

        assertEquals(0, summary.get("monitoredProducts"));
        assertTrue(String.valueOf(summary.get("skippedReason")).contains("无可预测对象"));
        verify(salesPredictionRepository, times(0)).save(any());
    }
}
