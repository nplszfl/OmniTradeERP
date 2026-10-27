package com.crossborder.erp.platform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.entity.PlatformConfig;
import com.crossborder.erp.platform.mapper.PlatformConfigMapper;
import com.crossborder.erp.platform.service.impl.PlatformConfigServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * 平台配置服务单元测试
 *
 * 覆盖 PlatformConfigServiceImpl 的配置去重保存、连接测试的平台路由与异常兜底、
 * 按店铺查询与状态局部更新。
 */
@SuppressWarnings("unchecked")
public class PlatformConfigServiceImplTest {

    private PlatformConfigServiceImpl platformConfigService;
    private PlatformConfigMapper platformConfigMapper;
    private PlatformOrderSync amazonSync;
    private PlatformOrderSync shopeeSync;
    private Map<String, PlatformOrderSync> platformSyncMap;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant builderAssistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        builderAssistant.setCurrentNamespace(
                "com.crossborder.erp.platform.mapper.PlatformConfigMapper");
        TableInfoHelper.initTableInfo(builderAssistant, PlatformConfig.class);
    }

    @BeforeEach
    void setUp() {
        platformConfigMapper = mock(PlatformConfigMapper.class);
        amazonSync = mock(PlatformOrderSync.class);
        shopeeSync = mock(PlatformOrderSync.class);

        platformSyncMap = new HashMap<>();
        platformSyncMap.put("amazon", amazonSync);
        platformSyncMap.put("shopee", shopeeSync);

        platformConfigService = new PlatformConfigServiceImpl(platformSyncMap);
        injectBaseMapper(platformConfigService, platformConfigMapper);
    }

    /**
     * 通过反射把 mock mapper 注入 ServiceImpl 的 baseMapper 字段
     */
    private void injectBaseMapper(ServiceImpl<?, ?> service, PlatformConfigMapper mapper) {
        try {
            Field field = ReflectionUtils.findField(ServiceImpl.class, "baseMapper");
            if (field == null) {
                throw new IllegalStateException("未找到 ServiceImpl.baseMapper 字段");
            }
            ReflectionUtils.makeAccessible(field);
            field.set(service, mapper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("注入 baseMapper 失败", e);
        }
    }

    private PlatformConfig buildConfig(Long id, String platform, String shopId) {
        PlatformConfig config = new PlatformConfig();
        config.setId(id);
        config.setPlatform(platform);
        config.setShopId(shopId);
        config.setStatus(1);
        return config;
    }

    // ==================== getConfigByShop ====================

    @Test
    @DisplayName("测试按平台与店铺查询配置 - 过滤条件正确")
    void testGetConfigByShop_FiltersByPlatformAndShop() {
        PlatformConfig expected = buildConfig(1L, "amazon", "SHOP001");
        // MyBatis-Plus 3.5.6 的 getOne(wrapper) 会转发到 selectOne(wrapper, throwEx) 两参重载
        when(platformConfigMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(expected);

        PlatformConfig result = platformConfigService.getConfigByShop("amazon", "SHOP001");

        assertSame(expected, result);

        ArgumentCaptor<Wrapper<PlatformConfig>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(platformConfigMapper, times(1)).selectOne(captor.capture(), anyBoolean());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("platform"), "应包含平台过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("shop_id"), "应包含店铺过滤: " + sqlSegment);
    }

    // ==================== saveConfig ====================

    @Test
    @DisplayName("测试保存配置 - 新增且无重复时落库")
    void testSaveConfig_New_NoDuplicate_Persists() {
        when(platformConfigMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(platformConfigMapper.insert(any(PlatformConfig.class))).thenReturn(1);

        PlatformConfig config = buildConfig(null, "amazon", "SHOP001");
        PlatformConfig result = platformConfigService.saveConfig(config);

        assertSame(config, result);
        verify(platformConfigMapper, times(1)).insert(any(PlatformConfig.class));
        verify(platformConfigMapper, never()).updateById(any(PlatformConfig.class));
    }

    @Test
    @DisplayName("测试保存配置 - 新增但店铺配置已存在时抛异常")
    void testSaveConfig_New_Duplicate_Throws() {
        when(platformConfigMapper.selectOne(any(Wrapper.class), anyBoolean()))
                .thenReturn(buildConfig(9L, "amazon", "SHOP001"));

        PlatformConfig config = buildConfig(null, "amazon", "SHOP001");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> platformConfigService.saveConfig(config));
        assertTrue(ex.getMessage().contains("已存在"), "异常信息应说明配置重复: " + ex.getMessage());

        verify(platformConfigMapper, never()).insert(any(PlatformConfig.class));
    }

    @Test
    @DisplayName("测试保存配置 - 已存在 ID 时走更新分支")
    void testSaveConfig_Existing_Updates() {
        PlatformConfig config = buildConfig(5L, "amazon", "SHOP001");
        when(platformConfigMapper.updateById(any(PlatformConfig.class))).thenReturn(1);

        PlatformConfig result = platformConfigService.saveConfig(config);

        assertSame(config, result);
        verify(platformConfigMapper, times(1)).updateById(config);
        // 更新分支不应做重复检查
        verify(platformConfigMapper, never()).selectOne(any(Wrapper.class));
        verify(platformConfigMapper, never()).insert(any(PlatformConfig.class));
    }

    // ==================== testConnection ====================

    @Test
    @DisplayName("测试连接测试 - 路由到对应平台并返回结果")
    void testTestConnection_DelegatesToPlatformSync() {
        PlatformConfig config = buildConfig(1L, "amazon", "SHOP001");
        when(platformConfigMapper.selectById(1L)).thenReturn(config);
        when(amazonSync.validateConfig("SHOP001")).thenReturn(true);

        boolean result = platformConfigService.testConnection(1L);

        assertTrue(result);
        verify(amazonSync, times(1)).validateConfig("SHOP001");
    }

    @Test
    @DisplayName("测试连接测试 - 平台名大小写不敏感")
    void testTestConnection_CaseInsensitivePlatform() {
        PlatformConfig config = buildConfig(1L, "Shopee", "SHOP002");
        when(platformConfigMapper.selectById(1L)).thenReturn(config);
        when(shopeeSync.validateConfig("SHOP002")).thenReturn(true);

        assertTrue(platformConfigService.testConnection(1L));

        verify(shopeeSync, times(1)).validateConfig("SHOP002");
    }

    @Test
    @DisplayName("测试连接测试 - 配置不存在时抛异常")
    void testTestConnection_ConfigNotFound_Throws() {
        when(platformConfigMapper.selectById(404L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> platformConfigService.testConnection(404L));
        assertTrue(ex.getMessage().contains("配置不存在"), "异常信息应说明配置不存在: " + ex.getMessage());
    }

    @Test
    @DisplayName("测试连接测试 - 不支持的平台时抛异常")
    void testTestConnection_UnsupportedPlatform_Throws() {
        PlatformConfig config = buildConfig(1L, "wish", "SHOP003");
        when(platformConfigMapper.selectById(1L)).thenReturn(config);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> platformConfigService.testConnection(1L));
        assertTrue(ex.getMessage().contains("暂不支持该平台"), "异常信息应说明平台不支持: " + ex.getMessage());
    }

    @Test
    @DisplayName("测试连接测试 - 平台同步抛异常时兜底返回 false")
    void testTestConnection_SyncThrows_ReturnsFalse() {
        PlatformConfig config = buildConfig(1L, "amazon", "SHOP001");
        when(platformConfigMapper.selectById(1L)).thenReturn(config);
        when(amazonSync.validateConfig("SHOP001")).thenThrow(new RuntimeException("网络超时"));

        assertFalse(platformConfigService.testConnection(1L));
    }

    // ==================== updateStatus ====================

    @Test
    @DisplayName("测试更新状态 - 只更新 ID 与状态字段")
    void testUpdateStatus_OnlyUpdatesIdAndStatus() {
        when(platformConfigMapper.updateById(any(PlatformConfig.class))).thenReturn(1);

        platformConfigService.updateStatus(7L, 0);

        ArgumentCaptor<PlatformConfig> captor = ArgumentCaptor.forClass(PlatformConfig.class);
        verify(platformConfigMapper, times(1)).updateById(captor.capture());

        PlatformConfig update = captor.getValue();
        assertEquals(7L, update.getId());
        assertEquals(0, update.getStatus());
        // 局部更新不应携带其他字段
        assertNull(update.getPlatform());
        assertNull(update.getShopId());
        assertNull(update.getApiKey());
    }

    // ==================== deleteConfig / getAllConfigs ====================

    @Test
    @DisplayName("测试删除配置 - 走逻辑删除并带上主键")
    void testDeleteConfig_CallsRemoveById() {
        when(platformConfigMapper.deleteById(any(PlatformConfig.class))).thenReturn(1);

        platformConfigService.deleteConfig(3L);

        // PlatformConfig 带 @TableLogic，removeById 会构造带主键的实体走 deleteById(T)
        ArgumentCaptor<PlatformConfig> captor = ArgumentCaptor.forClass(PlatformConfig.class);
        verify(platformConfigMapper, times(1)).deleteById(captor.capture());
        assertEquals(3L, captor.getValue().getId());
    }

    @Test
    @DisplayName("测试查询全部配置 - 按创建时间倒序")
    void testGetAllConfigs_OrdersByCreateTimeDesc() {
        when(platformConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        platformConfigService.getAllConfigs();

        ArgumentCaptor<Wrapper<PlatformConfig>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(platformConfigMapper, times(1)).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("create_time"), "应按创建时间排序: " + sqlSegment);
    }
}
