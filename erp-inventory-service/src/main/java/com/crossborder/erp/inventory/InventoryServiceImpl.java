package com.crossborder.erp.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crossborder.erp.common.exception.BusinessException;
import com.crossborder.erp.inventory.entity.Inventory;
import com.crossborder.erp.inventory.entity.InventoryLog;
import com.crossborder.erp.inventory.enums.OperationType;
import com.crossborder.erp.inventory.mapper.InventoryLogMapper;
import com.crossborder.erp.inventory.mapper.InventoryMapper;
import com.crossborder.erp.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    @Override
    public Inventory getInventory(Long productId, Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getProductId, productId)
               .eq(Inventory::getWarehouseId, warehouseId);
        return inventoryMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseInventory(Long productId, Long warehouseId, Integer quantity, String remark) {
        if (quantity <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }

        Inventory inventory = getInventory(productId, warehouseId);
        if (inventory == null) {
            inventory = new Inventory();
            inventory.setProductId(productId);
            inventory.setWarehouseId(warehouseId);
            inventory.setQuantity(quantity);
            inventory.setCreateTime(LocalDateTime.now());
            inventoryMapper.insert(inventory);
        } else {
            inventory.setQuantity(inventory.getQuantity() + quantity);
            inventory.setUpdateTime(LocalDateTime.now());
            inventoryMapper.updateById(inventory);
        }

        // 记录库存流水
        recordInventoryLog(inventory.getId(), productId, warehouseId, quantity,
                OperationType.INBOUND, inventory.getQuantity(), remark);

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

        if (inventory.getQuantity() < quantity) {
            throw new BusinessException("库存不足，当前库存: " + inventory.getQuantity());
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inventory);

        // 记录库存流水
        recordInventoryLog(inventory.getId(), productId, warehouseId, quantity,
                OperationType.OUTBOUND, inventory.getQuantity(), remark);

        log.info("出库成功: 商品ID={}, 仓库ID={}, 数量={}", productId, warehouseId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferInventory(Long productId, Long fromWarehouseId, Long toWarehouseId,
                                 Integer quantity, String remark) {
        // 先出库
        decreaseInventory(productId, fromWarehouseId, quantity, "调拨出库: " + remark);
        // 再入库
        increaseInventory(productId, toWarehouseId, quantity, "调拨入库: " + remark);

        log.info("调拨成功: 商品ID={}, 从仓库{}调拨{}个到仓库{}", productId, fromWarehouseId, quantity, toWarehouseId);
    }

    @Override
    public List<Inventory> getInventoryByWarehouse(Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getWarehouseId, warehouseId);
        return inventoryMapper.selectList(wrapper);
    }

    @Override
    public List<Inventory> getLowStockInventory(Integer threshold) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(Inventory::getQuantity, threshold);
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

    /**
     * 记录库存流水
     */
    private void recordInventoryLog(Long inventoryId, Long productId, Long warehouseId,
                                  Integer quantity, OperationType operationType,
                                  Integer afterQuantity, String remark) {
        InventoryLog log = new InventoryLog();
        log.setInventoryId(inventoryId);
        log.setProductId(productId);
        log.setWarehouseId(warehouseId);
        log.setQuantity(quantity);
        log.setOperationType(operationType);
        log.setBeforeQuantity(afterQuantity - quantity);
        log.setAfterQuantity(afterQuantity);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        inventoryLogMapper.insert(log);
    }
}
