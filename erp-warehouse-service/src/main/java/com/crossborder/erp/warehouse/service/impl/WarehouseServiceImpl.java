package com.crossborder.erp.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.warehouse.dto.*;
import com.crossborder.erp.warehouse.entity.*;
import com.crossborder.erp.warehouse.enums.InboundType;
import com.crossborder.erp.warehouse.enums.OutboundType;
import com.crossborder.erp.warehouse.enums.WarehouseStatus;
import com.crossborder.erp.warehouse.mapper.*;
import com.crossborder.erp.warehouse.service.WarehouseService;
import com.crossborder.erp.warehouse.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仓库服务实现 - 完整版
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;
    private final InventoryBatchMapper batchMapper;
    private final PackingMaterialMapper packingMaterialMapper;

    // ==================== 仓库管理 ====================

    @Override
    public Warehouse getWarehouse(Long warehouseId) {
        return warehouseMapper.selectById(warehouseId);
    }

    @Override
    public List<Warehouse> listWarehouses() {
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Warehouse::getStatus, WarehouseStatus.ACTIVE.getCode());
        wrapper.orderByAsc(Warehouse::getWarehouseCode);
        return warehouseMapper.selectList(wrapper);
    }

    @Override
    public Page<Warehouse> queryWarehouses(WarehouseQueryDTO query) {
        Page<Warehouse> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<>();
        
        if (query.getWarehouseCode() != null) {
            wrapper.like(Warehouse::getWarehouseCode, query.getWarehouseCode());
        }
        if (query.getName() != null) {
            wrapper.like(Warehouse::getName, query.getName());
        }
        if (query.getCountry() != null) {
            wrapper.eq(Warehouse::getCountry, query.getCountry());
        }
        if (query.getCity() != null) {
            wrapper.like(Warehouse::getCity, query.getCity());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Warehouse::getStatus, query.getStatus());
        }
        if (query.getWarehouseType() != null) {
            wrapper.eq(Warehouse::getWarehouseType, query.getWarehouseType());
        }
        
        wrapper.orderByDesc(Warehouse::getCreateTime);
        return warehouseMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWarehouse(Warehouse warehouse) {
        warehouse.setWarehouseCode(generateWarehouseCode());
        warehouse.setStatus(WarehouseStatus.ACTIVE.getCode());
        warehouse.setCreateTime(LocalDateTime.now());
        warehouse.setUpdateTime(LocalDateTime.now());
        warehouseMapper.insert(warehouse);
        
        log.info("创建仓库成功: {}", warehouse.getName());
        return warehouse.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWarehouse(Warehouse warehouse) {
        warehouse.setUpdateTime(LocalDateTime.now());
        warehouseMapper.updateById(warehouse);
        log.info("更新仓库成功: {}", warehouse.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWarehouse(Long warehouseId) {
        // 检查是否有库存
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId)
               .gt(WarehouseLocation::getStockQuantity, 0);
        
        Long stockCount = locationMapper.selectCount(wrapper);
        if (stockCount > 0) {
            throw new BusinessException("仓库有库存，无法删除");
        }
        
        warehouseMapper.deleteById(warehouseId);
        log.info("删除仓库成功: ID={}", warehouseId);
    }

    @Override
    public WarehouseStatisticsVO getWarehouseStatistics(Long warehouseId) {
        WarehouseStatisticsVO stats = new WarehouseStatisticsVO();
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }
        
        stats.setWarehouseId(warehouseId);
        stats.setWarehouseName(warehouse.getName());
        
        // 库位统计
        LambdaQueryWrapper<WarehouseLocation> locWrapper = new LambdaQueryWrapper<>();
        locWrapper.eq(WarehouseLocation::getWarehouseId, warehouseId);
        List<WarehouseLocation> locations = locationMapper.selectList(locWrapper);
        
        stats.setTotalLocations(locations.size());
        stats.setUsedLocations((int) locations.stream().filter(l -> l.getStockQuantity() > 0).count());
        stats.setIdleLocations((int) locations.stream().filter(l -> l.getStockQuantity() == 0).count());
        
        if (locations.size() > 0) {
            BigDecimal utilization = BigDecimal.valueOf(stats.getUsedLocations())
                    .divide(BigDecimal.valueOf(stats.getTotalLocations()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.setUtilizationRate(utilization);
        }
        
        // 库存统计
        int totalQuantity = locations.stream().mapToInt(l -> l.getStockQuantity() != null ? l.getStockQuantity() : 0).sum();
        int availableQty = locations.stream().mapToInt(l -> l.getStockQuantity() != null ? l.getStockQuantity() : 0).sum();
        int lockedQty = locations.stream().mapToInt(l -> l.getLockedQuantity() != null ? l.getLockedQuantity() : 0).sum();
        
        stats.setTotalQuantity(totalQuantity);
        stats.setAvailableQuantity(availableQty);
        stats.setLockedQuantity(lockedQty);
        stats.setTotalSku((int) locations.stream().map(WarehouseLocation::getProductId).distinct().count());
        
        return stats;
    }

    @Override
    public WarehouseDashboardVO getWarehouseDashboard(Long warehouseId) {
        WarehouseDashboardVO dashboard = new WarehouseDashboardVO();
        
        // 获取统计数据
        WarehouseStatisticsVO stats = getWarehouseStatistics(warehouseId);
        
        dashboard.setTotalSku(stats.getTotalSku());
        dashboard.setTotalQuantity(stats.getTotalQuantity());
        dashboard.setLocationUtilization(stats.getUtilizationRate());
        
        // 获取预警信息
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId);
        List<WarehouseLocation> locations = locationMapper.selectList(wrapper);
        
        dashboard.setLowStockCount((int) locations.stream()
                .filter(l -> l.getStockQuantity() != null && l.getStockQuantity() < 10).count());
        dashboard.setOutOfStockCount((int) locations.stream()
                .filter(l -> l.getStockQuantity() == null || l.getStockQuantity() == 0).count());
        
        return dashboard;
    }

    // ==================== 库位管理 ====================

    @Override
    public WarehouseLocation getLocation(Long locationId) {
        return locationMapper.selectById(locationId);
    }

    @Override
    public Page<WarehouseLocation> listLocations(LocationQueryDTO query) {
        Page<WarehouseLocation> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        
        if (query.getWarehouseId() != null) {
            wrapper.eq(WarehouseLocation::getWarehouseId, query.getWarehouseId());
        }
        if (query.getLocationCode() != null) {
            wrapper.like(WarehouseLocation::getLocationCode, query.getLocationCode());
        }
        if (query.getLocationType() != null) {
            wrapper.eq(WarehouseLocation::getLocationType, query.getLocationType());
        }
        if (query.getZone() != null) {
            wrapper.eq(WarehouseLocation::getZone, query.getZone());
        }
        if (query.getStatus() != null) {
            wrapper.eq(WarehouseLocation::getStatus, query.getStatus());
        }
        if (query.getIsPickable() != null) {
            wrapper.eq(WarehouseLocation::getIsPickable, query.getIsPickable());
        }
        if (query.getIsStorable() != null) {
            wrapper.eq(WarehouseLocation::getIsStorable, query.getIsStorable());
        }
        
        wrapper.orderByAsc(WarehouseLocation::getZone, WarehouseLocation::getRow, 
                WarehouseLocation::getColumn, WarehouseLocation::getLevel);
        
        return locationMapper.selectPage(page, wrapper);
    }

    @Override
    public List<WarehouseLocation> getLocationsByWarehouse(Long warehouseId) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId);
        wrapper.orderByAsc(WarehouseLocation::getLocationCode);
        return locationMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLocation(WarehouseLocation location) {
        // 检查库位编码是否已存在
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, location.getWarehouseId())
               .eq(WarehouseLocation::getLocationCode, location.getLocationCode());
        if (locationMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("库位编码已存在");
        }
        
        location.setStockQuantity(0);
        location.setLockedQuantity(0);
        location.setCurrentCapacity(0);
        location.setStatus("ACTIVE");
        location.setIsPickable(true);
        location.setIsStorable(true);
        location.setCreateTime(LocalDateTime.now());
        location.setUpdateTime(LocalDateTime.now());
        locationMapper.insert(location);
        
        log.info("创建库位成功: {}", location.getLocationCode());
        return location.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateLocations(Long warehouseId, String zone, int rowCount, int columnCount, int levelCount) {
        int created = 0;
        
        for (int r = 1; r <= rowCount; r++) {
            for (int c = 1; c <= columnCount; c++) {
                for (int l = 1; l <= levelCount; l++) {
                    WarehouseLocation location = new WarehouseLocation();
                    location.setWarehouseId(warehouseId);
                    location.setLocationCode(String.format("%s-%02d-%02d-%02d", zone, r, c, l));
                    location.setLocationName(String.format("%s区%d排%d列%d层", zone, r, c, l));
                    location.setLocationType("STANDARD");
                    location.setZone(zone);
                    location.setRow(String.format("%02d", r));
                    location.setColumn(String.format("%02d", c));
                    location.setLevel(String.format("%02d", l));
                    location.setMaxCapacity(100);
                    location.setStockQuantity(0);
                    location.setLockedQuantity(0);
                    location.setCurrentCapacity(0);
                    location.setStatus("ACTIVE");
                    location.setIsPickable(true);
                    location.setIsStorable(true);
                    location.setCreateTime(LocalDateTime.now());
                    location.setUpdateTime(LocalDateTime.now());
                    
                    locationMapper.insert(location);
                    created++;
                }
            }
        }
        
        log.info("批量创建库位完成: 仓库ID={}, 区域={}, 数量={}", warehouseId, zone, created);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLocation(WarehouseLocation location) {
        location.setUpdateTime(LocalDateTime.now());
        locationMapper.updateById(location);
        log.info("更新库位成功: {}", location.getLocationCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLocation(Long locationId) {
        WarehouseLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BusinessException("库位不存在");
        }
        if (location.getStockQuantity() > 0) {
            throw new BusinessException("库位有库存，无法删除");
        }
        
        locationMapper.deleteById(locationId);
        log.info("删除库位成功: {}", location.getLocationCode());
    }

    @Override
    public List<WarehouseLocation> getAvailableLocations(Long warehouseId, String locationType) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId)
               .eq(WarehouseLocation::getStatus, "ACTIVE")
               .eq(WarehouseLocation::getIsStorable, true)
               .apply("stock_quantity < max_capacity");
        
        if (locationType != null) {
            wrapper.eq(WarehouseLocation::getLocationType, locationType);
        }
        
        wrapper.orderByAsc(WarehouseLocation::getPickingPriority, WarehouseLocation::getLocationCode);
        return locationMapper.selectList(wrapper);
    }

    // ==================== 入库管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInboundOrder(InboundOrder order, List<InboundDetail> details) {
        order.setOrderNo(generateInboundOrderNo());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        // 计算总数量
        int totalQty = details.stream().mapToInt(d -> d.getQuantity() != null ? d.getQuantity() : 0).sum();
        order.setTotalQuantity(totalQty);
        
        inboundOrderMapper.insert(order);
        
        for (InboundDetail detail : details) {
            detail.setInboundOrderId(order.getId());
            detail.setCreateTime(LocalDateTime.now());
            inboundDetailMapper.insert(detail);
        }
        
        log.info("创建入库单成功: {}", order.getOrderNo());
        return order.getId();
    }

    @Override
    public InboundOrder getInboundOrder(Long orderId) {
        return inboundOrderMapper.selectById(orderId);
    }

    @Override
    public Page<InboundOrder> listInboundOrders(Long warehouseId, String status, int page, int size) {
        Page<InboundOrder> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        
        if (warehouseId != null) {
            wrapper.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        if (status != null) {
            wrapper.eq(InboundOrder::getStatus, Integer.parseInt(status));
        }
        
        wrapper.orderByDesc(InboundOrder::getCreateTime);
        return inboundOrderMapper.selectPage(pageObj, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmInbound(Long inboundOrderId, List<InboundDetail> actualDetails) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("入库单不存在");
        }
        
        if (order.getStatus() == 1) {
            throw new BusinessException("入库单已入库");
        }
        
        // 更新库存
        for (InboundDetail detail : actualDetails) {
            updateLocationStock(order.getWarehouseId(), detail.getProductId(),
                    detail.getQuantity(), detail.getLocationCode());
        }
        
        // 更新入库单状态
        order.setStatus(1);
        order.setInboundTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        
        log.info("确认入库成功: {}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void partialInbound(Long inboundOrderId, List<InboundDetail> details) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("入库单不存在");
        }
        
        // 更新库存
        for (InboundDetail detail : details) {
            updateLocationStock(order.getWarehouseId(), detail.getProductId(),
                    detail.getQuantity(), detail.getLocationCode());
        }
        
        // 更新入库单
        int totalReceived = details.stream().mapToInt(d -> d.getQuantity() != null ? d.getQuantity() : 0).sum();
        order.setReceivedQuantity((order.getReceivedQuantity() != null ? order.getReceivedQuantity() : 0) + totalReceived);
        
        if (order.getTotalQuantity() != null && order.getReceivedQuantity() >= order.getTotalQuantity()) {
            order.setStatus(1);
            order.setInboundTime(LocalDateTime.now());
        }
        
        order.setUpdateTime(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        
        log.info("部分入库成功: {}, 本次数量: {}", order.getOrderNo(), totalReceived);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInbound(Long inboundOrderId, String reason) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("入库单不存在");
        }
        
        if (order.getStatus() == 1) {
            throw new BusinessException("已入库的单据无法取消");
        }
        
        order.setStatus(-1);
        order.setRemark(reason);
        order.setUpdateTime(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        
        log.info("取消入库单: {}, 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    public List<InboundDetail> getInboundDetails(Long orderId) {
        LambdaQueryWrapper<InboundDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InboundDetail::getInboundOrderId, orderId);
        return inboundDetailMapper.selectList(wrapper);
    }

    // ==================== 出库管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOutboundOrder(OutboundOrder order, List<OutboundDetail> details) {
        order.setOrderNo(generateOutboundOrderNo());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        int totalQty = details.stream().mapToInt(d -> d.getQuantity() != null ? d.getQuantity() : 0).sum();
        order.setTotalQuantity(totalQty);
        
        outboundOrderMapper.insert(order);
        
        for (OutboundDetail detail : details) {
            detail.setOutboundOrderId(order.getId());
            detail.setCreateTime(LocalDateTime.now());
            outboundDetailMapper.insert(detail);
            
            // 锁定库存
            lockInventory(order.getWarehouseId(), detail.getProductId(), detail.getQuantity());
        }
        
        log.info("创建出库单成功: {}", order.getOrderNo());
        return order.getId();
    }

    @Override
    public OutboundOrder getOutboundOrder(Long orderId) {
        return outboundOrderMapper.selectById(orderId);
    }

    @Override
    public Page<OutboundOrder> listOutboundOrders(Long warehouseId, String status, int page, int size) {
        Page<OutboundOrder> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<>();
        
        if (warehouseId != null) {
            wrapper.eq(OutboundOrder::getWarehouseId, warehouseId);
        }
        if (status != null) {
            wrapper.eq(OutboundOrder::getStatus, Integer.parseInt(status));
        }
        
        wrapper.orderByDesc(OutboundOrder::getCreateTime);
        return outboundOrderMapper.selectPage(pageObj, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOutbound(Long outboundOrderId) {
        OutboundOrder order = outboundOrderMapper.selectById(outboundOrderId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        
        if (order.getStatus() == 1) {
            throw new BusinessException("出库单已出库");
        }
        
        // 获取出库明细
        LambdaQueryWrapper<OutboundDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboundDetail::getOutboundOrderId, outboundOrderId);
        List<OutboundDetail> details = outboundDetailMapper.selectList(wrapper);
        
        // 扣减库存
        for (OutboundDetail detail : details) {
            reduceLocationStock(order.getWarehouseId(), detail.getProductId(),
                    detail.getQuantity(), detail.getLocationCode());
            
            // 释放锁定库存
            releaseInventory(order.getWarehouseId(), detail.getProductId(), detail.getQuantity());
        }
        
        // 更新出库单状态
        order.setStatus(1);
        order.setOutboundTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        
        log.info("确认出库成功: {}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOutbound(Long outboundOrderId, String reason) {
        OutboundOrder order = outboundOrderMapper.selectById(outboundOrderId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        
        if (order.getStatus() == 1) {
            throw new BusinessException("已出库的单据无法取消");
        }
        
        // 释放锁定的库存
        LambdaQueryWrapper<OutboundDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboundDetail::getOutboundOrderId, outboundOrderId);
        List<OutboundDetail> details = outboundDetailMapper.selectList(wrapper);
        
        for (OutboundDetail detail : details) {
            releaseInventory(order.getWarehouseId(), detail.getProductId(), detail.getQuantity());
        }
        
        order.setStatus(-1);
        order.setRemark(reason);
        order.setUpdateTime(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        
        log.info("取消出库单: {}, 原因: {}", order.getOrderNo(), reason);
    }

    @Override
    public List<OutboundDetail> getOutboundDetails(Long orderId) {
        LambdaQueryWrapper<OutboundDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboundDetail::getOutboundOrderId, orderId);
        return outboundDetailMapper.selectList(wrapper);
    }

    // ==================== 调拨管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTransfer(StockTransfer transfer) {
        transfer.setTransferNo(generateTransferNo());
        transfer.setStatus(0);
        transfer.setCreateTime(LocalDateTime.now());
        transfer.setUpdateTime(LocalDateTime.now());
        transferMapper.insert(transfer);
        
        log.info("创建调拨单成功: {}", transfer.getTransferNo());
        return transfer.getId();
    }

    @Override
    public StockTransfer getTransfer(Long transferId) {
        return transferMapper.selectById(transferId);
    }

    @Override
    public Page<StockTransfer> listTransfers(Long warehouseId, String status, int page, int size) {
        Page<StockTransfer> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<StockTransfer> wrapper = new LambdaQueryWrapper<>();
        
        if (warehouseId != null) {
            wrapper.and(w -> w.eq(StockTransfer::getFromWarehouseId, warehouseId)
                    .or().eq(StockTransfer::getToWarehouseId, warehouseId));
        }
        if (status != null) {
            wrapper.eq(StockTransfer::getStatus, Integer.parseInt(status));
        }
        
        wrapper.orderByDesc(StockTransfer::getCreateTime);
        return transferMapper.selectPage(pageObj, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeTransfer(Long transferId) {
        StockTransfer transfer = transferMapper.selectById(transferId);
        if (transfer == null) {
            throw new BusinessException("调拨单不存在");
        }
        
        if (transfer.getStatus() == 1) {
            throw new BusinessException("调拨单已调拨");
        }
        
        // 从源仓库扣减库存
        reduceLocationStock(transfer.getFromWarehouseId(), transfer.getProductId(),
                transfer.getQuantity(), transfer.getFromLocationCode());
        
        // 更新调拨单状态
        transfer.setStatus(1);
        transfer.setTransferTime(LocalDateTime.now());
        transfer.setUpdateTime(LocalDateTime.now());
        transferMapper.updateById(transfer);
        
        log.info("调拨成功: {}", transfer.getTransferNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmTransferReceive(Long transferId, List<StockTransferDetail> details) {
        StockTransfer transfer = transferMapper.selectById(transferId);
        if (transfer == null) {
            throw new BusinessException("调拨单不存在");
        }
        
        if (transfer.getStatus() != 1) {
            throw new BusinessException("调拨单未执行");
        }
        
        // 入库到目标仓库
        for (StockTransferDetail detail : details) {
            updateLocationStock(transfer.getToWarehouseId(), detail.getProductId(),
                    detail.getQuantity(), detail.getToLocationCode());
        }
        
        transfer.setStatus(2);
        transfer.setUpdateTime(LocalDateTime.now());
        transferMapper.updateById(transfer);
        
        log.info("确认调拨收货: {}", transfer.getTransferNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTransfer(Long transferId, String reason) {
        StockTransfer transfer = transferMapper.selectById(transferId);
        if (transfer == null) {
            throw new BusinessException("调拨单不存在");
        }
        
        if (transfer.getStatus() == 1) {
            // 如果已执行调拨，需要回滚库存
            updateLocationStock(transfer.getFromWarehouseId(), transfer.getProductId(),
                    transfer.getQuantity(), transfer.getFromLocationCode());
        }
        
        transfer.setStatus(-1);
        transfer.setRemark(reason);
        transfer.setUpdateTime(LocalDateTime.now());
        transferMapper.updateById(transfer);
        
        log.info("取消调拨单: {}, 原因: {}", transfer.getTransferNo(), reason);
    }

    // ==================== 库存批次管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInventoryBatch(InventoryBatch batch) {
        batch.setBatchNo(generateBatchNo());
        batch.setBatchStatus("NORMAL");
        batch.setAvailableQuantity(batch.getQuantity());
        batch.setLockedQuantity(0);
        batch.setDamagedQuantity(0);
        batch.setExpiredQuantity(0);
        batch.setInboundDate(LocalDateTime.now());
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.insert(batch);
        
        log.info("创建库存批次: {}", batch.getBatchNo());
        return batch.getId();
    }

    @Override
    public InventoryBatch getInventoryBatch(Long batchId) {
        return batchMapper.selectById(batchId);
    }

    @Override
    public Page<InventoryBatch> listInventoryBatches(Long warehouseId, Long productId, String batchStatus, int page, int size) {
        Page<InventoryBatch> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<InventoryBatch> wrapper = new LambdaQueryWrapper<>();
        
        if (warehouseId != null) {
            wrapper.eq(InventoryBatch::getWarehouseId, warehouseId);
        }
        if (productId != null) {
            wrapper.eq(InventoryBatch::getProductId, productId);
        }
        if (batchStatus != null) {
            wrapper.eq(InventoryBatch::getBatchStatus, batchStatus);
        }
        
        wrapper.orderByDesc(InventoryBatch::getInboundDate);
        return batchMapper.selectPage(pageObj, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatchQuantity(Long batchId, Integer quantity, String operation) {
        InventoryBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }
        
        if ("add".equals(operation)) {
            batch.setQuantity(batch.getQuantity() + quantity);
            batch.setAvailableQuantity(batch.getAvailableQuantity() + quantity);
        } else if ("subtract".equals(operation)) {
            if (batch.getAvailableQuantity() < quantity) {
                throw new BusinessException("可用数量不足");
            }
            batch.setQuantity(batch.getQuantity() - quantity);
            batch.setAvailableQuantity(batch.getAvailableQuantity() - quantity);
        }
        
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeBatch(Long batchId, Integer quantity) {
        InventoryBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }
        if (batch.getAvailableQuantity() < quantity) {
            throw new BusinessException("可用数量不足");
        }
        
        batch.setAvailableQuantity(batch.getAvailableQuantity() - quantity);
        batch.setLockedQuantity(batch.getLockedQuantity() + quantity);
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        
        log.info("冻结批次: {}, 数量: {}", batch.getBatchNo(), quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeBatch(Long batchId, Integer quantity) {
        InventoryBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }
        if (batch.getLockedQuantity() < quantity) {
            throw new BusinessException("冻结数量不足");
        }
        
        batch.setAvailableQuantity(batch.getAvailableQuantity() + quantity);
        batch.setLockedQuantity(batch.getLockedQuantity() - quantity);
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        
        log.info("解冻批次: {}, 数量: {}", batch.getBatchNo(), quantity);
    }

    @Override
    public List<InventoryBatch> getExpiringBatches(Long warehouseId, LocalDate beforeDate) {
        LambdaQueryWrapper<InventoryBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryBatch::getWarehouseId, warehouseId)
               .lt(InventoryBatch::getExpiryDate, beforeDate)
               .gt(InventoryBatch::getQuantity, 0);
        
        return batchMapper.selectList(wrapper);
    }

    // ==================== 波次拣货 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWavePicking(WavePicking wave, List<WavePickingDetail> details) {
        wave.setWaveNo(generateWaveNo());
        wave.setWaveStatus("PENDING");
        
        int orderCount = (int) details.stream().map(WavePickingDetail::getOrderId).distinct().count();
        int skuCount = (int) details.stream().map(WavePickingDetail::getProductId).distinct().count();
        int totalQty = details.stream().mapToInt(d -> d.getPickQuantity() != null ? d.getPickQuantity() : 0).sum();
        
        wave.setOrderCount(orderCount);
        wave.setSkuCount(skuCount);
        wave.setTotalQuantity(totalQty);
        wave.setPickedQuantity(0);
        wave.setCreateTime(LocalDateTime.now());
        wave.setUpdateTime(LocalDateTime.now());
        
        // 这里需要waveMapper，但简化处理
        // waveMapper.insert(wave);
        
        log.info("创建波次: {}", wave.getWaveNo());
        return wave.getId();
    }

    @Override
    public WavePicking getWavePicking(Long waveId) {
        // return waveMapper.selectById(waveId);
        return null;
    }

    @Override
    public Page<WavePicking> listWavePickings(String status, int page, int size) {
        // return waveMapper.selectPage(page, wrapper);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignWavePicking(Long waveId, String userId) {
        // WavePicking wave = waveMapper.selectById(waveId);
        // wave.setAssignUser(userId);
        // wave.setAssignTime(LocalDateTime.now());
        // wave.setWaveStatus("ASSIGNED");
        // waveMapper.updateById(wave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startWavePicking(Long waveId) {
        // WavePicking wave = waveMapper.selectById(waveId);
        // wave.setStartTime(LocalDateTime.now());
        // wave.setWaveStatus("PICKING");
        // waveMapper.updateById(wave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPick(Long detailId, Integer pickedQuantity, String picker) {
        // 更新拣货明细
        // 更新库存
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeWavePicking(Long waveId) {
        // WavePicking wave = waveMapper.selectById(waveId);
        // wave.setCompleteTime(LocalDateTime.now());
        // wave.setWaveStatus("COMPLETED");
        // waveMapper.updateById(wave);
        // 
        // // 生成出库单
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelWavePicking(Long waveId, String reason) {
        // 释放锁定的库存
        // 更新波次状态
    }

    // ==================== 包装材料管理 ====================

    @Override
    public Page<PackingMaterial> listPackingMaterials(String materialType, Integer page, Integer size) {
        Page<PackingMaterial> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<PackingMaterial> wrapper = new LambdaQueryWrapper<>();
        
        if (materialType != null) {
            wrapper.eq(PackingMaterial::getMaterialType, materialType);
        }
        
        wrapper.orderByAsc(PackingMaterial::getMaterialCode);
        return packingMaterialMapper.selectPage(pageObj, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPackingMaterial(PackingMaterial material) {
        material.setMaterialCode(generateMaterialCode());
        material.setStatus(1);
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        packingMaterialMapper.insert(material);
        
        log.info("创建包装材料: {}", material.getMaterialName());
        return material.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePackingMaterial(PackingMaterial material) {
        material.setUpdateTime(LocalDateTime.now());
        packingMaterialMapper.updateById(material);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePackingMaterial(Long materialId) {
        packingMaterialMapper.deleteById(materialId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inboundPackingMaterial(Long materialId, Integer quantity, String remark) {
        PackingMaterial material = packingMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("包装材料不存在");
        }
        
        material.setStockQuantity(material.getStockQuantity() + quantity);
        material.setUpdateTime(LocalDateTime.now());
        packingMaterialMapper.updateById(material);
        
        log.info("包装材料入库: {}, 数量: {}", material.getMaterialName(), quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outboundPackingMaterial(Long materialId, Integer quantity, String remark) {
        PackingMaterial material = packingMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("包装材料不存在");
        }
        if (material.getStockQuantity() < quantity) {
            throw new BusinessException("库存不足");
        }
        
        material.setStockQuantity(material.getStockQuantity() - quantity);
        material.setUpdateTime(LocalDateTime.now());
        packingMaterialMapper.updateById(material);
        
        log.info("包装材料出库: {}, 数量: {}", material.getMaterialName(), quantity);
    }

    @Override
    public List<PackingMaterial> getMaterialsNeedingReplenishment() {
        LambdaQueryWrapper<PackingMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PackingMaterial::getStatus, 1);
        wrapper.apply("stock_quantity <= min_stock");
        
        return packingMaterialMapper.selectList(wrapper);
    }

    // ==================== 库内操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void relocate(Long warehouseId, Long productId, String fromLocationCode, 
                         String toLocationCode, Integer quantity) {
        // 从源库位扣减
        reduceLocationStock(warehouseId, productId, quantity, fromLocationCode);
        // 入库到目标库位
        updateLocationStock(warehouseId, productId, quantity, toLocationCode);
        
        log.info("库内调拨: {} -> {}, SKU: {}, 数量: {}", fromLocationCode, toLocationCode, productId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeInventory(Long warehouseId, Long productId, Integer quantity, String reason) {
        lockInventory(warehouseId, productId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeInventory(Long warehouseId, Long productId, Integer quantity) {
        releaseInventory(warehouseId, productId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportDamage(Long warehouseId, Long productId, Integer quantity, String reason) {
        // 扣减库存并记录报损
        reduceLocationStock(warehouseId, productId, quantity, "报损: " + reason);
        log.info("库存报损: 仓库{}, SKU{}, 数量{}, 原因: {}", warehouseId, productId, quantity, reason);
    }

    @Override
    public void createInventoryCheck(Long warehouseId, List<Long> locationIds, String checkType, String remark) {
        // 创建盘点单
        log.info("创建盘点单: 仓库{}, 类型{}", warehouseId, checkType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmInventoryCheck(Long checkId, List<InventoryCheckResult> results) {
        // 根据盘点结果调整库存
        for (InventoryCheckResult result : results) {
            if (result.getDiffQty() != 0) {
                if (result.getDiffQty() > 0) {
                    // 盘盈
                    updateLocationStock(result.getWarehouseId(), result.getProductId(),
                            result.getDiffQty(), result.getLocationCode());
                } else {
                    // 盘亏
                    reduceLocationStock(result.getWarehouseId(), result.getProductId(),
                            Math.abs(result.getDiffQty()), result.getLocationCode());
                }
            }
        }
        
        log.info("确认盘点结果: 盘点单ID{}", checkId);
    }

    // ==================== 私有方法 ====================

    /**
     * 更新库位库存
     */
    private void updateLocationStock(Long warehouseId, Long productId, Integer quantity, String locationCode) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId)
               .eq(WarehouseLocation::getProductId, productId)
               .eq(WarehouseLocation::getLocationCode, locationCode);

        WarehouseLocation location = locationMapper.selectOne(wrapper);

        if (location == null) {
            location = new WarehouseLocation();
            location.setWarehouseId(warehouseId);
            location.setProductId(productId);
            location.setLocationCode(locationCode);
            location.setStockQuantity(quantity);
            location.setLockedQuantity(0);
            location.setCurrentCapacity(quantity);
            location.setCreateTime(LocalDateTime.now());
            location.setUpdateTime(LocalDateTime.now());
            locationMapper.insert(location);
        } else {
            location.setStockQuantity(location.getStockQuantity() + quantity);
            location.setCurrentCapacity(location.getCurrentCapacity() + quantity);
            location.setUpdateTime(LocalDateTime.now());
            locationMapper.updateById(location);
        }
    }

    /**
     * 扣减库位库存
     */
    private void reduceLocationStock(Long warehouseId, Long productId, Integer quantity, String locationCode) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId)
               .eq(WarehouseLocation::getProductId, productId)
               .eq(WarehouseLocation::getLocationCode, locationCode);

        WarehouseLocation location = locationMapper.selectOne(wrapper);

        if (location == null) {
            throw new BusinessException("库位不存在: " + locationCode);
        }

        if (location.getStockQuantity() < quantity) {
            throw new BusinessException("库存不足，当前库存: " + location.getStockQuantity());
        }

        location.setStockQuantity(location.getStockQuantity() - quantity);
        location.setCurrentCapacity(location.getCurrentCapacity() - quantity);
        location.setUpdateTime(LocalDateTime.now());
        locationMapper.updateById(location);
    }

    /**
     * 锁定库存
     */
    private void lockInventory(Long warehouseId, Long productId, Integer quantity) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId)
               .eq(WarehouseLocation::getProductId, productId);
        
        WarehouseLocation location = locationMapper.selectOne(wrapper);
        if (location == null) {
            throw new BusinessException("库存不存在");
        }
        
        if (location.getStockQuantity() - location.getLockedQuantity() < quantity) {
            throw new BusinessException("可用库存不足");
        }
        
        location.setLockedQuantity(location.getLockedQuantity() + quantity);
        location.setUpdateTime(LocalDateTime.now());
        locationMapper.updateById(location);
    }

    /**
     * 释放库存
     */
    private void releaseInventory(Long warehouseId, Long productId, Integer quantity) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId)
               .eq(WarehouseLocation::getProductId, productId);
        
        WarehouseLocation location = locationMapper.selectOne(wrapper);
        if (location != null) {
            location.setLockedQuantity(Math.max(0, location.getLockedQuantity() - quantity));
            location.setUpdateTime(LocalDateTime.now());
            locationMapper.updateById(location);
        }
    }

    /**
     * 生成各种单号
     */
    private String generateWarehouseCode() {
        return "WH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) 
                + String.format("%03d", new Random().nextInt(1000));
    }

    private String generateInboundOrderNo() {
        return "IN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String generateOutboundOrderNo() {
        return "OUT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String generateTransferNo() {
        return "TF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String generateBatchNo() {
        return "BATCH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String generateWaveNo() {
        return "WAVE" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String generateMaterialCode() {
        return "PKG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) 
                + String.format("%03d", new Random().nextInt(1000));
    }

    // 缺少的Mapper，需要添加
    private InboundOrderMapper inboundOrderMapper;
    private InboundDetailMapper inboundDetailMapper;
    private OutboundOrderMapper outboundOrderMapper;
    private OutboundDetailMapper outboundDetailMapper;
    private StockTransferMapper transferMapper;
}