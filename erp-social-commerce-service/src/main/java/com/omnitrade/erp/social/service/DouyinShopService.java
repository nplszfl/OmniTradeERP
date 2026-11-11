package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.model.DouyinShopConfig;
import com.omnitrade.erp.social.repository.DouyinShopConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DouyinShopService {
    
    private final DouyinShopConfigRepository repository;
    
    public List<DouyinShopConfig> findAll() {
        return repository.findAll();
    }
    
    public DouyinShopConfig findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BizException("抖音店铺配置不存在: " + id));
    }
    
    public DouyinShopConfig findByShopId(String shopId) {
        return repository.findByShopId(shopId)
                .orElseThrow(() -> new BizException("抖音店铺不存在: " + shopId));
    }
    
    @Transactional
    public DouyinShopConfig create(DouyinShopConfig entity) {
        entity.setStatus(DouyinShopConfig.ConfigStatus.ACTIVE);
        return repository.save(entity);
    }
    
    @Transactional
    public DouyinShopConfig update(Long id, DouyinShopConfig entity) {
        DouyinShopConfig existing = repository.findById(id)
                .orElseThrow(() -> new BizException("抖音店铺配置不存在: " + id));
        
        if (entity.getShopName() != null) existing.setShopName(entity.getShopName());
        if (entity.getAccessToken() != null) existing.setAccessToken(entity.getAccessToken());
        if (entity.getRefreshToken() != null) existing.setRefreshToken(entity.getRefreshToken());
        if (entity.getTokenExpireTime() != null) existing.setTokenExpireTime(entity.getTokenExpireTime());
        if (entity.getAppKey() != null) existing.setAppKey(entity.getAppKey());
        if (entity.getAppSecret() != null) existing.setAppSecret(entity.getAppSecret());
        if (entity.getStatus() != null) existing.setStatus(entity.getStatus());
        
        return repository.save(existing);
    }
    
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    @Transactional
    public DouyinShopConfig refreshToken(Long id) {
        DouyinShopConfig entity = repository.findById(id)
                .orElseThrow(() -> new BizException("抖音店铺配置不存在: " + id));
        
        // TODO: 调用抖音API刷新token
        entity.setTokenExpireTime(LocalDateTime.now().plusDays(7));
        return repository.save(entity);
    }
}