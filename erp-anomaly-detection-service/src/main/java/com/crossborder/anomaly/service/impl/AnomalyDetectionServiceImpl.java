package com.crossborder.anomaly.service.impl;

import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.engine.AIScorer;
import com.crossborder.anomaly.engine.RuleEngine;
import com.crossborder.anomaly.service.AnomalyDetectionService;
import com.crossborder.anomaly.service.AnomalyRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常检测服务实现 - 规则引擎 + AI 评分双层融合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private final RuleEngine ruleEngine;
    private final AIScorer aiScorer;
    private final AnomalyRecordService anomalyRecordService;

    @Override
    public AnomalyDetectionResult detect(OrderFeatures features) {
        long start = System.currentTimeMillis();
        log.info("开始异常检测: orderId={}", features.getOrderId());

        // 1. 规则引擎评分
        RuleEngine.RuleResult ruleResult = ruleEngine.evaluate(features);
        int ruleScore = ruleResult.score();
        List<String> triggeredRules = ruleResult.triggeredRules();

        // 2. AI 模型评分
        int aiScore = aiScorer.score(features);

        // 3. 融合评估
        AnomalyDetectionResult result = fuseAndAssess(ruleScore, aiScore, triggeredRules, features.getOrderId());

        // 4. 补充解释
        result.setAiScore(aiScore);
        result.setRuleScore(ruleScore);
        result.setDetectionLatencyMs(System.currentTimeMillis() - start);

        // 5. 落库 —— 检测结果必须留下来，否则业务方没法查历史 / 人工复核 / 统计异常率
        try {
            Long recordId = anomalyRecordService.saveDetection(features, result);
            log.info("检测结果已落库: recordId={}, orderId={}", recordId, features.getOrderId());
        } catch (Exception e) {
            // 落库失败不能影响检测结果返回（检测本身是无状态的纯计算）
            log.error("检测结果落库失败: orderId={}", features.getOrderId(), e);
        }

        log.info("异常检测完成: orderId={}, riskScore={}, level={}, isAnomaly={}, latency={}ms",
            features.getOrderId(), result.getRiskScore(), result.getRiskLevel(),
            result.getIsAnomaly(), result.getDetectionLatencyMs());

        return result;
    }

    @Override
    public List<AnomalyDetectionResult> detectBatch(List<OrderFeatures> featuresList) {
        log.info("批量异常检测: 共 {} 个订单", featuresList.size());
        List<AnomalyDetectionResult> results = new ArrayList<>(featuresList.size());
        for (OrderFeatures f : featuresList) {
            results.add(detect(f));
        }
        return results;
    }

    @Override
    public AnomalyDetectionResult fuseAndAssess(int ruleScore, int aiScore, List<String> triggeredRules, String orderId) {
        // 融合策略：取较高分（保守策略，宁可错杀）
        int finalScore = Math.max(ruleScore, aiScore);

        // 加权融合（规则 60% + AI 40%）
        int weightedScore = (int) Math.round(ruleScore * 0.6 + aiScore * 0.4);
        finalScore = Math.max(finalScore, weightedScore);

        // 风险等级划分
        String riskLevel;
        if (finalScore >= 80) {
            riskLevel = "CRITICAL";
        } else if (finalScore >= 60) {
            riskLevel = "HIGH";
        } else if (finalScore >= 40) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        // 处置建议
        String recommendation = switch (riskLevel) {
            case "CRITICAL" -> "REJECT";
            case "HIGH" -> "HOLD";
            case "MEDIUM" -> "REVIEW";
            default -> "ALLOW";
        };

        // 异常类型提取
        List<String> anomalyTypes = extractAnomalyTypes(triggeredRules);

        // 详细说明
        String explanation = buildExplanation(finalScore, riskLevel, triggeredRules, ruleScore, aiScore);

        return AnomalyDetectionResult.builder()
            .orderId(orderId)
            .riskScore(finalScore)
            .riskLevel(riskLevel)
            .isAnomaly(finalScore >= 40)
            .anomalyTypes(anomalyTypes)
            .triggeredRules(triggeredRules)
            .recommendation(recommendation)
            .explanation(explanation)
            .build();
    }

    private List<String> extractAnomalyTypes(List<String> rules) {
        List<String> types = new ArrayList<>();
        for (String rule : rules) {
            if (rule.contains("新客户大额") || rule.contains("NEW 客户")) types.add("FIRST_ORDER_HIGH_AMOUNT");
            if (rule.contains("IP 与收货")) types.add("GEO_MISMATCH");
            if (rule.contains("高风险国家")) types.add("HIGH_RISK_GEO");
            if (rule.contains("支付延迟")) types.add("PAYMENT_ANOMALY");
            if (rule.contains("优惠券")) types.add("COUPON_ABUSE");
            if (rule.contains("新设备")) types.add("NEW_DEVICE");
            if (rule.contains("极高金额")) types.add("EXTREME_AMOUNT");
        }
        return types;
    }

    private String buildExplanation(int finalScore, String level, List<String> rules, int ruleScore, int aiScore) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("综合风险评分: %d/100 (%s级)。", finalScore, level));
        sb.append(String.format("规则引擎: %d分，AI 评分: %d分。", ruleScore, aiScore));
        if (!rules.isEmpty()) {
            sb.append("触发规则: ").append(String.join("; ", rules)).append("。");
        } else {
            sb.append("未触发具体规则，AI 模型识别到轻微风险。");
        }
        return sb.toString();
    }
}
