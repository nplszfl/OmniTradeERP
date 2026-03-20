package com.crossborder.pricing.service.impl;

import com.crossborder.pricing.entity.PriceHistory;
import com.crossborder.pricing.service.PriceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 价格历史服务单元测试
 */
@SpringBootTest
@DisplayName("价格历史服务测试")
class PriceHistoryServiceImplTest {

    @Autowired
    private PriceHistoryService priceHistoryService;

    private PriceHistory priceHistory;

    @BeforeEach
    void setUp() {
        priceHistory = new PriceHistory();
        priceHistory.setProductId(1L);
        priceHistory.setProductCode("SKU001");
        priceHistory.setOldPrice(new BigDecimal("99.00"));
        priceHistory.setNewPrice(new BigDecimal("88.00"));
        priceHistory.setChangeReason("竞品价格调整");
        priceHistory.setChangeType("AUTO");
    }

    @Test
    @DisplayName("测试记录价格变更")
    void testRecordPriceChange() {
        assertDoesNotThrow(() -> {
            priceHistoryService.recordPriceChange(priceHistory);
        });
    }

    @Test
    @DisplayName("测试获取产品价格历史")
    void testGetPriceHistory() {
        priceHistoryService.recordPriceChange(priceHistory);
        
        List<PriceHistory> history = priceHistoryService.getPriceHistory(1L);
        
        assertNotNull(history);
    }

    @Test
    @DisplayName("测试获取指定时间范围的价格历史")
    void testGetPriceHistory_TimeRange() {
        LocalDateTime startTime = LocalDateTime.now().minusDays(30);
        LocalDateTime endTime = LocalDateTime.now();
        
        List<PriceHistory> history = priceHistoryService.getPriceHistory(1L, startTime, endTime);
        
        assertNotNull(history);
    }

    @Test
    @DisplayName("测试获取最近N次价格变更")
    void testGetRecentPriceChanges() {
        // 先记录几次价格变更
        for (int i = 0; i < 5; i++) {
            PriceHistory ph = new PriceHistory();
            ph.setProductId(1L);
            ph.setProductCode("SKU001");
            ph.setOldPrice(new BigDecimal(String.valueOf(100 + i)));
            ph.setNewPrice(new BigDecimal(String.valueOf(90 + i)));
            ph.setChangeReason("测试变更" + i);
            ph.setChangeType("AUTO");
            priceHistoryService.recordPriceChange(ph);
        }
        
        List<PriceHistory> recentChanges = priceHistoryService.getRecentPriceChanges(1L, 3);
        
        assertNotNull(recentChanges);
        assertTrue(recentChanges.size() <= 3);
    }

    @Test
    @DisplayName("测试获取价格趋势分析")
    void testGetPriceTrendAnalysis() {
        // 先记录一些价格变更
        for (int i = 0; i < 3; i++) {
            PriceHistory ph = new PriceHistory();
            ph.setProductId(2L);
            ph.setProductCode("SKU002");
            ph.setOldPrice(new BigDecimal(String.valueOf(100 + i * 10)));
            ph.setNewPrice(new BigDecimal(String.valueOf(90 + i * 10)));
            ph.setChangeReason("测试趋势");
            ph.setChangeType("AUTO");
            priceHistoryService.recordPriceChange(ph);
        }
        
        PriceHistoryService.PriceTrendAnalysis analysis = 
                priceHistoryService.getPriceTrendAnalysis(2L, 30);
        
        assertNotNull(analysis);
        assertEquals(2L, analysis.getProductId());
    }

    @Test
    @DisplayName("测试价格趋势分析-计算价格变化")
    void testPriceTrendAnalysis_PriceChange() {
        PriceHistoryService.PriceTrendAnalysis analysis = 
                priceHistoryService.getPriceTrendAnalysis(1L, 30);
        
        if (analysis != null && analysis.getStartingPrice() != null && analysis.getEndingPrice() != null) {
            BigDecimal changeAmount = analysis.getEndingPrice().subtract(analysis.getStartingPrice());
            assertNotNull(changeAmount);
        }
    }

    @Test
    @DisplayName("测试价格趋势分析-时间范围")
    void testPriceTrendAnalysis_DateRange() {
        PriceHistoryService.PriceTrendAnalysis analysis = 
                priceHistoryService.getPriceTrendAnalysis(1L, 30);
        
        if (analysis != null) {
            assertNotNull(analysis.getStartDate());
            assertNotNull(analysis.getEndDate());
        }
    }

    @Test
    @DisplayName("测试价格趋势分析-变更次数")
    void testPriceTrendAnalysis_TotalChanges() {
        PriceHistoryService.PriceTrendAnalysis analysis = 
                priceHistoryService.getPriceTrendAnalysis(1L, 30);
        
        if (analysis != null) {
            assertTrue(analysis.getTotalChanges() >= 0);
        }
    }

    @Test
    @DisplayName("测试不存在产品的价格历史")
    void testGetPriceHistory_NotFound() {
        List<PriceHistory> history = priceHistoryService.getPriceHistory(99999L);
        
        assertNotNull(history);
    }

    @Test
    @DisplayName("测试空产品ID的价格历史")
    void testGetPriceHistory_NullProductId() {
        List<PriceHistory> history = priceHistoryService.getPriceHistory(null);
        
        assertNotNull(history);
    }

    @Test
    @DisplayName("测试记录多个价格变更")
    void testRecordMultiplePriceChanges() {
        for (int i = 0; i < 10; i++) {
            PriceHistory ph = new PriceHistory();
            ph.setProductId((long) (i % 3 + 10)); // 产品10, 11, 12
            ph.setProductCode("SKU00" + (i % 3 + 10));
            ph.setOldPrice(new BigDecimal("100.00"));
            ph.setNewPrice(new BigDecimal("80.00"));
            ph.setChangeReason("批量测试" + i);
            ph.setChangeType("MANUAL");
            
            assertDoesNotThrow(() -> priceHistoryService.recordPriceChange(ph));
        }
    }

    @Test
    @DisplayName("测试价格历史记录包含时间戳")
    void testPriceHistory_Timestamp() {
        priceHistoryService.recordPriceChange(priceHistory);
        
        List<PriceHistory> history = priceHistoryService.getPriceHistory(1L);
        
        if (!history.isEmpty()) {
            PriceHistory latest = history.get(0);
            assertNotNull(latest.getCreateTime());
        }
    }
}