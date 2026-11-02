package com.crossborder.inventoryprediction.service.impl;

import com.crossborder.inventoryprediction.dto.PredictionRequest;
import com.crossborder.inventoryprediction.dto.PredictionResponse;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestion;
import com.crossborder.inventoryprediction.dto.ReplenishmentSuggestionVO;
import com.crossborder.inventoryprediction.service.InventoryPredictionService;
import com.crossborder.inventoryprediction.service.ReplenishmentSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReplenishmentSuggestionService 单元测试
 *
 * <p>重点验证：之前 {@code urgency} / {@code needReplenishment} / {@code productSku}
 * 从来没被赋值，导致前端「紧急补货」列表永远是空的。</p>
 */
@DisplayName("ReplenishmentSuggestionService 单元测试")
class ReplenishmentSuggestionServiceTest {

    private InventoryPredictionService predictionService;
    private JdbcTemplate jdbcTemplate;
    private ReplenishmentSuggestionService service;

    @BeforeEach
    void setUp() {
        predictionService = mock(InventoryPredictionService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ReplenishmentSuggestionServiceImpl(predictionService, jdbcTemplate);

        PredictionResponse prediction = new PredictionResponse();
        prediction.setProductId(1L);
        prediction.setAvgDailySales(10.0);
        prediction.setTotalPredictedSales(300L);
        when(predictionService.predictSales(any(PredictionRequest.class))).thenReturn(prediction);

        // 库存快照：queryForObject 有行时返回 StockSnapshot，
        // 无行时返回 null（Spring 的真实行为），两种都要能处理。
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object.class)))
                .thenReturn(null);
    }

    @Test
    @DisplayName("generateSuggestions - PredictionRequest 必须带上 productId")
    void generateSuggestions_passesProductIdToPredictionRequest() {
        service.generateSuggestions(42L);

        ArgumentCaptor<PredictionRequest> captor = ArgumentCaptor.forClass(PredictionRequest.class);
        verify(predictionService).predictSales(captor.capture());

        // ⚠️ 之前传的是 new PredictionRequest()（productId 为 null），
        //    predictSales → getHistoricalSales 里 `productId * 10` 直接 NPE，
        //    再被 batchGenerateSuggestions 的 catch 吞掉 → 接口静默返回 []，
        //    看起来像"没有需要补货的商品"，实际是一个都没算出来。
        assertNotNull(captor.getValue().getProductId(), "productId 必须传给 predictSales");
        assertEquals(42L, captor.getValue().getProductId());
    }

    @Test
    @DisplayName("generateSuggestions - 库存无记录时按 0 处理，不 NPE")
    void generateSuggestions_handlesMissingInventoryRow() {
        // queryForObject 无匹配行时返回 null —— Spring 的真实行为
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object.class)))
                .thenReturn(null);

        List<ReplenishmentSuggestion> list = service.generateSuggestions(1L);

        assertEquals(1, list.size(), "查不到库存记录也要能算出建议，不能 NPE");
    }

    @Test
    @DisplayName("generateSuggestions - productId 为 null 时明确抛异常")
    void generateSuggestions_rejectsNullProductId() {
        assertThrows(IllegalArgumentException.class, () -> service.generateSuggestions(null));
    }

    @Test
    @DisplayName("generateSuggestions - 补齐 urgency / needReplenishment / sku / productName")
    void generateSuggestions_fillsFrontendFields() {
        List<ReplenishmentSuggestion> list = service.generateSuggestions(1L);

        assertEquals(1, list.size());
        ReplenishmentSuggestion s = list.get(0);
        assertNotNull(s.getUrgency(), "urgency 之前一直是 null，前端渲染不出来");
        assertTrue(List.of("HIGH", "MEDIUM", "LOW").contains(s.getUrgency()));
        assertNotNull(s.getNeedReplenishment(), "needReplenishment 之前一直是 null");
        assertNotNull(s.getProductSku(), "productSku 之前一直是 null，前端表格没 SKU");
        assertNotNull(s.getProductName(), "productName 之前一直是 null");
    }

    @Test
    @DisplayName("listSuggestions - 返回前端视图字段（sku / recommendedReplenishment / urgencyLabel）")
    void listSuggestions_mapsToFrontendShape() {
        List<ReplenishmentSuggestionVO> list = service.listSuggestions(List.of(1L), 50);

        assertEquals(1, list.size());
        ReplenishmentSuggestionVO vo = list.get(0);
        assertNotNull(vo.getSku());
        assertNotNull(vo.getUrgencyLabel());
        assertNotNull(vo.getRecommendedReplenishment());
        assertNotNull(vo.getCurrentStock());
        assertNotNull(vo.getSafetyStock());
        assertNotNull(vo.getPredicted30DaySales());
    }

    @Test
    @DisplayName("listSuggestions - 传空 productIds 时走默认监控列表")
    void listSuggestions_emptyIdsUsesDefaults() {
        List<ReplenishmentSuggestionVO> list = service.listSuggestions(null, 50);
        assertFalse(list.isEmpty());
    }

    @Test
    @DisplayName("listSuggestions - limit 生效，返回条数被截断")
    void listSuggestions_appliesLimit() {
        List<ReplenishmentSuggestionVO> list = service.listSuggestions(List.of(1L, 2L, 3L, 4L, 5L), 2);
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("getUrgentReplenishments - 不再返回空列表（之前是永远为空的桩）")
    void urgentReplenishments_notEmpty() {
        List<ReplenishmentSuggestion> list = service.getUrgentReplenishments();
        // 具体是否紧急取决于算法，但至少不应该是个写死的空 list
        assertNotNull(list);
        assertFalse(list.isEmpty(), "getUrgentReplenishments 之前 return new ArrayList<>()，永远是空");
    }

    @Test
    @DisplayName("getUrgentReplenishments - 只返回需要补货的建议")
    void urgentReplenishments_onlyNeedReplenishment() {
        List<ReplenishmentSuggestion> list = service.getUrgentReplenishments();
        for (ReplenishmentSuggestion s : list) {
            assertTrue(Boolean.TRUE.equals(s.getNeedReplenishment()));
        }
    }

    @Test
    @DisplayName("listSuggestions - 紧急程度高的排前面")
    void listSuggestions_sortedByUrgency() {
        List<ReplenishmentSuggestionVO> list = service.listSuggestions(List.of(1L, 2L, 3L, 4L, 5L), 50);
        for (int i = 1; i < list.size(); i++) {
            int prev = weight(list.get(i - 1).getUrgency());
            int curr = weight(list.get(i).getUrgency());
            assertTrue(prev >= curr, "建议列表必须按紧急程度倒序");
        }
    }

    @Test
    @DisplayName("batchGenerateSuggestions - 单个产品失败不影响其他产品")
    void batchGenerateSuggestions_isolatesFailure() {
        when(predictionService.predictSales(any(PredictionRequest.class)))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(predictionWith(5.0));

        List<ReplenishmentSuggestion> list = service.batchGenerateSuggestions(List.of(1L, 2L));

        // 第一个产品失败，第二个仍然应该有结果
        assertEquals(1, list.size());
        assertEquals(2L, list.get(0).getProductId());
    }

    private PredictionResponse predictionWith(double dailySales) {
        PredictionResponse p = new PredictionResponse();
        p.setAvgDailySales(dailySales);
        p.setTotalPredictedSales((long) (dailySales * 30));
        return p;
    }

    private int weight(String urgency) {
        if (urgency == null) {
            return 0;
        }
        return switch (urgency) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }
}
