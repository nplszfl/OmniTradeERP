package com.crossborder.anomaly.service.impl;

import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.engine.AIScorer;
import com.crossborder.anomaly.engine.RuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常检测服务测试 - 规则 + AI 融合
 */
@DisplayName("异常检测服务融合测试")
class AnomalyDetectionServiceImplTest {

    private AnomalyDetectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionServiceImpl(new RuleEngine(), new AIScorer(), new NoopAnomalyRecordService());
    }

    @Test
    @DisplayName("正常订单 - 风险等级应 LOW，处置 ALLOW")
    void testNormalOrder_LowRisk() {
        OrderFeatures f = OrderFeatures.builder()
            .orderId("ORD-001")
            .customerOrderCount(20)
            .customerTotalSpent(new BigDecimal("5000"))
            .customerLevel("VIP")
            .orderAmount(new BigDecimal("80"))
            .itemCount(2)
            .shippingCountry("US")
            .ipCountry("US")
            .paymentLatencySeconds(120L)
            .usedCoupon(false)
            .highRiskCountry(false)
            .newDevice(false)
            .build();

        AnomalyDetectionResult result = service.detect(f);

        assertNotNull(result);
        assertEquals("ORD-001", result.getOrderId());
        assertEquals("LOW", result.getRiskLevel());
        assertEquals("ALLOW", result.getRecommendation());
        assertFalse(result.getIsAnomaly());
        assertNotNull(result.getDetectionLatencyMs());
        assertTrue(result.getDetectionLatencyMs() >= 0);
    }

    @Test
    @DisplayName("可疑订单 - 风险等级应 >= MEDIUM")
    void testSuspiciousOrder_MediumRisk() {
        OrderFeatures f = OrderFeatures.builder()
            .orderId("ORD-002")
            .customerOrderCount(0)
            .customerLevel("NEW")
            .orderAmount(new BigDecimal("1500"))
            .itemCount(1)
            .shippingCountry("US")
            .ipCountry("CN")  // 地理不一致
            .paymentLatencySeconds(60L)
            .usedCoupon(false)
            .highRiskCountry(false)
            .newDevice(true)
            .build();

        AnomalyDetectionResult result = service.detect(f);

        assertTrue(result.getRiskScore() >= 40, "应为中等以上风险: " + result.getRiskScore());
        assertNotEquals("LOW", result.getRiskLevel());
        assertTrue(result.getIsAnomaly());
        assertNotNull(result.getAnomalyTypes());
        assertFalse(result.getAnomalyTypes().isEmpty(), "应有异常类型");
    }

    @Test
    @DisplayName("高风险订单 - 应触发 HOLD 或 REJECT 处置")
    void testHighRiskOrder_HoldOrReject() {
        OrderFeatures f = OrderFeatures.builder()
            .orderId("ORD-003")
            .customerOrderCount(0)
            .customerTotalSpent(BigDecimal.ZERO)
            .customerLevel("NEW")
            .orderAmount(new BigDecimal("5000"))
            .itemCount(1)
            .shippingCountry("XX")
            .ipCountry("YY")
            .paymentLatencySeconds(1L)
            .usedCoupon(true)
            .discountAmount(new BigDecimal("4000"))
            .highRiskCountry(true)
            .newDevice(true)
            .build();

        AnomalyDetectionResult result = service.detect(f);

        assertTrue(result.getRiskScore() >= 60, "应为高风险: " + result.getRiskScore());
        assertTrue("HIGH".equals(result.getRiskLevel()) || "CRITICAL".equals(result.getRiskLevel()));
        assertTrue("HOLD".equals(result.getRecommendation()) || "REJECT".equals(result.getRecommendation()));
    }

    @Test
    @DisplayName("风险等级阈值 - 80+ 为 CRITICAL")
    void testCriticalThreshold() {
        AnomalyDetectionResult result = service.fuseAndAssess(90, 95, List.of("R001"), "ORD-X");
        assertEquals("CRITICAL", result.getRiskLevel());
        assertEquals("REJECT", result.getRecommendation());
    }

    @Test
    @DisplayName("风险等级阈值 - 60-79 为 HIGH")
    void testHighThreshold() {
        AnomalyDetectionResult result = service.fuseAndAssess(70, 65, List.of("R001"), "ORD-X");
        assertEquals("HIGH", result.getRiskLevel());
        assertEquals("HOLD", result.getRecommendation());
    }

    @Test
    @DisplayName("风险等级阈值 - 40-59 为 MEDIUM")
    void testMediumThreshold() {
        AnomalyDetectionResult result = service.fuseAndAssess(50, 45, List.of("R001"), "ORD-X");
        assertEquals("MEDIUM", result.getRiskLevel());
        assertEquals("REVIEW", result.getRecommendation());
    }

    @Test
    @DisplayName("风险等级阈值 - 0-39 为 LOW")
    void testLowThreshold() {
        AnomalyDetectionResult result = service.fuseAndAssess(20, 25, List.of(), "ORD-X");
        assertEquals("LOW", result.getRiskLevel());
        assertEquals("ALLOW", result.getRecommendation());
        assertFalse(result.getIsAnomaly());
    }

    @Test
    @DisplayName("融合策略 - 应取规则和 AI 评分的较高者")
    void testFuseMaxStrategy() {
        // AI 评分显著高于规则评分时
        AnomalyDetectionResult result = service.fuseAndAssess(20, 90, List.of(), "ORD-X");
        assertTrue(result.getRiskScore() >= 80, "应反映 AI 高分");
    }

    @Test
    @DisplayName("解释文本 - 应包含评分和触发的规则")
    void testExplanationContent() {
        AnomalyDetectionResult result = service.fuseAndAssess(
            80, 75, List.of("R001: 新客户大额订单", "R002: IP 与收货国家不一致"), "ORD-X");

        assertNotNull(result.getExplanation());
        assertTrue(result.getExplanation().contains("80"), "应包含规则分");
        assertTrue(result.getExplanation().contains("R001"));
        assertTrue(result.getExplanation().contains("R002"));
    }

    @Test
    @DisplayName("批量检测 - 多个订单独立评估")
    void testBatchDetection() {
        OrderFeatures normal = OrderFeatures.builder()
            .orderId("BATCH-1")
            .customerOrderCount(20)
            .customerLevel("VIP")
            .orderAmount(new BigDecimal("80"))
            .shippingCountry("US").ipCountry("US")
            .build();

        OrderFeatures suspicious = OrderFeatures.builder()
            .orderId("BATCH-2")
            .customerOrderCount(0)
            .customerLevel("NEW")
            .orderAmount(new BigDecimal("3000"))
            .shippingCountry("XX").ipCountry("YY")
            .highRiskCountry(true)
            .build();

        List<AnomalyDetectionResult> results = service.detectBatch(List.of(normal, suspicious));

        assertEquals(2, results.size());
        assertEquals("BATCH-1", results.get(0).getOrderId());
        assertEquals("BATCH-2", results.get(1).getOrderId());
        assertEquals("LOW", results.get(0).getRiskLevel());
        assertNotEquals("LOW", results.get(1).getRiskLevel());
    }
}
