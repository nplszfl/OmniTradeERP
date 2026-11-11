package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.dto.CooperationDTO;
import com.omnitrade.erp.social.model.Cooperation;
import com.omnitrade.erp.social.model.Influencer;
import com.omnitrade.erp.social.model.TikTokOrder;
import com.omnitrade.erp.social.repository.CooperationRepository;
import com.omnitrade.erp.social.repository.InfluencerRepository;
import com.omnitrade.erp.social.repository.TikTokOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 达人合作佣金计算服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {
    
    private final CooperationRepository cooperationRepository;
    private final InfluencerRepository influencerRepository;
    private final TikTokOrderRepository orderRepository;
    
    /**
     * 计算订单佣金
     * 基于订单金额和达人佣金率
     */
    public BigDecimal calculateOrderCommission(String orderId) {
        TikTokOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BizException("订单不存在: " + orderId));
        
        if (order.getInfluencerId() == null) {
            return BigDecimal.ZERO;
        }
        
        Influencer influencer = influencerRepository.findById(order.getInfluencerId())
                .orElseThrow(() -> new BizException("达人不存在: " + order.getInfluencerId()));
        
        // 获取合作记录
        Optional<Cooperation> cooperation = cooperationRepository.findByInfluencerId(order.getInfluencerId())
                .stream()
                .filter(c -> c.getStatus() == Cooperation.CooperationStatus.IN_PROGRESS 
                        || c.getStatus() == Cooperation.CooperationStatus.COMPLETED)
                .findFirst();
        
        BigDecimal commissionRate;
        if (cooperation.isPresent() && cooperation.get().getCommissionRate() != null) {
            commissionRate = cooperation.get().getCommissionRate();
        } else {
            // 使用默认佣金率（达人设置的合作价格）
            commissionRate = influencer.getCooperationPrice() != null 
                    ? BigDecimal.valueOf(10) // 默认10%
                    : BigDecimal.ZERO;
        }
        
        // 计算佣金 = 订单金额 * 佣金率
        BigDecimal commission = order.getOrderAmount()
                .multiply(commissionRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        // 保存佣金信息到订单
        order.setCommissionAmount(commission);
        order.setCommissionRate(commissionRate);
        orderRepository.save(order);
        
        return commission;
    }
    
    /**
     * 计算合作项目的总佣金
     */
    public BigDecimal calculateProjectCommission(Long cooperationId) {
        Cooperation cooperation = cooperationRepository.findById(cooperationId)
                .orElseThrow(() -> new BizException("合作不存在: " + cooperationId));
        
        List<TikTokOrder> orders = orderRepository.findByInfluencerId(cooperation.getInfluencer().getId());
        
        BigDecimal totalCommission = BigDecimal.ZERO;
        
        for (TikTokOrder order : orders) {
            // 只计算合作期间内的订单
            if (cooperation.getActualStartDate() != null && cooperation.getActualEndDate() != null) {
                if (order.getCreateTime().isBefore(cooperation.getActualStartDate()) 
                        || order.getCreateTime().isAfter(cooperation.getActualEndDate())) {
                    continue;
                }
            }
            
            if (order.getCommissionAmount() != null) {
                totalCommission = totalCommission.add(order.getCommissionAmount());
            } else {
                // 计算并累加
                BigDecimal commission = calculateOrderCommission(order.getOrderId());
                totalCommission = totalCommission.add(commission);
            }
        }
        
        return totalCommission;
    }
    
    /**
     * 批量计算订单佣金（定时任务）
     */
    @Transactional
    public void batchCalculateCommission() {
        log.info("开始批量计算订单佣金");
        
        // 获取最近7天未计算佣金的订单
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<TikTokOrder> unsyncedOrders = orderRepository.findUnsyncedOrders(since);
        
        int calculated = 0;
        for (TikTokOrder order : unsyncedOrders) {
            if (order.getInfluencerId() != null && order.getCommissionAmount() == null) {
                try {
                    calculateOrderCommission(order.getOrderId());
                    calculated++;
                } catch (Exception e) {
                    log.error("计算订单佣金失败, orderId: {}", order.getOrderId(), e);
                }
            }
        }
        
        log.info("佣金计算完成, 共同计算: {} 条", calculated);
    }
    
    /**
     * 获取达人的佣金报表
     */
    public Map<String, Object> getInfluencerCommissionReport(Long influencerId, 
                                                               LocalDateTime startDate, 
                                                               LocalDateTime endDate) {
        Influencer influencer = influencerRepository.findById(influencerId)
                .orElseThrow(() -> new BizException("达人不存在: " + influencerId));
        
        Map<String, Object> report = new HashMap<>();
        
        // 基本信息
        report.put("influencerId", influencerId);
        report.put("influencerName", influencer.getName());
        report.put("platform", influencer.getPlatform().name());
        
        // 订单统计
        List<TikTokOrder> orders = orderRepository.findByInfluencerId(influencerId);
        
        // 过滤时间范围
        if (startDate != null) {
            orders = orders.stream()
                    .filter(o -> o.getCreateTime().isAfter(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            orders = orders.stream()
                    .filter(o -> o.getCreateTime().isBefore(endDate))
                    .collect(Collectors.toList());
        }
        
        int totalOrders = orders.size();
        BigDecimal totalSales = orders.stream()
                .filter(o -> o.getOrderAmount() != null)
                .map(TikTokOrder::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalCommission = orders.stream()
                .filter(o -> o.getCommissionAmount() != null)
                .map(TikTokOrder::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        report.put("totalOrders", totalOrders);
        report.put("totalSales", totalSales);
        report.put("totalCommission", totalCommission);
        
        // 平均客单价
        if (totalOrders > 0) {
            report.put("avgOrderValue", totalSales.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP));
        } else {
            report.put("avgOrderValue", BigDecimal.ZERO);
        }
        
        // 合作统计
        List<Cooperation> cooperations = cooperationRepository.findByInfluencerId(influencerId);
        int completedCooperations = (int) cooperations.stream()
                .filter(c -> c.getStatus() == Cooperation.CooperationStatus.COMPLETED)
                .count();
        
        report.put("totalCooperations", cooperations.size());
        report.put("completedCooperations", completedCooperations);
        
        // 按月统计
        Map<String, Map<String, Object>> monthlyStats = new TreeMap<>();
        for (TikTokOrder order : orders) {
            String monthKey = order.getCreateTime().getYear() + "-" 
                    + String.format("%02d", order.getCreateTime().getMonthValue());
            
            monthlyStats.computeIfAbsent(monthKey, k -> {
                Map<String, Object> stats = new HashMap<>();
                stats.put("orders", 0);
                stats.put("sales", BigDecimal.ZERO);
                stats.put("commission", BigDecimal.ZERO);
                return stats;
            });
            
            Map<String, Object> monthStats = monthlyStats.get(monthKey);
            monthStats.put("orders", (int) monthStats.get("orders") + 1);
            monthStats.put("sales", ((BigDecimal) monthStats.get("sales")).add(order.getOrderAmount()));
            if (order.getCommissionAmount() != null) {
                monthStats.put("commission", ((BigDecimal) monthStats.get("commission")).add(order.getCommissionAmount()));
            }
        }
        
        report.put("monthlyStats", monthlyStats);
        
        return report;
    }
    
    /**
     * 获取合作项目的佣金明细
     */
    public List<Map<String, Object>> getCooperationCommissionDetails(Long cooperationId) {
        Cooperation cooperation = cooperationRepository.findById(cooperationId)
                .orElseThrow(() -> new BizException("合作不存在: " + cooperationId));
        
        List<TikTokOrder> orders = orderRepository.findByInfluencerId(cooperation.getInfluencer().getId());
        
        // 过滤合作期间的订单
        final LocalDateTime startDate = cooperation.getActualStartDate();
        final LocalDateTime endDate = cooperation.getActualEndDate();
        
        List<Map<String, Object>> details = orders.stream()
                .filter(order -> {
                    if (startDate != null && order.getCreateTime().isBefore(startDate)) {
                        return false;
                    }
                    if (endDate != null && order.getCreateTime().isAfter(endDate)) {
                        return false;
                    }
                    return true;
                })
                .map(order -> {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("orderId", order.getOrderId());
                    detail.put("orderAmount", order.getOrderAmount());
                    detail.put("commissionRate", order.getCommissionRate() != null 
                            ? order.getCommissionRate() 
                            : cooperation.getCommissionRate());
                    detail.put("commission", order.getCommissionAmount());
                    detail.put("orderTime", order.getCreateTime());
                    detail.put("status", order.getStatus());
                    return detail;
                })
                .collect(Collectors.toList());
        
        return details;
    }
    
    /**
     * 预估合作佣金
     * 根据预期GMV和佣金率计算
     */
    public BigDecimal estimateCommission(BigDecimal expectedGmv, BigDecimal commissionRate) {
        if (expectedGmv == null || commissionRate == null) {
            return BigDecimal.ZERO;
        }
        return expectedGmv.multiply(commissionRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算达人收益（扣除平台费用后）
     */
    public BigDecimal calculateInfluencerEarnings(Long influencerId, LocalDateTime startDate, LocalDateTime endDate) {
        List<TikTokOrder> orders = orderRepository.findByInfluencerId(influencerId);
        
        // 过滤时间范围
        if (startDate != null) {
            orders = orders.stream()
                    .filter(o -> o.getCreateTime().isAfter(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            orders = orders.stream()
                    .filter(o -> o.getCreateTime().isBefore(endDate))
                    .collect(Collectors.toList());
        }
        
        // 计算总佣金
        BigDecimal totalCommission = orders.stream()
                .filter(o -> o.getCommissionAmount() != null)
                .map(TikTokOrder::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 扣除平台服务费（假设10%）
        BigDecimal platformFee = totalCommission.multiply(BigDecimal.valueOf(0.1))
                .setScale(2, RoundingMode.HALF_UP);
        
        // 达人实际收益
        BigDecimal netEarnings = totalCommission.subtract(platformFee);
        
        return netEarnings;
    }
}