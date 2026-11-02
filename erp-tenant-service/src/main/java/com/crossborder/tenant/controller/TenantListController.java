package com.crossborder.tenant.controller;

import com.crossborder.tenant.dto.TenantDTO;
import com.crossborder.tenant.entity.Tenant;
import com.crossborder.tenant.entity.TenantStatus;
import com.crossborder.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户列表控制器
 *
 * <p>网关路由：{@code Path=/api/tenant/**} + {@code StripPrefix=0}
 * → 服务收到的路径形如 /api/tenant/list，与本控制器的根路径一致。</p>
 *
 * <p>⚠️ 注意与 {@link TenantController} 的区别：后者根路径是 /api/v1/tenant，
 * 是 SaaS 租户自助管理（订阅、升级），两者不是一回事。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantListController {

    private final TenantRepository tenantRepository;

    /**
     * 租户列表（管理端用）
     *
     * @param status 可选状态过滤 ACTIVE / EXPIRED / SUSPENDED
     */
    @GetMapping("/list")
    public ResponseEntity<List<TenantDTO>> list(@RequestParam(required = false) String status) {
        List<Tenant> tenants;
        if (status != null && !status.isBlank()) {
            try {
                tenants = tenantRepository.findByStatus(TenantStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("非法的租户状态过滤条件: {}", status);
                tenants = new ArrayList<>();
            }
        } else {
            tenants = tenantRepository.findAllByOrderByCreatedAtDesc();
        }

        List<TenantDTO> result = tenants.stream()
                .map(TenantListController::toDTO)
                .toList();
        return ResponseEntity.ok(result);
    }

    private static TenantDTO toDTO(Tenant t) {
        return TenantDTO.builder()
                .id(t.getId())
                .name(t.getName())
                .domain(t.getDomain())
                .plan(t.getPlan())
                .status(t.getStatus())
                .region(t.getRegion())
                .settings(t.getSettings())
                .expiresAt(t.getExpiresAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
