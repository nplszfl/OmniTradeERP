package com.crossborder.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 租户新增/编辑请求
 *
 * <p>📌 管理端（{@code /api/tenant/**}）用的入参，和租户自助端
 * {@code RegisterRequest}（{@code /api/v1/auth/register}）不是一回事：
 * 自助注册会连带建管理员账号、发 JWT；管理端只建租户主体。</p>
 */
@Data
public class TenantUpsertRequest {

    /** 租户名称（企业名） */
    @NotBlank(message = "租户名称不能为空")
    private String name;

    /** 自定义域名，唯一 */
    private String domain;

    /** 套餐：STARTER / PROFESSIONAL / BUSINESS / ENTERPRISE */
    private String plan;

    /** 状态：PENDING / ACTIVE / SUSPENDED / EXPIRED */
    private String status;

    /** 数据区域 */
    private String region;

    /** 到期时间，格式 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd */
    private String expiresAt;

    /** 租户配置 JSON 字符串 */
    private String settings;
}
