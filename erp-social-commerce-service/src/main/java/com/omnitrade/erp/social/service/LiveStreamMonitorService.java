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
    // 📌 **noRollbackFor = BizException.class**（2026-11-10 22:45 互审发现）：
    //    `refreshToken` 失败时会 ① 把店铺标记成 ERROR ② 抛 BizException。
    //    BizException extends RuntimeException → **默认触发回滚** →
    //    ① 的标记被**撤销** → 店铺状态**永远留在 ACTIVE** →
    //    下一轮 `findByStatus(ACTIVE)` 又会取它 → **又失败 → 死循环 + 每 30 秒一条 error**。
    //    → 「降级事实必须真的落地」（规则 18 / 19）。
    @Transactional(noRollbackFor = BizException.class)
    public LiveStreamDTO syncLiveStreamData(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new BizException("直播不存在: " + liveStreamId));  // 📌 BizException 而非 RuntimeException：业务问题 → HTTP 400，不是「服务端故障」500（规则 28）
        
        if (liveStream.getStreamId() == null) {
            throw new BizException("直播streamId不存在，无法同步数据");
        }
        
        // 📌 **平台分派**（2026-11-10 实测修复）：
        //    本方法走的是 **TikTok 专属** API（apiClient = TikTokApiClient），
        //    而 live_stream 里还有 DOUYIN / TAOBAO_LIVE 的记录。
        //    之前不判平台 → 拿 TikTok 的店铺配置去同步抖音直播 → 必然失败。
        //    ⚠️ 这里**必须显式拒绝**而不是默默用错平台的数据（规则 12：静默失败）。
        if (liveStream.getPlatform() != LiveStream.Platform.TIKTOK) {
            throw new BizException("当前仅支持 TikTok 直播数据同步，"
                    + liveStream.getPlatform() + " 平台暂不支持");
        }
        
        // 📌 **店铺定位**（2026-11-10 实测修复）：
        //    之前写的是 `findByShopId(liveStream.getPlatform().name())` ——
        //    即拿 **平台枚举名**（'TIKTOK'）去查 **店铺 ID** 列。
        //    真实 MySQL 实测：`SELECT COUNT(*) FROM tiktok_shop_config WHERE shop_id='TIKTOK'` → **0 行**
        //    （真值是 'SHOP-TT-0001'）→ **每次同步都抛「店铺配置不存在」**
        //    → 被定时任务 catch 吞掉 → **直播数据永远不更新**（规则 12 / 18）。
        TikTokShopConfig shop = resolveShopConfig(liveStream);
        
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
            // 📌 **只同步 TikTok**（2026-11-10 实测修复）：
            //    其它平台没有对应的 API 客户端 → 调了也只会每次都报错 →
            //    每 30 秒刷一次 error 日志（定时任务 fixedRate=30000），
            //    把真正的失败**淹没在噪音里**（规则 43：WARN 级别的「没查到」= 虚假安全感）。
            //    ⚠️ 跳过是**诚实的**：不假装同步过，也不制造假告警。
            if (stream.getPlatform() != LiveStream.Platform.TIKTOK) {
                log.debug("跳过非 TikTok 直播的数据同步, liveStreamId: {}, platform: {}",
                        stream.getId(), stream.getPlatform());
                continue;
            }
            try {
                syncLiveStreamData(stream.getId());
            } catch (Exception e) {
                log.error("更新直播数据失败, liveStreamId: {}", stream.getId(), e);
            }
        }
    }
    
    /**
     * 📌 **定位直播所属的 TikTok 店铺**（2026-11-10 实测修复）
     *
     * <p><b>主路径</b>：{@code live_stream.shop_id} → {@code findByShopId(shopId)}
     * <br><b>存量降级</b>：{@code shop_id} 为空（列是后加的）→ 按平台找 ACTIVE 店铺；
     * <b>恰好一个</b> → 用它并**回填** {@code shop_id}；
     * <b>零个或多个</b> → 抛 <b>BizException</b>，**不瞎猜**。
     *
     * <p>📌 **降级判据是「这个值还能不能用」而不是「值是不是空」**（规则 57）：
     * 一个 ACTIVE 店铺 = 可用 → 可用就回填；两个 = **无法判定是哪个** → 宁可报错也不能猜。
     * <p>📌 **降级必须带原因且调用方能看见**（规则 19）：回填时打 info 日志说明为什么。
     */
    private TikTokShopConfig resolveShopConfig(LiveStream liveStream) {
        String shopId = liveStream.getShopId();
        
        if (shopId != null && !shopId.isBlank()) {
            return shopConfigRepository.findByShopId(shopId)
                    .orElseThrow(() -> new BizException(
                            "店铺配置不存在: shopId=" + shopId
                            + "（直播记录 id=" + liveStream.getId() + "）"));
        }
        
        // 存量数据：shop_id 为空 → 按平台回退
        List<TikTokShopConfig> activeShops = shopConfigRepository
                .findByStatus(TikTokShopConfig.ConfigStatus.ACTIVE);
        if (activeShops.size() != 1) {
            throw new BizException("直播记录未关联店铺，且当前有 " + activeShops.size()
                    + " 个 ACTIVE 店铺，无法判定归属（直播记录 id=" + liveStream.getId() + "）");
        }
        TikTokShopConfig fallback = activeShops.get(0);
        log.info("直播记录缺少 shop_id，按唯一 ACTIVE 店铺回退并回填: liveStreamId={}, shopId={}",
                liveStream.getId(), fallback.getShopId());
        liveStream.setShopId(fallback.getShopId());
        return fallback;
    }
    
    /**
     * 获取直播数据统计
     */
    public Map<String, Object> getLiveStreamStats(Long liveStreamId) {
        LiveStream liveStream = liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new BizException("直播不存在: " + liveStreamId));  // 📌 BizException 而非 RuntimeException：业务问题 → HTTP 400，不是「服务端故障」500（规则 28）
        
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
                    .orElseThrow(() -> new BizException("直播不存在: " + liveStreamId));  // 📌 BizException 而非 RuntimeException：业务问题 → HTTP 400，不是「服务端故障」500（规则 28）
            
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
            // 📌 **不能用 int 除法**（2026-11-10 22:45 互审发现）：
            //    `totalViewers / totalStreams` 全是 int → **整数除法截断**
            //    → 3 个观众 2 场直播 → 1（真实值 1.5）→ 报表**少报一半**。
            //    📌 而且 `avgGmv` 是 BigDecimal、`avgViewers` 却是 Integer → **同一张表两种精度语义**。
            analytics.put("avgViewers", totalStreams > 0
                    ? BigDecimal.valueOf(totalViewers)
                            .divide(BigDecimal.valueOf(totalStreams), 2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO);
            analytics.put("totalGmv", totalGmv);
            analytics.put("avgGmv", totalStreams > 0 ? totalGmv.divide(BigDecimal.valueOf(totalStreams), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
        }
        
        return analytics;
    }
    
    // ========== 私有方法 ==========
    
    private void updateLiveStreamStatus(LiveStream liveStream, TikTokApiClient.TikTokLiveStatusData statusData) {
        String status = statusData.getStatus();

        // 📌 **status 可能为 null**（平台在某些状态下不返回 status）：
        //    `switch (status)` 遇到 null 会抛 **NullPointerException**
        //    → 被 `monitorLiveStream` 的 catch 吞掉 → **这一轮监控白跑**
        //    → 而且日志上看起来是「网络失败」，实际是「字段缺失」（规则 12：别把两件事混成一件）
        if (status == null) {
            log.warn("直播状态返回缺少 status 字段, streamId: {}, 跳过状态更新",
                    liveStream.getStreamId());
            return;
        }

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
                // 📌 **2026-11-10 修复**：原来 `shop` 参数**完全没被使用** ——
                //    店铺归属在写入时就丢了 → 直播记录无法定位到店铺
                //    → syncLiveStreamData 只能瞎猜（它确实瞎猜了：拿 platform.name() 当 shopId）。
                .shopId(shop.getShopId())
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

        // 📌 **刷新失败必须抛出去，不能吞掉**（2026-11-10 22:45 互审发现）：
        //    之前 catch 里只 log + 标 ERROR 就**返回了** → 调用方
        //    （`monitorLiveStream` / `syncLiveStreamData`）**继续用那个过期 token 去调 API**
        //    → 必然 401 → 再被外层 catch 吞掉 → **每 30 秒（fixedRate=30000）刷一条 error**
        //    → 真正的故障被**噪音淹没**（规则 43）。
        // 📌 **同一模块两种口径**（规则 44 / 107）：`LiveProductService.refreshToken`
        //    失败时**会抛** → 本方法必须**对齐**，否则同一概念两种行为。
        try {
            TikTokApiClient.TokenResponse tokenResponse = apiClient.refreshAccessToken(shop.getRefreshToken());
            shop.setAccessToken(tokenResponse.getAccessToken());
            shop.setRefreshToken(tokenResponse.getRefreshToken());
            shop.setTokenExpireTime(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
            shopConfigRepository.save(shop);
            log.info("Token刷新成功, shopId: {}", shop.getShopId());
        } catch (Exception e) {
            log.error("Token刷新失败, shopId: {}", shop.getShopId(), e);
            // 📌 **标记状态也要走 DB，但它自己失败时不能盖掉原始异常**（否则根因丢失）
            try {
                shop.setStatus(TikTokShopConfig.ConfigStatus.ERROR);
                shopConfigRepository.save(shop);
            } catch (Exception dbEx) {
                log.error("标记店铺状态失败, shopId: {}", shop.getShopId(), dbEx);
            }
            // ⚠️ 用 BizException（业务问题 → 400）而不是 RuntimeException（→ 500）
            //    理由同 `LiveProductService`：这是「token 暂时刷不了」，不是「服务端故障」。
            throw new BizException("Token刷新失败: " + e.getMessage());
        }
    }
}