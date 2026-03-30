package com.crossborder.erp.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.warehouse.dto.*;
import com.crossborder.erp.warehouse.entity.*;
import com.crossborder.erp.warehouse.vo.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 仓库服务接口
 */
public interface WarehouseService {

    // ==================== 仓库管理 ====================
    
    /**
     * 获取仓库详情
     */
    Warehouse getWarehouse(Long warehouseId);

    /**
     * 获取仓库列表
     */
    List<Warehouse> listWarehouses();

    /**
     * 分页查询仓库
     */
    Page<Warehouse> queryWarehouses(WarehouseQueryDTO query);

    /**
     * 创建仓库
     */
    Long createWarehouse(Warehouse warehouse);

    /**
     * 更新仓库
     */
    void updateWarehouse(Warehouse warehouse);

    /**
     * 删除仓库
     */
    void deleteWarehouse(Long warehouseId);

    /**
     * 获取仓库统计
     */
    WarehouseStatisticsVO getWarehouseStatistics(Long warehouseId);

    /**
     * 获取仓库仪表盘
     */
    WarehouseDashboardVO getWarehouseDashboard(Long warehouseId);

    // ==================== 库位管理 ====================

    /**
     * 获取库位详情
     */
    WarehouseLocation getLocation(Long locationId);

    /**
     * 获取仓库库位列表
     */
    Page<WarehouseLocation> listLocations(LocationQueryDTO query);

    /**
     * 获取仓库所有库位
     */
    List<WarehouseLocation> getLocationsByWarehouse(Long warehouseId);

    /**
     * 创建库位
     */
    Long createLocation(WarehouseLocation location);

    /**
     * 批量创建库位
     */
    void batchCreateLocations(Long warehouseId, String zone, int rowCount, int columnCount, int levelCount);

    /**
     * 更新库位
     */
    void updateLocation(WarehouseLocation location);

    /**
     * 删除库位
     */
    void deleteLocation(Long locationId);

    /**
     * 获取可用库位
     */
    List<WarehouseLocation> getAvailableLocations(Long warehouseId, String locationType);

    // ==================== 入库管理 ====================

    /**
     * 创建入库单
     */
    Long createInboundOrder(InboundOrder order, List<InboundDetail> details);

    /**
     * 获取入库单
     */
    InboundOrder getInboundOrder(Long orderId);

    /**
     * 获取入库单列表
     */
    Page<InboundOrder> listInboundOrders(Long warehouseId, String status, int page, int size);

    /**
     * 确认入库（实际入库）
     */
    void confirmInbound(Long inboundOrderId, List<InboundDetail> actualDetails);

    /**
     * 部分入库
     */
    void partialInbound(Long inboundOrderId, List<InboundDetail> details);

    /**
     * 取消入库单
     */
    void cancelInbound(Long inboundOrderId, String reason);

    /**
     * 获取入库单明细
     */
    List<InboundDetail> getInboundDetails(Long orderId);

    // ==================== 出库管理 ====================

    /**
     * 创建出库单
     */
    Long createOutboundOrder(OutboundOrder order, List<OutboundDetail> details);

    /**
     * 获取出库单
     */
    OutboundOrder getOutboundOrder(Long orderId);

    /**
     * 获取出库单列表
     */
    Page<OutboundOrder> listOutboundOrders(Long warehouseId, String status, int page, int size);

    /**
     * 确认出库
     */
    void confirmOutbound(Long outboundOrderId);

    /**
     * 取消出库单
     */
    void cancelOutbound(Long outboundOrderId, String reason);

    /**
     * 获取出库单明细
     */
    List<OutboundDetail> getOutboundDetails(Long orderId);

    // ==================== 调拨管理 ====================

    /**
     * 创建调拨单
     */
    Long createTransfer(StockTransfer transfer);

    /**
     * 获取调拨单
     */
    StockTransfer getTransfer(Long transferId);

    /**
     * 获取调拨单列表
     */
    Page<StockTransfer> listTransfers(Long warehouseId, String status, int page, int size);

    /**
     * 执行调拨
     */
    void executeTransfer(Long transferId);

    /**
     * 确认调拨收货
     */
    void confirmTransferReceive(Long transferId, List<StockTransferDetail> details);

    /**
     * 取消调拨
     */
    void cancelTransfer(Long transferId, String reason);

    // ==================== 库存批次管理 ====================

    /**
     * 创建库存批次
     */
    Long createInventoryBatch(InventoryBatch batch);

    /**
     * 获取库存批次
     */
    InventoryBatch getInventoryBatch(Long batchId);

    /**
     * 获取库存批次列表
     */
    Page<InventoryBatch> listInventoryBatches(Long warehouseId, Long productId, String batchStatus, int page, int size);

    /**
     * 更新批次库存
     */
    void updateBatchQuantity(Long batchId, Integer quantity, String operation);

    /**
     * 冻结批次
     */
    void freezeBatch(Long batchId, Integer quantity);

    /**
     * 解冻批次
     */
    void unfreezeBatch(Long batchId, Integer quantity);

    /**
     * 获取即将过期批次
     */
    List<InventoryBatch> getExpiringBatches(Long warehouseId, LocalDate beforeDate);

    // ==================== 波次拣货 ====================

    /**
     * 创建波次
     */
    Long createWavePicking(WavePicking wave, List<WavePickingDetail> details);

    /**
     * 获取波次
     */
    WavePicking getWavePicking(Long waveId);

    /**
     * 获取波次列表
     */
    Page<WavePicking> listWavePickings(String status, int page, int size);

    /**
     * 分配波次
     */
    void assignWavePicking(Long waveId, String userId);

    /**
     * 开始波次拣货
     */
    void startWavePicking(Long waveId);

    /**
     * 确认拣货
     */
    void confirmPick(Long detailId, Integer pickedQuantity, String picker);

    /**
     * 完成波次
     */
    void completeWavePicking(Long waveId);

    /**
     * 取消波次
     */
    void cancelWavePicking(Long waveId, String reason);

    // ==================== 包装材料管理 ====================

    /**
     * 获取包装材料列表
     */
    Page<PackingMaterial> listPackingMaterials(String materialType, Integer page, Integer size);

    /**
     * 创建包装材料
     */
    Long createPackingMaterial(PackingMaterial material);

    /**
     * 更新包装材料
     */
    void updatePackingMaterial(PackingMaterial material);

    /**
     * 删除包装材料
     */
    void deletePackingMaterial(Long materialId);

    /**
     * 包装材料入库
     */
    void inboundPackingMaterial(Long materialId, Integer quantity, String remark);

    /**
     * 包装材料出库
     */
    void outboundPackingMaterial(Long materialId, Integer quantity, String remark);

    /**
     * 获取需要补货的包装材料
     */
    List<PackingMaterial> getMaterialsNeedingReplenishment();

    // ==================== 库内操作 ====================

    /**
     * 库内调拨（同一仓库不同库位）
     */
    void relocate(Long warehouseId, Long productId, String fromLocationCode, String toLocationCode, Integer quantity);

    /**
     * 库存冻结
     */
    void freezeInventory(Long warehouseId, Long productId, Integer quantity, String reason);

    /**
     * 库存解冻
     */
    void unfreezeInventory(Long warehouseId, Long productId, Integer quantity);

    /**
     * 库存报损
     */
    void reportDamage(Long warehouseId, Long productId, Integer quantity, String reason);

    /**
     * 库存盘 点
     */
    void createInventoryCheck(Long warehouseId, List<Long> locationIds, String checkType, String remark);

    /**
     * 确认盘点结果
     */
    void confirmInventoryCheck(Long checkId, List<InventoryCheckResult> results);
}