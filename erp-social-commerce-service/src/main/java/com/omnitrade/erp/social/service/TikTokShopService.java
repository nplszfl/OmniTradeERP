package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.dto.TikTokShopConfigDTO;
import com.omnitrade.erp.social.model.TikTokShopConfig;
import com.omnitrade.erp.social.repository.TikTokShopConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TikTokShopService {
    
    private final TikTokShopConfigRepository repository;
    
    public List<TikTokShopConfigDTO> findAll() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public TikTokShopConfigDTO findById(Long id) {
        return repository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("TikTok店铺配置不存在: " + id));
    }
    
    public TikTokShopConfigDTO findByShopId(String shopId) {
        return repository.findByShopId(shopId)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("TikTok店铺不存在: " + shopId));
    }
    
    @Transactional
    public TikTokShopConfigDTO create(TikTokShopConfigDTO dto) {
        TikTokShopConfig entity = toEntity(dto);
        entity.setStatus(TikTokShopConfig.ConfigStatus.ACTIVE);
        TikTokShopConfig saved = repository.save(entity);
        return toDTO(saved);
    }
    
    @Transactional
    public TikTokShopConfigDTO update(Long id, TikTokShopConfigDTO dto) {
        TikTokShopConfig entity = repository.findById(id)
                .orElseThrow(() -> new BizException("TikTok店铺配置不存在: " + id));
        
        entity.setShopName(dto.getShopName());
        if (dto.getAccessToken() != null) entity.setAccessToken(dto.getAccessToken());
        if (dto.getRefreshToken() != null) entity.setRefreshToken(dto.getRefreshToken());
        if (dto.getTokenExpireTime() != null) {
            entity.setTokenExpireTime(LocalDateTime.parse(dto.getTokenExpireTime()));
        }
        if (dto.getAppKey() != null) entity.setAppKey(dto.getAppKey());
        if (dto.getAppSecret() != null) entity.setAppSecret(dto.getAppSecret());
        if (dto.getStatus() != null) {
            entity.setStatus(TikTokShopConfig.ConfigStatus.valueOf(dto.getStatus()));
        }
        
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    @Transactional
    public TikTokShopConfigDTO refreshToken(Long id) {
        TikTokShopConfig entity = repository.findById(id)
                .orElseThrow(() -> new BizException("TikTok店铺配置不存在: " + id));
        
        // TODO: 调用TikTok API刷新token
        // 这里先模拟刷新逻辑
        entity.setTokenExpireTime(LocalDateTime.now().plusDays(7));
        return toDTO(repository.save(entity));
    }
    
    private TikTokShopConfigDTO toDTO(TikTokShopConfig entity) {
        return TikTokShopConfigDTO.builder()
                .id(entity.getId())
                .shopName(entity.getShopName())
                .shopId(entity.getShopId())
                .accessToken(entity.getAccessToken())
                .refreshToken(entity.getRefreshToken())
                .tokenExpireTime(entity.getTokenExpireTime() != null ? entity.getTokenExpireTime().toString() : null)
                .appKey(entity.getAppKey())
                .appSecret(entity.getAppSecret())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
    
    private TikTokShopConfig toEntity(TikTokShopConfigDTO dto) {
        return TikTokShopConfig.builder()
                .shopName(dto.getShopName())
                .shopId(dto.getShopId())
                .accessToken(dto.getAccessToken())
                .refreshToken(dto.getRefreshToken())
                .tokenExpireTime(dto.getTokenExpireTime() != null ? LocalDateTime.parse(dto.getTokenExpireTime()) : null)
                .appKey(dto.getAppKey())
                .appSecret(dto.getAppSecret())
                .build();
    }
}