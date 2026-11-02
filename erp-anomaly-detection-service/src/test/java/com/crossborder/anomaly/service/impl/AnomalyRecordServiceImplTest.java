package com.crossborder.anomaly.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.AnomalyStats;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.entity.AnomalyRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常检测记录服务集成测试（H2 + 真实 MyBatis-Plus）
 *
 * <p>重点验证三件事：</p>
 * <ol>
 *   <li>检测结果真的落库了（之前这个服务是「算完就丢」的）</li>
 *   <li>同一订单重复检测是<b>更新</b>而不是无限堆积记录</li>
 *   <li>人工复核状态流转正确，非法状态被拒</li>
 * </ol>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootTest
@ActiveProfiles("dev")
class AnomalyRecordServiceImplTest {

    @Autowired
    private AnomalyRecordServiceImpl service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM t_anomaly_record");
    }

    private OrderFeatures features(String orderId) {
        return OrderFeatures.builder()
                .orderId(orderId)
                .platform("amazon")
                .orderAmount(new BigDecimal("89.99"))
                .customerOrderCount(0)
                .customerLevel("NEW")
                .itemCount(1)
                .shippingCountry("US")
                .ipCountry("US")
                .build();
    }

    private AnomalyDetectionResult result(int score, String level) {
        return AnomalyDetectionResult.builder()
                .orderId("ORD-001")
                .riskScore(score)
                .riskLevel(level)
                .isAnomaly(score >= 40)
                .anomalyTypes(List.of("EXTREME_AMOUNT"))
                .triggeredRules(List.of("金额异常"))
                .aiScore(score)
                .ruleScore(score)
                .recommendation("REVIEW")
                .explanation("测试")
                .detectionLatencyMs(12L)
                .build();
    }

    @Test
    @DisplayName("检测结果落库后可查回")
    void detectionIsPersisted() {
        Long id = service.saveDetection(features("ORD-001"), result(85, "CRITICAL"));

        assertNotNull(id);
        AnomalyRecord record = service.getById(id);
        assertNotNull(record, "检测结果必须落库，不能算完就丢");
        assertEquals("ORD-001", record.getOrderId());
        assertEquals("amazon", record.getPlatform());
        assertEquals(85, record.getRiskScore());
        assertEquals("CRITICAL", record.getRiskLevel());
        assertEquals("PENDING", record.getStatus());
        assertTrue(record.getAnomalyTypes().contains("EXTREME_AMOUNT"));
    }

    @Test
    @DisplayName("同一订单重复检测 → 更新原记录，不堆积")
    void repeatedDetectionUpdatesSameRow() {
        Long first = service.saveDetection(features("ORD-002"), result(50, "MEDIUM"));
        Long second = service.saveDetection(features("ORD-002"), result(90, "CRITICAL"));

        assertEquals(first, second, "重复检测应复用同一条记录");
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_anomaly_record", Integer.class));
        assertEquals(90, service.getById(first).getRiskScore());
    }

    @Test
    @DisplayName("人工复核：确认异常 / 驳回")
    void confirmUpdatesStatus() {
        Long id = service.saveDetection(features("ORD-003"), result(75, "HIGH"));

        AnomalyRecord confirmed = service.confirm(id, "CONFIRMED", "确认为欺诈订单", "ops01");
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals("ops01", confirmed.getConfirmedBy());
        assertEquals("确认为欺诈订单", confirmed.getConfirmRemark());
        assertNotNull(confirmed.getConfirmedAt());

        AnomalyRecord rejected = service.confirm(id, "REJECTED", "误报", "ops02");
        assertEquals("REJECTED", rejected.getStatus());
    }

    @Test
    @DisplayName("非法复核状态被拒绝")
    void confirmRejectsInvalidStatus() {
        Long id = service.saveDetection(features("ORD-004"), result(75, "HIGH"));
        assertThrows(IllegalArgumentException.class,
                () -> service.confirm(id, "WHATEVER", null, "ops01"));
    }

    @Test
    @DisplayName("复核不存在的记录 → 明确报错")
    void confirmMissingRecordThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.confirm(999999L, "CONFIRMED", null, "ops01"));
    }

    @Test
    @DisplayName("分页查询可按状态与关键字过滤")
    void pageFiltersByStatusAndKeyword() {
        service.saveDetection(features("ORD-A"), result(90, "CRITICAL"));
        service.saveDetection(features("ORD-B"), result(20, "LOW"));
        Long b = service.getById(service.saveDetection(features("ORD-B"), result(20, "LOW"))).getId();

        IPage<AnomalyRecord> all = service.page(new Page<>(1, 10), null, null);
        assertEquals(2, all.getTotal());

        IPage<AnomalyRecord> pending = service.page(new Page<>(1, 10), "PENDING", null);
        assertEquals(2, pending.getTotal());

        IPage<AnomalyRecord> byKeyword = service.page(new Page<>(1, 10), null, "ORD-A");
        assertEquals(1, byKeyword.getTotal());

        service.confirm(b, "REJECTED", "正常", "ops01");
        IPage<AnomalyRecord> rejected = service.page(new Page<>(1, 10), "REJECTED", null);
        assertEquals(1, rejected.getTotal());
    }

    @Test
    @DisplayName("统计概览：总数/待复核/异常率")
    void statsSummarizes() {
        service.saveDetection(features("ORD-S1"), result(90, "CRITICAL"));
        service.saveDetection(features("ORD-S2"), result(70, "HIGH"));
        Long s3 = service.saveDetection(features("ORD-S3"), result(10, "LOW"));
        service.confirm(s3, "REJECTED", "正常", "ops01");

        AnomalyStats stats = service.stats();
        assertEquals(3L, stats.getTotal());
        assertEquals(2L, stats.getPending());
        assertEquals(1L, stats.getRejected());
        assertEquals(2L, stats.getHighRisk());
        // 异常数按「模型判定」(anomaly_flag) 算，不是按「人工复核状态」算：
        // S1(90) + S2(70) 判定为异常，S3(10) 正常 → 2/3 = 66.67%
        assertEquals(2L, stats.getAnomaly());
        assertEquals(1L, stats.getNormal());
        assertEquals(66.67, stats.getAnomalyRate(), 0.01);
        assertEquals(66.67, stats.getRate(), 0.01);
        assertTrue(stats.getAvgRiskScore() > 0);
    }
}
