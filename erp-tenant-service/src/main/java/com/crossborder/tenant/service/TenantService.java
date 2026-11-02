package com.crossborder.tenant.service;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.tenant.config.JwtTokenProvider;
import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.*;
import com.crossborder.tenant.dto.auth.AuthResponse;
import com.crossborder.tenant.dto.auth.RegisterRequest;
import com.crossborder.tenant.entity.*;
import com.crossborder.tenant.repository.TenantRepository;
import com.crossborder.tenant.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户服务 - 核心多租户管理
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 注册新租户（企业）
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名/邮箱是否存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BizException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BizException("邮箱已被使用");
        }

        // 创建租户
        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .plan(TenantPlan.valueOf(request.getPlan()))
                .status(TenantStatus.ACTIVE)
                .region("cn-beijing")
                .expiresAt(LocalDateTime.now().plusDays(30)) // 免费试用30天
                .build();
        tenant = tenantRepository.save(tenant);

        // 创建租户管理员
        TenantUser user = TenantUser.builder()
                .tenantId(tenant.getId())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .name(request.getCompanyName())
                .role(UserRole.OWNER)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        // 生成Token
        String token = jwtTokenProvider.generateToken(user.getId(), tenant.getId(), user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .user(toUserDTO(user))
                .tenant(toDTO(tenant))
                .build();
    }

    /**
     * 用户登录
     */
    public AuthResponse login(String username, String password) {
        TenantUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("密码错误");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException("账号已被禁用");
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new RuntimeException("租户不存在"));

        // 更新最后登录
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), tenant.getId(), user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .user(toUserDTO(user))
                .tenant(toDTO(tenant))
                .build();
    }

    /**
     * 获取当前租户信息
     */
    public TenantDTO getCurrentTenant() {
        String tenantId = TenantContext.getTenant();
        return toDTO(tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("租户不存在")));
    }

    /**
     * 获取当前用户信息
     */
    public TenantUserDTO getCurrentUser() {
        String userId = TenantContext.getUser();
        return toUserDTO(userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在")));
    }

    /**
     * 获取租户下的所有用户
     */
    public List<TenantUserDTO> getTenantUsers() {
        String tenantId = TenantContext.getTenant();
        return userRepository.findByTenantId(tenantId).stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    /**
     * 升级订阅计划
     */
    @Transactional
    public TenantDTO upgradePlan(String plan) {
        String tenantId = TenantContext.getTenant();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("租户不存在"));
        
        tenant.setPlan(TenantPlan.valueOf(plan));
        tenant = tenantRepository.save(tenant);
        
        return toDTO(tenant);
    }

    private TenantDTO toDTO(Tenant tenant) {
        return TenantDTO.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .domain(tenant.getDomain())
                .plan(tenant.getPlan())
                .status(tenant.getStatus())
                .region(tenant.getRegion())
                .settings(tenant.getSettings())
                .expiresAt(tenant.getExpiresAt())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    private TenantUserDTO toUserDTO(TenantUser user) {
        return TenantUserDTO.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .name(user.getName())
                .role(user.getRole())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}