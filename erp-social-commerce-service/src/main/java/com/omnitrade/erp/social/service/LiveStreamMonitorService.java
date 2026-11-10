package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.client.TikTokApiClient;
import com.omnitrade.erp.social.dto.LiveStreamDTO;
import com.omnitrade.erp.social.model.LiveStream;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.LiveStreamRepository;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TikTok 直播状态监听服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamMonitorService {
    
    private final LiveStreamRepository liveStreamRepository;
    private final TikTokShopConfigRepository shopConfigRepository;
    private final TikTokApiClient apiClient;
    private final LiveStreamService liveStreamService;
    
    /**
     * 监控所有活跃店铺的直播状态
     */
    public void monitorAllLiveStreams() {
        List<TikTokShopConfig> activeShops = shopConfigRepository.findByStatus(TikTokShopConfig.ConfigStatus.ACTIVE);
        for (TikTokShopConfig shop : activeShops) {
            try {
                monitorLiveStream(shop);
            } catch (Exception e) {
                log.error("监控直播状态失败, shopId: {}", shop.getShopId(), e);
            }
        }
    }
    
    /**
     * 监控单个店铺的直播状态
     */
    @Transactional
    public void monitorLiveStream(TikTokShopConfig shop) {
        log.info("开始监控TikTok直播状态, shopId: {}", shop.getShopId());
        
        // 检查token是否过期
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        try {
            // 获取当前直播状态
            TikTokApiClient.TikTokLiveStatusResponse statusResponse = apiClient.getLiveStatus(
                    shop.getAccessToken(), shop.getShopId());
            
            if (statusResponse.getData() != null) {
                TikTokApiClient.TikTokLiveStatusData statusData = statusResponse.getData();
                
                // 根据直播状态更新本地记录
                String liveId = statusData.getLiveId();
                String status = statusData.getStatus();
                
                if (liveId != null) {
                    // 查找本地是否有对应的直播记录
                    LiveStream existingStream = liveStreamRepository.findByStreamId(liveId)
                            .orElse(null);
                    
                    if (existingStream != null) {
                        // 更新现有记录
                        updateLiveStreamStatus(existingStream, statusData);
                    } else {
                        // 创建新记录（如果是直播中）
                        if ("LIVE".equals(status)) {
                            createLiveStreamRecord(shop, statusData);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取直播状态失败, shopId: {}", shop.getShopId(), e);
        }
    }
    
    /**
     * 同步直播数据（观众数、销售额等）
     */
    @Transactional
    public LiveStreamDTO syncLiveStreamData(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
        
        if (liveStream.getStreamId() == null) {
            throw new BizException("直播streamId不存在，无法同步数据");
        }
        
        TikTokShopConfig shop = shopConfigRepository.findByShopId(liveStream.getPlatform().name())
                .orElseThrow(() -> new RuntimeException("店铺配置不存在"));
        
        if (isTokenExpired(shop)) {
            refreshToken(shop);
        }
        
        // 📌 **数据库写入必须放在 try 之外**（规则 14）：
        //    若 `liveStreamRepository.save(...)` 在 try 里、catch 又把 `e.getMessage()`
        //    塞进 RuntimeException → 抛出去的是**普通 RuntimeException**（不在黑名单里）
        //    → 黑名单完全失效 → **SQL / 表名 / 列名直达前端**。
        //    实测（2026-11-10）：本项目正是这个形态（闸门 check-internal-detail-leak.py 抓到）。
        // 📌 **上游 API 的消息仍然透出**（规则 13）：平台拒绝了这次同步时，
        //    用户需要知道真实原因，而不是一句「系统繁忙」。
        TikTokApiClient.TikTokLiveStatsData statsData;
        try {
            // 获取直播统计数据
            TikTokApiClient.TikTokLiveStatsResponse statsResponse = apiClient.getLiveStats(
                    shop.getAccessToken(), shop.getShopId(), liveStream.getStreamId());
            if (statsResponse.getData() == null) {
                return liveStreamService.toDTO(liveStream);
            }
            statsData = statsResponse.getData();
        } catch (Exception e) {
            log.error("同步直播数据失败, liveStreamId: {}", liveStreamId, e);
            throw new RuntimeException("同步直播数据失败: " + e.getMessage(), e);
        }

        liveStream.setViewerCount(statsData.getViewerCount());
        liveStream.setPeakViewers(statsData.getPeakViewers());
        liveStream.setLikeCount(statsData.getLikeCount());
        liveStream.setShareCount(statsData.getShareCount());
        liveStream.setCommentCount(statsData.getCommentCount());
        liveStream.setNewFollowers(statsData.getNewFollowers());
        liveStream.setTotalSales(BigDecimal.valueOf(statsData.getTotalSales()));
        liveStream.setTotalOrders(statsData.getTotalOrders());
        liveStream.setTotalGmv(BigDecimal.valueOf(statsData.getTotalGmv()));

        LiveStream saved = liveStreamRepository.save(liveStream);
        return liveStreamService.toDTO(saved);
    }
    
    /**
     * 定时监控任务 - 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void scheduledMonitor() {
        log.debug("开始定时监控TikTok直播状态");
        try {
            monitorAllLiveStreams();
            
            // 同时更新正在进行中的直播数据
            updateLiveStreamsData();
        } catch (Exception e) {
            log.error("定时监控直播失败", e);
        }
    }
    
    /**
     * 更新所有直播中的直播数据
     */
    private void updateLiveStreamsData() {
        List<LiveStream> liveStreams = liveStreamRepository.findCurrentLiveStreams();
        for (LiveStream stream : liveStreams) {
            try {
                syncLiveStreamData(stream.getId());
            } catch (Exception e) {
                log.error("更新直播数据失败, liveStreamId: {}", stream.getId(), e);
            }
        }
    }
    
    /**
     * 获取直播数据统计
     */
    public Map<String, Object> getLiveStreamStats(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("viewerCount", liveStream.getViewerCount());
        stats.put("peakViewers", liveStream.getPeakViewers());
        stats.put("likeCount", liveStream.getLikeCount());
        stats.put("shareCount", liveStream.getShareCount());
        stats.put("commentCount", liveStream.getCommentCount());
        stats.put("newFollowers", liveStream.getNewFollowers());
        stats.put("totalSales", liveStream.getTotalSales());
        stats.put("totalOrders", liveStream.getTotalOrders());
        stats.put("totalGmv", liveStream.getTotalGmv());
        
        // 计算转化率
        if (liveStream.getViewerCount() != null && liveStream.getViewerCount() > 0 
                && liveStream.getTotalOrders() != null) {
            double conversionRate = (double) liveStream.getTotalOrders() / liveStream.getViewerCount() * 100;
            stats.put("conversionRate", String.format("%.2f%%", conversionRate));
        }
        
        // 计算客单价
        if (liveStream.getTotalOrders() != null && liveStream.getTotalOrders() > 0 
                && liveStream.getTotalSales() != null) {
            double avgOrderValue = liveStream.getTotalSales().doubleValue() / liveStream.getTotalOrders();
            stats.put("avgOrderValue", String.format("%.2f", avgOrderValue));
        }
        
        return stats;
    }
    
    /**
     * 获取直播效果分析
     */
    public Map<String, Object> getLiveStreamAnalytics(Long liveStreamId, Long influencerId) {
        Map<String, Object> analytics = new HashMap<>();
        
        if (liveStreamId != null) {
            LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                    .orElseThrow(() -> new RuntimeException("直播不存在: " + liveStreamId));
            
            analytics.put("duration", liveStream.getDuration());
            analytics.put("viewerCount", liveStream.getViewerCount());
            analytics.put("peakViewers", liveStream.getPeakViewers());
            analytics.put("likeCount", liveStream.getLikeCount());
            analytics.put("shareCount", liveStream.getShareCount());
            analytics.put("commentCount", liveStream.getCommentCount());
            analytics.put("newFollowers", liveStream.getNewFollowers());
            analytics.put("totalSales", liveStream.getTotalSales());
            analytics.put("totalOrders", liveStream.getTotalOrders());
            analytics.put("totalGmv", liveStream.getTotalGmv());
            
            // ROI计算
            if (liveStream.getTotalGmv() != null) {
                analytics.put("gmv", liveStream.getTotalGmv());
            }
        }
        
        if (influencerId != null) {
            // 获取该达人的历史直播数据
            List<LiveStream> influencerStreams = liveStreamRepository.findByInfluencerId(influencerId);
            
            int totalStreams = influencerStreams.size();
            int totalViewers = influencerStreams.stream()
                    .filter(s -> s.getViewerCount() != null)
                    .mapToInt(LiveStream::getViewerCount)
                    .sum();
            BigDecimal totalGmv = influencerStreams.stream()
                    .filter(s -> s.getTotalGmv() != null)
                    .map(LiveStream::getTotalGmv)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            analytics.put("totalStreams", totalStreams);
            analytics.put("avgViewers", totalStreams > 0 ? totalViewers / totalStreams : 0);
            analytics.put("totalGmv", totalGmv);
            analytics.put("avgGmv", totalStreams > 0 ? totalGmv.divide(BigDecimal.valueOf(totalStreams), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
        }
        
        return analytics;
    }
    
    // ========== 私有方法 ==========
    
    private void updateLiveStreamStatus(LiveStream liveStream, TikTokApiClient.TikTokLiveStatusData statusData) {
        String status = statusData.getStatus();
        
        switch (status) {
            case "LIVE":
                if (liveStream.getStatus() != LiveStream.StreamStatus.LIVE) {
                    liveStream.setStatus(LiveStream.StreamStatus.LIVE);
                    if (statusData.getActualStartTime() != null) {
                        long startTime = Long.parseLong(statusData.getActualStartTime());
                        liveStream.setActualStartTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochSecond(startTime), ZoneId.systemDefault()));
                    }
                }
                break;
            case "ENDED":
                if (liveStream.getStatus() == LiveStream.StreamStatus.LIVE) {
                    liveStream.setStatus(LiveStream.StreamStatus.ENDED);
                    liveStream.setEndTime(LocalDateTime.now());
                    if (liveStream.getActualStartTime() != null) {
                        liveStream.setDuration((int) java.time.Duration.between(
                                liveStream.getActualStartTime(), liveStream.getEndTime()).toMinutes());
                    }
                }
                break;
            case "SCHEDULED":
                if (statusData.getScheduledStartTime() != null) {
                    long scheduledTime = Long.parseLong(statusData.getScheduledStartTime());
                    liveStream.setScheduledStartTime(LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(scheduledTime), ZoneId.systemDefault()));
                }
                break;
        }
        
        liveStreamRepository.save(liveStream);
    }
    
    private void createLiveStreamRecord(TikTokShopConfig shop, TikTokApiClient.TikTokLiveStatusData statusData) {
        LiveStream liveStream = LiveStream.builder()
                .streamId(statusData.getLiveId())
                .title("TikTok直播_" + statusData.getLiveId())
                .platform(LiveStream.Platform.TIKTOK)
                .status(LiveStream.StreamStatus.LIVE)
                .actualStartTime(LocalDateTime.now())
                .build();
        
        liveStreamRepository.save(liveStream);
        log.info("创建直播记录, streamId: {}", statusData.getLiveId());
    }
    
    private boolean isTokenExpired(TikTokShopConfig shop) {
        if (shop.getTokenExpireTime() == null) {
            return true;
        }
        return shop.getTokenExpireTime().isBefore(LocalDateTime.now().plusHours(1));
    }
    
    private void refreshToken(TikTokShopConfig shop) {
        if (shop.getRefreshToken() == null) {
            throw new BizException("Refresh Token不存在，无法刷新");
        }
        
        try {
            TikTokApiClient.TokenResponse tokenResponse = apiClient.refreshAccessToken(shop.getRefreshToken());
            shop.setAccessToken(tokenResponse.getAccessToken());
            shop.setRefreshToken(tokenResponse.getRefreshToken());
            shop.setTokenExpireTime(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
            shopConfigRepository.save(shop);
            log.info("Token刷新成功, shopId: {}", shop.getShopId());
        } catch (Exception e) {
            log.error("Token刷新失败, shopId: {}", shop.getShopId(), e);
            shop.setStatus(TikTokShopConfig.ConfigStatus.ERROR);
            shopConfigRepository.save(shop);
        }
    }
}