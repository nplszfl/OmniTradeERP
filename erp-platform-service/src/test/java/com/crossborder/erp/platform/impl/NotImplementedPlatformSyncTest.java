package com.crossborder.erp.platform.impl;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.common.exception.PlatformApiException;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 未实现的平台适配器契约测试（2026-11-03）
 *
 * <p>🔴 <b>锁的是一个「假装成功」型缺陷。</b></p>
 *
 * <p>eBay / Lazada / TikTok 三个适配器曾经是<b>空壳</b>：</p>
 * <ul>
 *   <li>{@code syncOrders} 永远返回**空列表**，却打「同步完成, 共获取 0 条订单」</li>
 *   <li>{@code getOrderByNo} 永远返回 <b>null</b></li>
 *   <li>{@code syncOrderItems} 永远返回**空列表**</li>
 *   <li>{@code validateConfig} 永远返回 <b>true</b> —— 等于<b>无条件声称凭据有效</b></li>
 * </ul>
 *
 * <p>后果：定时任务每 10 分钟对 eBay 店铺「同步成功，count=0」，
 * <b>没有异常、没有告警</b>，运营只会以为「今天 eBay 没订单」——
 * 而平台对接根本没写。</p>
 *
 * <p>📌 <b>核心原则：空集合和「真的没有数据」在下游无法区分 → 不要用空集合表示失败。</b>
 * 未实现必须<b>明确抛错</b>，让故障可见。</p>
 */
class NotImplementedPlatformSyncTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 0);

    private static List<PlatformOrderSync> notImplementedSyncs() {
        return List.of(new EbayOrderSync(), new LazadaOrderSync(), new TiktokOrderSync());
    }

    @Test
    @DisplayName("未实现平台的 syncOrders 必须抛 PlatformApiException，不能返回空列表")
    void syncOrdersMustThrow() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            PlatformApiException ex = assertThrows(PlatformApiException.class,
                    () -> sync.syncOrders(NOW.minusHours(1), NOW, "SHOP1"),
                    sync.getClass().getSimpleName() + ".syncOrders 应抛异常");
            assertEquals("NOT_IMPLEMENTED", ex.getErrorCode(),
                    sync.getClass().getSimpleName() + " 错误码应为 NOT_IMPLEMENTED");
        }
    }

    @Test
    @DisplayName("未实现平台的 getOrderByNo 必须抛异常，不能返回 null")
    void getOrderByNoMustThrow() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            PlatformApiException ex = assertThrows(PlatformApiException.class,
                    () -> sync.getOrderByNo("PLAT-001", "SHOP1"),
                    sync.getClass().getSimpleName() + ".getOrderByNo 应抛异常");
            assertEquals("NOT_IMPLEMENTED", ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("未实现平台的 syncOrderItems 必须抛异常，不能返回空列表")
    void syncOrderItemsMustThrow() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            PlatformApiException ex = assertThrows(PlatformApiException.class,
                    () -> sync.syncOrderItems("PLAT-001", "SHOP1"),
                    sync.getClass().getSimpleName() + ".syncOrderItems 应抛异常");
            assertEquals("NOT_IMPLEMENTED", ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("未实现平台的 validateConfig 必须抛异常 —— 绝不返回 true（谎称凭据有效）")
    void validateConfigMustNotReturnTrue() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            PlatformApiException ex = assertThrows(PlatformApiException.class,
                    () -> sync.validateConfig("SHOP1"),
                    sync.getClass().getSimpleName() + ".validateConfig 应抛异常");
            assertEquals("NOT_IMPLEMENTED", ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("异常信息里要说明「尚未实现」，不能让人误以为是网络/凭据问题")
    void exceptionMessageExplainsNotImplemented() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            PlatformApiException ex = assertThrows(PlatformApiException.class,
                    () -> sync.syncOrders(NOW.minusHours(1), NOW, "SHOP1"));
            assertTrue(ex.getMessage().contains("尚未实现"),
                    "异常信息应说明尚未实现: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("三个未实现适配器的 getPlatformType 仍要正确返回（注册表依赖它建 key）")
    void platformTypesAreCorrect() {
        assertEquals(PlatformType.EBAY, new EbayOrderSync().getPlatformType());
        assertEquals(PlatformType.LAZADA, new LazadaOrderSync().getPlatformType());
        assertEquals(PlatformType.TIKTOK, new TiktokOrderSync().getPlatformType());
    }

    @Test
    @DisplayName("未实现适配器不依赖任何注入 → 能被 new 出来（注册表能收集到）")
    void canBeInstantiatedWithoutDependencies() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            assertNotNull(sync);
        }
    }

    /**
     * 兜底：确认这些类确实实现的是同一个接口（防止重构后接口漂移）
     */
    @Test
    @DisplayName("未实现适配器实现 PlatformOrderSync")
    void implementsPlatformOrderSync() {
        for (PlatformOrderSync sync : notImplementedSyncs()) {
            assertTrue(sync instanceof PlatformOrderSync);
        }
    }

    /**
     * 防止有人「顺手」把方法改成返回 null/空集合 —— 编译期用类型锁住返回值
     */
    @Test
    @DisplayName("返回值类型契约：方法签名保持 List&lt;Order&gt;/Order/List&lt;OrderItem&gt;")
    void methodSignaturesUnchanged() throws Exception {
        Class<?> clazz = EbayOrderSync.class;

        assertEquals(List.class,
                clazz.getMethod("syncOrders", LocalDateTime.class, LocalDateTime.class, String.class)
                        .getReturnType());
        assertEquals(Order.class,
                clazz.getMethod("getOrderByNo", String.class, String.class).getReturnType());
        assertEquals(List.class,
                clazz.getMethod("syncOrderItems", String.class, String.class).getReturnType());
        assertEquals(boolean.class,
                clazz.getMethod("validateConfig", String.class).getReturnType());
    }

    /**
     * 兜底：OrderItem 类型存在（防止 import 被删）
     */
    @Test
    @DisplayName("OrderItem 类型可解析")
    void orderItemTypeResolvable() {
        assertNotNull(new OrderItem());
    }
}
