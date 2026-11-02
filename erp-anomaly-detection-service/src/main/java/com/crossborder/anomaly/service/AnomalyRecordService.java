package com.crossborder.anomaly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.AnomalyStats;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.entity.AnomalyRecord;

import java.util.List;

/**
 * 异常检测记录服务
 *
 * <p>负责检测结果的落库与复核，检测本身在 {@link AnomalyDetectionService}。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public interface AnomalyRecordService {

    /**
     * 检测完成后的落库（同一订单重复检测会更新原记录而不是无限堆积）
     *
     * @return 记录ID
     */
    Long saveDetection(OrderFeatures features, AnomalyDetectionResult result);

    /**
     * 分页查询检测记录
     *
     * @param page    分页
     * @param status  复核状态，可空
     * @param keyword 订单号/订单ID 模糊匹配，可空
     */
    IPage<AnomalyRecord> page(IPage<AnomalyRecord> page, String status, String keyword);

    /**
     * 人工复核
     *
     * @param id       记录ID
     * @param status   CONFIRMED / REJECTED
     * @param remark   复核备注
     * @param operator 操作人
     */
    AnomalyRecord confirm(Long id, String status, String remark, String operator);

    /**
     * 按ID查单条记录
     */
    AnomalyRecord getById(Long id);

    /**
     * 统计概览
     */
    AnomalyStats stats();
}
