package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.SalesForecastDTO;
import com.crossborder.analytics.repository.AnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 销售预测服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class SalesForecastServiceTest {

    @Mock
    private AnalyticsRepository repository;

    @InjectMocks
    private SalesForecastService salesForecastService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(salesForecastService, "defaultForecastDays", 30);
        ReflectionTestUtils.setField(salesForecastService, "minHistoricalDays", 7);

        // ⚠️ 历史数据现在来自数据库，不再是 Math.random() 造的。
        //    这里 stub 出确定的 30 天序列，验证预测算法本身。
        List<AnalyticsRepository.DailyAggregate> history = new java.util.ArrayList<>();
        LocalDate from = LocalDate.now().minusDays(29);
        for (int i = 0; i < 30; i++) {
            long qty = 10L + (i % 7);
            history.add(new AnalyticsRepository.DailyAggregate(
                    from.plusDays(i), qty, BigDecimal.valueOf(qty * 45L).setScale(2, RoundingMode.HALF_UP)));
        }
        when(repository.getSkuDailySales(anyString(), anyInt())).thenReturn(history);
    }

    @Test
    void testGetSalesForecast() {
        // 执行
        SalesForecastDTO forecast = salesForecastService.getSalesForecast("P0001", 30);

        // 验证
        assertNotNull(forecast);
        assertEquals("P0001", forecast.getProductId());
        assertNotNull(forecast.getProductName());
        assertNotNull(forecast.getStartDate());
        assertNotNull(forecast.getEndDate());
        assertNotNull(forecast.getForecasts());
        assertNotNull(forecast.getHistoricalData());
        assertNotNull(forecast.getSummary());
        assertNotNull(forecast.getModelType());
    }

    @Test
    void testForecastDays() {
        // 执行 - 测试不同预测天数
        SalesForecastDTO forecast7 = salesForecastService.getSalesForecast("P0001", 7);
        SalesForecastDTO forecast14 = salesForecastService.getSalesForecast("P0001", 14);
        SalesForecastDTO forecast30 = salesForecastService.getSalesForecast("P0001", 30);

        // 验证
        assertEquals(7, forecast7.getForecasts().size());
        assertEquals(14, forecast14.getForecasts().size());
        assertEquals(30, forecast30.getForecasts().size());
    }

    @Test
    void testHistoricalData() {
        // 执行
        SalesForecastDTO forecast = salesForecastService.getSalesForecast("P0001", 30);

        // 验证历史数据
        assertNotNull(forecast.getHistoricalData());
        assertEquals(30, forecast.getHistoricalData().size());
        
        // 验证历史数据日期连续性
        for (int i = 1; i < forecast.getHistoricalData().size(); i++) {
            assertTrue(forecast.getHistoricalData().get(i).getDate()
                    .isAfter(forecast.getHistoricalData().get(i - 1).getDate()));
        }
    }

    @Test
    void testForecastData() {
        // 执行
        SalesForecastDTO forecast = salesForecastService.getSalesForecast("P0001", 30);

        // 验证预测数据
        for (SalesForecastDTO.ForecastData f : forecast.getForecasts()) {
            assertNotNull(f.getDate());
            assertNotNull(f.getPredictedSales());
            assertTrue(f.getPredictedSales().compareTo(java.math.BigDecimal.ZERO) >= 0);
            assertNotNull(f.getConfidenceLower());
            assertNotNull(f.getConfidenceUpper());
            assertTrue(f.getConfidenceLower().compareTo(java.math.BigDecimal.ZERO) >= 0);
            assertTrue(f.getConfidenceUpper().compareTo(f.getConfidenceLower()) >= 0);
        }
    }

    @Test
    void testForecastSummary() {
        // 执行
        SalesForecastDTO forecast = salesForecastService.getSalesForecast("P0001", 30);

        // 验证摘要
        SalesForecastDTO.ForecastSummary summary = forecast.getSummary();
        assertNotNull(summary);
        assertNotNull(summary.getTotalPredictedSales());
        assertNotNull(summary.getTotalPredictedRevenue());
        assertNotNull(summary.getTotalPredictedQuantity());
        assertNotNull(summary.getAvgDailySales());
        assertTrue(summary.getTotalPredictedSales().compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    @Test
    void testAccuracyMetrics() {
        // 执行
        SalesForecastDTO forecast = salesForecastService.getSalesForecast("P0001", 30);

        // 验证准确度指标
        SalesForecastDTO.AccuracyMetrics metrics = forecast.getAccuracyMetrics();
        assertNotNull(metrics);
        assertNotNull(metrics.getMape());
        assertNotNull(metrics.getMae());
        assertNotNull(metrics.getRmse());
        assertNotNull(metrics.getAccuracy());
        assertNotNull(metrics.getGrade());
        
        // 验证准确度范围
        assertTrue(metrics.getAccuracy().doubleValue() >= 0);
        assertTrue(metrics.getAccuracy().doubleValue() <= 100);
    }

    @Test
    void testBatchForecast() {
        // 执行
        List<String> productIds = List.of("P0001", "P0002", "P0003");
        List<SalesForecastDTO> forecasts = salesForecastService.batchForecast(productIds, 7);

        // 验证
        assertNotNull(forecasts);
        assertEquals(3, forecasts.size());
        
        for (SalesForecastDTO forecast : forecasts) {
            assertNotNull(forecast.getProductId());
            assertEquals(7, forecast.getForecasts().size());
        }
    }
}