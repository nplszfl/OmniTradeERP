package com.crossborder.anomaly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.AnomalyStats;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.entity.AnomalyRecord;
import com.crossborder.anomaly.mapper.AnomalyRecordMapper;
import com.crossborder.anomaly.service.AnomalyRecordService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 异常检测记录服务实现
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyRecordServiceImpl implements AnomalyRecordService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final AnomalyRecordMapper anomalyRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDetection(OrderFeatures features, AnomalyDetectionResult result) {
        if (features == null || result == null) {
            return null;
        }
        AnomalyRecord record = anomalyRecordMapper.selectOne(new LambdaQueryWrapper<AnomalyRecord>()
                .eq(AnomalyRecord::getOrderId, features.getOrderId())
                .last("limit 1"));
        boolean isNew = record == null;
        if (isNew) {
            record = new AnomalyRecord();
            record.setOrderId(features.getOrderId());
            record.setStatus(STATUS_PENDING);
        }
        record.setOrderNo(features.getOrderId());
        record.setPlatform(features.getPlatform());
        record.setOrderAmount(features.getOrderAmount());
        record.setRiskScore(result.getRiskScore());
        record.setRiskLevel(result.getRiskLevel());
        record.setAnomalyFlag(result.getIsAnomaly());
        record.setAnomalyTypes(toJson(result.getAnomalyTypes()));
        record.setTriggeredRules(toJson(result.getTriggeredRules()));
        record.setAiScore(result.getAiScore());
        record.setRuleScore(result.getRuleScore());
        record.setRecommendation(result.getRecommendation());
        record.setExplanation(result.getExplanation());
        record.setDetectionLatencyMs(result.getDetectionLatencyMs());

        if (isNew) {
            anomalyRecordMapper.insert(record);
        } else {
            // 重复检测：已复核过的记录保持复核结论，不被自动覆盖回 PENDING
            anomalyRecordMapper.updateById(record);
        }
        return record.getId();
    }

    @Override
    public IPage<AnomalyRecord> page(IPage<AnomalyRecord> page, String status, String keyword) {
        LambdaQueryWrapper<AnomalyRecord> wrapper = new LambdaQueryWrapper<AnomalyRecord>()
                .eq(StringUtils.hasText(status), AnomalyRecord::getStatus, status)
                .orderByDesc(AnomalyRecord::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AnomalyRecord::getOrderNo, keyword)
                    .or().like(AnomalyRecord::getOrderId, keyword));
        }
        return anomalyRecordMapper.selectPage(page, wrapper);
    }

    @Override
    public AnomalyRecord getById(Long id) {
        return anomalyRecordMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnomalyRecord confirm(Long id, String status, String remark, String operator) {
        AnomalyRecord record = anomalyRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("异常记录不存在: " + id);
        }
        if (!STATUS_CONFIRMED.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw new IllegalArgumentException("复核状态必须是 CONFIRMED 或 REJECTED");
        }
        record.setStatus(status);
        record.setConfirmRemark(remark);
        record.setConfirmedBy(operator);
        record.setConfirmedAt(java.time.LocalDateTime.now());
        anomalyRecordMapper.updateById(record);
        return record;
    }

    @Override
    public AnomalyStats stats() {
        long total = anomalyRecordMapper.selectCount(null);
        long pending = countByStatus(STATUS_PENDING);
        long confirmed = countByStatus(STATUS_CONFIRMED);
        long rejected = countByStatus(STATUS_REJECTED);

        Long highRisk = anomalyRecordMapper.selectCount(new LambdaQueryWrapper<AnomalyRecord>()
                .in(AnomalyRecord::getRiskLevel, List.of("HIGH", "CRITICAL")));

        List<AnomalyRecord> all = anomalyRecordMapper.selectList(new LambdaQueryWrapper<AnomalyRecord>()
                .select(AnomalyRecord::getRiskScore));
        double avg = all.stream()
                .map(AnomalyRecord::getRiskScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0d);

        long anomalyCount = confirmed + rejected;
        double rate = total == 0 ? 0d : Math.round(anomalyCount * 10000d / total) / 100d;

        return AnomalyStats.builder()
                .total(total)
                .pending(pending)
                .confirmed(confirmed)
                .rejected(rejected)
                .highRisk(highRisk)
                .avgRiskScore(Math.round(avg * 100d) / 100d)
                .anomalyRate(rate)
                .build();
    }

    private long countByStatus(String status) {
        return anomalyRecordMapper.selectCount(new LambdaQueryWrapper<AnomalyRecord>()
                .eq(AnomalyRecord::getStatus, status));
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            log.warn("序列化失败，降级为简单拼接: {}", e.getMessage());
            return values.stream()
                    .map(v -> "\"" + v.replace("\"", "") + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
    }
}
