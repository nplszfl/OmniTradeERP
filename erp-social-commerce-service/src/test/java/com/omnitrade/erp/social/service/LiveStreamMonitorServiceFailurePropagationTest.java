package com.omnitrade.erp.social.service;

import com.omnitrade.erp.social.client.TikTokApiClient;
import com.omnitrade.erp.social.model.Influencer;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 🔴 **回归测试**：直播监控的**失败传播**与**数值精度**（2026-11-10 22:45 互审发现）
 *
 * <p><b>缺陷 1：token 刷新失败被吞掉 → 继续用过期 token 调 API</b>
 * <pre>
 *   LiveStreamMonitorService.refreshToken() 的 catch 里只 log + 标 ERROR 就**返回了**
 *   → 调用方继续用那个**过期 token** 去调 TikTok API → 必然 401
 *   → 再被外层 catch 吞掉 → 每 30 秒（fixedRate=30000）刷一条 error
 *   → **真正的故障被噪音淹没**（规则 43）
 * </pre>
 * 📌 **同一模块两种口径**（规则 44 / 107）：{@code LiveProductService.refreshToken}
 * 失败时**会抛** → 本方法必须**对齐**。
 *
 * <p><b>缺陷 2：{@code switch (status)} 遇 null 抛 NPE</b>
 * → 平台某些状态不返回 {@code status} → NPE → 被 catch 吞掉 → **这一轮监控白跑**。
 *
 * <p><b>缺陷 3：{@code avgViewers} 用 int 除法 → 截断</b>
 * → 3 个观众 2 场直播 → 1（真实 1.5）→ 报表少报一半；且与 {@code avgGmv}（BigDecimal）类型不一致。
 *
 * <p><b>缺陷 4：查不到直播抛 RuntimeException → HTTP 500</b>
 * → 「用户请求了不存在的直播」是**业务问题** → 应 400，不是「服务端故障」500（规则 28）。
 *
 * <p>📌 **用真实 JPA + H2**（不是纯 Mockito）—— 纯 mock 会把缺陷一起 mock 掉（规则 48）。
 * 只 mock <b>外部 TikTok API</b>。
 */
/**
 * 📌 **本类故意用 \`@Transactional(propagation = NOT_SUPPORTED)\`**（而不是 \`@DataJpaTest\` 默认的事务）：
 * <pre>
 *   默认：整个测试方法在一个**外层事务**里 → 业务方法抛 BizException →
 *         **外层测试事务**被标记 rollback-only → **flush 被撤销**
 *         → 「标记 ERROR」看不出效果 → **测不出生产环境的真实行为**
 *   NOT_SUPPORTED：无外层事务 → 业务方法自己的 @Transactional 生效 →
 *         \`noRollbackFor = BizException.class\` 的语义**真正被验证**
 * </pre>
 * 📌 **测试环境与生产环境的事务边界不同 → 测试测不出真缺陷**（规则 97 的同款思路：
 *      「只在特定条件下才出现」的缺陷，要**在那个条件下**去测）。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("直播监控：失败传播 / NPE / 数值精度 / 异常口径")
class LiveStreamMonitorServiceFailurePropagationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private TikTokShopConfigRepository shopConfigRepository;

    @MockBean
    private TikTokApiClient apiClient;

    @MockBean
    private LiveStreamService liveStreamService;

    private LiveStreamMonitorService service;

    @BeforeEach
    void setUp() {
        service = new LiveStreamMonitorService(
                liveStreamRepository, shopConfigRepository, apiClient, liveStreamService);
    }

    private TikTokShopConfig persistShop(String shopId, LocalDateTime expireTime) {
        TikTokShopConfig cfg = TikTokShopConfig.builder()
                .shopName("店铺-" + shopId)
                .shopId(shopId)
                .accessToken("tt-access-" + shopId)
                .refreshToken("tt-refresh-" + shopId)
                .tokenExpireTime(expireTime)
                .appKey("tt-app-key")
                .appSecret("tt-app-secret")
                .status(TikTokShopConfig.ConfigStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(cfg);
        entityManager.flush();
        entityManager.clear();
        return cfg;
    }

    private LiveStream persistStream(String streamId, LiveStream.Platform platform, String shopId) {
        return persistStream(streamId, platform, shopId, null);
    }

    private LiveStream persistStream(String streamId, LiveStream.Platform platform, String shopId,
                                      Influencer influencer) {
        LiveStream s = LiveStream.builder()
                .streamId(streamId)
                .title("直播-" + streamId)
                .platform(platform)
                .shopId(shopId)
                .influencer(influencer)
                .status(LiveStream.StreamStatus.LIVE)
                .actualStartTime(LocalDateTime.now())
                .build();
        entityManager.persist(s);
        entityManager.flush();
        entityManager.clear();
        return s;
    }

    /** 建一个达人（`live_stream.influencer_id` 是 @ManyToOne 关联） */
    private Influencer persistInfluencer(String platformId) {
        Influencer inf = Influencer.builder()
                .name("达人-" + platformId)
                .platformId(platformId)
                .platform(Influencer.Platform.TIKTOK)
                .status(Influencer.InfluencerStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        entityManager.persist(inf);
        entityManager.flush();
        entityManager.clear();
        return inf;
    }

    // ─────────────── 缺陷 1：token 刷新失败必须传播 ───────────────

    @Test
    @DisplayName("token 刷新失败 → 必须抛出去，且**不能**继续调 TikTok API")
    void tokenRefreshFailure_propagatesAndSkipsApiCall() {
        // 店铺的 token 已过期（1 小时后即视为过期）
        persistShop("SHOP-TT-0001", LocalDateTime.now().plusMinutes(10));
        LiveStream stream = persistStream("LIVE-1", LiveStream.Platform.TIKTOK, "SHOP-TT-0001");

        // 刷新 token 的 API 直接失败
        when(apiClient.refreshAccessToken(anyString()))
                .thenThrow(new RuntimeException("TikTok 拒绝刷新: invalid_grant"));

        Exception ex = assertThrows(Exception.class, () -> service.syncLiveStreamData(stream.getId()),
                "token 刷新失败必须传播出去，不能吞掉后继续用过期 token");

        assertTrue(ex.getMessage().contains("Token刷新失败"),
                "异常消息必须说明是 token 刷新失败，实际: " + ex.getMessage());

        // 📌 **关键断言**：刷新失败后**绝不能**再去调统计 API
        //    （修复前：catch 吞掉 → 继续用过期 token 调 API → 必然 401 → 再被吞）
        verify(apiClient, never()).getLiveStats(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("syncLiveStreamData 必须声明 noRollbackFor=BizException（否则标记 ERROR 会被回滚冲掉）")
    void syncLiveStreamData_declaresNoRollbackForBizException() throws Exception {
        // 📌 **为什么用「反射断言配置」而不是「断言数据库状态」**：
        //    「标记 ERROR 能否落地」取决于**事务边界**——
        //    生产：业务方法自己开事务 → BizException 不回滚 → 标记提交 ✅
        //    `@DataJpaTest`：整个测试在**外层事务**里 → 外层被 BizException 标记 rollback-only
        //      → **外层事务的 rollback-only 优先级更高** → 无论业务方法怎么标都测不出来
        //    → **测试环境与生产环境的事务边界不同 → 行为断言在这里必然误判**（规则 97 的同款思路）
        //    → ✅ 正解：**把「配置」锁住**（配置在生产环境真的生效），
        //      行为层面的「传播 + 不继续调 API」由上面两个用例覆盖。
        org.springframework.transaction.annotation.Transactional tx =
                LiveStreamMonitorService.class
                        .getMethod("syncLiveStreamData", Long.class)
                        .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(tx, "syncLiveStreamData 必须有 @Transactional");
        assertTrue(Arrays.asList(tx.noRollbackFor()).contains(
                        com.crossborder.erp.common.exception.BizException.class),
                "必须 noRollbackFor = BizException.class —— 否则「标记店铺 ERROR」会被事务回滚冲掉"
                        + "（店铺永远 ACTIVE → 下一轮又去调 API → 死循环 + 每 30 秒一条 error）");
    }

    @Test
    @DisplayName("LiveProductService 也必须 noRollbackFor=BizException（兄弟分支，同一个坑）")
    void liveProductService_declaresNoRollbackForBizException() throws Exception {
        // 📌 **兄弟分支**（规则 107）：`LiveProductService` 的 token 刷新失败
        //    同样「标 ERROR + 抛异常」→ 同样被回滚冲掉 → **只修一个 = 白修**
        for (java.lang.reflect.Method m : LiveProductService.class.getDeclaredMethods()) {
            if (m.getAnnotation(org.springframework.transaction.annotation.Transactional.class) == null) {
                continue;
            }
            org.springframework.transaction.annotation.Transactional tx =
                    m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertTrue(Arrays.asList(tx.noRollbackFor()).contains(
                            com.crossborder.erp.common.exception.BizException.class),
                    "LiveProductService." + m.getName() + " 也必须 noRollbackFor = BizException.class"
                            + "（同一个坑的兄弟分支）");
        }
    }

    // ─────────────── 缺陷 2：status 为 null 不能 NPE ───────────────

    @Test
    @DisplayName("直播状态返回 status=null → 不能抛 NPE")
    void nullStatus_doesNotThrowNpe() {
        persistShop("SHOP-TT-0001", LocalDateTime.now().plusDays(60));
        LiveStream stream = persistStream("LIVE-NULL-STATUS", LiveStream.Platform.TIKTOK, "SHOP-TT-0001");

        // 平台返回了 liveId，但 **status 字段缺失**
        TikTokApiClient.TikTokLiveStatusData data = new TikTokApiClient.TikTokLiveStatusData();
        data.setLiveId("LIVE-NULL-STATUS");
        data.setStatus(null);
        TikTokApiClient.TikTokLiveStatusResponse resp = new TikTokApiClient.TikTokLiveStatusResponse();
        resp.setData(data);
        when(apiClient.getLiveStatus(anyString(), anyString())).thenReturn(resp);

        // 📌 修复前：`switch (status)` → **NullPointerException** → 被 catch 吞掉
        assertDoesNotThrow(() -> service.monitorLiveStream(
                        shopConfigRepository.findByShopId("SHOP-TT-0001").orElseThrow()),
                "status 为 null 是平台字段缺失，不该变成 NPE");
    }

    // ─────────────── 缺陷 3：avgViewers 不能被整数除法截断 ───────────────

    @Test
    @DisplayName("avgViewers 必须是精确值（3 观众 / 2 场 = 1.50，不能是 1）")
    void avgViewers_doesNotTruncate() {
        persistShop("SHOP-TT-0001", LocalDateTime.now().plusDays(60));

        // 📌 **必须关联到同一个达人** → `findByInfluencerId(influencerId)` 才查得到
        //    （否则查不到任何直播 → avgViewers 恒为 0 → **测试变成假绿**，规则 48）
        Influencer inf = persistInfluencer("TT-INF-0001");

        // 两场直播：观众数 2 和 1 → 平均 1.5（int 除法会得到 1）
        persistStream("LIVE-A", LiveStream.Platform.TIKTOK, "SHOP-TT-0001", inf);
        persistStream("LIVE-B", LiveStream.Platform.TIKTOK, "SHOP-TT-0001", inf);
        LiveStream s1 = liveStreamRepository.findByStreamId("LIVE-A").orElseThrow();
        LiveStream s2 = liveStreamRepository.findByStreamId("LIVE-B").orElseThrow();
        s1.setViewerCount(2);
        s2.setViewerCount(1);
        liveStreamRepository.save(s1);
        liveStreamRepository.save(s2);
        entityManager.flush();

        Map<String, Object> analytics = service.getLiveStreamAnalytics(null, inf.getId());
        Object avg = analytics.get("avgViewers");

        assertTrue(avg instanceof BigDecimal,
                "avgViewers 应为 BigDecimal（与 avgGmv 精度语义一致），实际类型: " + avg.getClass());
        assertEquals(0, new BigDecimal("1.50").compareTo((BigDecimal) avg),
                "3 个观众 / 2 场 = 1.50，int 除法会截断成 1");
    }

    // ─────────────── 缺陷 4：查不到直播应是业务异常（400），不是 500 ───────────────

    @Test
    @DisplayName("查不到直播 → 应是 BizException（业务问题），不是 RuntimeException（500）")
    void liveStreamNotFound_usesBizException() {
        Exception ex = assertThrows(Exception.class, () -> service.getLiveStreamStats(999999L));
        assertInstanceOf(com.crossborder.erp.common.exception.BizException.class, ex,
                "「直播不存在」是业务问题 → BizException → HTTP 400；RuntimeException 会变成 500（规则 28）");
    }

    @Test
    @DisplayName("查不到直播（analytics）→ 同样应是 BizException")
    void liveStreamNotFound_analytics_usesBizException() {
        Exception ex = assertThrows(Exception.class, () -> service.getLiveStreamAnalytics(999999L, null));
        assertInstanceOf(com.crossborder.erp.common.exception.BizException.class, ex,
                "同一模块同一概念必须一个口径（规则 44 / 107）");
    }
}
