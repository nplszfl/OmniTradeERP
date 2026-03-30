package com.crossborder.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.inventory.entity.*;
import com.crossborder.erp.inventory.enums.*;
import com.crossborder.erp.inventory.mapper.*;
import com.crossborder.erp.inventory.service.InventoryService;
import com.crossborder.erp.inventory.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存服务实现 - 完整版
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final InventoryAlertMapper alertMapper;
    private final InventoryLockMapper lockMapper;
    private final InventoryCheckMapper checkMapper;

    // ==================== 基础库存操作 ====================

    @Override
    public Inventory getInventory(Long productId, Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getProductId, productId)
               .eq(Inventory::getWarehouseId, warehouseId);
        return inventoryMapper.selectOne(wrapper);
    }

    @Override
    public Inventory getInventoryById(Long id) {
        return inventoryMapper.selectById(id);
    }

    @Override
    public List<Inventory> listInventories(InventoryQuery query) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        
        if (query.getWarehouseId() != null) {
            wrapper.eq(Inventory::getWarehouseId, query.getWarehouseId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(Inventory::getProductId, query.getProductId());
        }
        if (query.getSkuCode() != null) {
            wrapper.like(Inventory::getSkuCode, query.getSkuCode());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Inventory::getStatus, query.getStatus());
        }
        
        wrapper.orderByDesc(Inventory::getUpdateTime);
        
        Page<Inventory> page = new Page<>(query.getPage(), query.getSize());
        return inventoryMapper.selectPage(page, wrapper).getRecords();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseInventory(Long productId, Long warehouseId, Integer quantity, String remark) {
        if (quantity <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }

        Inventory inventory = getInventory(productId, warehouseId);
        Integer beforeQty = inventory != null ? inventory.getQuantity() : 0;
        
        if (inventory == null) {
            inventory = new Inventory();
            inventory.setProductId(productId);
            inventory.setWarehouseId(warehouseId);
            inventory.setSkuCode("SKU-" + productId);
            inventory.setQuantity(quantity);
            inventory.setAvailableQuantity(quantity);
            inventory.setLockedQuantity(0);
            inventory.setSafetyStock(10);
            inventory.setStatus(InventoryStatus.NORMAL);
            inventory.setCreateTime(LocalDateTime.now());
            inventory.setUpdateTime(LocalDateTime.now());
            inventoryMapper.insert(inventory);
        } else {
            inventory.setQuantity(inventory.getQuantity() + quantity);
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
            inventory.setUpdateTime(LocalDateTime.now());
            inventoryMapper.updateById(inventory);
        }

        // 记录库存流水
        recordInventoryLog(inventory.getId(), productId, warehouseId, quantity,
                OperationType.INBOUND, beforeQty, inventory.getQuantity(), remark);

        // 检查是否需要生成预警
        checkAndCreateAlert(inventory);

        log.info("入库成功: 商品ID={}, 仓库ID={}, 数量={}", productId, warehouseId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decreaseInventory(Long productId, Long warehouseId, Integer quantity, String remark) {
        if (quantity <= 0) {
            throw new BusinessException("出库数量必须大于0");
        }

        Inventory inventory = getInventory(productId, warehouseId);
        if (inventory == null) {
            throw new BusinessException("库存不存在");
        }

        // 检查可用库存是否足够（排除锁定的）
        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessException("可用库存不足，当前可用: " + inventory.getAvailableQuantity());
        }

        Integer beforeQty = inventory.getQuantity();
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inventory);

        // 记录库存流水
        recordInventoryLog(inventory.getId(), productId, warehouseId, quantity,
                OperationType.OUTBOUND, beforeQty, inventory.getQuantity(), remark);

        // 检查是否需要生成预警
        checkAndCreateAlert(inventory);

        log.info("出库成功: 商品ID={}, 仓库ID={}, 数量={}", productId, warehouseId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferInventory(Long productId, Long fromWarehouseId, Long toWarehouseId,
                                 Integer quantity, String remark) {
        // 锁定库存，防止重复操作
        Inventory fromInventory = getInventory(productId, fromWarehouseId);
        if (fromInventory == null) {
            throw new BusinessException("源仓库库存不存在");
        }
        if (fromInventory.getAvailableQuantity() < quantity) {
            throw new BusinessException("源仓库可用库存不足");
        }

        // 预留库存
        reserveInventory(productId, fromWarehouseId, quantity, "调拨预留");

        try {
            // 从源仓库出库
            decreaseInventory(productId, fromWarehouseId, quantity, "调拨出库: " + remark);
            // 入库到目标仓库
            increaseInventory(productId, toWarehouseId, quantity, "调拨入库: " + remark);

            log.info("调拨成功: 商品ID={}, 从仓库{}调拨{}个到仓库{}", productId, fromWarehouseId, quantity, toWarehouseId);
        } catch (Exception e) {
            // 回滚预留
            releaseInventory(productId, fromWarehouseId, quantity, "调拨失败释放");
            throw e;
        }
    }

    // ==================== 库存锁定与解锁 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockInventory(Long productId, Long warehouseId, Integer quantity, 
                             LockType lockType, String orderNo, String remark) {
        Inventory inventory = getInventory(productId, warehouseId);
        if (inventory == null) {
            throw new BusinessException("库存不存在");
        }
        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessException("可用库存不足，无法锁定");
        }

        // 创建锁定记录
        InventoryLock lock = new InventoryLock();
        lock.setInventoryId(inventory.getId());
        lock.setProductId(productId);
        lock.setWarehouseId(warehouseId);
        lock.setLockQuantity(quantity);
        lock.setLockType(lockType);
        lock.setOrderNo(orderNo);
        lock.setRemark(remark);
        lock.setLockStatus(LockStatus.LOCKED);
        lock.setCreateTime(LocalDateTime.now());
        lockMapper.insert(lock);

        // 更新库存可用数量
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setLockedQuantity(inventory.getLockedQuantity() + quantity);
        inventory.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inventory);

        log.info("库存锁定成功: 商品ID={}, 仓库ID={}, 数量={}, 类型={}", 
                productId, warehouseId, quantity, lockType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseInventory(Long productId, Long warehouseId, Integer quantity, String remark) {
        Inventory inventory = getInventory(productId, warehouseId);
        if (inventory == null) {
            throw new BusinessException("库存不存在");
        }

        // 查询有效的锁定记录
        LambdaQueryWrapper<InventoryLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryLock::getProductId, productId)
               .eq(InventoryLock::getWarehouseId, warehouseId)
               .eq(InventoryLock::getLockStatus, LockStatus.LOCKED)
               .orderByAsc(InventoryLock::getCreateTime);
        
        List<InventoryLock> locks = lockMapper.selectList(wrapper);
        int remaining = quantity;
        
        for (InventoryLock lock : locks) {
            if (remaining <= 0) break;
            
            int unlockQty = Math.min(lock.getLockQuantity() - lock.getUsedQuantity(), remaining);
            lock.setUsedQuantity(lock.getUsedQuantity() + unlockQty);
            if (lock.getUsedQuantity() >= lock.getLockQuantity()) {
                lock.setLockStatus(LockStatus.RELEASED);
            }
            lock.setUpdateTime(LocalDateTime.now());
            lockMapper.updateById(lock);
            
            remaining -= unlockQty;
        }

        // 更新库存
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventory.setLockedQuantity(inventory.getLockedQuantity() - quantity);
        inventory.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inventory);

        log.info("库存释放成功: 商品ID={}, 仓库ID={}, 数量={}", productId, warehouseId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveInventory(Long productId, Long warehouseId, Integer quantity, String remark) {
        lockInventory(productId, warehouseId, quantity, LockType.RESERVATION, "", remark);
    }

    // ==================== 库存预警 ====================

    @Override
    public List<InventoryAlert> getAlerts(Long warehouseId, AlertStatus status) {
        LambdaQueryWrapper<InventoryAlert> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            wrapper.eq(InventoryAlert::getWarehouseId, warehouseId);
        }
        if (status != null) {
            wrapper.eq(InventoryAlert::getAlertStatus, status);
        }
        wrapper.orderByDesc(InventoryAlert::getCreateTime);
        return alertMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAlert(Long alertId, String handler, String handleRemark) {
        InventoryAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException("预警不存在");
        }
        
        alert.setAlertStatus(AlertStatus.PROCESSED);
        alert.setHandler(handler);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleRemark(handleRemark);
        alert.setUpdateTime(LocalDateTime.now());
        alertMapper.updateById(alert);
        
        log.info("预警处理完成: ID={}, 处理人={}", alertId, handler);
    }

    @Override
    public InventoryAlertStats getAlertStats(Long warehouseId) {
        InventoryAlertStats stats = new InventoryAlertStats();
        
        LambdaQueryWrapper<InventoryAlert> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            wrapper.eq(InventoryAlert::getWarehouseId, warehouseId);
        }
        
        // 待处理预警
        LambdaQueryWrapper<InventoryAlert> pendingWrapper = new LambdaQueryWrapper<>(wrapper);
        pendingWrapper.eq(InventoryAlert::getAlertStatus, AlertStatus.PENDING);
        stats.setPendingCount(alertMapper.selectCount(pendingWrapper));
        
        // 已处理预警
        LambdaQueryWrapper<InventoryAlert> processedWrapper = new LambdaQueryWrapper<>(wrapper);
        processedWrapper.eq(InventoryAlert::getAlertStatus, AlertStatus.PROCESSED);
        stats.setProcessedCount(alertMapper.selectCount(processedWrapper));
        
        // 低库存预警
        LambdaQueryWrapper<InventoryAlert> lowWrapper = new LambdaQueryWrapper<>(wrapper);
        lowWrapper.eq(InventoryAlert::getAlertType, AlertType.LOW_STOCK);
        stats.setLowStockCount(alertMapper.selectCount(lowWrapper));
        
        // 缺货预警
        LambdaQueryWrapper<InventoryAlert> outWrapper = new LambdaQueryWrapper<>(wrapper);
        outWrapper.eq(InventoryAlert::getAlertType, AlertType.OUT_OF_STOCK);
        stats.setOutOfStockCount(alertMapper.selectCount(outWrapper));
        
        // 滞销预警
        LambdaQueryWrapper<InventoryAlert> slowWrapper = new LambdaQueryWrapper<>(wrapper);
        slowWrapper.eq(InventoryAlert::getAlertType, AlertType.SLOW_MOVING);
        stats.setSlowMovingCount(alertMapper.selectCount(slowWrapper));
        
        return stats;
    }

    // ==================== 库存盘点 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCheck(InventoryCheck check) {
        check.setCheckNo(generateCheckNo());
        check.setCheckStatus(CheckStatus.PENDING);
        check.setCreateTime(LocalDateTime.now());
        check.setUpdateTime(LocalDateTime.now());
        checkMapper.insert(check);
        
        log.info("创建盘点单: {}", check.getCheckNo());
        return check.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeCheck(Long checkId, List<InventoryCheckDetail> details) {
        InventoryCheck check = checkMapper.selectById(checkId);
        if (check == null) {
            throw new BusinessException("盘点单不存在");
        }
        if (check.getCheckStatus() != CheckStatus.PENDING) {
            throw new BusinessException("盘点单状态不正确");
        }

        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        
        for (InventoryCheckDetail detail : details) {
            Inventory inventory = getInventoryById(detail.getInventoryId());
            if (inventory == null) continue;
            
            int diff = detail.getCheckQty() - inventory.getQuantity();
            detail.setCheckId(checkId);
            detail.setBeforeQty(inventory.getQuantity());
            detail.setDiffQty(diff);
            detail.setCreateTime(LocalDateTime.now());
            
            // 计算盈亏金额
            if (diff != 0) {
                detail.setDiffAmount(BigDecimal.valueOf(diff).multiply(inventory.getUnitCost()));
                if (diff > 0) {
                    totalProfit = totalProfit.add(detail.getDiffAmount());
                } else {
                    totalLoss = totalLoss.add(detail.getDiffAmount().abs());
                }
                
                // 更新库存
                inventory.setQuantity(detail.getCheckQty());
                inventory.setAvailableQuantity(detail.getCheckQty() - inventory.getLockedQuantity());
                inventory.setUpdateTime(LocalDateTime.now());
                inventoryMapper.updateById(inventory);
            }
        }

        // 更新盘点单状态
        check.setCheckStatus(CheckStatus.COMPLETED);
        check.setCompleteTime(LocalDateTime.now());
        check.setTotalProfit(totalProfit);
        check.setTotalLoss(totalLoss);
        check.setNetProfit(totalProfit.subtract(totalLoss));
        check.setUpdateTime(LocalDateTime.now());
        checkMapper.updateById(check);
        
        log.info("盘点完成: {}, 盈利: {}, 亏损: {}", check.getCheckNo(), totalProfit, totalLoss);
    }

    // ==================== 库存查询 ====================

    @Override
    public List<Inventory> getInventoryByWarehouse(Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getWarehouseId, warehouseId);
        return inventoryMapper.selectList(wrapper);
    }

    @Override
    public List<Inventory> getLowStockInventory(Integer threshold) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(Inventory::getQuantity, threshold)
               .orderByAsc(Inventory::getQuantity);
        return inventoryMapper.selectList(wrapper);
    }

    @Override
    public List<Inventory> getSlowMovingInventory(Integer days, Integer minSales) {
        // 获取指定天数内销量低于阈值的商品
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        
        LambdaQueryWrapper<InventoryLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.ge(InventoryLog::getCreateTime, startDate)
                 .eq(InventoryLog::getOperationType, OperationType.OUTBOUND);
        List<InventoryLog> logs = inventoryLogMapper.selectList(logWrapper);
        
        // 按商品分组统计销量
        Map<Long, Integer> salesMap = logs.stream()
                .collect(Collectors.groupingBy(InventoryLog::getProductId, 
                        Collectors.summingInt(InventoryLog::getQuantity)));
        
        // 筛选滞销商品
        List<Long> slowProductIds = salesMap.entrySet().stream()
                .filter(e -> e.getValue() < minSales)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        if (slowProductIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Inventory::getProductId, slowProductIds)
               .gt(Inventory::getQuantity, 0);
        return inventoryMapper.selectList(wrapper);
    }

    @Override
    public List<InventoryLog> getInventoryLogs(Long inventoryId, int page, int size) {
        LambdaQueryWrapper<InventoryLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryLog::getInventoryId, inventoryId)
               .orderByDesc(InventoryLog::getCreateTime);
        wrapper.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        return inventoryLogMapper.selectList(wrapper);
    }

    @Override
    public List<InventoryLog> getInventoryLogsByProduct(Long productId, LocalDateTime startTime, 
                                                         LocalDateTime endTime) {
        LambdaQueryWrapper<InventoryLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryLog::getProductId, productId);
        if (startTime != null) {
            wrapper.ge(InventoryLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(InventoryLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(InventoryLog::getCreateTime);
        return inventoryLogMapper.selectList(wrapper);
    }

    // ==================== 库存统计 ====================

    @Override
    public InventoryStatistics getStatistics(Long warehouseId, LocalDateTime startTime, 
                                              LocalDateTime endTime) {
        InventoryStatistics stats = new InventoryStatistics();
        
        // 查询库存记录
        LambdaQueryWrapper<Inventory> invWrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            invWrapper.eq(Inventory::getWarehouseId, warehouseId);
        }
        List<Inventory> inventories = inventoryMapper.selectList(invWrapper);
        
        // 计算库存统计
        stats.setTotalSku(inventories.size());
        stats.setTotalQuantity(inventories.stream().mapToInt(Inventory::getQuantity).sum());
        stats.setAvailableQuantity(inventories.stream().mapToInt(Inventory::getAvailableQuantity).sum());
        stats.setLockedQuantity(inventories.stream().mapToInt(Inventory::getLockedQuantity).sum());
        
        // 库存状态分布
        long normalCount = inventories.stream().filter(i -> i.getStatus() == InventoryStatus.NORMAL).count();
        long warningCount = inventories.stream().filter(i -> i.getStatus() == InventoryStatus.WARNING).count();
        long dangerCount = inventories.stream().filter(i -> i.getStatus() == InventoryStatus.DANGER).count();
        
        stats.setNormalCount((int) normalCount);
        stats.setWarningCount((int) warningCount);
        stats.setDangerCount((int) dangerCount);
        
        // 计算库存金额
        BigDecimal totalAmount = inventories.stream()
                .map(i -> i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalAmount(totalAmount);
        
        // 查询流水统计
        if (startTime != null && endTime != null) {
            LambdaQueryWrapper<InventoryLog> logWrapper = new LambdaQueryWrapper<>();
            logWrapper.between(InventoryLog::getCreateTime, startTime, endTime);
            List<InventoryLog> logs = inventoryLogMapper.selectList(logWrapper);
            
            int inboundQty = logs.stream()
                    .filter(l -> l.getOperationType() == OperationType.INBOUND)
                    .mapToInt(InventoryLog::getQuantity).sum();
            int outboundQty = logs.stream()
                    .filter(l -> l.getOperationType() == OperationType.OUTBOUND)
                    .mapToInt(InventoryLog::getQuantity).sum();
            
            stats.setInboundQuantity(inboundQty);
            stats.setOutboundQuantity(outboundQty);
            stats.setNetQuantity(inboundQty - outboundQty);
        }
        
        return stats;
    }

    @Override
    public List<InventoryTrend> getInventoryTrend(Long productId, Long warehouseId, 
                                                    LocalDate startDate, LocalDate endDate) {
        List<InventoryTrend> trends = new ArrayList<>();
        
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            InventoryTrend trend = new InventoryTrend();
            trend.setDate(current);
            
            // 查询当天入库
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.plusDays(1).atStartOfDay();
            
            LambdaQueryWrapper<InventoryLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(InventoryLog::getProductId, productId)
                   .eq(InventoryLog::getWarehouseId, warehouseId)
                   .between(InventoryLog::getCreateTime, dayStart, dayEnd);
            
            List<InventoryLog> logs = inventoryLogMapper.selectList(wrapper);
            
            int inbound = logs.stream()
                    .filter(l -> l.getOperationType() == OperationType.INBOUND)
                    .mapToInt(InventoryLog::getQuantity).sum();
            int outbound = logs.stream()
                    .filter(l -> l.getOperationType() == OperationType.OUTBOUND)
                    .mapToInt(InventoryLog::getQuantity).sum();
            
            trend.setInboundQty(inbound);
            trend.setOutboundQty(outbound);
            trend.setNetQty(inbound - outbound);
            
            trends.add(trend);
            current = current.plusDays(1);
        }
        
        return trends;
    }

    // ==================== 批量操作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchIncreaseInventory(List<InventoryBatchOperation> operations) {
        BatchOperationResult result = new BatchOperationResult();
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        
        for (InventoryBatchOperation op : operations) {
            try {
                increaseInventory(op.getProductId(), op.getWarehouseId(), 
                                op.getQuantity(), op.getRemark());
                successList.add(String.format("SKU%d-仓库%d", op.getProductId(), op.getWarehouseId()));
            } catch (Exception e) {
                failedList.add(String.format("SKU%d-仓库%d: %s", 
                        op.getProductId(), op.getWarehouseId(), e.getMessage()));
            }
        }
        
        result.setSuccessCount(successList.size());
        result.setFailedCount(failedList.size());
        result.setSuccessList(successList);
        result.setFailedList(failedList);
        
        log.info("批量入库完成: 成功={}, 失败={}", successList.size(), failedList.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchDecreaseInventory(List<InventoryBatchOperation> operations) {
        BatchOperationResult result = new BatchOperationResult();
        List<String> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        
        for (InventoryBatchOperation op : operations) {
            try {
                decreaseInventory(op.getProductId(), op.getWarehouseId(), 
                                op.getQuantity(), op.getRemark());
                successList.add(String.format("SKU%d-仓库%d", op.getProductId(), op.getWarehouseId()));
            } catch (Exception e) {
                failedList.add(String.format("SKU%d-仓库%d: %s", 
                        op.getProductId(), op.getWarehouseId(), e.getMessage()));
            }
        }
        
        result.setSuccessCount(successList.size());
        result.setFailedCount(failedList.size());
        result.setSuccessList(successList);
        result.setFailedList(failedList);
        
        log.info("批量出库完成: 成功={}, 失败={}", successList.size(), failedList.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateSafetyStock(List<SafetyStockUpdate> updates) {
        for (SafetyStockUpdate update : updates) {
            Inventory inventory = inventoryMapper.selectById(update.getInventoryId());
            if (inventory != null) {
                inventory.setSafetyStock(update.getSafetyStock());
                inventory.setUpdateTime(LocalDateTime.now());
                inventoryMapper.updateById(inventory);
                
                // 重新检查预警
                checkAndCreateAlert(inventory);
            }
        }
        log.info("批量更新安全库存完成: {}条", updates.size());
    }

    // ==================== 私有方法 ====================

    /**
     * 记录库存流水
     */
    private void recordInventoryLog(Long inventoryId, Long productId, Long warehouseId,
                                  Integer quantity, OperationType operationType,
                                  Integer beforeQty, Integer afterQty, String remark) {
        InventoryLog log = new InventoryLog();
        log.setInventoryId(inventoryId);
        log.setProductId(productId);
        log.setWarehouseId(warehouseId);
        log.setQuantity(quantity);
        log.setOperationType(operationType);
        log.setBeforeQuantity(beforeQty);
        log.setAfterQuantity(afterQty);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        inventoryLogMapper.insert(log);
    }

    /**
     * 检查并创建预警
     */
    private void checkAndCreateAlert(Inventory inventory) {
        AlertType alertType = null;
        
        if (inventory.getQuantity() == 0) {
            alertType = AlertType.OUT_OF_STOCK;
        } else if (inventory.getQuantity() < inventory.getSafetyStock()) {
            alertType = AlertType.LOW_STOCK;
        }
        
        if (alertType != null) {
            // 检查是否已存在未处理的预警
            LambdaQueryWrapper<InventoryAlert> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(InventoryAlert::getInventoryId, inventory.getId())
                   .eq(InventoryAlert::getAlertType, alertType)
                   .eq(InventoryAlert::getAlertStatus, AlertStatus.PENDING);
            
            Long existCount = alertMapper.selectCount(wrapper);
            if (existCount == 0) {
                InventoryAlert alert = new InventoryAlert();
                alert.setInventoryId(inventory.getId());
                alert.setProductId(inventory.getProductId());
                alert.setWarehouseId(inventory.getWarehouseId());
                alert.setAlertType(alertType);
                alert.setCurrentQty(inventory.getQuantity());
                alert.setSafetyStock(inventory.getSafetyStock());
                alert.setAlertStatus(AlertStatus.PENDING);
                alert.setCreateTime(LocalDateTime.now());
                alert.setUpdateTime(LocalDateTime.now());
                alertMapper.insert(alert);
                
                log.info("创建库存预警: 类型={}, 商品ID={}, 当前={}, 安全={}", 
                        alertType, inventory.getProductId(), 
                        inventory.getQuantity(), inventory.getSafetyStock());
            }
        }
    }

    /**
     * 生成盘点单号
     */
    private String generateCheckNo() {
        return "CHK" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}