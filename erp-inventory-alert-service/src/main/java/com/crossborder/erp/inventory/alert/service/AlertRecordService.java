package com.crossborder.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.entity.InventoryAlertRule;
import com.crossborder.erp.inventory.alert.mapper.InventoryAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预警记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecordService extends ServiceImpl<InventoryAlertMapper, InventoryAlert> {

    /**
     * 创建预警记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createAlert(InventoryAlert alert) {
        if (alert.getStatus() == null) {
            alert.setStatus("PENDING");
        }
        if (alert.getNotified() == null) {
            alert.setNotified(false);
        }
        
        save(alert);
        log.info("创建预警记录成功: id={}, sku={}, alertType={}", alert.getId(), alert.getSku(), alert.getAlertType());
        return alert.getId();
    }

    /**
     * 根据规则创建预警记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createAlertByRule(InventoryAlertRule rule, Integer currentStock) {
        InventoryAlert alert = new InventoryAlert();
        alert.setRuleId(rule.getId());
        alert.setProductId(rule.getProductId());
        alert.setSku(rule.getSku());
        alert.setProductName(rule.getProductName());
        alert.setWarehouseId(rule.getWarehouseId());
        alert.setWarehouseName(rule.getWarehouseName());
        alert.setCurrentStock(currentStock);
        alert.setAlertStock(rule.getAlertStock());
        alert.setSafeStock(rule.getSafeStock());
        
        // 判断预警类型
        if (currentStock == 0) {
            alert.setAlertType("OUT");
        } else if (currentStock <= rule.getMinStock()) {
            alert.setAlertType("OUT");
        } else if (currentStock <= rule.getAlertStock()) {
            alert.setAlertType("LOW");
        } else {
            alert.setAlertType("SAFE");
        }
        
        alert.setMessage(buildAlertMessage(alert));
        alert.setStatus("PENDING");
        alert.setNotified(false);
        alert.setNotifyType(rule.getNotifyType());
        
        return createAlert(alert);
    }

    /**
     * 构建预警消息
     */
    private String buildAlertMessage(InventoryAlert alert) {
        String typeName = switch (alert.getAlertType()) {
            case "OUT" -> "缺货";
            case "LOW" -> "库存偏低";
            case "SAFE" -> "安全库存警告";
            default -> "未知";
        };
        
        return String.format("%s: SKU=%s, 产品=%s, 当前库存=%d, 预警值=%d, 安全库存=%d",
                typeName, alert.getSku(), alert.getProductName(), 
                alert.getCurrentStock(), alert.getAlertStock(), alert.getSafeStock());
    }

    /**
     * 分页查询预警记录
     */
    public IPage<InventoryAlert> queryAlerts(Integer page, Integer size, String warehouseId, 
                                               String alertType, String status, 
                                               LocalDateTime startTime, LocalDateTime endTime) {
        Page<InventoryAlert> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<InventoryAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(warehouseId), InventoryAlert::getWarehouseId, warehouseId)
               .eq(StringUtils.hasText(alertType), InventoryAlert::getAlertType, alertType)
               .eq(StringUtils.hasText(status), InventoryAlert::getStatus, status)
               .ge(startTime != null, InventoryAlert::getCreateTime, startTime)
               .le(endTime != null, InventoryAlert::getCreateTime, endTime)
               .orderByDesc(InventoryAlert::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    /**
     * 查询待处理预警
     */
    public List<InventoryAlert> getPendingAlerts() {
        return lambdaQuery()
                .eq(InventoryAlert::getStatus, "PENDING")
                .orderByAsc(InventoryAlert::getCreateTime)
                .list();
    }

    /**
     * 查询未发送通知的预警
     */
    public List<InventoryAlert> getUnnotifiedAlerts() {
        return lambdaQuery()
                .eq(InventoryAlert::getNotified, false)
                .eq(InventoryAlert::getStatus, "PENDING")
                .list();
    }

    /**
     * 处理预警
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long alertId, Long handlerId, String handlerName, String remark) {
        InventoryAlert alert = getById(alertId);
        if (alert == null) {
            throw new BizException("预警记录不存在: " + alertId);
        }
        
        alert.setStatus("HANDLED");
        alert.setHandlerId(handlerId);
        alert.setHandlerName(handlerName);
        alert.setHandleRemark(remark);
        alert.setHandledTime(LocalDateTime.now());
        
        updateById(alert);
        log.info("处理预警成功: alertId={}, handler={}", alertId, handlerName);
    }

    /**
     * 忽略预警
     */
    @Transactional(rollbackFor = Exception.class)
    public void ignoreAlert(Long alertId, Long handlerId, String handlerName, String remark) {
        InventoryAlert alert = getById(alertId);
        if (alert == null) {
            throw new BizException("预警记录不存在: " + alertId);
        }
        
        alert.setStatus("IGNORED");
        alert.setHandlerId(handlerId);
        alert.setHandlerName(handlerName);
        alert.setHandleRemark(remark);
        alert.setHandledTime(LocalDateTime.now());
        
        updateById(alert);
        log.info("忽略预警: alertId={}, handler={}", alertId, handlerName);
    }

    /**
     * 标记为已通知
     */
    @Transactional(rollbackFor = Exception.class)
    public void markAsNotified(Long alertId) {
        InventoryAlert alert = new InventoryAlert();
        alert.setId(alertId);
        alert.setNotified(true);
        updateById(alert);
    }

    /**
     * 统计预警数量
     */
    public Long countAlerts(String warehouseId, String alertType, String status, 
                             LocalDateTime startTime, LocalDateTime endTime) {
        return lambdaQuery()
                .eq(StringUtils.hasText(warehouseId), InventoryAlert::getWarehouseId, warehouseId)
                .eq(StringUtils.hasText(alertType), InventoryAlert::getAlertType, alertType)
                .eq(StringUtils.hasText(status), InventoryAlert::getStatus, status)
                .ge(startTime != null, InventoryAlert::getCreateTime, startTime)
                .le(endTime != null, InventoryAlert::getCreateTime, endTime)
                .count();
    }

    /**
     * 获取今日预警数量
     */
    public Long getTodayAlertCount(String warehouseId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        return countAlerts(warehouseId, null, null, startOfDay, null);
    }
}