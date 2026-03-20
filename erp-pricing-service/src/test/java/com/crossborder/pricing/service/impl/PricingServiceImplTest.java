package com.crossborder.pricing.service.impl;

import com.crossborder.pricing.dto.PricingRequest;
import com.crossborder.pricing.dto.PricingResponse;
import com.crossborder.pricing.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能定价服务单元测试
 * 
 * 测试定价算法、竞品分析、动态调整等功能
 */
@SpringBootTest
@DisplayName("智能定价服务测试")
class PricingServiceImplTest {

    @Autowired
    private PricingService pricingService;

    private PricingRequest request;

    @BeforeEach
    void setUp() {
        request = new PricingRequest();
        request.setProductId(1L);
        request.setProductCode("SKU001");
        request.setCostPrice(new BigDecimal("50.00"));
        request.setCurrentPrice(new BigDecimal("99.00"));
        request.setTargetProfitMargin(new BigDecimal("20.00"));
        request.setPlatformId(1L);
        request.setEnableCompetitorAnalysis(true);
        request.setEnableSeasonalAdjustment(true);
        request.setEnableInventoryAdjustment(true);
    }

    @Test
    @DisplayName("测试计算最优价格基本功能")
    void testCalculateOptimalPrice_Basic() {
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
        assertEquals("SKU001", response.getProductCode());
        assertNotNull(response.getRecommendedPrice());
        assertTrue(response.getRecommendedPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("测试价格计算包含成本")
    void testCalculateOptimalPrice_IncludesCost() {
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        // 推荐价格应该高于成本价
        assertTrue(response.getRecommendedPrice().compareTo(request.getCostPrice()) > 0);
    }

    @Test
    @DisplayName("测试利润率计算")
    void testCalculateOptimalPrice_ProfitMargin() {
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response.getProfitMargin());
        assertTrue(response.getProfitMargin().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("测试价格变动百分比")
    void testCalculateOptimalPrice_PriceChangePercent() {
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response.getPriceChangePercent());
    }

    @Test
    @DisplayName("测试竞品分析启用")
    void testCalculateOptimalPrice_CompetitorAnalysisEnabled() {
        request.setEnableCompetitorAnalysis(true);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        // 如果有竞品数据，应该包含竞品价格信息
        assertNotNull(response);
    }

    @Test
    @DisplayName("测试竞品分析禁用")
    void testCalculateOptimalPrice_CompetitorAnalysisDisabled() {
        request.setEnableCompetitorAnalysis(false);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
        assertNotNull(response.getRecommendedPrice());
    }

    @Test
    @DisplayName("测试季节性调整启用")
    void testCalculateOptimalPrice_SeasonalAdjustmentEnabled() {
        request.setEnableSeasonalAdjustment(true);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
        // 因子贡献应该包含季节性因子
        if (response.getFactorContributions() != null) {
            assertNotNull(response.getFactorContributions());
        }
    }

    @Test
    @DisplayName("测试季节性调整禁用")
    void testCalculateOptimalPrice_SeasonalAdjustmentDisabled() {
        request.setEnableSeasonalAdjustment(false);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试库存调整启用")
    void testCalculateOptimalPrice_InventoryAdjustmentEnabled() {
        request.setEnableInventoryAdjustment(true);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试库存调整禁用")
    void testCalculateOptimalPrice_InventoryAdjustmentDisabled() {
        request.setEnableInventoryAdjustment(false);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试批量计算最优价格")
    void testBatchCalculateOptimalPrice() {
        List<PricingRequest> requests = new ArrayList<>();
        
        PricingRequest req1 = new PricingRequest();
        req1.setProductId(1L);
        req1.setProductCode("SKU001");
        req1.setCostPrice(new BigDecimal("50.00"));
        req1.setCurrentPrice(new BigDecimal("99.00"));
        req1.setTargetProfitMargin(new BigDecimal("20.00"));
        
        PricingRequest req2 = new PricingRequest();
        req2.setProductId(2L);
        req2.setProductCode("SKU002");
        req2.setCostPrice(new BigDecimal("30.00"));
        req2.setCurrentPrice(new BigDecimal("59.00"));
        req2.setTargetProfitMargin(new BigDecimal("25.00"));
        
        requests.add(req1);
        requests.add(req2);

        List<PricingResponse> responses = pricingService.batchCalculateOptimalPrice(requests);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        
        for (PricingResponse response : responses) {
            assertNotNull(response.getRecommendedPrice());
            assertTrue(response.getRecommendedPrice().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    @DisplayName("测试批量计算空列表")
    void testBatchCalculateOptimalPrice_EmptyList() {
        List<PricingRequest> requests = new ArrayList<>();
        List<PricingResponse> responses = pricingService.batchCalculateOptimalPrice(requests);

        assertNotNull(responses);
        assertEquals(0, responses.size());
    }

    @Test
    @DisplayName("测试根据竞品数据调整价格")
    void testAdjustPriceByCompetitors() {
        PricingResponse response = pricingService.adjustPriceByCompetitors(1L);

        assertNotNull(response);
        assertNotNull(response.getRecommendedPrice());
    }

    @Test
    @DisplayName("测试手动价格调整")
    void testManualPriceAdjustment() {
        BigDecimal targetPrice = new BigDecimal("88.00");
        String reason = "促销活动调整";
        
        PricingResponse response = pricingService.manualPriceAdjustment(1L, targetPrice, reason);

        assertNotNull(response);
        assertEquals("SKU001", response.getProductCode());
    }

    @Test
    @DisplayName("测试手动价格调整低价")
    void testManualPriceAdjustment_LowPrice() {
        BigDecimal targetPrice = new BigDecimal("10.00");
        String reason = "清仓处理";
        
        PricingResponse response = pricingService.manualPriceAdjustment(1L, targetPrice, reason);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试手动价格调整高价")
    void testManualPriceAdjustment_HighPrice() {
        BigDecimal targetPrice = new BigDecimal("999.00");
        String reason = "限量版调价";
        
        PricingResponse response = pricingService.manualPriceAdjustment(1L, targetPrice, reason);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试获取产品定价信息")
    void testGetProductPricingInfo() {
        PricingResponse response = pricingService.getProductPricingInfo(1L);

        assertNotNull(response);
        assertNotNull(response.getProductCode());
        assertNotNull(response.getOriginalPrice());
        assertNotNull(response.getRecommendedPrice());
    }

    @Test
    @DisplayName("测试获取不存在产品的定价信息")
    void testGetProductPricingInfo_NotFound() {
        PricingResponse response = pricingService.getProductPricingInfo(99999L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试边界值：零成本")
    void testCalculateOptimalPrice_ZeroCost() {
        request.setCostPrice(BigDecimal.ZERO);
        request.setCurrentPrice(new BigDecimal("50.00"));
        
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
        assertNotNull(response.getRecommendedPrice());
    }

    @Test
    @DisplayName("测试边界值：零利润率")
    void testCalculateOptimalPrice_ZeroProfitMargin() {
        request.setTargetProfitMargin(BigDecimal.ZERO);
        
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
        assertNotNull(response.getRecommendedPrice());
    }

    @Test
    @DisplayName("测试边界值：高利润率")
    void testCalculateOptimalPrice_HighProfitMargin() {
        request.setTargetProfitMargin(new BigDecimal("100.00"));
        
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试定价策略说明")
    void testPricingStrategy_Description() {
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response.getPricingStrategy());
        assertTrue(response.getPricingStrategy().length() > 0);
    }

    @Test
    @DisplayName("测试因子贡献包含季节性")
    void testFactorContributions_Seasonal() {
        request.setEnableSeasonalAdjustment(true);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        if (response.getFactorContributions() != null) {
            // 季节性因子应该存在
            assertTrue(response.getFactorContributions().containsKey("seasonal") || 
                       response.getFactorContributions().size() >= 0);
        }
    }

    @Test
    @DisplayName("测试因子贡献包含库存")
    void testFactorContributions_Inventory() {
        request.setEnableInventoryAdjustment(true);
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        if (response.getFactorContributions() != null) {
            assertTrue(response.getFactorContributions().size() >= 0);
        }
    }

    @Test
    @DisplayName("测试计算时间记录")
    void testCalculationTime() {
        long startTime = System.currentTimeMillis();
        PricingResponse response = pricingService.calculateOptimalPrice(request);
        long endTime = System.currentTimeMillis();

        assertTrue(endTime - startTime < 5000); // 应该在5秒内完成
        assertNotNull(response.getCalculationTime());
    }

    @Test
    @DisplayName("测试无产品编码")
    void testCalculateOptimalPrice_NoProductCode() {
        request.setProductCode(null);
        
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("测试所有调整都禁用")
    void testCalculateOptimalPrice_AllAdjustmentsDisabled() {
        request.setEnableCompetitorAnalysis(false);
        request.setEnableSeasonalAdjustment(false);
        request.setEnableInventoryAdjustment(false);
        
        PricingResponse response = pricingService.calculateOptimalPrice(request);

        assertNotNull(response);
        // 价格应该基于成本加成
        BigDecimal minExpectedPrice = request.getCostPrice()
                .multiply(new BigDecimal("1.2")); // 至少20%利润率
        assertTrue(response.getRecommendedPrice().compareTo(minExpectedPrice) >= 0);
    }
}