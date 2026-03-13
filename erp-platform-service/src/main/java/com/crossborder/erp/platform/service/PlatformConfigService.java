package com.crossborder.erp.platform.service;

import com.crossborder.erp.platform.entity.PlatformConfig;

import java.util.List;

/**
 * 平台配置服务接口
 */
public interface PlatformConfigService {

    /**
     * 获取所有配置
     */
    List<PlatformConfig> getAllConfigs();

    /**
     * 根据ID获取配置
     */
    PlatformConfig getConfigById(Long id);

    /**
     * 根据平台和店铺ID获取配置
     */
    PlatformConfig getConfigByShop(String platform, String shopId);

    /**
     * 保存配置
     */
    PlatformConfig saveConfig(PlatformConfig config);

    /**
     * 删除配置
     */
    void deleteConfig(Long id);

    /**
     * 测试连接
     */
    boolean testConnection(Long id);

    /**
     * 更新状态
     */
    void updateStatus(Long id, Integer status);
}
