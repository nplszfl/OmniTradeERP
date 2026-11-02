package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.ROIAnalysisDTO;
import com.crossborder.analytics.repository.AnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * ROIAnalysisService 单元测试
 *
 * <p>⚠️ 之前这个 Service 的实现是纯模拟（写死 125000 营收 / 75000 成本 / 2500 订单），
 * 所以旧测试断言的是那批假数字。现在实现改成查数据库，测试改成 mock Repository，
 * 断言的是<b>真实计算逻辑</b>（利润、ROI、ROAS、AOV 是否由营收/成本/订单数推导）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ROIAnalysisService 单元测试")
class ROIAnalysisServiceTest {

    @Mock
    private AnalyticsRepository repository;

    @InjectMocks
    private ROIAnalysisService roiAnalysisService;

    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        startDate = LocalDate.now().minusDays(30);
        endDate = LocalDate.now();

        // ⚠️ lenient：不同测试只用到其中一部分 stub，Mockito 严格模式会报 UnnecessaryStubbing。
        //    这里统一在 setUp 里给出「一份真实数据」，各用例按需覆盖。
        lenient().when(repository.getPeriodAggregate(any(), any())).thenReturn(
                new AnalyticsRepository.PeriodAggregate(
                        new BigDecimal("10000.00"),   // revenue
                        new BigDecimal("6000.00"),    // cost
                        200L,                       // orderCount
                        500L));                     // unitsSold
        lenient().when(repository.getProductAggregates(any(), any(), anyInt())).thenReturn(List.of(
                new AnalyticsRepository.ProductAggregate("SKU-A", "商品A", 100L,
                        new BigDecimal("6000.00"), new BigDecimal("3000.00")),
                new AnalyticsRepository.ProductAggregate("SKU-B", "商品B", 60L,
                        new BigDecimal("4000.00"), new BigDecimal("3000.00"))));
        lenient().when(repository.getPlatformAggregates(any(), any())).thenReturn(List.of(
                new AnalyticsRepository.PlatformAggregate("Amazon", 120L,
                        new BigDecimal("7000.00"), new BigDecimal("3500.00")),
                new AnalyticsRepository.PlatformAggregate("Shopee", 80L,
                        new BigDecimal("3000.00"), new BigDecimal("2500.00"))));
        lenient().when(repository.getWeeklyAggregates(any(), any())).thenReturn(List.of(
                new AnalyticsRepository.WeeklyAggregate(startDate,
                        new BigDecimal("5500.00"), new BigDecimal("3500.00")),
                new AnalyticsRepository.WeeklyAggregate(startDate.plusWeeks(1),
                        new BigDecimal("4500.00"), new BigDecimal("2500.00"))));
    }

    @Test
    @DisplayName("getProductROI - 汇总由真实营收/成本推导")
    void testGetProductROI() {
        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);

        assertNotNull(roi);
        assertEquals("PRODUCT", roi.getDimension());
        assertNotNull(roi.getSummary());
        assertNotNull(roi.getDetails());
        assertNotNull(roi.getTrends());
        assertNotNull(roi.getTopPerformers());
    }

    @Test
    @DisplayName("getPlatformROI - 明细按平台")
    void testGetPlatformROI() {
        ROIAnalysisDTO roi = roiAnalysisService.getPlatformROI(startDate, endDate);

        assertNotNull(roi);
        assertEquals("PLATFORM", roi.getDimension());
        assertEquals(2, roi.getDetails().size());
        assertEquals("Amazon", roi.getDetails().get(0).getId());
    }

    @Test
    @DisplayName("ROI 汇总：利润/ROI/ROAS/AOV 全部由营收成本订单推导")
    void testROISummary() {
        ROIAnalysisDTO.ROISummary summary = roiAnalysisService.getProductROI(startDate, endDate).getSummary();

        assertEquals(0, summary.getTotalRevenue().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, summary.getTotalCost().compareTo(new BigDecimal("6000.00")));
        assertEquals(0, summary.getTotalProfit().compareTo(new BigDecimal("4000.00")));

        // ROI = 4000/6000 = 66.67%
        assertEquals(0, summary.getOverallROI().compareTo(new BigDecimal("66.67")));
        // ROAS = 10000/6000 = 1.67
        assertEquals(0, summary.getRoas().compareTo(new BigDecimal("1.67")));
        assertEquals(200, summary.getTotalOrders());
        // AOV = 10000/200 = 50.00
        assertEquals(0, summary.getAov().compareTo(new BigDecimal("50.00")));

        // ⚠️ 转化率需要流量/埋点表，项目里没有，必须是 0 而不是编 3.5%
        assertEquals(0, summary.getConversionRate().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("ROI 明细：逐个商品的利润/ROI 正确")
    void testROIDetails() {
        List<ROIAnalysisDTO.ROIDetail> details =
                roiAnalysisService.getProductROI(startDate, endDate).getDetails();

        assertFalse(details.isEmpty());

        ROIAnalysisDTO.ROIDetail a = details.get(0);
        assertEquals("SKU-A", a.getId());
        assertEquals("商品A", a.getName());
        assertEquals(0, a.getRevenue().compareTo(new BigDecimal("6000.00")));
        assertEquals(0, a.getCost().compareTo(new BigDecimal("3000.00")));
        assertEquals(0, a.getProfit().compareTo(new BigDecimal("3000.00")));
        // ROI = 3000/3000 = 100%
        assertEquals(0, a.getRoi().compareTo(new BigDecimal("100.00")));
        // ROAS = 6000/3000 = 2.00
        assertEquals(0, a.getRoas().compareTo(new BigDecimal("2.00")));
        assertEquals(100, a.getOrders());
        // AOV = 6000/100 = 60.00
        assertEquals(0, a.getAov().compareTo(new BigDecimal("60.00")));
    }

    @Test
    @DisplayName("ROI 明细为空的场合不炸")
    void testROIDetailsEmpty() {
        when(repository.getProductAggregates(any(), any(), anyInt())).thenReturn(List.of());

        ROIAnalysisDTO roi = roiAnalysisService.getProductROI(startDate, endDate);
        assertTrue(roi.getDetails().isEmpty());
        assertTrue(roi.getTopPerformers().isEmpty());
    }

    @Test
    @DisplayName("ROI 趋势：按周来自 getWeeklyAggregates")
    void testROITrends() {
        List<ROIAnalysisDTO.ROITrend> trends =
                roiAnalysisService.getProductROI(startDate, endDate).getTrends();

        assertEquals(2, trends.size());
        assertNotNull(trends.get(0).getPeriod());
        assertEquals(0, trends.get(0).getRevenue().compareTo(new BigDecimal("5500.00")));
        assertEquals(0, trends.get(0).getCost().compareTo(new BigDecimal("3500.00")));
        // ROI = 2000/3500 = 57.14%
        assertEquals(0, trends.get(0).getRoi().compareTo(new BigDecimal("57.14")));
    }

    @Test
    @DisplayName("Top Performer 按 ROI 降序，最多 3 个")
    void testTopPerformers() {
        List<ROIAnalysisDTO.TopPerformer> top =
                roiAnalysisService.getProductROI(startDate, endDate).getTopPerformers();

        assertTrue(top.size() <= 3);
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).getRoi().compareTo(top.get(i).getRoi()) >= 0);
        }
    }

    @Test
    @DisplayName("getCampaignROI - 项目里没有广告投放表，details 为空而不是编")
    void testGetCampaignROI() {
        ROIAnalysisDTO roi = roiAnalysisService.getCampaignROI(startDate, endDate);

        assertNotNull(roi);
        assertEquals("CAMPAIGN", roi.getDimension());
        assertTrue(roi.getDetails().isEmpty());
    }

    @Test
    @DisplayName("ROI 计算自洽：profit = revenue - cost")
    void testROICalculation() {
        ROIAnalysisDTO.ROISummary summary = roiAnalysisService.getProductROI(startDate, endDate).getSummary();

        BigDecimal expectedProfit = summary.getTotalRevenue().subtract(summary.getTotalCost());
        assertEquals(0, expectedProfit.compareTo(summary.getTotalProfit()));

        BigDecimal expectedROI = expectedProfit
                .divide(summary.getTotalCost(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, expectedROI.compareTo(summary.getOverallROI()));
    }

    @Test
    @DisplayName("成本为 0 时 ROI/ROAS 返回 0，不抛除零异常")
    void testZeroCostNoDivideByZero() {
        when(repository.getPeriodAggregate(any(), any())).thenReturn(
                new AnalyticsRepository.PeriodAggregate(
                        new BigDecimal("1000.00"), BigDecimal.ZERO, 10L, 10L));

        ROIAnalysisDTO.ROISummary summary = roiAnalysisService.getProductROI(startDate, endDate).getSummary();

        assertEquals(0, summary.getOverallROI().compareTo(BigDecimal.ZERO));
        assertEquals(0, summary.getRoas().compareTo(BigDecimal.ZERO));
        assertEquals(0, summary.getTotalProfit().compareTo(new BigDecimal("1000.00")));
    }
}
