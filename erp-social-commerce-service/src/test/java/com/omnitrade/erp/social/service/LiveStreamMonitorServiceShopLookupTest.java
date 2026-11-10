package com.omnitrade.erp.social.service;

import com.omnitrade.erp.social.client.TikTokApiClient;
import com.omnitrade.erp.social.model.LiveStream;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.LiveStreamRepository;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 🔴 **回归测试**：直播数据同步的店铺定位（2026-11-10 实测缺陷）
 *
 * <p><b>缺陷本体</b>：{@code LiveStreamMonitorService.syncLiveStreamData()} 写的是
 * <pre>shopConfigRepository.findByShopId(liveStream.getPlatform().name())</pre>
 * 即拿**平台枚举名**（{@code 'TIKTOK'}）去查**店铺 ID** 列。
 *
 * <p><b>真实 MySQL 实测</b>（{@code scripts/start-audit-mysql.sh} 起的 MySQL 8.0）：
 * <pre>
 *   SELECT COUNT(*) FROM tiktok_shop_config WHERE shop_id='TIKTOK';       → 0   ← 代码传的值
 *   SELECT COUNT(*) FROM tiktok_shop_config WHERE shop_id='SHOP-TT-0001'; → 1   ← 真值
 * </pre>
 * → **每一次同步都必然抛「店铺配置不存在」** → 被定时任务 catch 吞掉 →
 * **直播数据永远不更新**（规则 12 / 18：静默失败）。
 *
 * <p><b>本测试用真实 JPA + H2</b>（不是纯 Mockito）——
 * 因为纯 mock 会**把缺陷一起 mock 掉**（规则 48：别写「假测试」）。
 * 只 mock <b>外部 TikTok API</b>（{@code apiClient}），数据库和仓储全是真的。
 *
 * <p>📌 **修复前的表现**：用例 1 会抛「店铺配置不存在」→ 红；
 * <p>📌 **修复后的表现**：用例 1 绿，且 viewerCount 被真正写入。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("直播数据同步：店铺定位（真实 JPA + H2）")
class LiveStreamMonitorServiceShopLookupTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private TikTokShopConfigRepository shopConfigRepository;

    /** 外部 TikTok API —— 唯一被 mock 的东西（它真的在网络另一头）。 */
    @MockBean
    private TikTokApiClient apiClient;

    /** DTO 转换器 —— 与本用例无关，纯 mock。 */
    @MockBean
    private LiveStreamService liveStreamService;

    private LiveStreamMonitorService service;

    @BeforeEach
    void setUp() {
        // 构造器顺序 = 字段声明顺序（@RequiredArgsConstructor）
        service = new LiveStreamMonitorService(
                liveStreamRepository, shopConfigRepository, apiClient, liveStreamService);
    }

    private TikTokShopConfig persistShop(String shopId, TikTokShopConfig.ConfigStatus status) {
        TikTokShopConfig cfg = TikTokShopConfig.builder()
                .shopName("店铺-" + shopId)
                .shopId(shopId)
                .accessToken("tt-access-" + shopId)
                .refreshToken("tt-refresh-" + shopId)
                .tokenExpireTime(LocalDateTime.now().plusDays(60))
                .appKey("tt-app-key")
                .appSecret("tt-app-secret")
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(cfg);
        entityManager.flush();
        entityManager.clear();
        return cfg;
    }

    private LiveStream persistStream(String streamId, LiveStream.Platform platform, String shopId) {
        LiveStream s = LiveStream.builder()
                .streamId(streamId)
                .title("直播-" + streamId)
                .platform(platform)
                .shopId(shopId)
                .status(LiveStream.StreamStatus.LIVE)
                .actualStartTime(LocalDateTime.now())
                .build();
        entityManager.persist(s);
        entityManager.flush();
        entityManager.clear();
        return s;
    }

    private void mockStats(int viewers) {
        TikTokApiClient.TikTokLiveStatsData data = new TikTokApiClient.TikTokLiveStatsData();
        data.setViewerCount(viewers);
        data.setPeakViewers(viewers + 10);
        data.setLikeCount(100);
        data.setShareCount(20);
        data.setCommentCount(5);
        data.setNewFollowers(3);
        data.setTotalSales(1000.0);
        data.setTotalOrders(10);
        data.setTotalGmv(1200.0);
        TikTokApiClient.TikTokLiveStatsResponse resp = new TikTokApiClient.TikTokLiveStatsResponse();
        resp.setData(data);
        when(apiClient.getLiveStats(anyString(), anyString(), anyString())).thenReturn(resp);
    }

    // ─────────────────────────── 根因锚点 ───────────────────────────

    @Test
    @DisplayName("A. 根因锚点：库里存的是 'SHOP-TT-0001'，而 'TIKTOK' 查不到")
    void platformNameIsNotAShopId() {
        persistShop("SHOP-TT-0001", TikTokShopConfig.ConfigStatus.ACTIVE);

        assertTrue(shopConfigRepository.findByShopId("SHOP-TT-0001").isPresent(),
                "真店铺 ID 必须查得到");
        assertFalse(shopConfigRepository.findByShopId("TIKTOK").isPresent(),
                "平台枚举名当店铺 ID 必然查不到 —— 这就是缺陷的根因");
    }

    // ─────────────────────── 修复后的正常路径 ───────────────────────

    @Test
    @DisplayName("B. 修复后：按 shop_id 定位到店铺，同步成功且统计被真正写入")
    void syncWithShopId_succeeds() {
        persistShop("SHOP-TT-0001", TikTokShopConfig.ConfigStatus.ACTIVE);
        LiveStream stream = persistStream("LIVE-T1", LiveStream.Platform.TIKTOK, "SHOP-TT-0001");
        mockStats(555);

        service.syncLiveStreamData(stream.getId());

        LiveStream saved = liveStreamRepository.findById(stream.getId()).orElseThrow();
        assertEquals(555, saved.getViewerCount(), "统计必须真的写进库里");
        assertEquals(565, saved.getPeakViewers());
        assertEquals("SHOP-TT-0001", saved.getShopId());
    }

    // ─────────────────────── 存量数据降级路径 ───────────────────────

    @Test
    @DisplayName("C. 存量数据（shop_id 为空）+ 唯一 ACTIVE 店铺 → 回退成功并回填 shop_id")
    void legacyRecordWithoutShopId_fallsBackAndBackfills() {
        persistShop("SHOP-TT-0001", TikTokShopConfig.ConfigStatus.ACTIVE);
        LiveStream stream = persistStream("LIVE-LEGACY", LiveStream.Platform.TIKTOK, null);
        mockStats(777);

        service.syncLiveStreamData(stream.getId());

        LiveStream saved = liveStreamRepository.findById(stream.getId()).orElseThrow();
        assertEquals(777, saved.getViewerCount());
        assertEquals("SHOP-TT-0001", saved.getShopId(),
                "存量行必须被回填 shop_id（否则每 30 秒还要重算一次回退）");
    }

    @Test
    @DisplayName("D. 存量数据 + 多个 ACTIVE 店铺 → 抛 BizException，不瞎猜")
    void legacyRecordWithMultipleActiveShops_refusesToGuess() {
        persistShop("SHOP-TT-0001", TikTokShopConfig.ConfigStatus.ACTIVE);
        persistShop("SHOP-TT-0002", TikTokShopConfig.ConfigStatus.ACTIVE);
        LiveStream stream = persistStream("LIVE-LEGACY-2", LiveStream.Platform.TIKTOK, null);

        Exception ex = assertThrows(Exception.class, () -> service.syncLiveStreamData(stream.getId()));
        assertTrue(ex.getMessage().contains("无法判定归属"),
                "必须明确说出「无法判定归属」，而不是随便挑一个店铺（规则 51 / 57）");
    }

    // ─────────────────────── 平台分派 ───────────────────────

    @Test
    @DisplayName("E. 非 TikTok 平台 → 明确拒绝，不用 TikTok 的店铺配置去同步")
    void nonTikTokPlatform_isRejectedExplicitly() {
        persistShop("SHOP-TT-0001", TikTokShopConfig.ConfigStatus.ACTIVE);
        LiveStream stream = persistStream("LIVE-DY", LiveStream.Platform.DOUYIN, "SHOP-DY-0001");
        mockStats(999);

        Exception ex = assertThrows(Exception.class, () -> service.syncLiveStreamData(stream.getId()));
        assertTrue(ex.getMessage().contains("仅支持 TikTok"),
                "必须说明「当前仅支持 TikTok」，而不是默默用错平台的数据（规则 12）");
    }

    @Test
    @DisplayName("F. 直播记录能存能读 shopId（实体 ↔ 表结构一致）")
    void liveStreamPersistsShopId() {
        persistShop("SHOP-TT-0001", TikTokShopConfig.ConfigStatus.ACTIVE);
        LiveStream stream = persistStream("LIVE-T2", LiveStream.Platform.TIKTOK, "SHOP-TT-0001");
        entityManager.clear();

        Optional<LiveStream> found = liveStreamRepository.findById(stream.getId());
        assertTrue(found.isPresent());
        assertEquals("SHOP-TT-0001", found.get().getShopId());
    }
}
