package com.crossborder.erp.inventory.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.inventory.alert.entity.*;
import com.crossborder.erp.inventory.alert.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存预警控制器
 */
@Slf4j
@RestController
@RequestMapping("/inventory/alert")
@RequiredArgsConstructor
public class InventoryAlertController {

    private final InventoryAlertService alertService;
    private final AlertRecordService alertRecordService;
    private final ReplenishmentSuggestionService replenishmentSuggestionService;
    private final AlertStatisticsService alertStatisticsService;
    private final SlowMovingInventoryService slowMovingInventoryService;

    // ========== 预警规则管理 ==========

    /**
     * 创建预警规则
     */
    @PostMapping("/rule")
    public Result<String> createRule(@RequestBody InventoryAlertRule rule) {
        Long id = alertService.createRule(rule);
        return Result.successId(id);
    }

    /**
     * 分页查询预警规则
     */
    @GetMapping("/rules")
    public Result<IPage<InventoryAlertRule>> queryRules(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) Boolean enabled) {
        IPage<InventoryAlertRule> result = alertService.queryRules(page, size, warehouseId, enabled);
        return Result.success(result);
    }

    /**
     * 获取所有启用的预警规则
     */
    @GetMapping("/rules/enabled")
    public Result<List<InventoryAlertRule>> getEnabledRules() {
        return Result.success(alertService.getEnabledRules());
    }

    /**
     * 获取产品的预警规则
     */
    @GetMapping("/rules/product/{productId}")
    public Result<List<InventoryAlertRule>> getRulesByProduct(@PathVariable Long productId) {
        return Result.success(alertService.getRulesByProduct(productId));
    }

    /**
     * 更新预警规则
     */
    @PutMapping("/rule/{id}")
    public Result<Void> updateRule(@PathVariable Long id, @RequestBody InventoryAlertRule rule) {
        rule.setId(id);
        alertService.updateRule(rule);
        return Result.success();
    }

    /**
     * 删除预警规则
     */
    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return Result.success();
    }

    /**
     * 批量设置预警规则
     */
    @PostMapping("/rules/batch")
    public Result<Void> batchCreateRules(@RequestBody List<InventoryAlertRule> rules) {
        for (InventoryAlertRule rule : rules) {
            alertService.createRule(rule);
        }
        return Result.success();
    }

    // ========== 预警记录管理 ==========

    /**
     * 分页查询预警记录
     */
    @GetMapping("/records")
    public Result<IPage<InventoryAlert>> queryAlerts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        IPage<InventoryAlert> result = alertRecordService.queryAlerts(page, size, warehouseId, alertType, status, startTime, endTime);
        return Result.success(result);
    }

    /**
     * 获取待处理预警列表
     */
    @GetMapping("/records/pending")
    public Result<List<InventoryAlert>> getPendingAlerts() {
        return Result.success(alertRecordService.getPendingAlerts());
    }

    /**
     * 处理预警
     */
    @PostMapping("/record/{id}/handle")
    public Result<Void> handleAlert(
            @PathVariable Long id,
            @RequestParam Long handlerId,
            @RequestParam String handlerName,
            @RequestParam(required = false) String remark) {
        alertRecordService.handleAlert(id, handlerId, handlerName, remark);
        return Result.success();
    }

    /**
     * 忽略预警
     */
    @PostMapping("/record/{id}/ignore")
    public Result<Void> ignoreAlert(
            @PathVariable Long id,
            @RequestParam Long handlerId,
            @RequestParam String handlerName,
            @RequestParam(required = false) String remark) {
        alertRecordService.ignoreAlert(id, handlerId, handlerName, remark);
        return Result.success();
    }

    // ========== 预警统计 ==========

    /**
     * 获取实时预警统计
     */
    @GetMapping("/statistics/realtime")
    public Result<Map<String, Object>> getRealTimeStatistics(@RequestParam(required = false) String warehouseId) {
        return Result.success(alertService.getRealTimeStatistics(warehouseId));
    }

    /**
     * 获取预警趋势数据
     */
    @GetMapping("/statistics/trend")
    public Result<List<Map<String, Object>>> getTrendData(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String warehouseId) {
        return Result.success(alertStatisticsService.getTrendData(days, warehouseId));
    }

    /**
     * 获取预警类型分布
     */
    @GetMapping("/statistics/distribution")
    public Result<Map<String, Integer>> getAlertTypeDistribution(
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertStatisticsService.getAlertTypeDistribution(warehouseId, startTime, endTime));
    }

    /**
     * 获取处理效率统计
     */
    @GetMapping("/statistics/efficiency")
    public Result<Map<String, Object>> getProcessingEfficiency(
            @RequestParam(required = false) String warehouseId,
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(alertStatisticsService.getProcessingEfficiency(warehouseId, days));
    }

    // ========== 补货建议管理 ==========

    /**
     * 分页查询补货建议
     */
    @GetMapping("/replenishments")
    public Result<IPage<ReplenishmentSuggestion>> queryReplenishments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String status) {
        IPage<ReplenishmentSuggestion> result = alertService.getReplenishmentSuggestions(page, size, warehouseId, status);
        return Result.success(result);
    }

    /**
     * 获取待确认的补货建议
     */
    @GetMapping("/replenishments/pending")
    public Result<List<ReplenishmentSuggestion>> getPendingSuggestions() {
        return Result.success(replenishmentSuggestionService.getPendingSuggestions());
    }

    /**
     * 确认补货建议
     */
    @PostMapping("/replenishment/{id}/confirm")
    public Result<Void> confirmReplenishment(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam String userName,
            @RequestParam(required = false) String remark) {
        alertService.confirmReplenishment(id, userId, userName, remark);
        return Result.success();
    }

    /**
     * 取消补货建议
     */
    @PostMapping("/replenishment/{id}/cancel")
    public Result<Void> cancelReplenishment(
            @PathVariable Long id,
            @RequestParam(required = false) String remark) {
        alertService.cancelReplenishment(id, remark);
        return Result.success();
    }

    /**
     * 预估总采购金额
     */
    @GetMapping("/replenishments/estimate-amount")
    public Result<java.math.BigDecimal> estimateTotalAmount(@RequestParam(required = false) String warehouseId) {
        return Result.success(replenishmentSuggestionService.estimateTotalAmount(warehouseId));
    }

    // ========== 库存检查 ==========

    /**
     * 检查库存（库存变动时调用）
     */
    @PostMapping("/check")
    public Result<Void> checkInventory(
            @RequestParam Long productId,
            @RequestParam String sku,
            @RequestParam String warehouseId,
            @RequestParam Integer currentStock,
            @RequestParam String productName,
            @RequestParam String warehouseName) {
        alertService.checkAndAlert(productId, sku, warehouseId, currentStock, productName, warehouseName);
        return Result.success();
    }

    /**
     * 批量检查库存（定时任务调用）
     */
    @PostMapping("/check/batch")
    public Result<Void> batchCheckInventory(@RequestBody List<Map<String, Object>> inventoryList) {
        alertService.batchCheckInventory(inventoryList);
        return Result.success();
    }

    /**
     * 扫描活库存并生成预警（预警引擎的手动入口）
     *
     * 📌 与 /check 的区别：/check 要求调用方自己传 currentStock，
     *    实际上没有任何调用方，导致预警引擎从来没跑过；
     *    这里直接从活表 erp_inventory.erp_inventory 读真实可用库存来判定。
     *
     * @return 本次新建的预警条数
     */
    @PostMapping("/check/scan")
    public Result<Integer> scanLiveInventory() {
        int created = alertService.scanLiveInventory();
        return Result.success(created);
    }

    // ========== 滞销商品管理 ==========

    /**
     * 检测滞销商品
     */
    @PostMapping("/slow-moving/detect")
    public Result<SlowMovingInventory> detectSlowMoving(
            @RequestParam Long productId,
            @RequestParam String sku,
            @RequestParam(required = false) String warehouseId) {
        SlowMovingInventory result = slowMovingInventoryService.detectSlowMoving(productId, sku, warehouseId);
        return Result.success(result);
    }

    /**
     * 分页查询滞销商品
     */
    @GetMapping("/slow-moving")
    public Result<IPage<SlowMovingInventory>> querySlowMoving(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String alertLevel) {
        return Result.success(slowMovingInventoryService.querySlowMoving(page, size, warehouseId, alertLevel));
    }

    /**
     * 获取待推送的滞销预警
     */
    @GetMapping("/slow-moving/pending")
    public Result<IPage<SlowMovingInventory>> getPendingSlowMovingAlerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(slowMovingInventoryService.getPendingAlerts(page, size));
    }

    /**
     * 标记滞销预警已通知
     */
    @PostMapping("/slow-moving/{id}/notified")
    public Result<Void> markSlowMovingNotified(@PathVariable Long id) {
        slowMovingInventoryService.markAsNotified(id);
        return Result.success();
    }
}