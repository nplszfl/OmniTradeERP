package com.crossborder.inventoryprediction.service.impl;

import com.crossborder.inventoryprediction.dto.AccuracyEvaluation;
import com.crossborder.inventoryprediction.dto.PredictionRequest;
import com.crossborder.inventoryprediction.dto.PredictionResponse;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestion;
import com.crossborder.inventoryprediction.dto.ServiceStatistics;
import com.crossborder.inventoryprediction.repository.InventoryRepository;
import com.crossborder.inventoryprediction.repository.SalesHistoryRepository;
import com.crossborder.inventoryprediction.service.InventoryPredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InventoryPredictionService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class InventoryPredictionServiceImplTest {

    @InjectMocks
    private InventoryPredictionServiceImpl inventoryPredictionService;

    // 📌 **2026-11-14 补**：重构把「Random 现造销量/库存」换成了真查
    //    {@code sales_history} / {@code inventory} 两张表 → 实现类多了两个
    //    **构造器依赖**（@RequiredArgsConstructor + final 字段）。
    //    测试没给这两个 @Mock → Mockito 注入 null → 全员 NPE（规则 23 的形态）。
    @Mock
    private SalesHistoryRepository salesHistoryRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private PredictionRequest request;

    @Test
    @DisplayName("预测结果不许自称「Prophet + ARIMA」，也不许编造置信度（规则 18 / 56）")
    void prediction_doesNotClaimMLModelNorFakeConfidence() {
        PredictionResponse resp = inventoryPredictionService.predictSales(request);

        assertNotNull(resp.getModelType());
        assertFalse(resp.getModelType().contains("Prophet"),
                "没有调用任何 ML 模型 → 不许自称 Prophet");
        assertFalse(resp.getModelType().contains("ARIMA"),
                "没有调用任何 ML 模型 → 不许自称 ARIMA");
        // 📌 **2026-11-14 语义修正**：历史数据已从「Random 现造」改成**真查 sales_history 表**
        //    → 「模拟」这个描述已经不准确了（那是在描述数据来源）；
        //    但**算法本身仍是启发式**（移动平均 + 季节性因子），依旧不是机器学习模型
        //    → 模型说明必须如实说这一点（规则 18 / 56 的真意是「不许谎报算法能力」，
        //    而不是「必须自曝数据是模拟的」）。
        assertTrue(resp.getModelType().contains("启发式")
                        || resp.getModelType().contains("非机器学习模型"),
                "算法是启发式 → 模型说明必须如实写出来：" + resp.getModelType());

        // 📌 置信度：启发式算法没有概率模型 → 给置信度就是**假事实** → 不给数
        resp.getDailyPredictions().forEach(d ->
                assertNull(d.getConfidence(), "启发式预测不许给出置信度"));
    }

    @BeforeEach
    void setUp() {
        request = new PredictionRequest();
        request.setProductId(1001L);
        request.setProductCode("PROD-001");
        request.setPredictionDays(30);
        request.setStartDate(LocalDate.now());
        request.setUseSeasonalModel(true);
        request.setUseTrendModel(true);
        request.setConfidenceLevel(0.9);
    }

    @Test
    void testPredictSales_ReturnsValidResponse() {
        PredictionResponse response = inventoryPredictionService.predictSales(request);

        assertNotNull(response);
        assertEquals(1001L, response.getProductId());
        assertEquals("PROD-001", response.getProductCode());
        assertEquals(30, response.getPredictionDays());
        assertNotNull(response.getStartDate());
        assertNotNull(response.getEndDate());
        assertTrue(response.getEndDate().isAfter(response.getStartDate()));
    }

    @Test
    void testPredictSales_CalculatesAverageSales() {
        PredictionResponse response = inventoryPredictionService.predictSales(request);

        assertNotNull(response.getAvgDailySales());
    }

    @Test
    void testPredictSales_GeneratesPredictions() {
        PredictionResponse response = inventoryPredictionService.predictSales(request);

        assertNotNull(response.getDailyPredictions());
        assertFalse(response.getDailyPredictions().isEmpty());
    }

    @Test
    void testPredictSales_HasModelType() {
        PredictionResponse response = inventoryPredictionService.predictSales(request);

        assertNotNull(response.getModelType());
    }

    @Test
    void testPredictSales_ValidPredictionDays() {
        // 测试预测天数能正常工作
        PredictionResponse response = inventoryPredictionService.predictSales(request);
        
        assertTrue(response.getPredictionDays() > 0);
        assertEquals(30, response.getPredictionDays());
    }

    @Test
    void testBatchPredictSales_MultipleProducts() {
        PredictionRequest request2 = new PredictionRequest();
        request2.setProductId(1002L);
        request2.setProductCode("PROD-002");
        request2.setPredictionDays(30);

        List<PredictionRequest> requests = Arrays.asList(request, request2);
        
        List<PredictionResponse> responses = inventoryPredictionService.batchPredictSales(requests);
        
        assertNotNull(responses);
        assertEquals(2, responses.size());
    }

    @Test
    void testGetReplenishmentSuggestion_ReturnsSuggestion() {
        ReplenishmentSuggestion suggestion = inventoryPredictionService.getReplenishmentSuggestion(1001L, 30);

        assertNotNull(suggestion);
        assertEquals(1001L, suggestion.getProductId());
    }

    @Test
    void testCalculateSafetyStock_ReturnsStock() {
        int safetyStock = inventoryPredictionService.calculateSafetyStock(1001L);
        
        assertTrue(safetyStock >= 0);
    }

    @Test
    void testWarnLowStock_ReturnsProductList() {
        List<Long> lowStockProducts = inventoryPredictionService.warnLowStock(30);
        
        assertNotNull(lowStockProducts);
    }

    @Test
    void testCalculateInventoryTurnover_ReturnsRate() {
        double turnover = inventoryPredictionService.calculateInventoryTurnover(1001L, 30);
        
        assertTrue(turnover >= 0);
    }

    @Test
    void testGetStatistics_ReturnsStatistics() {
        ServiceStatistics stats = inventoryPredictionService.getStatistics();

        assertNotNull(stats);
    }

    @Test
    void testEvaluateAccuracy_ReturnsEvaluation() {
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        
        AccuracyEvaluation evaluation = inventoryPredictionService.evaluateAccuracy(1001L, startDate, endDate);
        
        assertNotNull(evaluation);
    }

    @Test
    void testBatchEvaluateAccuracy_ReturnsList() {
        List<Long> productIds = Arrays.asList(1001L, 1002L, 1003L);
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        
        List<AccuracyEvaluation> evaluations = inventoryPredictionService.batchEvaluateAccuracy(
            productIds, startDate, endDate);
        
        assertNotNull(evaluations);
        assertEquals(3, evaluations.size());
    }

    @Test
    void testGetAccuracyTrend_ReturnsTrend() {
        List<AccuracyEvaluation> trend = inventoryPredictionService.getAccuracyTrend(1001L, 30);
        
        assertNotNull(trend);
    }

    @Test
    void testPredictSales_LongPredictionPeriod() {
        request.setPredictionDays(90);
        
        PredictionResponse response = inventoryPredictionService.predictSales(request);
        
        assertNotNull(response);
        assertEquals(90, response.getPredictionDays());
    }

    @Test
    void testPredictSales_WithoutSeasonalModel() {
        request.setUseSeasonalModel(false);
        
        PredictionResponse response = inventoryPredictionService.predictSales(request);
        
        assertNotNull(response);
    }

    @Test
    void testPredictSales_WithoutTrendModel() {
        request.setUseTrendModel(false);
        
        PredictionResponse response = inventoryPredictionService.predictSales(request);
        
        assertNotNull(response);
    }

    @Test
    void testBatchPredictSales_EmptyList() {
        List<PredictionResponse> responses = inventoryPredictionService.batchPredictSales(Arrays.asList());
        
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void testPredictSales_ZeroConfidenceLevel() {
        request.setConfidenceLevel(0.0);
        
        PredictionResponse response = inventoryPredictionService.predictSales(request);
        
        assertNotNull(response);
    }

    @Test
    void testOptimizeInventoryStructure_Executes() {
        // 测试不抛出异常
        assertDoesNotThrow(() -> inventoryPredictionService.optimizeInventoryStructure());
    }

    @Test
    void testResetStatistics_Executes() {
        assertDoesNotThrow(() -> inventoryPredictionService.resetStatistics());
    }
}