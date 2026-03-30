package com.crossborder.analytics.service;

import com.crossborder.analytics.dto.SalesForecastDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售预测服务
 * 使用简单的时间序列预测算法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesForecastService {

    @Value("${analytics.prediction.default-forecast-days:30}")
    private int defaultForecastDays;

    @Value("${analytics.prediction.min-historical-days:7}")
    private int minHistoricalDays;

    /**
     * 获取产品销售预测
     */
    public SalesForecastDTO getSalesForecast(String productId, int forecastDays) {
        log.info("Generating sales forecast for product: {}, days: {}", productId, forecastDays);
        
        int days = forecastDays > 0 ? forecastDays : defaultForecastDays;
        
        // 获取历史数据
        List<HistoricalData> historicalData = getHistoricalData(productId);
        
        // 生成预测
        List<ForecastData> forecasts = generateForecast(productId, days, historicalData);
        
        // 计算摘要
        ForecastSummary summary = calculateSummary(forecasts);
        
        // 计算准确度指标
        AccuracyMetrics accuracyMetrics = calculateAccuracyMetrics(historicalData);
        
        return SalesForecastDTO.builder()
                .productId(productId)
                .productName("Product " + productId)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(days))
                .forecasts(forecasts)
                .historicalData(historicalData)
                .summary(summary)
                .modelType("SIMPLE_MOVING_AVERAGE")
                .accuracyMetrics(accuracyMetrics)
                .build();
    }

    /**
     * 批量预测多个产品
     */
    public List<SalesForecastDTO> batchForecast(List<String> productIds, int forecastDays) {
        List<SalesForecastDTO> results = new ArrayList<>();
        
        for (String productId : productIds) {
            try {
                results.add(getSalesForecast(productId, forecastDays));
            } catch (Exception e) {
                log.error("Failed to generate forecast for product: {}", productId, e);
            }
        }
        
        return results;
    }

    /**
     * 获取历史销售数据
     */
    private List<HistoricalData> getHistoricalData(String productId) {
        List<HistoricalData> data = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        // 生成最近30天的历史数据
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int baseSales = 50 + (i % 7) * 5; // 周期性波动
            int quantity = baseSales + (int) (Math.random() * 20);
            BigDecimal sales = BigDecimal.valueOf(quantity * 45.0)
                    .setScale(2, RoundingMode.HALF_UP);
            
            data.add(HistoricalData.builder()
                    .date(date)
                    .actualSales(sales)
                    .actualQuantity(quantity)
                    .build());
        }
        
        return data;
    }

    /**
     * 生成预测数据 - 使用简单移动平均算法
     */
    private List<ForecastData> generateForecast(String productId, int days, 
                                                  List<HistoricalData> historicalData) {
        List<ForecastData> forecasts = new ArrayList<>();
        
        // 计算过去7天的平均值作为基础
        double avgSales = historicalData.stream()
                .limit(7)
                .mapToDouble(h -> h.getActualSales().doubleValue())
                .average()
                .orElse(100.0);
        
        // 计算趋势系数
        double trend = calculateTrend(historicalData);
        
        LocalDate startDate = LocalDate.now().plusDays(1);
        
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            
            // 预测值 = 平均值 + 趋势 * 天数 + 季节性调整
            double seasonalFactor = Math.sin(2 * Math.PI * i / 7) * 10; // 周周期
            double predictedSales = avgSales + (trend * i) + seasonalFactor;
            predictedSales = Math.max(predictedSales, 0); // 确保非负
            
            BigDecimal sales = BigDecimal.valueOf(predictedSales)
                    .setScale(2, RoundingMode.HALF_UP);
            
            // 置信区间
            double confidenceRange = avgSales * 0.2 * (1 + i * 0.02); // 随时间扩大
            BigDecimal lower = sales.subtract(BigDecimal.valueOf(confidenceRange))
                    .max(BigDecimal.ZERO);
            BigDecimal upper = sales.add(BigDecimal.valueOf(confidenceRange));
            
            forecasts.add(ForecastData.builder()
                    .date(date)
                    .predictedSales(sales)
                    .predictedRevenue(sales)
                    .predictedQuantity((int) (predictedSales / 45))
                    .confidenceLower(lower)
                    .confidenceUpper(upper)
                    .build());
        }
        
        return forecasts;
    }

    /**
     * 计算趋势
     */
    private double calculateTrend(List<HistoricalData> data) {
        if (data.size() < 2) return 0;
        
        // 简单线性回归
        int n = data.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = data.get(i).getActualSales().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }

    /**
     * 计算预测摘要
     */
    private ForecastSummary calculateSummary(List<ForecastData> forecasts) {
        BigDecimal totalSales = BigDecimal.ZERO;
        int totalQuantity = 0;
        
        for (ForecastData f : forecasts) {
            totalSales = totalSales.add(f.getPredictedSales());
            totalQuantity += f.getPredictedQuantity();
        }
        
        BigDecimal avgDaily = totalSales.divide(
                BigDecimal.valueOf(forecasts.size()), 
                2, RoundingMode.HALF_UP);
        
        return ForecastSummary.builder()
                .totalPredictedSales(totalSales)
                .totalPredictedRevenue(totalSales)
                .totalPredictedQuantity(totalQuantity)
                .avgDailySales(avgDaily)
                .growthRate(BigDecimal.valueOf(5.2).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * 计算准确度指标（基于历史数据的回测）
     */
    private AccuracyMetrics calculateAccuracyMetrics(List<HistoricalData> historicalData) {
        // 使用简单移动平均进行回测
        if (historicalData.size() < 14) {
            return AccuracyMetrics.builder()
                    .mape(BigDecimal.valueOf(0))
                    .mae(BigDecimal.ZERO)
                    .rmse(BigDecimal.ZERO)
                    .accuracy(BigDecimal.valueOf(0))
                    .grade("INSUFFICIENT_DATA")
                    .build();
        }
        
        List<Double> errors = new ArrayList<>();
        
        // 从第8天开始预测并与实际对比
        for (int i = 7; i < historicalData.size(); i++) {
            double sum = 0;
            for (int j = i - 7; j < i; j++) {
                sum += historicalData.get(j).getActualSales().doubleValue();
            }
            double predicted = sum / 7;
            double actual = historicalData.get(i).getActualSales().doubleValue();
            errors.add(Math.abs(predicted - actual));
        }
        
        double mape = errors.stream()
                .mapToDouble(e -> e / historicalData.subList(7, historicalData.size())
                        .stream()
                        .mapToDouble(h -> h.getActualSales().doubleValue())
                        .average()
                        .orElse(1))
                .average()
                .orElse(0) * 100;
        
        double mae = errors.stream().mapToDouble(e -> e).average().orElse(0);
        double rmse = Math.sqrt(errors.stream()
                .mapToDouble(e -> e * e).average().orElse(0));
        
        BigDecimal accuracy = BigDecimal.valueOf(Math.max(100 - mape, 0))
                .setScale(2, RoundingMode.HALF_UP);
        
        String grade;
        if (accuracy.compareTo(BigDecimal.valueOf(90)) >= 0) {
            grade = "EXCELLENT";
        } else if (accuracy.compareTo(BigDecimal.valueOf(80)) >= 0) {
            grade = "GOOD";
        } else if (accuracy.compareTo(BigDecimal.valueOf(70)) >= 0) {
            grade = "FAIR";
        } else {
            grade = "POOR";
        }
        
        return AccuracyMetrics.builder()
                .mape(BigDecimal.valueOf(mape).setScale(2, RoundingMode.HALF_UP))
                .mae(BigDecimal.valueOf(mae).setScale(2, RoundingMode.HALF_UP))
                .rmse(BigDecimal.valueOf(rmse).setScale(2, RoundingMode.HALF_UP))
                .accuracy(accuracy)
                .grade(grade)
                .build();
    }
}