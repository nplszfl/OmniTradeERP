package com.crossborder.anomaly.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.AnomalyStats;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.entity.AnomalyRecord;

/**
 * 测试用的空实现：检测服务的单元测试只关心「算」，
 * 不关心「存」，所以这里给一个不落库的桩。
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public class NoopAnomalyRecordService implements com.crossborder.anomaly.service.AnomalyRecordService {

    @Override
    public Long saveDetection(OrderFeatures features, AnomalyDetectionResult result) {
        return 1L;
    }

    @Override
    public IPage<AnomalyRecord> page(IPage<AnomalyRecord> page, String status, String keyword) {
        return page;
    }

    @Override
    public AnomalyRecord confirm(Long id, String status, String remark, String operator) {
        AnomalyRecord record = new AnomalyRecord();
        record.setId(id);
        record.setStatus(status);
        record.setConfirmRemark(remark);
        record.setConfirmedBy(operator);
        return record;
    }

    @Override
    public AnomalyRecord getById(Long id) {
        return null;
    }

    @Override
    public AnomalyStats stats() {
        return AnomalyStats.builder()
                .total(0L).pending(0L).confirmed(0L).rejected(0L)
                .highRisk(0L).avgRiskScore(0d).anomalyRate(0d)
                .build();
    }
}
