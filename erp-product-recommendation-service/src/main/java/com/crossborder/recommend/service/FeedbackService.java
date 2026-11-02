package com.crossborder.recommend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.recommend.dto.FeedbackRequest;
import com.crossborder.recommend.dto.FeedbackStats;
import com.crossborder.recommend.entity.RecommendFeedback;
import com.crossborder.recommend.engine.WeightTuner;
import com.crossborder.recommend.repository.RecommendFeedbackRepository;
import com.crossborder.recommend.repository.RecommendResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈服务 - v1.9.0 Feedback 闭环入口
 *
 * 职责：
 *   1. 记录卖家反馈 (ADOPTED/REJECTED/IGNORED)
 *   2. 计算 accuracy_score
 *   3. 提供统计查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final RecommendFeedbackRepository feedbackRepository;
    private final RecommendResultRepository resultRepository;
    private final WeightTuner weightTuner;

    private static final List<String> ALLOWED_TYPES = List.of("ADOPTED", "REJECTED", "IGNORED");

    /**
     * 提交反馈（核心入口）
     */
    @Transactional
    public RecommendFeedback submitFeedback(FeedbackRequest req) {
        validate(req);

        // 自动计算 accuracy_score
        double accuracy = weightTuner.computeAccuracyScore(
            req.getFeedbackType(),
            req.getSellerRating(),
            req.getActual30dSales(),
            req.getActual30dProfit()
        );

        // 从 result 中读取 sku（如果请求没带），category/platform 可选
        String sku = req.getSku();
        if (sku == null) {
            try {
                var result = resultRepository.selectById(req.getResultId());
                if (result != null) sku = result.getSku();
            } catch (Exception e) {
                log.debug("可选关联查询失败: {}", e.getMessage());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        RecommendFeedback fb = RecommendFeedback.builder()
            .resultId(req.getResultId())
            .candidateId(req.getCandidateId())
            .sku(sku)
            .category(req.getCategory())
            .platform(req.getPlatform())
            .feedbackType(req.getFeedbackType())
            .sellerRating(req.getSellerRating())
            .actual30dSales(req.getActual30dSales())
            .actual30dProfit(req.getActual30dProfit())
            .actualConversionRate(req.getActualConversionRate())
            .accuracyScore(accuracy)
            .feedbackNote(req.getFeedbackNote())
            .feedbackBy(req.getFeedbackBy())
            .createdAt(now)
            .updatedAt(now)
            .build();
        feedbackRepository.insert(fb);

        // 同步更新 recommend_result 的 adoption_status（如果能查到）
        try {
            var result = resultRepository.selectById(req.getResultId());
            if (result != null) {
                result.setAdoptionStatus(req.getFeedbackType());
                result.setUpdatedAt(now);
                resultRepository.updateById(result);
            }
        } catch (Exception e) {
            log.warn("同步更新 recommend_result 失败 (resultId={}): {}", req.getResultId(), e.getMessage());
        }

        log.info("反馈已记录: resultId={}, type={}, accuracy={}",
            req.getResultId(), req.getFeedbackType(), String.format("%.3f", accuracy));
        return fb;
    }

    /**
     * 查询反馈列表
     */
    public List<RecommendFeedback> findFeedbacks(String feedbackType, String category, String platform,
                                                  Integer limit) {
        QueryWrapper<RecommendFeedback> q = new QueryWrapper<>();
        if (feedbackType != null && !feedbackType.isBlank()) {
            q.eq("feedback_type", feedbackType);
        }
        if (category != null && !category.isBlank()) {
            q.eq("category", category);
        }
        if (platform != null && !platform.isBlank()) {
            q.eq("platform", platform);
        }
        q.orderByDesc("created_at");
        if (limit != null && limit > 0) {
            q.last("LIMIT " + limit);
        }
        return feedbackRepository.selectList(q);
    }

    /**
     * 计算反馈统计
     */
    public FeedbackStats computeStats(String category, String platform) {
        QueryWrapper<RecommendFeedback> q = new QueryWrapper<>();
        if (category != null && !category.isBlank()) q.eq("category", category);
        if (platform != null && !platform.isBlank()) q.eq("platform", platform);
        List<RecommendFeedback> all = feedbackRepository.selectList(q);

        if (all.isEmpty()) {
            return FeedbackStats.builder()
                .totalCount(0L).adoptedCount(0L).rejectedCount(0L).ignoredCount(0L)
                .adoptRate(0.0).outcomeCount(0L).avgSellerRating(0.0)
                .avgAccuracy(0.0).avgActualProfit(0.0)
                .build();
        }

        long adopted = all.stream().filter(f -> "ADOPTED".equals(f.getFeedbackType())).count();
        long rejected = all.stream().filter(f -> "REJECTED".equals(f.getFeedbackType())).count();
        long ignored = all.stream().filter(f -> "IGNORED".equals(f.getFeedbackType())).count();
        long outcome = all.stream().filter(f ->
            (f.getActual30dSales() != null && f.getActual30dSales() > 0)
            || (f.getActual30dProfit() != null && f.getActual30dProfit().doubleValue() != 0)
        ).count();

        DoubleSummary rating = new DoubleSummary();
        all.stream()
            .filter(f -> f.getSellerRating() != null)
            .mapToInt(f -> f.getSellerRating())
            .forEach(rating::acceptInt);

        DoubleSummary accuracy = new DoubleSummary();
        all.stream()
            .filter(f -> f.getAccuracyScore() != null)
            .mapToDouble(f -> f.getAccuracyScore())
            .forEach(accuracy::accept);

        DoubleSummary profit = new DoubleSummary();
        all.stream()
            .filter(f -> f.getActual30dProfit() != null)
            .mapToDouble(f -> f.getActual30dProfit().doubleValue())
            .forEach(profit::accept);

        double adoptRate = adopted / (double) all.size();

        return FeedbackStats.builder()
            .totalCount((long) all.size())
            .adoptedCount(adopted)
            .rejectedCount(rejected)
            .ignoredCount(ignored)
            .adoptRate(round4(adoptRate))
            .outcomeCount(outcome)
            .avgSellerRating(round4(rating.getAverage()))
            .avgAccuracy(round4(accuracy.getAverage()))
            .avgActualProfit(round4(profit.getAverage()))
            .build();
    }

    private void validate(FeedbackRequest req) {
        if (req.getResultId() == null) {
            throw new BizException("resultId 不能为空");
        }
        if (req.getCandidateId() == null) {
            throw new BizException("candidateId 不能为空");
        }
        if (req.getFeedbackType() == null || !ALLOWED_TYPES.contains(req.getFeedbackType())) {
            throw new BizException("feedbackType 必须是 ADOPTED/REJECTED/IGNORED 之一");
        }
        if (req.getSellerRating() != null && (req.getSellerRating() < 1 || req.getSellerRating() > 5)) {
            throw new BizException("sellerRating 必须在 1~5 之间");
        }
    }

    static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * 内部 Double 统计工具 (避免引入 commons-math3 等额外依赖)
     */
    static class DoubleSummary {
        private double sum = 0;
        private long count = 0;

        public void accept(double v) {
            sum += v;
            count++;
        }

        public void acceptInt(int v) {
            sum += v;
            count++;
        }

        public void combine(DoubleSummary other) {
            sum += other.sum;
            count += other.count;
        }

        public double getAverage() {
            return count == 0 ? 0.0 : sum / count;
        }
    }
}