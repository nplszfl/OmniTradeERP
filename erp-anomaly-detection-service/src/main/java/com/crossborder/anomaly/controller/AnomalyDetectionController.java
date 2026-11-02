package com.crossborder.anomaly.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.anomaly.dto.AnomalyDetectionResult;
import com.crossborder.anomaly.dto.AnomalyStats;
import com.crossborder.anomaly.dto.OrderFeatures;
import com.crossborder.anomaly.entity.AnomalyRecord;
import com.crossborder.anomaly.service.AnomalyDetectionService;
import com.crossborder.anomaly.service.AnomalyRecordService;
import com.crossborder.anomaly.vo.AnomalyRecordVO;
import com.crossborder.erp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 异常检测控制器
 *
 * <p>⚠️ 之前这个服务只有「算」的接口，没有「查」的接口 ——
 * 检测结果算完就丢，业务方既查不到历史，也没法人工复核。
 * 现在补齐了记录查询 / 人工复核 / 统计三个能力。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/anomaly")
@RequiredArgsConstructor
@Tag(name = "AI 订单异常检测", description = "基于规则引擎 + AI 评分的订单风险检测")
public class AnomalyDetectionController {

    private final AnomalyDetectionService detectionService;
    private final AnomalyRecordService anomalyRecordService;

    @PostMapping("/detect")
    @Operation(summary = "单订单异常检测")
    public AnomalyDetectionResult detect(@RequestBody OrderFeatures features) {
        return detectionService.detect(features);
    }

    @PostMapping("/detect-batch")
    @Operation(summary = "批量异常检测")
    public List<AnomalyDetectionResult> detectBatch(@RequestBody List<OrderFeatures> featuresList) {
        return detectionService.detectBatch(featuresList);
    }

    @GetMapping("/orders")
    @Operation(summary = "分页查询异常检测记录")
    public Result<IPage<AnomalyRecordVO>> listOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        IPage<AnomalyRecord> result = anomalyRecordService.page(new Page<>(page, size), status, keyword);
        return Result.success(AnomalyRecordVO.from(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单条异常检测记录")
    public Result<AnomalyRecordVO> getOne(@PathVariable Long id) {
        return Result.success(AnomalyRecordVO.from(anomalyRecordService.getById(id)));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "人工复核：确认异常 / 驳回")
    public Result<AnomalyRecordVO> confirm(@PathVariable Long id,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String remark,
                                            @RequestParam(required = false) String operator) {
        AnomalyRecord record = anomalyRecordService.confirm(id, status, remark, operator);
        return Result.success(AnomalyRecordVO.from(record));
    }

    @GetMapping("/stats")
    @Operation(summary = "异常统计概览")
    public Result<AnomalyStats> stats() {
        return Result.success(anomalyRecordService.stats());
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public String health() {
        return "AI 异常检测服务运行正常";
    }
}
