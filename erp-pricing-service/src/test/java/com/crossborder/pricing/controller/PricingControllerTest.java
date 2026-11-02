package com.crossborder.pricing.controller;

import com.crossborder.pricing.dto.PricingRequest;
import com.crossborder.pricing.dto.PricingResponse;
import com.crossborder.pricing.service.PricingService;
import com.crossborder.pricing.service.PricingSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PricingController 单元测试 (v2.0.0) - 纯 Mockito 单测.
 *
 * 复用 v1.9.1 FeedbackControllerTest 的模式：
 * 避开 @WebMvcTest + Nacos 的应用上下文加载复杂性，
 * 只验证 controller 层的委托逻辑、参数传递和返回值透传。
 *
 * JSON 序列化/路由/参数校验层由集成测试覆盖 (团队后续补)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PricingController 单元测试 (v2.0.0)")
class PricingControllerTest {

    @Mock
    private PricingService pricingService;

    @Mock
    private PricingSuggestionService pricingSuggestionService;

    private PricingController controller;

    @BeforeEach
    void setUp() {
        controller = new PricingController(pricingService, pricingSuggestionService);
    }

    @Test
    @DisplayName("calculateOptimalPrice - 透传 PricingRequest 到 Service 并返回结果")
    void calculateOptimalPrice_delegatesToService() {
        PricingRequest req = new PricingRequest();
        req.setProductId(100L);
        req.setCostPrice(new BigDecimal("100.00"));

        PricingResponse expected = new PricingResponse();
        expected.setProductId(100L);
        expected.setRecommendedPrice(new BigDecimal("120.00"));
        expected.setCalculationTime(LocalDateTime.now());
        when(pricingService.calculateOptimalPrice(req)).thenReturn(expected);

        PricingResponse actual = controller.calculateOptimalPrice(req);

        assertSame(expected, actual);
        assertEquals(100L, actual.getProductId());
        assertEquals(0, new BigDecimal("120.00").compareTo(actual.getRecommendedPrice()));
        verify(pricingService, times(1)).calculateOptimalPrice(req);
    }

    @Test
    @DisplayName("batchCalculateOptimalPrice - 透传 List<PricingRequest> 到 Service")
    void batchCalculateOptimalPrice_delegatesToService() {
        PricingRequest r1 = new PricingRequest();
        r1.setProductId(1L);
        PricingRequest r2 = new PricingRequest();
        r2.setProductId(2L);

        PricingResponse p1 = new PricingResponse();
        p1.setProductId(1L);
        PricingResponse p2 = new PricingResponse();
        p2.setProductId(2L);

        when(pricingService.batchCalculateOptimalPrice(anyList()))
                .thenReturn(List.of(p1, p2));

        List<PricingResponse> actual = controller.batchCalculateOptimalPrice(List.of(r1, r2));

        assertEquals(2, actual.size());
        assertEquals(1L, actual.get(0).getProductId());
        assertEquals(2L, actual.get(1).getProductId());
        verify(pricingService).batchCalculateOptimalPrice(List.of(r1, r2));
    }

    @Test
    @DisplayName("adjustPriceByCompetitors - 路径变量 productId 透传到 Service")
    void adjustPriceByCompetitors_delegatesToService() {
        PricingResponse expected = new PricingResponse();
        expected.setProductId(999L);
        expected.setApplied(true);
        when(pricingService.adjustPriceByCompetitors(eq(999L))).thenReturn(expected);

        PricingResponse actual = controller.adjustPriceByCompetitors(999L);

        assertSame(expected, actual);
        assertEquals(999L, actual.getProductId());
        assertEquals(Boolean.TRUE, actual.getApplied());
        verify(pricingService).adjustPriceByCompetitors(999L);
    }

    @Test
    @DisplayName("manualPriceAdjustment - 三个 @RequestParam 全部透传到 Service")
    void manualPriceAdjustment_propagatesAllParams() {
        PricingResponse expected = new PricingResponse();
        expected.setProductId(100L);
        expected.setRecommendedPrice(new BigDecimal("199.99"));
        when(pricingService.manualPriceAdjustment(
                anyLong(), any(BigDecimal.class), any(String.class)))
                .thenReturn(expected);

        PricingResponse actual = controller.manualPriceAdjustment(
                100L, new BigDecimal("199.99"), "运营调价");

        assertEquals(0, new BigDecimal("199.99").compareTo(actual.getRecommendedPrice()));
        verify(pricingService).manualPriceAdjustment(
                100L, new BigDecimal("199.99"), "运营调价");
    }

    @Test
    @DisplayName("getProductPricingInfo - 路径变量 productId 透传")
    void getProductPricingInfo_delegatesToService() {
        PricingResponse expected = new PricingResponse();
        expected.setProductId(42L);
        when(pricingService.getProductPricingInfo(42L)).thenReturn(expected);

        PricingResponse actual = controller.getProductPricingInfo(42L);

        assertSame(expected, actual);
        verify(pricingService).getProductPricingInfo(42L);
    }

    // ⚠️ health 已从 PricingController 移除：它和 HealthController 都声明了
    // GET /api/v1/pricing/health，Spring 启动期直接抛 Ambiguous mapping，
    // **整个 pricing 服务起不来**（单元测不出，要真起服务才暴露）。
    // 健康检查统一由 HealthController 提供，路由冲突由
    // ControllerMappingConflictTest 静态守护。
}