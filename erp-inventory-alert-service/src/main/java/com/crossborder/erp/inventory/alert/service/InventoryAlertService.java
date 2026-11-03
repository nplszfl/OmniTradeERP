package com.crossborder.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.inventory.alert.entity.InventoryAlert;
import com.crossborder.erp.inventory.alert.entity.InventoryAlertRule;
import com.crossborder.erp.inventory.alert.entity.ReplenishmentSuggestion;
import com.crossborder.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.crossborder.erp.inventory.alert.mapper.LiveInventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存预警规则服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAlertService extends ServiceImpl<InventoryAlertRuleMapper, InventoryAlertRule> {

    private final AlertRecordService alertRecordService;
    private final AlertNotifyService alertNotifyService;
    private final ReplenishmentSuggestionService replenishmentSuggestionService;
    private final AlertStatisticsService alertStatisticsService;
    private final LiveInventoryMapper liveInventoryMapper;

    /**
     * 创建预警规则
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(InventoryAlertRule rule) {
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getAlertType() == null) {
            rule.setAlertType("LOW");
        }
        if (rule.getMinStock() == null) {
            rule.setMinStock(0);
        }
        if (rule.getAdvanceDays() == null) {
            rule.setAdvanceDays(3);
        }
        if (rule.getNotifyType() == null) {
            rule.setNotifyType("ALL");
        }
        
        save(rule);
        log.info("创建库存预警规则成功: SKU={}, alertStock={}", rule.getSku(), rule.getAlertStock());
        return rule.getId();
    }

    /**
     * 检查库存并触发预警
     * 当库存低于预警值时返回需要预警的规则
     *
     * @return 本次新建的预警条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int checkAndAlert(Long productId, String sku, String warehouseId, 
                              Integer currentStock, String productName, String warehouseName) {
        // 查找匹配的预警规则
        LambdaQueryWrapper<InventoryAlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryAlertRule::getProductId, productId)
               .eq(InventoryAlertRule::getWarehouseId, warehouseId)
               .eq(InventoryAlertRule::getEnabled, true);
        
        List<InventoryAlertRule> rules = list(wrapper);
        
        // 如果没有找到特定产品规则，检查是否有通用规则
        if (rules.isEmpty()) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.isNull(InventoryAlertRule::getProductId)
                   .eq(InventoryAlertRule::getWarehouseId, warehouseId)
                   .eq(InventoryAlertRule::getEnabled, true);
            rules = list(wrapper);
        }
        
        int created = 0;
        for (InventoryAlertRule rule : rules) {
            boolean shouldAlert = false;
            String alertType = "LOW";
            
            if (currentStock <= rule.getMinStock()) {
                shouldAlert = true;
                alertType = "OUT";
            } else if (currentStock <= rule.getAlertStock()) {
                shouldAlert = true;
                alertType = "LOW";
            } else if (rule.getSafeStock() != null && currentStock < rule.getSafeStock()) {
                shouldAlert = true;
                alertType = "SAFE";
            }
            
            if (shouldAlert) {
                // ⚠️ 去重：扫描是定时任务（默认 5 分钟一次），
                //    没有去重的话同一个 SKU 每轮都会插一条 PENDING，
                //    一天能堆出几百条重复预警，把「待处理」列表彻底冲垮。
                //    口径：同 (商品, 仓库, 预警类型) 已有 PENDING 就不重复建。
                if (hasPendingAlert(productId, warehouseId, alertType)) {
                    log.debug("跳过重复预警: SKU={}, 仓库={}, 类型={}", sku, warehouseId, alertType);
                    continue;
                }

                // 创建预警记录
                InventoryAlert alert = new InventoryAlert();
                alert.setRuleId(rule.getId());
                alert.setProductId(productId);
                alert.setSku(sku);
                alert.setProductName(productName);
                alert.setWarehouseId(warehouseId);
                alert.setWarehouseName(warehouseName);
                alert.setCurrentStock(currentStock);
                alert.setAlertStock(rule.getAlertStock());
                alert.setSafeStock(rule.getSafeStock());
                alert.setAlertType(alertType);
                alert.setMessage(buildAlertMessage(alertType, sku, productName, warehouseName, 
                        currentStock, rule.getAlertStock(), rule.getSafeStock()));
                alert.setStatus("PENDING");
                alert.setNotified(false);
                alert.setNotifyType(rule.getNotifyType());
                
                alertRecordService.createAlert(alert);
                
                // 发送通知
                sendNotification(alert, rule);
                
                // 自动生成补货建议
                if ("OUT".equals(alertType) || "LOW".equals(alertType)) {
                    replenishmentSuggestionService.createSuggestionByAlert(alert, rule);
                }
                
                log.warn("🚨 库存预警: SKU={}, 当前库存={}, 预警值={}, 类型={}", 
                        sku, currentStock, rule.getAlertStock(), alertType);
                created++;
            }
        }
        return created;
    }

    /**
     * 同 (商品, 仓库, 预警类型) 是否已有未处理的预警
     */
    private boolean hasPendingAlert(Long productId, String warehouseId, String alertType) {
        try {
            long count = alertRecordService.count(new LambdaQueryWrapper<InventoryAlert>()
                    .eq(InventoryAlert::getProductId, productId)
                    .eq(InventoryAlert::getWarehouseId, warehouseId)
                    .eq(InventoryAlert::getAlertType, alertType)
                    .eq(InventoryAlert::getStatus, "PENDING"));
            return count > 0;
        } catch (Exception e) {
            // 查重失败不能阻断预警创建（宁可多一条也不能少一条）
            log.warn("查重失败，跳过去重: productId={}, warehouseId={}, error={}",
                    productId, warehouseId, e.getMessage());
            return false;
        }
    }

    /**
     * 扫描活库存并批量触发预警（预警引擎的真正入口）
     *
     * 📌 背景：这套预警引擎以前**从来没有被触发过** ——
     *    checkAndAlert() 唯一的入口是 HTTP 接口 POST /inventory/alert/check，
     *    而那个接口要求调用方自己传 currentStock，
     *    全仓（含前端）**没有任何调用方**；
     *    本服务也没有任何 Feign / RestTemplate / 定时任务去拉库存。
     *    结果：t_inventory_alert 永远 0 行，前端「待处理预警」恒空。
     *
     * 📌 现在改成：直接从活表 erp_inventory.erp_inventory 读，
     *    关联 erp_warehouse 拿仓库编码、关联 erp_product 拿商品名，
     *    再逐条送进 checkAndAlert()。
     *
     * @return 本次新建的预警条数
     */
    public int scanLiveInventory() {
        List<Map<String, Object>> rows;
        try {
            rows = liveInventoryMapper.selectLiveInventory();
        } catch (Exception e) {
            // ⚠️ 跨 schema 查不到活表时不能抛出去 —— 否则一次抖动就把整轮扫描废掉；
            //    但「静默返回 0」又会让页面显示成「一切正常」（那是最坏情况）。
            //    所以：记 ERROR 日志 + 返回 0，让日志/监控能看见。
            log.error("读取活库存失败，本轮扫描跳过: {}", e.getMessage(), e);
            return 0;
        }
        if (rows == null || rows.isEmpty()) {
            log.info("活库存为空，无需扫描");
            return 0;
        }

        int created = 0;
        int skipped = 0;
        for (Map<String, Object> row : rows) {
            Long productId = toLong(row.get("productId"));
            Integer currentStock = toInt(row.get("currentStock"));
            String sku = toStr(row.get("sku"));
            if (productId == null || currentStock == null || sku.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                created += checkAndAlert(productId, sku,
                        toStr(row.get("warehouseCode")), currentStock,
                        toStr(row.get("productName")), toStr(row.get("warehouseName")));
            } catch (Exception e) {
                // 一条脏数据不能打断整轮扫描
                skipped++;
                log.warn("处理库存行失败: productId={}, sku={}, error={}", productId, sku, e.getMessage());
            }
        }
        log.info("库存预警扫描完成: 扫描={} 条, 新增预警={} 条, 跳过={} 条", rows.size(), created, skipped);
        return created;
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        Long l = toLong(v);
        return l == null ? null : l.intValue();
    }

    private static String toStr(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    /**
     * 构建预警消息
     */
    private String buildAlertMessage(String alertType, String sku, String productName, 
                                      String warehouseName, int currentStock, 
                                      int alertStock, Integer safeStock) {
        String typeName = switch (alertType) {
            case "OUT" -> "缺货";
            case "LOW" -> "库存偏低";
            case "SAFE" -> "安全库存警告";
            default -> "未知";
        };
        
        if (safeStock != null) {
            return String.format("%s: SKU=%s, 产品=%s, 仓库=%s, 当前库存=%d, 预警值=%d, 安全库存=%d",
                    typeName, sku, productName, warehouseName, currentStock, alertStock, safeStock);
        }
        return String.format("%s: SKU=%s, 产品=%s, 仓库=%s, 当前库存=%d, 预警值=%d",
                typeName, sku, productName, warehouseName, currentStock, alertStock);
    }

    /**
     * 发送通知
     */
    private void sendNotification(InventoryAlert alert, InventoryAlertRule rule) {
        try {
            boolean sent = alertNotifyService.sendNotification(
                    alert, 
                    rule.getNotifyType(), 
                    rule.getEmailList(), 
                    rule.getPhoneList()
            );
            // ⚠️ 只有真的发出去了才标「已通知」。
            //    邮件列表为空 / 短信是模拟实现 / 发送异常 —— 这三种情况一个人都没通知到，
            //    标成 notified=true 会让列表页显示「已通知」，实际没人收到过。
            if (sent) {
                alertRecordService.markAsNotified(alert.getId());
            } else {
                log.info("预警未发出通知（无接收人或发送失败），不标记已通知: alertId={}", alert.getId());
            }
        } catch (Exception e) {
            log.error("发送通知失败: alertId={}, error={}", alert.getId(), e.getMessage());
        }
    }

    /**
     * 获取所有启用的预警规则
     */
    public List<InventoryAlertRule> getEnabledRules() {
        return lambdaQuery()
            .eq(InventoryAlertRule::getEnabled, true)
            .orderByAsc(InventoryAlertRule::getAlertStock)
            .list();
    }

    /**
     * 获取产品的预警规则
     */
    public List<InventoryAlertRule> getRulesByProduct(Long productId) {
        return lambdaQuery()
            .eq(InventoryAlertRule::getProductId, productId)
            .orderByDesc(InventoryAlertRule::getCreateTime)
            .list();
    }

    /**
     * 批量设置预警规则（为产品批量设置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateRules(Long productId, String sku, String productName,
                                  String warehouseId, String warehouseName,
                                  Integer safeStock, Integer alertStock) {
        InventoryAlertRule rule = new InventoryAlertRule();
        rule.setProductId(productId);
        rule.setSku(sku);
        rule.setProductName(productName);
        rule.setWarehouseId(warehouseId);
        rule.setWarehouseName(warehouseName);
        rule.setSafeStock(safeStock);
        rule.setAlertStock(alertStock);
        rule.setAlertType("LOW");
        rule.setEnabled(true);
        rule.setMinStock(0);
        rule.setAdvanceDays(3);
        rule.setNotifyType("ALL");
        
        save(rule);
    }

    /**
     * 更新规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(InventoryAlertRule rule) {
        updateById(rule);
        log.info("更新库存预警规则: id={}", rule.getId());
    }

    /**
     * 删除规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        removeById(id);
        log.info("删除库存预警规则: id={}", id);
    }

    /**
     * 分页查询预警规则
     */
    public IPage<InventoryAlertRule> queryRules(Integer page, Integer size, 
                                                  String warehouseId, Boolean enabled) {
        Page<InventoryAlertRule> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<InventoryAlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(warehouseId), InventoryAlertRule::getWarehouseId, warehouseId)
               .eq(enabled != null, InventoryAlertRule::getEnabled, enabled)
               .orderByDesc(InventoryAlertRule::getCreateTime);
        
        return page(pageParam, wrapper);
    }

    /**
     * 批量检查库存（定时任务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCheckInventory(List<Map<String, Object>> inventoryList) {
        for (Map<String, Object> inventory : inventoryList) {
            try {
                Long productId = ((Number) inventory.get("productId")).longValue();
                String sku = (String) inventory.get("sku");
                String warehouseId = (String) inventory.get("warehouseId");
                Integer currentStock = ((Number) inventory.get("currentStock")).intValue();
                String productName = (String) inventory.get("productName");
                String warehouseName = (String) inventory.get("warehouseName");
                
                checkAndAlert(productId, sku, warehouseId, currentStock, productName, warehouseName);
            } catch (Exception e) {
                log.error("检查库存失败: inventory={}, error={}", inventory, e.getMessage());
            }
        }
    }

    /**
     * 获取实时统计
     */
    public Map<String, Object> getRealTimeStatistics(String warehouseId) {
        return alertStatisticsService.getRealTimeStatistics(warehouseId);
    }

    /**
     * 获取补货建议列表
     */
    public IPage<ReplenishmentSuggestion> getReplenishmentSuggestions(Integer page, Integer size, 
                                                                       String warehouseId, String status) {
        return replenishmentSuggestionService.querySuggestions(page, size, warehouseId, status, null, null);
    }

    /**
     * 确认补货建议
     */
    public void confirmReplenishment(Long suggestionId, Long userId, String userName, String remark) {
        replenishmentSuggestionService.confirmSuggestion(suggestionId, userId, userName, remark);
    }

    /**
     * 取消补货建议
     */
    public void cancelReplenishment(Long suggestionId, String remark) {
        replenishmentSuggestionService.cancelSuggestion(suggestionId, remark);
    }
}