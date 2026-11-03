package com.crossborder.erp.platform.support;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PlatformSyncRegistry} 单元测试
 *
 * <p>📌 <b>这个测试锁的是一个全项目级的静默失效缺陷：</b>
 * 原代码用 {@code Map<String, PlatformOrderSync>} 注入 + {@code get(platform.toLowerCase())}
 * 查表，而 <b>Spring 注入 Map 的 key 是 bean 名</b>（{@code amazonOrderSync}），
 * 不是平台码（{@code amazon}）→ 三个入口（手动同步 / 测试连接 / 定时同步）
 * <b>永远</b>判定「不支持的平台」。</p>
 *
 * <p>📌 <b>为什么旧单测没发现</b>：旧测试手工 {@code map.put("amazon", mock)}，
 * 人工修正了 Spring 的错误行为。本测试改为用<b>真实的 registry</b>，
 * 并显式断言「key 是平台码」。</p>
 */
class PlatformSyncRegistryTest {

    private static PlatformOrderSync mockSync(PlatformType type) {
        PlatformOrderSync sync = mock(PlatformOrderSync.class);
        when(sync.getPlatformType()).thenReturn(type);
        return sync;
    }

    @Test
    @DisplayName("按平台码注册 —— key 必须是 amazon/ebay 而不是 bean 名")
    void registersByPlatformCodeNotBeanName() {
        PlatformOrderSync amazon = mockSync(PlatformType.AMAZON);
        PlatformOrderSync ebay = mockSync(PlatformType.EBAY);

        PlatformSyncRegistry registry = new PlatformSyncRegistry(List.of(amazon, ebay));

        assertEquals(2, registry.registeredPlatforms().size());
        assertTrue(registry.registeredPlatforms().contains("amazon"));
        assertTrue(registry.registeredPlatforms().contains("ebay"));

        // 📌 关键断言：查 "amazon" 必须命中。
        //    如果这里退化成 bean 名 key，查 "amazon" 会是 null —— 正是线上缺陷。
        assertSame(amazon, registry.get("amazon"));
        assertSame(ebay, registry.get("ebay"));

        // 📌 bean 风格的 key 不应该命中（说明 key 语义确实是平台码）
        assertNull(registry.get("amazonOrderSync"));
    }

    @Test
    @DisplayName("平台码大小写不敏感 + 前后空格容忍")
    void lookupIsCaseInsensitiveAndTrimmed() {
        PlatformOrderSync amazon = mockSync(PlatformType.AMAZON);
        PlatformSyncRegistry registry = new PlatformSyncRegistry(List.of(amazon));

        assertSame(amazon, registry.get("amazon"));
        assertSame(amazon, registry.get("AMAZON"));
        assertSame(amazon, registry.get("Amazon"));
        assertSame(amazon, registry.get("  amazon  "));
    }

    @Test
    @DisplayName("未注册平台 → get 返回 null，require 抛 BizException（非 500）")
    void unregisteredPlatform() {
        PlatformSyncRegistry registry = new PlatformSyncRegistry(List.of());

        assertNull(registry.get("wish"));
        assertNull(registry.get(null));
        assertNull(registry.get("  "));

        BizException ex = assertThrows(BizException.class, () -> registry.require("wish"));
        // 📌 业务异常应能被全局 handler 转成 400，而不是掉进兜底分支报「系统繁忙」
        assertTrue(ex.getMessage().contains("暂不支持该平台"), ex.getMessage());
    }

    @Test
    @DisplayName("构造时忽略 null 元素与 getPlatformType() 为 null 的实现")
    void skipsNullsAndTypelessImplementations() {
        PlatformOrderSync noType = mock(PlatformOrderSync.class);
        when(noType.getPlatformType()).thenReturn(null);

        PlatformSyncRegistry registry = new PlatformSyncRegistry(
                java.util.Arrays.asList(null, noType, mockSync(PlatformType.SHOPEE)));

        assertEquals(1, registry.registeredPlatforms().size());
        assertTrue(registry.registeredPlatforms().contains("shopee"));
    }

    @Test
    @DisplayName("null 列表不炸")
    void handlesNullList() {
        PlatformSyncRegistry registry = new PlatformSyncRegistry(null);
        assertTrue(registry.registeredPlatforms().isEmpty());
    }

    @Test
    @DisplayName("同一平台注册两个实现 → 后者覆盖前者，但已注册平台集合不重复")
    void duplicatePlatformOverwrites() {
        PlatformOrderSync first = mockSync(PlatformType.AMAZON);
        PlatformOrderSync second = mockSync(PlatformType.AMAZON);

        PlatformSyncRegistry registry = new PlatformSyncRegistry(List.of(first, second));

        assertEquals(1, registry.registeredPlatforms().size());
        assertSame(second, registry.get("amazon"));
    }

    /**
     * 冒烟：registry 返回的实现能真的被调用（防止 registry 只是个摆设）
     */
    @Test
    @DisplayName("registry 取出的实现可以被真实调用")
    void retrievedSyncIsUsable() {
        PlatformOrderSync amazon = mockSync(PlatformType.AMAZON);
        // 📌 用 any() 而不是具体时间戳 —— LocalDateTime.now() 每次调用值都不同，
        //    写成具体值会导致 stub 与实际调用参数不匹配（mock 返回 null）。
        when(amazon.syncOrders(any(LocalDateTime.class), any(LocalDateTime.class), eq("SHOP1")))
                .thenReturn(List.of(new Order()));

        PlatformSyncRegistry registry = new PlatformSyncRegistry(List.of(amazon));
        PlatformOrderSync sync = registry.require("amazon");

        List<Order> orders = sync.syncOrders(
                LocalDateTime.now().minusHours(1), LocalDateTime.now(), "SHOP1");
        assertEquals(1, orders.size());
    }

    /**
     * 保证 Map<String, PlatformOrderSync> 这个签名在 registry 里也有等价语义
     * （供文档/巡检脚本断言使用）
     */
    @Test
    @DisplayName("平台码集合可导出为 Map 视图")
    void exposesRegisteredPlatforms() {
        PlatformSyncRegistry registry = new PlatformSyncRegistry(
                List.of(mockSync(PlatformType.AMAZON), mockSync(PlatformType.TIKTOK)));

        Map<String, PlatformOrderSync> view = new java.util.HashMap<>();
        for (String code : registry.registeredPlatforms()) {
            view.put(code, registry.get(code));
        }
        assertEquals(2, view.size());
        assertTrue(view.containsKey("amazon"));
        assertTrue(view.containsKey("tiktok"));
    }

    /**
     * 兜底：OrderItem 也要能被 registry 里的实现返回（防止只处理了 Order）
     */
    @Test
    @DisplayName("registry 取出的实现能返回商品明细")
    void retrievedSyncReturnsItems() {
        PlatformOrderSync amazon = mockSync(PlatformType.AMAZON);
        when(amazon.syncOrderItems("AMZ-1", "SHOP1")).thenReturn(List.of(new OrderItem()));

        PlatformSyncRegistry registry = new PlatformSyncRegistry(List.of(amazon));
        List<OrderItem> items = registry.require("amazon").syncOrderItems("AMZ-1", "SHOP1");
        assertEquals(1, items.size());
    }
}
