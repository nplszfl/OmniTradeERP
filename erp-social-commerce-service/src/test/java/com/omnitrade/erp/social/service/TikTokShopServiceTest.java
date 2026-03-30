package com.omnitrade.erp.social.service;

import com.omnitrade.erp.social.dto.TikTokShopConfigDTO;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTok店铺服务测试")
class TikTokShopServiceTest {

    @Mock
    private TikTokShopConfigRepository repository;

    @InjectMocks
    private TikTokShopService service;

    private TikTokShopConfig testConfig;
    private TikTokShopConfigDTO testDTO;

    @BeforeEach
    void setUp() {
        testConfig = TikTokShopConfig.builder()
                .id(1L)
                .shopName("测试店铺")
                .shopId("shop123")
                .accessToken("test_token")
                .refreshToken("refresh_token")
                .tokenExpireTime(LocalDateTime.now().plusDays(7))
                .appKey("app_key")
                .appSecret("app_secret")
                .status(TikTokShopConfig.ConfigStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testDTO = TikTokShopConfigDTO.builder()
                .id(1L)
                .shopName("测试店铺")
                .shopId("shop123")
                .accessToken("test_token")
                .refreshToken("refresh_token")
                .tokenExpireTime(LocalDateTime.now().plusDays(7).toString())
                .appKey("app_key")
                .appSecret("app_secret")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("获取所有店铺配置")
    void testFindAll() {
        when(repository.findAll()).thenReturn(Arrays.asList(testConfig));

        var result = service.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试店铺", result.get(0).getShopName());
        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("根据ID获取店铺配置")
    void testFindById() {
        when(repository.findById(1L)).thenReturn(Optional.of(testConfig));

        var result = service.findById(1L);

        assertNotNull(result);
        assertEquals("shop123", result.getShopId());
        verify(repository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("根据ID获取店铺配置 - 不存在")
    void testFindByIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.findById(999L));
    }

    @Test
    @DisplayName("根据ShopId获取店铺配置")
    void testFindByShopId() {
        when(repository.findByShopId("shop123")).thenReturn(Optional.of(testConfig));

        var result = service.findByShopId("shop123");

        assertNotNull(result);
        assertEquals("测试店铺", result.getShopName());
        verify(repository, times(1)).findByShopId("shop123");
    }

    @Test
    @DisplayName("创建店铺配置")
    void testCreate() {
        when(repository.save(any(TikTokShopConfig.class))).thenReturn(testConfig);

        var result = service.create(testDTO);

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
        verify(repository, times(1)).save(any(TikTokShopConfig.class));
    }

    @Test
    @DisplayName("更新店铺配置")
    void testUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(testConfig));
        when(repository.save(any(TikTokShopConfig.class))).thenReturn(testConfig);

        TikTokShopConfigDTO updateDTO = TikTokShopConfigDTO.builder()
                .shopName("新店铺名")
                .build();

        var result = service.update(1L, updateDTO);

        assertNotNull(result);
        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).save(any(TikTokShopConfig.class));
    }

    @Test
    @DisplayName("删除店铺配置")
    void testDelete() {
        doNothing().when(repository).deleteById(1L);

        service.delete(1L);

        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("刷新Token")
    void testRefreshToken() {
        when(repository.findById(1L)).thenReturn(Optional.of(testConfig));
        when(repository.save(any(TikTokShopConfig.class))).thenReturn(testConfig);

        var result = service.refreshToken(1L);

        assertNotNull(result);
        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).save(any(TikTokShopConfig.class));
    }

    @Test
    @DisplayName("DTO转实体转换")
    void testToEntity() {
        when(repository.save(any(TikTokShopConfig.class))).thenReturn(testConfig);

        var entity = service.create(testDTO);

        assertNotNull(entity);
        assertNotNull(entity.getShopId());
    }
}