package com.crossborder.erp.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.platform.api.PlatformOrderSync;
import com.crossborder.erp.platform.entity.PlatformConfig;
import com.crossborder.erp.platform.mapper.PlatformConfigMapper;
import com.crossborder.erp.platform.service.PlatformConfigService;
import com.crossborder.erp.platform.support.PlatformSyncRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformConfigServiceImpl extends ServiceImpl<PlatformConfigMapper, PlatformConfig>
        implements PlatformConfigService {

    private final PlatformSyncRegistry platformSyncRegistry;

    @Override
    public List<PlatformConfig> getAllConfigs() {
        LambdaQueryWrapper<PlatformConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PlatformConfig::getCreateTime);
        return list(wrapper);
    }

    @Override
    public PlatformConfig getConfigById(Long id) {
        return getById(id);
    }

    @Override
    public PlatformConfig getConfigByShop(String platform, String shopId) {
        LambdaQueryWrapper<PlatformConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformConfig::getPlatform, platform)
                .eq(PlatformConfig::getShopId, shopId);
        return getOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformConfig saveConfig(PlatformConfig config) {
        if (config.getId() == null) {
            // 新增：检查是否重复
            PlatformConfig existing = getConfigByShop(config.getPlatform(), config.getShopId());
            if (existing != null) {
                throw new BizException("该平台的店铺配置已存在");
            }
            save(config);
        } else {
            // 更新
            updateById(config);
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        removeById(id);
    }

    @Override
    public boolean testConnection(Long id) {
        PlatformConfig config = getConfigById(id);
        if (config == null) {
            throw new BizException("配置不存在");
        }

        PlatformOrderSync sync = platformSyncRegistry.require(config.getPlatform());

        try {
            return sync.validateConfig(config.getShopId());
        } catch (Exception e) {
            // 📌 注意：这里 catch 住异常返回 false 是**有意的**（连接测试语义就是
            //    「连不上」），但必须**记下原因** —— 否则页面只显示「测试失败」，
            //    运营完全不知道是「凭据无效」还是「平台未实现」。
            //    📌 另一个坑：不能把 PlatformApiException（平台未实现）也吞成 false，
            //    那会把「功能没做」伪装成「凭据不对」。
            log.error("测试连接失败: platform={}, shopId={}", config.getPlatform(),
                    config.getShopId(), e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        PlatformConfig config = new PlatformConfig();
        config.setId(id);
        config.setStatus(status);
        updateById(config);
    }
}
