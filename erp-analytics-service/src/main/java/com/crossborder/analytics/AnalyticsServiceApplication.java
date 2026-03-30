package com.crossborder.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据分析服务主类
 * 提供实时数据看板、销售预测、ROI分析、竞品分析等功能
 */
@SpringBootApplication
@EnableScheduling
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}