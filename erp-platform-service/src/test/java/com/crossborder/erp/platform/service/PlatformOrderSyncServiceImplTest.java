package com.crossborder.erp.platform.service;

import com.crossborder.erp.common.constant.PlatformType;
import com.crossborder.erp.order.entity.Order;
import com.crossborder.erp.order.entity.OrderItem;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.service.impl.PlatformOrderSyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 平台订单同步服务单元测试
 *
 * 覆盖 PlatformOrderSyncServiceImpl 的平台路由（大小写不敏感）、
 * 不支持平台的异常分支，以及对平台适配器的正确委托。
 */
public class PlatformOrderSyncServiceImplTest {

    private PlatformOrderSyncServiceImpl platformOrderSyncService;
    private PlatformOrderSync amazonSync;
    private PlatformOrderSync shopeeSync;
    private Map<String, PlatformOrderSync> platformSyncMap;

    @BeforeEach
    void setUp() {
        amazonSync = mock(PlatformOrderSync.class);
        shopeeSync = mock(PlatformOrderSync.class);

        platformSyncMap = new HashMap<>();
        platformSyncMap.put("amazon", amazonSync);
        platformSyncMap.put("shopee", shopeeSync);

        platformOrderSyncService = new PlatformOrderSyncServiceImpl(platformSyncMap);
    }

    // ==================== syncOrders ====================

    @Test
    @DisplayName("测试同步订单 - 路由到对应平台适配器")
    void testSyncOrders_DelegatesToPlatformSync() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 28, 23, 59);
        List<Order> expected = List.of(new Order());
        when(amazonSync.syncOrders(start, end, "SHOP001")).thenReturn(expected);

        List<Order> result = platformOrderSyncService.syncOrders("amazon", "SHOP001", start, end);

        assertSame(expected, result);
        verify(amazonSync, times(1)).syncOrders(start, end, "SHOP001");
        verify(shopeeSync, never()).syncOrders(any(), any(), anyString());
    }

    @Test
    @DisplayName("测试同步订单 - 平台名大小写不敏感")
    void testSyncOrders_CaseInsensitivePlatform() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 28, 23, 59);
        when(shopeeSync.syncOrders(start, end, "SHOP002")).thenReturn(List.of());

        platformOrderSyncService.syncOrders("SHOPEE", "SHOP002", start, end);

        verify(shopeeSync, times(1)).syncOrders(start, end, "SHOP002");
    }

    @Test
    @DisplayName("测试同步订单 - 不支持的平台时抛异常")
    void testSyncOrders_UnsupportedPlatform_Throws() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = LocalDateTime.now();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> platformOrderSyncService.syncOrders("wish", "SHOP003", start, end));
        assertTrue(ex.getMessage().contains("暂不支持该平台"), "异常信息应说明平台不支持: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("wish"), "异常信息应包含平台名: " + ex.getMessage());
    }

    // ==================== getOrderByNo ====================

    @Test
    @DisplayName("测试查询订单详情 - 委托平台适配器")
    void testGetOrderByNo_DelegatesToPlatformSync() {
        Order expected = new Order();
        when(amazonSync.getOrderByNo("PLAT-001", "SHOP001")).thenReturn(expected);

        Order result = platformOrderSyncService.getOrderByNo("amazon", "PLAT-001", "SHOP001");

        assertSame(expected, result);
        verify(amazonSync, times(1)).getOrderByNo("PLAT-001", "SHOP001");
    }

    @Test
    @DisplayName("测试查询订单详情 - 不支持的平台时抛异常")
    void testGetOrderByNo_UnsupportedPlatform_Throws() {
        assertThrows(RuntimeException.class,
                () -> platformOrderSyncService.getOrderByNo("wish", "PLAT-001", "SHOP003"));
    }

    // ==================== validateConfig ====================

    @Test
    @DisplayName("测试校验配置 - 返回平台适配器结果")
    void testValidateConfig_ReturnsSyncResult() {
        when(amazonSync.validateConfig("SHOP001")).thenReturn(true);
        when(shopeeSync.validateConfig("SHOP002")).thenReturn(false);

        assertTrue(platformOrderSyncService.validateConfig("amazon", "SHOP001"));
        assertFalse(platformOrderSyncService.validateConfig("shopee", "SHOP002"));
    }

    @Test
    @DisplayName("测试校验配置 - 不支持的平台时抛异常")
    void testValidateConfig_UnsupportedPlatform_Throws() {
        assertThrows(RuntimeException.class,
                () -> platformOrderSyncService.validateConfig("wish", "SHOP003"));
    }

    // ==================== getSupportedPlatforms ====================

    @Test
    @DisplayName("测试获取支持的平台列表 - 包含五大平台")
    void testGetSupportedPlatforms_ContainsFivePlatforms() {
        List<PlatformType> platforms = platformOrderSyncService.getSupportedPlatforms();

        assertNotNull(platforms);
        assertEquals(5, platforms.size());
        assertTrue(platforms.contains(PlatformType.AMAZON));
        assertTrue(platforms.contains(PlatformType.EBAY));
        assertTrue(platforms.contains(PlatformType.SHOPEE));
        assertTrue(platforms.contains(PlatformType.LAZADA));
        assertTrue(platforms.contains(PlatformType.TIKTOK));
    }
}
