package com.crossborder.recommend.service;

import com.crossborder.erp.common.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.crossborder.recommend.dto.FeedbackRequest;
import com.crossborder.recommend.dto.FeedbackStats;
import com.crossborder.recommend.engine.WeightTuner;
import com.crossborder.recommend.entity.RecommendFeedback;
import com.crossborder.recommend.entity.RecommendResult;
import com.crossborder.recommend.repository.RecommendFeedbackRepository;
import com.crossborder.recommend.repository.RecommendResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private RecommendFeedbackRepository feedbackRepository;

    @Mock
    private RecommendResultRepository resultRepository;

    private WeightTuner weightTuner;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        // WeightTuner 需要 ScoringEngine 实例
        com.crossborder.recommend.engine.ScoringEngine scoringEngine =
            new com.crossborder.recommend.engine.ScoringEngine();
        weightTuner = new WeightTuner(feedbackRepository,
            org.mockito.Mockito.mock(com.crossborder.recommend.repository.RecommendWeightSnapshotRepository.class),
            scoringEngine);
        service = new FeedbackService(feedbackRepository, resultRepository, weightTuner);
    }

    @Test
    @DisplayName("ADOPTED + 高利润 → accuracy_score 应为正")
    void submitAdoptedHighProfit() {
        when(resultRepository.selectById(1L)).thenReturn(buildResult(1L, "AMZ-001"));

        FeedbackRequest req = FeedbackRequest.builder()
            .resultId(1L).candidateId(100L).feedbackType("ADOPTED")
            .sellerRating(5)
            .actual30dSales(3000)
            .actual30dProfit(new BigDecimal("12000.00"))
            .feedbackBy("seller-A")
            .build();

        RecommendFeedback fb = service.submitFeedback(req);

        assertNotNull(fb);
        assertNotNull(fb.getAccuracyScore());
        assertTrue(fb.getAccuracyScore() > 0.5,
            "高利润采纳应得到高准确度分，实际=" + fb.getAccuracyScore());
        assertEquals("AMZ-001", fb.getSku());
        verify(feedbackRepository, times(1)).insert(any(RecommendFeedback.class));
    }

    @Test
    @DisplayName("ADOPTED + 低利润 → accuracy_score 应较低")
    void submitAdoptedLowProfit() {
        when(resultRepository.selectById(2L)).thenReturn(buildResult(2L, "AMZ-002"));

        FeedbackRequest req = FeedbackRequest.builder()
            .resultId(2L).candidateId(101L).feedbackType("ADOPTED")
            .sellerRating(2)
            .actual30dSales(100)
            .actual30dProfit(new BigDecimal("50.00"))
            .feedbackBy("seller-B")
            .build();

        RecommendFeedback fb = service.submitFeedback(req);

        assertNotNull(fb);
        assertTrue(fb.getAccuracyScore() < 0.5,
            "低利润采纳应得到较低准确度分，实际=" + fb.getAccuracyScore());
    }

    @Test
    @DisplayName("REJECTED → accuracy_score 应为负")
    void submitRejected() {
        FeedbackRequest req = FeedbackRequest.builder()
            .resultId(3L).candidateId(102L).feedbackType("REJECTED")
            .sellerRating(1).feedbackBy("seller-C")
            .build();

        RecommendFeedback fb = service.submitFeedback(req);

        assertNotNull(fb);
        assertTrue(fb.getAccuracyScore() < 0,
            "拒绝应得到负准确度分，实际=" + fb.getAccuracyScore());
    }

    @Test
    @DisplayName("IGNORED → accuracy_score 应为 0")
    void submitIgnored() {
        FeedbackRequest req = FeedbackRequest.builder()
            .resultId(4L).candidateId(103L).feedbackType("IGNORED")
            .feedbackBy("seller-D")
            .build();

        RecommendFeedback fb = service.submitFeedback(req);

        assertNotNull(fb);
        assertEquals(0.0, fb.getAccuracyScore(), 0.001);
    }

    @Test
    @DisplayName("非法 feedbackType 应抛异常")
    void submitInvalidType() {
        FeedbackRequest req = FeedbackRequest.builder()
            .resultId(5L).candidateId(104L).feedbackType("UNKNOWN")
            .build();

        assertThrows(BizException.class, () -> service.submitFeedback(req));
    }

    @Test
    @DisplayName("sellerRating 越界应抛异常")
    void submitInvalidRating() {
        FeedbackRequest req = FeedbackRequest.builder()
            .resultId(6L).candidateId(105L).feedbackType("ADOPTED")
            .sellerRating(99)
            .build();

        assertThrows(BizException.class, () -> service.submitFeedback(req));
    }

    @Test
    @DisplayName("缺 resultId 应抛异常")
    void submitMissingResultId() {
        FeedbackRequest req = FeedbackRequest.builder()
            .candidateId(106L).feedbackType("ADOPTED")
            .build();

        assertThrows(BizException.class, () -> service.submitFeedback(req));
    }

    @Test
    @DisplayName("findFeedbacks: 按类型过滤")
    void findFeedbacksByType() {
        when(feedbackRepository.selectList(any(Wrapper.class))).thenReturn(List.of(
            buildFeedback(1L, "ADOPTED"),
            buildFeedback(2L, "ADOPTED")
        ));

        List<RecommendFeedback> result = service.findFeedbacks("ADOPTED", null, null, 10);

        assertEquals(2, result.size());
        verify(feedbackRepository, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("computeStats: 空数据返回零值")
    void computeStatsEmpty() {
        when(feedbackRepository.selectList(any(Wrapper.class))).thenReturn(List.of());

        FeedbackStats stats = service.computeStats(null, null);

        assertEquals(0L, stats.getTotalCount());
        assertEquals(0.0, stats.getAdoptRate());
        assertEquals(0.0, stats.getAvgAccuracy());
    }

    @Test
    @DisplayName("computeStats: 多类型混合正确统计")
    void computeStatsMixed() {
        when(feedbackRepository.selectList(any(Wrapper.class))).thenReturn(List.of(
            buildFeedbackWithOutcome(1L, "ADOPTED", 5, 5, new BigDecimal("8000")),
            buildFeedbackWithOutcome(2L, "ADOPTED", 4, 4, new BigDecimal("3000")),
            buildFeedbackWithOutcome(3L, "REJECTED", 2, 0, null),
            buildFeedbackWithOutcome(4L, "IGNORED", 1, 0, null)
        ));

        FeedbackStats stats = service.computeStats(null, null);

        assertEquals(4L, stats.getTotalCount());
        assertEquals(2L, stats.getAdoptedCount());
        assertEquals(1L, stats.getRejectedCount());
        assertEquals(1L, stats.getIgnoredCount());
        assertEquals(0.5, stats.getAdoptRate(), 0.001);
        assertEquals(2L, stats.getOutcomeCount(),
            "有实际销量/利润的反馈才算 outcome (ADOPTED+5k/3k → 2条)");
        assertTrue(stats.getAvgSellerRating() > 0);
    }

    @Test
    @DisplayName("computeStats: 类目过滤")
    void computeStatsByCategory() {
        when(feedbackRepository.selectList(any(Wrapper.class))).thenReturn(List.of(
            buildFeedbackWithOutcome(1L, "ADOPTED", 5, 5, new BigDecimal("1000"))
        ));

        FeedbackStats stats = service.computeStats("Electronics", null);

        assertEquals(1L, stats.getTotalCount());
        assertEquals(1L, stats.getAdoptedCount());
    }

    // helpers

    private RecommendResult buildResult(Long id, String sku) {
        return RecommendResult.builder()
            .id(id).candidateId(100L).sku(sku)
            .score(75.0).recommendLevel("BUY")
            .adoptionStatus("PENDING")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    private RecommendFeedback buildFeedback(Long id, String type) {
        return RecommendFeedback.builder()
            .id(id).resultId(id).candidateId(id * 10).sku("X-" + id)
            .feedbackType(type).accuracyScore(0.5)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    private RecommendFeedback buildFeedbackWithOutcome(Long id, String type, int rating,
                                                        int sales, BigDecimal profit) {
        return RecommendFeedback.builder()
            .id(id).resultId(id).candidateId(id * 10).sku("X-" + id)
            .feedbackType(type).sellerRating(rating)
            .actual30dSales(sales).actual30dProfit(profit)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }
}