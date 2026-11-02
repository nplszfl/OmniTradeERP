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

    @PostMapping("/detect/{orderId}")
    @Operation(summary = "按订单ID重新检测（前端「对所有未审核订单重新检测」）")
    public AnomalyDetectionResult detectByOrderId(@PathVariable String orderId,
                                                   @RequestBody(required = false) OrderFeatures features) {
        OrderFeatures f = features == null ? new OrderFeatures() : features;
        f.setOrderId(orderId);
        return detectionService.detect(f);
    }

    @GetMapping("/orders")
    @Operation(summary = "查询异常检测记录")
    public List<AnomalyRecordVO> listOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        // ⚠️ 必须返<b>裸数组</b>：前端是
        //     list.value = await anomalyApi.list(filter) 然后 :data="list"
        //  包成 Result<IPage> 前端拿到的就是对象，el-table 渲染不出来。
        IPage<AnomalyRecord> result = anomalyRecordService.page(new Page<>(page, size), status, keyword);
        return result.getRecords().stream().map(AnomalyRecordVO::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单条异常检测记录")
    public AnomalyRecordVO getOne(@PathVariable Long id) {
        return AnomalyRecordVO.from(anomalyRecordService.getById(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "人工复核：确认异常 / 驳回")
    public AnomalyRecordVO confirm(@PathVariable Long id,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String remark,
                                   @RequestParam(required = false) String operator) {
        AnomalyRecord record = anomalyRecordService.confirm(id, status, remark, operator);
        return AnomalyRecordVO.from(record);
    }

    @GetMapping("/stats")
    @Operation(summary = "异常统计概览")
    public AnomalyStats stats() {
        // 同上：前端直接 stats.value = await anomalyApi.stats() → 必须返裸对象
        return anomalyRecordService.stats();
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public String health() {
        return "AI 异常检测服务运行正常";
    }
}
