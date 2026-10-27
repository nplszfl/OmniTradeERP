package com.crossborder.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.inventory.entity.Inventory;
import com.crossborder.erp.inventory.entity.InventoryAlert;
import com.crossborder.erp.inventory.entity.InventoryCheck;
import com.crossborder.erp.inventory.entity.InventoryCheckDetail;
import com.crossborder.erp.inventory.entity.InventoryLog;
import com.crossborder.erp.inventory.enums.AlertStatus;
import com.crossborder.erp.inventory.enums.LockType;
import com.crossborder.erp.inventory.vo.BatchOperationResult;
import com.crossborder.erp.inventory.vo.InventoryAlertStats;
import com.crossborder.erp.inventory.vo.InventoryBatchOperation;
import com.crossborder.erp.inventory.vo.InventoryQuery;
import com.crossborder.erp.inventory.vo.InventoryStatistics;
import com.crossborder.erp.inventory.vo.InventoryTrend;
import com.crossborder.erp.inventory.vo.SafetyStockUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存服务接口
 *
 * <p>负责：出入库、库存调拨、库存锁定与释放、库存预警、库存盘点、库存统计与趋势。</p>
 */
public interface InventoryService {

    // ==================== 基础库存操作 ====================

    /**
     * 按商品 + 仓库查询库存
     */
    Inventory getInventory(Long productId, Long warehouseId);

    /**
     * 按 ID 查询库存
     */
    Inventory getInventoryById(Long id);

    /**
     * 按条件分页查询库存（只返回当页数据）
     */
    List<Inventory> listInventories(InventoryQuery query);

    /**
     * 按条件分页查询库存（返回完整分页信息，含 total）
     *
     * <p>{@link #listInventories(InventoryQuery)} 会把分页对象丢成 records 列表，
     * 前端分页组件拿不到总条数，这里保留带 total 的版本。</p>
     */
    Page<Inventory> pageInventories(InventoryQuery query);

    /**
     * 入库：库存增加并写入流水
     */
    void increaseInventory(Long productId, Long warehouseId, Integer quantity, String remark);

    /**
     * 出库：可用库存不足时拒绝
     */
    void decreaseInventory(Long productId, Long warehouseId, Integer quantity, String remark);

    /**
     * 调拨：源仓库出库 + 目标仓库入库
     */
    void transferInventory(Long productId, Long fromWarehouseId, Long toWarehouseId,
                           Integer quantity, String remark);

    // ==================== 库存锁定与解锁 ====================

    /**
     * 锁定库存
     */
    void lockInventory(Long productId, Long warehouseId, Integer quantity,
                       LockType lockType, String orderNo, String remark);

    /**
     * 释放库存
     */
    void releaseInventory(Long productId, Long warehouseId, Integer quantity, String remark);

    /**
     * 预留库存（订单占用）
     */
    void reserveInventory(Long productId, Long warehouseId, Integer quantity, String remark);

    // ==================== 库存预警 ====================

    /**
     * 查询预警列表
     */
    List<InventoryAlert> getAlerts(Long warehouseId, AlertStatus status);

    /**
     * 处理预警
     */
    void processAlert(Long alertId, String handler, String handleRemark);

    /**
     * 预警统计
     */
    InventoryAlertStats getAlertStats(Long warehouseId);

    // ==================== 库存盘点 ====================

    /**
     * 创建盘点单
     */
    Long createCheck(InventoryCheck check);

    /**
     * 执行盘点，按实盘数量调整库存并结算盈亏
     */
    void executeCheck(Long checkId, List<InventoryCheckDetail> details);

    // ==================== 库存查询 ====================

    /**
     * 按仓库查询库存
     */
    List<Inventory> getInventoryByWarehouse(Long warehouseId);

    /**
     * 低库存查询
     */
    List<Inventory> getLowStockInventory(Integer threshold);

    /**
     * 滞销库存查询
     */
    List<Inventory> getSlowMovingInventory(Integer days, Integer minSales);

    /**
     * 库存流水分页查询
     */
    List<InventoryLog> getInventoryLogs(Long inventoryId, int page, int size);

    /**
     * 按商品 + 时间区间查询库存流水
     */
    List<InventoryLog> getInventoryLogsByProduct(Long productId, LocalDateTime startTime,
                                                 LocalDateTime endTime);

    // ==================== 库存统计 ====================

    /**
     * 库存统计
     */
    InventoryStatistics getStatistics(Long warehouseId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 库存趋势
     */
    List<InventoryTrend> getInventoryTrend(Long productId, Long warehouseId,
                                            LocalDate startDate, LocalDate endDate);

    // ==================== 批量操作 ====================

    /**
     * 批量入库
     */
    BatchOperationResult batchIncreaseInventory(List<InventoryBatchOperation> operations);

    /**
     * 批量出库
     */
    BatchOperationResult batchDecreaseInventory(List<InventoryBatchOperation> operations);

    /**
     * 批量更新安全库存
     */
    void batchUpdateSafetyStock(List<SafetyStockUpdate> updates);
}
