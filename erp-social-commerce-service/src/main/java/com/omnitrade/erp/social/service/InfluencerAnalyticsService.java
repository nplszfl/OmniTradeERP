package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.dto.InfluencerDTO;
import com.omnitrade.erp.social.model.Cooperation;
import com.omnitrade.erp.social.model.Influencer;
import com.omnitrade.erp.social.model.LiveStream;
import com.omnitrade.erp.social.model.TikTokOrder;
import com.omnitrade.erp.social.repository.CooperationRepository;
import com.omnitrade.erp.social.repository.InfluencerRepository;
import com.omnitrade.erp.social.repository.LiveStreamRepository;
import com.omnitrade.erp.social.repository.TikTokOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 达人数据分析服务
 * 提供达人绩效分析、排名、对比等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfluencerAnalyticsService {
    
    private final InfluencerRepository influencerRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final TikTokOrderRepository orderRepository;
    private final CooperationRepository cooperationRepository;
    
    /**
     * 获取达人综合评分
     */
    public BigDecimal getInfluencerScore(Long influencerId) {
        Influencer influencer = influencerRepository.findById(influencerId)
                .orElseThrow(() -> new BizException("达人不存在: " + influencerId));
        
        // 评分因子
        double followerScore = calculateFollowerScore(influencer);
        double engagementScore = calculateEngagementScore(influencer);
        double cooperationScore = calculateCooperationScore(influencerId);
        double salesScore = calculateSalesScore(influencerId);
        
        // 综合评分 = 粉丝数20% + 互动率30% + 合作效果25% + 销售额25%
        double totalScore = followerScore * 0.2 + engagementScore * 0.3 
                + cooperationScore * 0.25 + salesScore * 0.25;
        
        return BigDecimal.valueOf(totalScore).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 获取达人排名
     */
    public List<Map<String, Object>> getInfluencerRankings(String platform, String sortBy, int limit) {
        List<Influencer> influencers = influencerRepository.findAll();
        
        // 按平台过滤
        if (platform != null && !platform.isEmpty()) {
            influencers = influencers.stream()
                    .filter(i -> i.getPlatform().name().equals(platform))
                    .collect(Collectors.toList());
        }
        
        // 计算每个达人的综合评分和统计数据
        List<Map<String, Object>> rankings = influencers.stream()
                .map(influencer -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("influencerId", influencer.getId());
                    data.put("name", influencer.getName());
                    data.put("platform", influencer.getPlatform().name());
                    data.put("level", influencer.getLevel() != null ? influencer.getLevel().name() : null);
                    data.put("followerCount", influencer.getFollowerCount());
                    data.put("score", getInfluencerScore(influencer.getId()));
                    
                    // 统计数据
                    List<LiveStream> streams = liveStreamRepository.findByInfluencerId(influencer.getId());
                    List<TikTokOrder> orders = orderRepository.findByInfluencerId(influencer.getId());
                    
                    data.put("totalStreams", streams.size());
                    data.put("totalOrders", orders.size());
                    
                    BigDecimal totalGmv = orders.stream()
                            .filter(o -> o.getOrderAmount() != null)
                            .map(TikTokOrder::getOrderAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    data.put("totalGmv", totalGmv);
                    
                    return data;
                })
                .collect(Collectors.toList());
        
        // 排序
        switch (sortBy != null ? sortBy : "score") {
            case "follower":
                rankings.sort((a, b) -> {
                    Integer f1 = (Integer) a.getOrDefault("followerCount", 0);
                    Integer f2 = (Integer) b.getOrDefault("followerCount", 0);
                    return f2.compareTo(f1);
                });
                break;
            case "gmv":
                rankings.sort((a, b) -> {
                    BigDecimal g1 = (BigDecimal) a.getOrDefault("totalGmv", BigDecimal.ZERO);
                    BigDecimal g2 = (BigDecimal) b.getOrDefault("totalGmv", BigDecimal.ZERO);
                    return g2.compareTo(g1);
                });
                break;
            case "orders":
                rankings.sort((a, b) -> {
                    Integer o1 = (Integer) a.getOrDefault("totalOrders", 0);
                    Integer o2 = (Integer) b.getOrDefault("totalOrders", 0);
                    return o2.compareTo(o1);
                });
                break;
            default:
                rankings.sort((a, b) -> {
                    BigDecimal s1 = (BigDecimal) a.getOrDefault("score", BigDecimal.ZERO);
                    BigDecimal s2 = (BigDecimal) b.getOrDefault("score", BigDecimal.ZERO);
                    return s2.compareTo(s1);
                });
        }
        
        return rankings.stream().limit(limit).collect(Collectors.toList());
    }
    
    /**
     * 获取达人详细的分析报告
     */
    public Map<String, Object> getInfluencerAnalyticsReport(Long influencerId) {
        Influencer influencer = influencerRepository.findById(influencerId)
                .orElseThrow(() -> new BizException("达人不存在: " + influencerId));
        
        Map<String, Object> report = new HashMap<>();
        
        // 基本信息
        report.put("id", influencer.getId());
        report.put("name", influencer.getName());
        report.put("platform", influencer.getPlatform().name());
        report.put("platformId", influencer.getPlatformId());
        report.put("level", influencer.getLevel() != null ? influencer.getLevel().name() : null);
        report.put("followerCount", influencer.getFollowerCount());
        report.put("avatarUrl", influencer.getAvatarUrl());
        
        // 综合评分
        report.put("score", getInfluencerScore(influencerId));
        
        // 直播数据
        List<LiveStream> streams = liveStreamRepository.findByInfluencerId(influencerId);
        report.put("totalStreams", streams.size());
        
        // 直播统计
        int totalLiveTime = streams.stream()
                .filter(s -> s.getDuration() != null)
                .mapToInt(LiveStream::getDuration)
                .sum();
        int avgViewers = streams.stream()
                .filter(s -> s.getViewerCount() != null)
                .mapToInt(LiveStream::getViewerCount)
                .sum() / (streams.size() > 0 ? streams.size() : 1);
        
        report.put("totalLiveTime", totalLiveTime);
        report.put("avgViewers", avgViewers);
        
        // 订单数据
        List<TikTokOrder> orders = orderRepository.findByInfluencerId(influencerId);
        report.put("totalOrders", orders.size());
        
        BigDecimal totalSales = orders.stream()
                .filter(o -> o.getOrderAmount() != null)
                .map(TikTokOrder::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        report.put("totalSales", totalSales);
        
        BigDecimal totalCommission = orders.stream()
                .filter(o -> o.getCommissionAmount() != null)
                .map(TikTokOrder::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        report.put("totalCommission", totalCommission);
        
        // 合作数据
        List<Cooperation> cooperations = cooperationRepository.findByInfluencerId(influencerId);
        report.put("totalCooperations", cooperations.size());
        
        int completedCooperations = (int) cooperations.stream()
                .filter(c -> c.getStatus() == Cooperation.CooperationStatus.COMPLETED)
                .count();
        report.put("completedCooperations", completedCooperations);
        
        // 合作成功率
        if (!cooperations.isEmpty()) {
            double successRate = (double) completedCooperations / cooperations.size() * 100;
            report.put("cooperationSuccessRate", String.format("%.1f%%", successRate));
        }
        
        // 效果分析
        Map<String, Object> effectiveness = new HashMap<>();
        
        // 转化率
        if (totalLiveTime > 0 && orders.size() > 0) {
            double conversionRate = (double) orders.size() / totalLiveTime * 100;
            effectiveness.put("conversionRate", String.format("%.2f%%", conversionRate));
        }
        
        // 客单价
        if (orders.size() > 0) {
            BigDecimal avgOrderValue = totalSales.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
            effectiveness.put("avgOrderValue", avgOrderValue);
        }
        
        // ROI
        if (totalCommission.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal roi = totalSales.divide(totalCommission, 2, RoundingMode.HALF_UP);
            effectiveness.put("roi", roi);
        }
        
        report.put("effectiveness", effectiveness);
        
        // 趋势分析（按月）
        Map<String, Map<String, Object>> monthlyTrend = new TreeMap<>();
        
        for (LiveStream stream : streams) {
            if (stream.getActualStartTime() != null) {
                String month = stream.getActualStartTime().getYear() + "-" 
                        + String.format("%02d", stream.getActualStartTime().getMonthValue());
                
                monthlyTrend.computeIfAbsent(month, k -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("streams", 0);
                    m.put("viewers", 0);
                    m.put("sales", BigDecimal.ZERO);
                    return m;
                });
                
                Map<String, Object> monthData = monthlyTrend.get(month);
                monthData.put("streams", (int) monthData.get("streams") + 1);
                monthData.put("viewers", (int) monthData.get("viewers") 
                        + (stream.getViewerCount() != null ? stream.getViewerCount() : 0));
            }
        }
        
        for (TikTokOrder order : orders) {
            if (order.getCreateTime() != null) {
                String month = order.getCreateTime().getYear() + "-" 
                        + String.format("%02d", order.getCreateTime().getMonthValue());
                
                if (monthlyTrend.containsKey(month)) {
                    Map<String, Object> monthData = monthlyTrend.get(month);
                    monthData.put("sales", ((BigDecimal) monthData.get("sales")).add(order.getOrderAmount()));
                }
            }
        }
        
        report.put("monthlyTrend", monthlyTrend);
        
        return report;
    }
    
    /**
     * 达人对比分析
     */
    public List<Map<String, Object>> compareInfluencers(List<Long> influencerIds) {
        return influencerIds.stream()
                .map(id -> {
                    Map<String, Object> comparison = new HashMap<>();
                    
                    Influencer influencer = influencerRepository.findById(id)
                            .orElse(null);
                    
                    if (influencer == null) {
                        comparison.put("error", "达人不存在: " + id);
                        return comparison;
                    }
                    
                    comparison.put("id", influencer.getId());
                    comparison.put("name", influencer.getName());
                    comparison.put("platform", influencer.getPlatform().name());
                    comparison.put("followerCount", influencer.getFollowerCount());
                    comparison.put("score", getInfluencerScore(id));
                    
                    // 订单统计
                    List<TikTokOrder> orders = orderRepository.findByInfluencerId(id);
                    comparison.put("totalOrders", orders.size());
                    
                    BigDecimal totalGmv = orders.stream()
                            .filter(o -> o.getOrderAmount() != null)
                            .map(TikTokOrder::getOrderAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    comparison.put("totalGmv", totalGmv);
                    
                    // 直播统计
                    List<LiveStream> streams = liveStreamRepository.findByInfluencerId(id);
                    comparison.put("totalStreams", streams.size());
                    
                    int totalViewers = streams.stream()
                            .filter(s -> s.getViewerCount() != null)
                            .mapToInt(LiveStream::getViewerCount)
                            .sum();
                    comparison.put("totalViewers", totalViewers);
                    
                    return comparison;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取达人效果最佳的合作类型
     */
    public Map<String, Object> getBestCooperationType(Long influencerId) {
        List<Cooperation> cooperations = cooperationRepository.findByInfluencerId(influencerId);
        
        if (cooperations.isEmpty()) {
            return Collections.singletonMap("message", "暂无合作数据");
        }
        
        // 按合作类型分组统计
        Map<Cooperation.CooperationType, List<Cooperation>> byType = cooperations.stream()
                .collect(Collectors.groupingBy(Cooperation::getType));
        
        Map<String, Map<String, Object>> typeStats = new HashMap<>();
        
        for (Map.Entry<Cooperation.CooperationType, List<Cooperation>> entry : byType.entrySet()) {
            String typeName = entry.getKey().name();
            List<Cooperation> typeCooperations = entry.getValue();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", typeCooperations.size());
            
            int completed = (int) typeCooperations.stream()
                    .filter(c -> c.getStatus() == Cooperation.CooperationStatus.COMPLETED)
                    .count();
            stats.put("completed", completed);
            
            BigDecimal totalGmv = BigDecimal.ZERO;
            for (Cooperation c : typeCooperations) {
                if (c.getActualGmv() != null) {
                    totalGmv = totalGmv.add(c.getActualGmv());
                }
            }
            stats.put("totalGmv", totalGmv);
            
            typeStats.put(typeName, stats);
        }
        
        // 找出效果最好的类型
        String bestType = null;
        BigDecimal bestGmv = BigDecimal.ZERO;
        
        for (Map.Entry<String, Map<String, Object>> entry : typeStats.entrySet()) {
            BigDecimal gmv = (BigDecimal) entry.getValue().get("totalGmv");
            if (gmv.compareTo(bestGmv) > 0) {
                bestGmv = gmv;
                bestType = entry.getKey();
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("byType", typeStats);
        result.put("bestType", bestType);
        result.put("bestGmv", bestGmv);
        
        return result;
    }
    
    /**
     * 推荐优质达人（基于条件）
     */
    public List<InfluencerDTO> recommendInfluencers(String platform, String category, 
                                                      Integer minFollowers, String level, int limit) {
        List<Influencer> allInfluencers = influencerRepository.findAll();
        
        // 过滤条件
        Stream<Influencer> filtered = allInfluencers.stream();
        
        if (platform != null && !platform.isEmpty()) {
            filtered = filtered.filter(i -> i.getPlatform().name().equals(platform));
        }
        
        if (category != null && !category.isEmpty()) {
            filtered = filtered.filter(i -> i.getCategories() != null 
                    && i.getCategories().contains(category));
        }
        
        if (minFollowers != null) {
            filtered = filtered.filter(i -> i.getFollowerCount() != null 
                    && i.getFollowerCount() >= minFollowers);
        }
        
        if (level != null && !level.isEmpty()) {
            filtered = filtered.filter(i -> i.getLevel() != null 
                    && i.getLevel().name().equals(level));
        }
        
        // 按评分排序
        return filtered
                .sorted((i1, i2) -> getInfluencerScore(i2.getId()).compareTo(getInfluencerScore(i1.getId())))
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    // ========== 私有方法 ==========
    
    private double calculateFollowerScore(Influencer influencer) {
        if (influencer.getFollowerCount() == null) {
            return 0;
        }
        
        int followers = influencer.getFollowerCount();
        
        // 分数计算：0-100
        if (followers >= 1000000) return 100;  // 100万+
        if (followers >= 500000) return 90;    // 50万+
        if (followers >= 100000) return 80;    // 10万+
        if (followers >= 50000) return 70;     // 5万+
        if (followers >= 10000) return 60;     // 1万+
        if (followers >= 5000) return 50;      // 5000+
        return Math.min(40, followers / 200);  // <5000
    }
    
    private double calculateEngagementScore(Influencer influencer) {
        if (influencer.getTotalLikes() == null || influencer.getTotalViews() == null) {
            return 0;
        }
        
        int likes = influencer.getTotalLikes();
        int views = influencer.getTotalViews();
        
        if (views == 0) return 0;
        
        double engagementRate = (double) likes / views * 100;
        
        // 分数计算：0-100
        if (engagementRate >= 10) return 100;
        if (engagementRate >= 5) return 80;
        if (engagementRate >= 3) return 60;
        if (engagementRate >= 1) return 40;
        return engagementRate * 10;
    }
    
    private double calculateCooperationScore(Long influencerId) {
        List<Cooperation> cooperations = cooperationRepository.findByInfluencerId(influencerId);
        
        if (cooperations.isEmpty()) return 50; // 默认中等
        
        int completed = (int) cooperations.stream()
                .filter(c -> c.getStatus() == Cooperation.CooperationStatus.COMPLETED)
                .count();
        
        double completionRate = (double) completed / cooperations.size() * 100;
        
        // 计算GMV达成率
        double gmvAchievement = 0;
        int count = 0;
        
        for (Cooperation c : cooperations) {
            if (c.getExpectedGmv() != null && c.getExpectedGmv().compareTo(BigDecimal.ZERO) > 0
                    && c.getActualGmv() != null) {
                gmvAchievement += c.getActualGmv().divide(c.getExpectedGmv(), 4, RoundingMode.HALF_UP).doubleValue() * 100;
                count++;
            }
        }
        
        double avgGmvAchievement = count > 0 ? gmvAchievement / count : 100;
        
        // 综合分数 = 完成率40% + GMV达成率60%
        return completionRate * 0.4 + avgGmvAchievement * 0.6;
    }
    
    private double calculateSalesScore(Long influencerId) {
        List<TikTokOrder> orders = orderRepository.findByInfluencerId(influencerId);
        
        if (orders.isEmpty()) return 0;
        
        BigDecimal totalGmv = orders.stream()
                .filter(o -> o.getOrderAmount() != null)
                .map(TikTokOrder::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 分数计算：基于GMV
        double gmv = totalGmv.doubleValue();
        
        if (gmv >= 1000000) return 100;
        if (gmv >= 500000) return 90;
        if (gmv >= 100000) return 80;
        if (gmv >= 50000) return 70;
        if (gmv >= 10000) return 60;
        if (gmv >= 5000) return 50;
        
        return Math.min(40, gmv / 200);
    }
    
    private InfluencerDTO toDTO(Influencer entity) {
        return InfluencerDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .platformId(entity.getPlatformId())
                .platform(entity.getPlatform().name())
                .avatarUrl(entity.getAvatarUrl())
                .followerCount(entity.getFollowerCount())
                .level(entity.getLevel() != null ? entity.getLevel().name() : null)
                .status(entity.getStatus().name())
                .categories(entity.getCategories())
                .build();
    }
}