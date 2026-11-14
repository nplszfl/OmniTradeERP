package com.crossborder.pricing.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.pricing.dto.PricingRequest;
import com.crossborder.pricing.dto.PricingResponse;
import com.crossborder.pricing.service.CompetitorScrapeService;
import com.crossborder.pricing.service.CompetitorScrapeService.CompetitorPriceStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PricingServiceImpl 单元测试 (v2.0.0)
 *
 * 覆盖：
 * - calculateOptimalPrice 全分支：基础计算 / 竞品上调 / 竞品下调 / 等价不调 / 钳制
 * - validatePrice 边界：低于成本钳制 / 超过 50% 加成钳制 / 正常区间不动
 * - calculateBasePrice 边界：成本 <= 0 抛异常 / null 利润率用默认 20%
 * - batchCalculateOptimalPrice 空集合 / null 入参 / 多产品
 * - adjustPriceByCompetitors 走完整流程
 * - manualPriceAdjustment 应用状态
 * - getProductPricingInfo 兜底数据
 * - CompetitorScrapeService 抛异常时不挂主流程
 * - CompetitorScrapeService 返回 null 时不调用调整逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PricingServiceImpl 单元测试 (v2.0.0)")
class PricingServiceImplTest {

    @Mock
    private CompetitorScrapeService competitorScrapeService;

    private PricingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PricingServiceImpl(competitorScrapeService);
    }

    // ========== calculateOptimalPrice - 基础计算 ==========

    @Test
    @DisplayName("calculateOptimalPrice - 关闭所有调整时按 20% 利润率计算基础价")
    void calculateOptimalPrice_disabledAdjustments_usesBaseMargin() {
        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertNotNull(resp);
        assertEquals(100L, resp.getProductId());
        assertEquals("SKU-001", resp.getProductCode());
        // 成本 100 + 20% 利润率 = 120.00
        assertEquals(0, new BigDecimal("120.00").compareTo(resp.getRecommendedPrice()));
        assertEquals(0, new BigDecimal("16.67").compareTo(resp.getProfitMargin())); // (120-100)/120 = 16.67%
        assertNull(resp.getCompetitorAvgPrice());
        verify(competitorScrapeService, never()).getCompetitorPriceStats(anyLong());
    }

    @Test
    @DisplayName("calculateOptimalPrice - 自定义目标利润率 30% 正确生效")
    void calculateOptimalPrice_customProfitMargin_appliesCorrectly() {
        PricingRequest req = baseRequest();
        req.setTargetProfitMargin(new BigDecimal("30"));
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        // 100 * 1.30 = 130
        assertEquals(0, new BigDecimal("130.00").compareTo(resp.getRecommendedPrice()));
    }

    @Test
    @DisplayName("calculateOptimalPrice - 原始价 150 vs 推荐 120 时显示价格下降")
    void calculateOptimalPrice_priceChangeReason_descending() {
        PricingRequest req = baseRequest();
        req.setCurrentPrice(new BigDecimal("150.00"));
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        // 120 - 150 / 150 = -20%
        assertTrue(resp.getPriceChangePercent().compareTo(BigDecimal.ZERO) < 0);
        assertTrue(resp.getAdjustmentReason().contains("价格下降"));
    }

    @Test
    @DisplayName("calculateOptimalPrice - 原始价 100 vs 推荐 120 时显示价格上涨")
    void calculateOptimalPrice_priceChangeReason_ascending() {
        PricingRequest req = baseRequest();
        req.setCurrentPrice(new BigDecimal("100.00"));
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertTrue(resp.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(resp.getAdjustmentReason().contains("价格上涨"));
    }

    @Test
    @DisplayName("calculateOptimalPrice - 价格不变时返回'价格无需调整'")
    void calculateOptimalPrice_unchangedPrice_returnsNoAdjustReason() {
        PricingRequest req = baseRequest();
        req.setCurrentPrice(new BigDecimal("120.00")); // 刚好等于推荐价
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertEquals("价格无需调整", resp.getAdjustmentReason());
        assertEquals(0, BigDecimal.ZERO.compareTo(resp.getPriceChangePercent()));
    }

    // ========== calculateOptimalPrice - 竞品分析 ==========

    @Test
    @DisplayName("calculateOptimalPrice - 当前价低于市场均价时上调")
    void calculateOptimalPrice_belowMarketAvg_priceIncreased() {
        when(competitorScrapeService.getCompetitorPriceStats(100L))
                .thenReturn(statsOf(100L, "130.00", "180.00", "150.00", 3));

        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(true);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);
        // 基础价 120 (成本 100 * 1.20), 市场均价 150, 差距 30
        // 调整 30 * 0.5 = 15, 所以 120 + 15 = 135 (钳制 15% 内 ✓)

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertEquals(0, new BigDecimal("135.00").compareTo(resp.getRecommendedPrice()));
        assertEquals(0, new BigDecimal("150.00").compareTo(resp.getCompetitorAvgPrice()));
        verify(competitorScrapeService, times(1)).getCompetitorPriceStats(100L);
    }

    @Test
    @DisplayName("calculateOptimalPrice - 基础价高于市场均价时下调")
    void calculateOptimalPrice_aboveMarketAvg_priceDecreased() {
        // 基础价 120 (成本 100 * 1.20), 市场均价 100, 差距 20
        // 调整 20 * 0.3 = 6, 所以 120 - 6 = 114 (钳制 10% 内: 120 * 0.10 = 12 ✓)
        when(competitorScrapeService.getCompetitorPriceStats(100L))
                .thenReturn(statsOf(100L, "80.00", "120.00", "100.00", 3));

        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(true);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertEquals(0, new BigDecimal("114.00").compareTo(resp.getRecommendedPrice()));
    }

    @Test
    @DisplayName("calculateOptimalPrice - 当前价等于市场均价时不调整")
    void calculateOptimalPrice_equalToMarketAvg_noChange() {
        when(competitorScrapeService.getCompetitorPriceStats(100L))
                .thenReturn(statsOf(100L, "100.00", "140.00", "120.00", 3));

        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(true);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);
        // 基础价 120, 市场均价 120, 相等不调

        PricingResponse resp = service.calculateOptimalPrice(req);

        // 基础 120, 上下调整 0
        assertEquals(0, new BigDecimal("120.00").compareTo(resp.getRecommendedPrice()));
    }

    @Test
    @DisplayName("calculateOptimalPrice - 竞品分析开启但服务返回 null 时不挂流程")
    void calculateOptimalPrice_competitorServiceReturnsNull_gracefullyContinues() {
        when(competitorScrapeService.getCompetitorPriceStats(anyLong())).thenReturn(null);

        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(true);
        // 关掉季节性/库存调整，否则结果随当前月份变化（3-5月 +2%、11-12月 +5%），
        // 断言就不再是确定的 120 了
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertNotNull(resp);
        assertNull(resp.getCompetitorAvgPrice());
        // 仍然按基础价 120 走
        assertEquals(0, new BigDecimal("120.00").compareTo(resp.getRecommendedPrice()));
    }

    @Test
    @DisplayName("calculateOptimalPrice - 竞品服务抛异常时不影响定价")
    void calculateOptimalPrice_competitorServiceThrows_continuesWithBasePrice() {
        when(competitorScrapeService.getCompetitorPriceStats(anyLong()))
                .thenThrow(new RuntimeException("Nacos timeout"));

        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(true);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertNotNull(resp);
        assertNull(resp.getCompetitorAvgPrice());
        // 走基础价 120 (无季节性/库存调整)
        assertEquals(0, new BigDecimal("120.00").compareTo(resp.getRecommendedPrice()));
    }

    // ========== validatePrice 边界 ==========

    @Test
    @DisplayName("validatePrice - 价格低于成本 5% 时钳制到 cost*1.05")
    void validatePrice_belowMinFloor_clampsToMinimum() {
        PricingRequest req = baseRequest();
        req.setCostPrice(new BigDecimal("100.00"));
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);
        // 通过手动构造一个会让 basePrice 极低的请求
        // 实际触发路径：targetProfitMargin 负数
        req.setTargetProfitMargin(new BigDecimal("-50"));
        // 基础价 100 * 0.5 = 50, 低于成本, validatePrice 应该钳到 105

        PricingResponse resp = service.calculateOptimalPrice(req);

        // 钳制后最低 100 * 1.05 = 105
        assertEquals(0, new BigDecimal("105.00").compareTo(resp.getRecommendedPrice()));
    }

    @Test
    @DisplayName("validatePrice - 价格超过成本 50% 加成时钳制到 cost*1.50")
    void validatePrice_aboveMaxCeiling_clampsToMaximum() {
        PricingRequest req = baseRequest();
        req.setCostPrice(new BigDecimal("100.00"));
        req.setTargetProfitMargin(new BigDecimal("100")); // 100% 利润率
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);
        // 基础价 200, 超过 cost*1.5=150, 钳到 150

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertEquals(0, new BigDecimal("150.00").compareTo(resp.getRecommendedPrice()));
    }

    @Test
    @DisplayName("validatePrice - 正常区间不动")
    void validatePrice_normalRange_unchanged() {
        PricingRequest req = baseRequest();
        req.setEnableCompetitorAnalysis(false);
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);
        // 基础价 120, 在 [105, 150] 区间内

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertEquals(0, new BigDecimal("120.00").compareTo(resp.getRecommendedPrice()));
    }

    // ========== calculateBasePrice 边界 ==========

    @Test
    @DisplayName("calculateBasePrice - costPrice 为 null 抛 BizException（客户端错误 → HTTP 400）")
    void calculateBasePrice_nullCostPrice_throws() {
        PricingRequest req = baseRequest();
        req.setCostPrice(null);
        req.setEnableCompetitorAnalysis(false);

        assertThrows(BizException.class, () -> service.calculateOptimalPrice(req));
    }

    @Test
    @DisplayName("calculateBasePrice - costPrice 为 0 抛 BizException（客户端错误 → HTTP 400）")
    void calculateBasePrice_zeroCostPrice_throws() {
        PricingRequest req = baseRequest();
        req.setCostPrice(BigDecimal.ZERO);
        req.setEnableCompetitorAnalysis(false);

        assertThrows(BizException.class, () -> service.calculateOptimalPrice(req));
    }

    @Test
    @DisplayName("calculateBasePrice - costPrice 为负数抛 BizException（客户端错误 → HTTP 400）")
    void calculateBasePrice_negativeCostPrice_throws() {
        PricingRequest req = baseRequest();
        req.setCostPrice(new BigDecimal("-1.00"));
        req.setEnableCompetitorAnalysis(false);

        assertThrows(BizException.class, () -> service.calculateOptimalPrice(req));
    }

    // ========== batchCalculateOptimalPrice ==========

    @Test
    @DisplayName("batchCalculateOptimalPrice - 多产品各自计算")
    void batchCalculateOptimalPrice_multipleProducts() {
        when(competitorScrapeService.getCompetitorPriceStats(anyLong())).thenReturn(null);

        PricingRequest r1 = baseRequest();
        r1.setProductId(1L);
        PricingRequest r2 = baseRequest();
        r2.setProductId(2L);
        r2.setTargetProfitMargin(new BigDecimal("30"));

        List<PricingResponse> responses = service.batchCalculateOptimalPrice(List.of(r1, r2));

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).getProductId());
        assertEquals(2L, responses.get(1).getProductId());
    }

    @Test
    @DisplayName("batchCalculateOptimalPrice - 空集合返回空列表不抛异常")
    void batchCalculateOptimalPrice_emptyList_returnsEmpty() {
        List<PricingResponse> responses = service.batchCalculateOptimalPrice(List.of());

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    @DisplayName("batchCalculateOptimalPrice - null 入参返回空列表不抛异常")
    void batchCalculateOptimalPrice_nullList_returnsEmpty() {
        List<PricingResponse> responses = service.batchCalculateOptimalPrice(null);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    // ========== adjustPriceByCompetitors ==========

    @Test
    @DisplayName("adjustPriceByCompetitors - 走完整流程并标记 applied=true")
    void adjustPriceByCompetitors_returnsAppliedResponse() {
        when(competitorScrapeService.getCompetitorPriceStats(anyLong()))
                .thenReturn(statsOf(100L, "100.00", "200.00", "150.00", 3));

        PricingResponse resp = service.adjustPriceByCompetitors(100L);

        assertNotNull(resp);
        assertEquals(Boolean.TRUE, resp.getApplied());
        assertEquals(100L, resp.getProductId());
        assertEquals("PROD-100", resp.getProductCode());
    }

    // ========== manualPriceAdjustment ==========

    @Test
    @DisplayName("manualPriceAdjustment - 给出目标价，但**如实说没落库**（applied=false）")
    void manualPriceAdjustment_usesTargetPriceDirectly() {
        PricingResponse resp = service.manualPriceAdjustment(
                100L, new BigDecimal("199.99"), "运营手动调价");

        assertEquals(100L, resp.getProductId());
        assertEquals(0, new BigDecimal("199.99").compareTo(resp.getRecommendedPrice()));
        // 📌 2026-11-12：这里原来是 `assertEquals(Boolean.TRUE, resp.getApplied())` ——
        //    也就是**测试把「假成功」当成正确行为钉住了**（服务里 applied=true 但**什么都没写**，
        //    只留了个 `TODO: 实际更新数据库中的价格`）→ 规则 80 / 82（假成功）。
        //    ✅ 现在如实返回 applied=false，并把「未落库」写进原因里。
        assertEquals(Boolean.FALSE, resp.getApplied(),
                "没有落库就不许说「已应用」");
        assertTrue(resp.getAdjustmentReason().contains("手动调整: 运营手动调价"));
        assertTrue(resp.getAdjustmentReason().contains("未落库"),
                "原因里必须说明「没落库」，否则调用方无从判断");
    }

    @Test
    @DisplayName("定价策略不许自称「AI」—— 实际是确定性规则（规则 18 / 56）")
    void pricingStrategy_doesNotClaimAI() {
        PricingRequest req = new PricingRequest();
        req.setProductId(100L);
        req.setProductCode("PROD-100");
        req.setCurrentPrice(new BigDecimal("150.00"));
        req.setCostPrice(new BigDecimal("100.00"));
        req.setTargetProfitMargin(new BigDecimal("30"));

        PricingResponse resp = service.calculateOptimalPrice(req);

        assertNotNull(resp.getPricingStrategy());
        assertFalse(resp.getPricingStrategy().contains("AI智能定价"),
                "没有调用任何 LLM → 不许自称 AI 定价算法");
        assertTrue(resp.getPricingStrategy().contains("规则"),
                "策略名要说明这是规则定价：" + resp.getPricingStrategy());
    }

    @Test
    @DisplayName("getProductPricingInfo - 占位数据要写明「不是真实报价」")
    void getProductPricingInfo_marksPlaceholderData() {
        PricingResponse resp = service.getProductPricingInfo(42L);

        assertTrue(resp.getAdjustmentReason().contains("占位数据"),
                "返回的是写死的占位值 → 原因里必须说明");
    }

    // ========== getProductPricingInfo ==========

    @Test
    @DisplayName("getProductPricingInfo - 返回固定演示数据")
    void getProductPricingInfo_returnsDemoData() {
        PricingResponse resp = service.getProductPricingInfo(42L);

        assertEquals(42L, resp.getProductId());
        assertEquals("PROD-42", resp.getProductCode());
        assertEquals(0, new BigDecimal("150.00").compareTo(resp.getOriginalPrice()));
        assertEquals(0, new BigDecimal("145.00").compareTo(resp.getRecommendedPrice()));
        assertTrue(resp.getCalculationTime() != null);
    }

    // ========== 辅助方法 ==========

    private PricingRequest baseRequest() {
        PricingRequest req = new PricingRequest();
        req.setProductId(100L);
        req.setProductCode("SKU-001");
        req.setCostPrice(new BigDecimal("100.00"));
        req.setCurrentPrice(new BigDecimal("120.00"));
        req.setTargetProfitMargin(new BigDecimal("20"));
        req.setEnableCompetitorAnalysis(true);
        // 默认关闭季节性/库存调整：季节性因子随当前月份变化（3-5月 +2%、11-12月 +5%），
        // 开着会让断言变成“随日期翻车”的定时炸弹。需要测季节性的用例自行打开。
        req.setEnableSeasonalAdjustment(false);
        req.setEnableInventoryAdjustment(false);
        return req;
    }

    private CompetitorPriceStats statsOf(Long productId, String min, String max, String avg, long count) {
        CompetitorPriceStats stats = new CompetitorPriceStats();
        stats.setProductId(productId);
        stats.setMinPrice(new BigDecimal(min));
        stats.setMaxPrice(new BigDecimal(max));
        stats.setAvgPrice(new BigDecimal(avg));
        stats.setCompetitorCount(count);
        return stats;
    }
}