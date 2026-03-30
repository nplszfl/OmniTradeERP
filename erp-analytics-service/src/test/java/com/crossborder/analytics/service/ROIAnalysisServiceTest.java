package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.ROIAnalysisDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROI分析服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class ROIAnalysisServiceTest {

    @InjectMocks
    private ROIAnalysisService roiAnalysisService;

    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(roiAnalysisService, "defaultPeriodDays", 30);
        ReflectionTestUtils.setField(roiAnalysisService, "currency", "USD");
        
        startDate = LocalDate.now().minusDays(30);
        endDate = LocalDate.now();
    }

    @Test
    void testGetProductROI() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);

        // 验证
        assertNotNull(roi);
        assertEquals("PRODUCT", roi.getDimension());
        assertNotNull(roi.getSummary());
        assertNotNull(roi.getDetails());
        assertNotNull(roi.getTrends());
        assertNotNull(roi.getTopPerformers());
    }

    @Test
    void testGetPlatformROI() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getPlatformROI(startDate, endDate);

        // 验证
        assertNotNull(roi);
        assertEquals("PLATFORM", roi.getDimension());
        assertNotNull(roi.getSummary());
        assertNotNull(roi.getDetails());
    }

    @Test
    void testROISummary() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);
        ROIAnalysisDTO.ROISummary summary = roi.getSummary();

        // 验证汇总数据
        assertNotNull(summary);
        assertTrue(summary.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalCost().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalProfit().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getOverallROI().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getRoas().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalOrders() > 0);
        assertTrue(summary.getAov().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testROIDetails() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);

        // 验证明细数据
        List<ROIAnalysisDTO.ROIDetail> details = roi.getDetails();
        assertNotNull(details);
        assertFalse(details.isEmpty());

        for (ROIAnalysisDTO.ROIDetail detail : details) {
            assertNotNull(detail.getId());
            assertNotNull(detail.getName());
            assertTrue(detail.getRevenue().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(detail.getCost().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(detail.getProfit().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(detail.getRoi().compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(detail.getRoas().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    void testROITrends() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);

        // 验证趋势数据
        List<ROIAnalysisDTO.ROITrend> trends = roi.getTrends();
        assertNotNull(trends);
        assertFalse(trends.isEmpty());

        for (ROIAnalysisDTO.ROITrend trend : trends) {
            assertNotNull(trend.getPeriod());
            assertNotNull(trend.getRevenue());
            assertNotNull(trend.getCost());
            assertNotNull(trend.getRoi());
        }
    }

    @Test
    void testTopPerformers() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);

        // 验证Top Performer
        List<ROIAnalysisDTO.TopPerformer> topPerformers = roi.getTopPerformers();
        assertNotNull(topPerformers);
        assertTrue(topPerformers.size() <= 3);

        // 验证按ROI排序
        if (topPerformers.size() > 1) {
            for (int i = 1; i < topPerformers.size(); i++) {
                assertTrue(topPerformers.get(i - 1).getRoi()
                        .compareTo(topPerformers.get(i).getRoi()) >= 0);
            }
        }
    }

    @Test
    void testGetCampaignROI() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getCampaignROI(startDate, endDate);

        // 验证
        assertNotNull(roi);
        assertEquals("CAMPAIGN", roi.getDimension());
    }

    @Test
    void testROICalculation() {
        // 执行
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);
        ROIAnalysisDTO.ROISummary summary = roi.getSummary();

        // 验证ROI计算正确性
        BigDecimal expectedProfit = summary.getTotalRevenue().subtract(summary.getTotalCost());
        assertEquals(0, expectedProfit.compareTo(summary.getTotalProfit()));

        BigDecimal expectedROI = expectedProfit.divide(summary.getTotalCost(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        assertEquals(0, expectedROI.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(summary.getOverallROI()));
    }
}