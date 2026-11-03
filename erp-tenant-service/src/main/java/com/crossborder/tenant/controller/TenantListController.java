package com.crossborder.tenant.controller;

import com.crossborder.tenant.dto.TenantDTO;
import com.crossborder.tenant.dto.TenantUpsertRequest;
import com.crossborder.tenant.entity.Tenant;
import com.crossborder.tenant.entity.TenantPlan;
import com.crossborder.tenant.entity.TenantStatus;
import com.crossborder.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            // 📌 默认不返回 DELETED 的（软删的租户不该出现在管理列表里）
            tenants = tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(t -> t.getStatus() != TenantStatus.DELETED)
                    .toList();
        }

        List<TenantDTO> result = tenants.stream()
                .map(TenantListController::toDTO)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 新建租户（管理端）
     *
     * <p>📌 之前前端 erp-web/src/views/tenant/index.vue 的「新建租户」按钮
     * 只弹了个 demo 提示，根本没调后端；后端也没有对应接口。</p>
     */
    @PostMapping
    public ResponseEntity<TenantDTO> create(@RequestBody TenantUpsertRequest request) {
        if (request.getDomain() != null && !request.getDomain().isBlank()
                && tenantRepository.findAll().stream()
                    .anyMatch(t -> request.getDomain().equalsIgnoreCase(t.getDomain()))) {
            return ResponseEntity.badRequest().body(null);
        }

        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .domain(blankToNull(request.getDomain()))
                .plan(parsePlan(request.getPlan()))
                .status(parseStatus(request.getStatus()))
                .region(blankToNull(request.getRegion()))
                .settings(blankToNull(request.getSettings()))
                .expiresAt(parseDateTime(request.getExpiresAt()))
                .build();

        Tenant saved = tenantRepository.save(tenant);
        log.info("新建租户: id={}, name={}", saved.getId(), saved.getName());
        return ResponseEntity.ok(toDTO(saved));
    }

    /**
     * 编辑租户（管理端）
     */
    @PutMapping("/{id}")
    public ResponseEntity<TenantDTO> update(@PathVariable String id,
                                            @RequestBody TenantUpsertRequest request) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            tenant.setName(request.getName());
        }
        if (request.getDomain() != null) {
            tenant.setDomain(blankToNull(request.getDomain()));
        }
        if (request.getPlan() != null && !request.getPlan().isBlank()) {
            tenant.setPlan(parsePlan(request.getPlan()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            tenant.setStatus(parseStatus(request.getStatus()));
        }
        if (request.getRegion() != null) {
            tenant.setRegion(blankToNull(request.getRegion()));
        }
        if (request.getSettings() != null) {
            tenant.setSettings(blankToNull(request.getSettings()));
        }
        if (request.getExpiresAt() != null) {
            tenant.setExpiresAt(parseDateTime(request.getExpiresAt()));
        }
        Tenant saved = tenantRepository.save(tenant);
        return ResponseEntity.ok(toDTO(saved));
    }

    /**
     * 删除租户（管理端）—— 软删，置为 DELETED
     *
     * <p>📌 硬删会触发 tenant_users 的 ON DELETE CASCADE，
     * 但 tenant_usage_records / tenant_bills 等没有外键约束 → 会残留孤儿数据。
     * 所以走软删。</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }
        tenant.setStatus(TenantStatus.DELETED);
        tenantRepository.save(tenant);
        log.info("删除租户（软删）: id={}, name={}", tenant.getId(), tenant.getName());
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static TenantPlan parsePlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return TenantPlan.STARTER;
        }
        try {
            return TenantPlan.valueOf(plan.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知的套餐: {}，回退为 STARTER", plan);
            return TenantPlan.STARTER;
        }
    }

    private static TenantStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return TenantStatus.ACTIVE;
        }
        try {
            return TenantStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知的租户状态: {}，回退为 ACTIVE", status);
            return TenantStatus.ACTIVE;
        }
    }

    /** 接受 yyyy-MM-dd HH:mm:ss 和 yyyy-MM-dd 两种格式 */
    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().replace('T', ' ');
        try {
            if (v.length() <= 10) {
                return LocalDateTime.parse(v + " 00:00:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("无法解析的到期时间: {}", value);
            return null;
        }
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
