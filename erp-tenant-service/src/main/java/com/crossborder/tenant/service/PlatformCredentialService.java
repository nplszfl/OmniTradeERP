package com.crossborder.tenant.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.platform.PlatformCredentialDTO;
import com.crossborder.tenant.entity.platform.CredentialStatus;
import com.crossborder.tenant.entity.platform.PlatformCredential;
import com.crossborder.tenant.repository.PlatformCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 平台凭证服务 - 管理租户的电商平台店铺连接
 */
@Service
@RequiredArgsConstructor
public class PlatformCredentialService {

    private final PlatformCredentialRepository credentialRepository;

    /**
     * 获取当前租户的所有平台凭证
     */
    public List<PlatformCredentialDTO> getCredentials() {
        String tenantId = TenantContext.getTenant();
        return credentialRepository.findByTenantId(tenantId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定平台的凭证
     */
    public List<PlatformCredentialDTO> getCredentialsByPlatform(String platform) {
        String tenantId = TenantContext.getTenant();
        return credentialRepository.findByTenantIdAndPlatform(tenantId, platform).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 添加平台凭证
     */
    public PlatformCredentialDTO addCredential(PlatformCredentialDTO dto) {
        String tenantId = TenantContext.getTenant();
        
        PlatformCredential credential = PlatformCredential.builder()
                .tenantId(tenantId)
                .platform(dto.getPlatform())
                .shopName(dto.getShopName())
                .shopId(dto.getShopId())
                .clientId(dto.getClientId())
                .clientSecret(dto.getClientSecret())
                .developerId(dto.getDeveloperId())
                .refreshToken(dto.getRefreshToken())
                .marketplace(dto.getMarketplace())
                .status(CredentialStatus.ACTIVE)
                .build();
        
        credential = credentialRepository.save(credential);
        return toDTO(credential);
    }

    /**
     * 更新凭证
     */
    public PlatformCredentialDTO updateCredential(String id, PlatformCredentialDTO dto) {
        String tenantId = TenantContext.getTenant();
        
        PlatformCredential credential = credentialRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BizException("凭证不存在"));
        
        if (dto.getShopName() != null) credential.setShopName(dto.getShopName());
        if (dto.getClientId() != null) credential.setClientId(dto.getClientId());
        // 🔴 敏感凭证：**忽略掩码值**，否则前端拿列表里的 `rt-am****0001` 回传
        //    就会把库里的真实 token 覆盖成掩码 → 凭证直接失效（而且不报错，
        //    要等到真正调平台 API 401 才发现）。
        if (isRealSecret(dto.getClientSecret())) credential.setClientSecret(dto.getClientSecret());
        if (isRealSecret(dto.getRefreshToken())) credential.setRefreshToken(dto.getRefreshToken());
        
        credential = credentialRepository.save(credential);
        return toDTO(credential);
    }

    /**
     * 判断入参是不是「真实明文」而不是出参的掩码
     *
     * <p>出参掩码格式固定为 {@code 前4位 + **** + 后4位}，
     * 所以只要中间带 {@code ****} 就当成「用户没改」→ 不覆盖库里的值。</p>
     */
    private boolean isRealSecret(String value) {
        return value != null && !value.isBlank() && !value.contains("****");
    }

    /**
     * 删除凭证
     */
    public void deleteCredential(String id) {
        String tenantId = TenantContext.getTenant();
        PlatformCredential credential = credentialRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BizException("凭证不存在"));
        credentialRepository.delete(credential);
    }

    /**
     * 实体 → DTO
     *
     * 🔴 <b>敏感字段脱敏</b>：accessToken / refreshToken / clientSecret 是
     *    <b>能直接调平台 API 的凭证</b>，明文出参 = 任何拿到列表接口的人
     *    都能冒充该店铺操作平台。
     *    前端（erp-web/src/views/platform）只把它们当<b>输入</b>（密码框），
     *    列表展示并不需要明文 → 所以出参只给「是否存在」的标记。
     *
     * <p>📌 保持 DTO 字段本身不变（前端 contract 不动），
     * 只在<b>出参</b>时替换成掩码 —— 入参仍然收明文。</p>
     */
    private PlatformCredentialDTO toDTO(PlatformCredential credential) {
        return PlatformCredentialDTO.builder()
                .id(credential.getId())
                .tenantId(credential.getTenantId())
                .platform(credential.getPlatform())
                .shopName(credential.getShopName())
                .shopId(credential.getShopId())
                .clientId(credential.getClientId())
                .developerId(credential.getDeveloperId())
                // 敏感凭证：只告知「有没有」，不给明文
                .clientSecret(mask(credential.getClientSecret()))
                .refreshToken(mask(credential.getRefreshToken()))
                .accessToken(mask(credential.getAccessToken()))
                .marketplace(credential.getMarketplace())
                .status(credential.getStatus())
                .tokenExpiresAt(credential.getTokenExpiresAt())
                .lastSyncAt(credential.getLastSyncAt())
                .createdAt(credential.getCreatedAt())
                .build();
    }

    /**
     * 凭证掩码：null → null；非空 → 只保留首尾各 4 位，中间用 * 遮住。
     *
     * <p>为什么不是直接置 null：前端要显示「已配置 / 未配置」，
     * 置 null 会让页面误判成没配过。</p>
     */
    private String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}