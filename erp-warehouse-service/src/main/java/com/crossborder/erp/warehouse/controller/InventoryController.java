package com.crossborder.erp.warehouse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.warehouse.entity.InventoryBatch;
import com.crossborder.erp.warehouse.entity.InventoryCheckResult;
import com.crossborder.erp.warehouse.service.WarehouseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存 Controller
 *
 * <p>覆盖 {@link WarehouseService} 中的库存批次管理与库内操作（库内调拨、冻结/解冻、报损、盘点）。</p>
 *
 * <p>业务异常统一由 erp-common 的 {@code GlobalExceptionHandler} 处理，Controller 层不做异常包装。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequestMapping("/warehouse/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final WarehouseService warehouseService;

    // ==================== 库存批次管理 ====================

    /**
     * 创建库存批次
     */
    @PostMapping("/batches")
    public Result<Long> createInventoryBatch(@RequestBody InventoryBatch batch) {
        // Service 内部会用 quantity 回填 availableQuantity，这里兜底避免空值拆箱
        if (batch.getQuantity() == null) {
            batch.setQuantity(0);
        }
        log.info("创建库存批次请求: 仓库={}, SKU={}, 数量={}",
                batch.getWarehouseId(), batch.getProductId(), batch.getQuantity());
        Long batchId = warehouseService.createInventoryBatch(batch);
        return Result.success(batchId);
    }

    /**
     * 查询库存批次详情
     */
    @GetMapping("/batches/{batchId}")
    public Result<InventoryBatch> getInventoryBatch(@PathVariable Long batchId) {
        return Result.success(warehouseService.getInventoryBatch(batchId));
    }

    /**
     * 分页查询库存批次
     */
    @GetMapping("/batches/page")
    public Result<Page<InventoryBatch>> listInventoryBatches(@RequestParam(required = false) Long warehouseId,
                                                              @RequestParam(required = false) Long productId,
                                                              @RequestParam(required = false) String batchStatus,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        log.info("分页查询库存批次: 仓库={}, SKU={}, 批次状态={}", warehouseId, productId, batchStatus);
        return Result.success(warehouseService.listInventoryBatches(warehouseId, productId, batchStatus, page, size));
    }

    /**
     * 调整批次库存数量（operation = add / subtract）
     */
    @PutMapping("/batches/{batchId}/quantity")
    public Result<Void> updateBatchQuantity(@PathVariable Long batchId,
                                             @RequestParam Integer quantity,
                                             @RequestParam String operation) {
        log.info("调整批次库存: 批次={}, 数量={}, 操作={}", batchId, quantity, operation);
        warehouseService.updateBatchQuantity(batchId, quantity, operation);
        return Result.success();
    }

    /**
     * 冻结批次库存
     */
    @PostMapping("/batches/{batchId}/freeze")
    public Result<Void> freezeBatch(@PathVariable Long batchId, @RequestParam Integer quantity) {
        log.info("冻结批次: 批次={}, 数量={}", batchId, quantity);
        warehouseService.freezeBatch(batchId, quantity);
        return Result.success();
    }

    /**
     * 解冻批次库存
     */
    @PostMapping("/batches/{batchId}/unfreeze")
    public Result<Void> unfreezeBatch(@PathVariable Long batchId, @RequestParam Integer quantity) {
        log.info("解冻批次: 批次={}, 数量={}", batchId, quantity);
        warehouseService.unfreezeBatch(batchId, quantity);
        return Result.success();
    }

    /**
     * 查询即将过期的批次
     */
    @GetMapping("/batches/expiring")
    public Result<List<InventoryBatch>> getExpiringBatches(@RequestParam Long warehouseId,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           LocalDate beforeDate) {
        log.info("查询临期批次: 仓库={}, 截止日期={}", warehouseId, beforeDate);
        return Result.success(warehouseService.getExpiringBatches(warehouseId, beforeDate));
    }

    // ==================== 库内操作 ====================

    /**
     * 库内调拨（同一仓库不同库位之间移库）
     */
    @PostMapping("/relocate")
    public Result<Void> relocate(@RequestBody RelocateRequest request) {
        log.info("库内调拨: 仓库={}, SKU={}, {} -> {}, 数量={}", request.getWarehouseId(), request.getProductId(),
                request.getFromLocationCode(), request.getToLocationCode(), request.getQuantity());
        warehouseService.relocate(request.getWarehouseId(), request.getProductId(),
                request.getFromLocationCode(), request.getToLocationCode(), request.getQuantity());
        return Result.success();
    }

    /**
     * 库存冻结
     */
    @PostMapping("/freeze")
    public Result<Void> freezeInventory(@RequestBody InventoryOperationRequest request) {
        log.info("库存冻结: 仓库={}, SKU={}, 数量={}, 原因={}", request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), request.getReason());
        warehouseService.freezeInventory(request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), request.getReason());
        return Result.success();
    }

    /**
     * 库存解冻
     */
    @PostMapping("/unfreeze")
    public Result<Void> unfreezeInventory(@RequestBody InventoryOperationRequest request) {
        log.info("库存解冻: 仓库={}, SKU={}, 数量={}", request.getWarehouseId(), request.getProductId(),
                request.getQuantity());
        warehouseService.unfreezeInventory(request.getWarehouseId(), request.getProductId(), request.getQuantity());
        return Result.success();
    }

    /**
     * 库存报损
     */
    @PostMapping("/damage")
    public Result<Void> reportDamage(@RequestBody InventoryOperationRequest request) {
        log.info("库存报损: 仓库={}, SKU={}, 数量={}, 原因={}", request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), request.getReason());
        warehouseService.reportDamage(request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), request.getReason());
        return Result.success();
    }

    /**
     * 创建盘点单
     */
    @PostMapping("/checks")
    public Result<Void> createInventoryCheck(@RequestBody InventoryCheckRequest request) {
        List<Long> locationIds = request.getLocationIds() == null ? List.of() : request.getLocationIds();
        log.info("创建盘点单: 仓库={}, 类型={}, 库位数={}", request.getWarehouseId(), request.getCheckType(),
                locationIds.size());
        warehouseService.createInventoryCheck(request.getWarehouseId(), locationIds,
                request.getCheckType(), request.getRemark());
        return Result.success();
    }

    /**
     * 确认盘点结果（按差异量调整库存）
     */
    @PostMapping("/checks/{checkId}/confirm")
    public Result<Void> confirmInventoryCheck(@PathVariable Long checkId,
                                               @RequestBody List<InventoryCheckResult> results) {
        List<InventoryCheckResult> checkResults = results == null ? List.of() : results;
        log.info("确认盘点结果: 盘点单={}, 结果行数={}", checkId, checkResults.size());
        warehouseService.confirmInventoryCheck(checkId, checkResults);
        return Result.success();
    }

    /**
     * 库内调拨请求
     */
    @Data
    public static class RelocateRequest {
        private Long warehouseId;
        private Long productId;
        private String fromLocationCode;
        private String toLocationCode;
        private Integer quantity;
    }

    /**
     * 库内库存操作请求（冻结 / 解冻 / 报损）
     */
    @Data
    public static class InventoryOperationRequest {
        private Long warehouseId;
        private Long productId;
        private Integer quantity;
        private String reason;
    }

    /**
     * 盘点单创建请求
     */
    @Data
    public static class InventoryCheckRequest {
        private Long warehouseId;
        private List<Long> locationIds;
        private String checkType;
        private String remark;
    }
}
