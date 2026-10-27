package com.crossborder.erp.warehouse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.warehouse.entity.StockTransfer;
import com.crossborder.erp.warehouse.entity.StockTransferDetail;
import com.crossborder.erp.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存调拨 Controller
 *
 * <p>覆盖 {@link WarehouseService} 中的调拨单相关方法。</p>
 *
 * <p>业务异常统一由 erp-common 的 {@code GlobalExceptionHandler} 处理，Controller 层不做异常包装。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@Slf4j
@RestController
@RequestMapping("/warehouse/stock-transfer")
@RequiredArgsConstructor
public class TransferController {

    private final WarehouseService warehouseService;

    /**
     * 创建调拨单
     */
    @PostMapping
    public Result<Long> createTransfer(@RequestBody StockTransfer transfer) {
        log.info("创建调拨单请求: {} -> {}", transfer.getFromWarehouseId(), transfer.getToWarehouseId());
        Long transferId = warehouseService.createTransfer(transfer);
        return Result.success(transferId);
    }

    /**
     * 查询调拨单详情
     */
    @GetMapping("/{transferId}")
    public Result<StockTransfer> getTransfer(@PathVariable Long transferId) {
        return Result.success(warehouseService.getTransfer(transferId));
    }

    /**
     * 分页查询调拨单
     */
    @GetMapping("/page")
    public Result<Page<StockTransfer>> listTransfers(@RequestParam(required = false) Long warehouseId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        log.info("分页查询调拨单: 仓库={}, 状态={}", warehouseId, status);
        return Result.success(warehouseService.listTransfers(warehouseId, status, page, size));
    }

    /**
     * 执行调拨（从源仓库扣减库存）
     */
    @PostMapping("/{transferId}/execute")
    public Result<Void> executeTransfer(@PathVariable Long transferId) {
        log.info("执行调拨: {}", transferId);
        warehouseService.executeTransfer(transferId);
        return Result.success();
    }

    /**
     * 确认调拨收货（入库到目标仓库）
     */
    @PostMapping("/{transferId}/receive")
    public Result<Void> confirmTransferReceive(@PathVariable Long transferId,
                                               @RequestBody List<StockTransferDetail> details) {
        List<StockTransferDetail> receiveDetails = details == null ? List.of() : details;
        log.info("确认调拨收货: 单据={}, 明细行数={}", transferId, receiveDetails.size());
        warehouseService.confirmTransferReceive(transferId, receiveDetails);
        return Result.success();
    }

    /**
     * 取消调拨
     */
    @PostMapping("/{transferId}/cancel")
    public Result<Void> cancelTransfer(@PathVariable Long transferId,
                                       @RequestParam(required = false) String reason) {
        log.info("取消调拨: 单据={}, 原因={}", transferId, reason);
        warehouseService.cancelTransfer(transferId, reason);
        return Result.success();
    }
}
