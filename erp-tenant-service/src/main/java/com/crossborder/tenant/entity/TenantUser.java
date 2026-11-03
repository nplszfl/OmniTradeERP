package com.crossborder.tenant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 租户用户实体
 */
@Entity
@Table(name = "tenant_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 租户ID
     */
    @Column(nullable = false)
    private String tenantId;

    /**
     * 用户名
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * 邮箱
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * 密码（加密）
     *
     * 🔴 加 {@code @JsonIgnore} 是双保险：现在 /api/v1/auth/me 走 DTO（password 是 null），
     *    但只要哪天有人图省事直接返回实体，bcrypt 哈希就会**原样发给前端**。
     *    密码哈希泄露 = 可离线爆破，不该依赖调用方自觉。
     */
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 姓名
     */
    private String name;

    /**
     * 角色
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * 状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = UserStatus.ACTIVE;
    }
}