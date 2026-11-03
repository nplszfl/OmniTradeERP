package com.crossborder.tenant.config;

import com.crossborder.erp.common.exception.BizException;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 租户上下文 - ThreadLocal存储当前请求的租户信息
 */
@Getter
public class TenantContext {
    
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    
    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }
    
    public static String getTenant() {
        return CURRENT_TENANT.get();
    }
    
    public static void setUser(String userId) {
        CURRENT_USER.set(userId);
    }
    
    public static String getUser() {
        return CURRENT_USER.get();
    }
    
    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
    }
    
    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * 临时切换租户作用域（管理端按 tenantId 查某个租户的数据时用）
     *
     * <p>📌 为什么要这个：看板/账单/用量/套餐这些服务的实现都直接读
     * {@code TenantContext.getTenant()}（来自 JWT），而<b>管理端</b>的 ERP 控制台
     * 手里是 erp-user-service 的 token，里面<b>没有 tenantId</b> → 拿不到上下文。
     * 改法不应该是给每个 Service 方法都加一个 tenantId 参数（几十处调用），
     * 而是用一个作用域把 ThreadLocal 临时换掉，退出时恢复。</p>
     *
     * <pre>{@code
     * try (TenantContext.Scope ignored = TenantContext.scope(tenantId)) {
     *     return dashboardService.getDashboard();
     * }
     * }</pre>
     */
    public static Scope scope(String tenantId) {
        return new Scope(tenantId);
    }

    /**
     * 解析本次请求要操作哪个租户
     *
     * <p>优先级：显式传入的 tenantId（管理端） > JWT 里的租户上下文（租户自助）。</p>
     *
     * @param requestedTenantId 显式传入的租户 ID，可为空
     * @return 租户 ID
     * @throws BizException 两个来源都拿不到时抛出（会转成 400）
     */
    public static String resolve(String requestedTenantId) {
        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId.trim();
        }
        String current = getTenant();
        if (StringUtils.hasText(current)) {
            return current;
        }
        throw new BizException("缺少租户标识：请传 tenantId 参数，或使用租户账号登录后调用");
    }

    /**
     * 租户作用域（AutoCloseable，退出时自动恢复原上下文）
     */
    public static final class Scope implements AutoCloseable {
        private final String previousTenant;
        private final String previousUser;

        private Scope(String tenantId) {
            this.previousTenant = getTenant();
            this.previousUser = getUser();
            setTenant(tenantId);
        }

        @Override
        public void close() {
            if (previousTenant == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previousTenant);
            }
            if (previousUser == null) {
                CURRENT_USER.remove();
            } else {
                CURRENT_USER.set(previousUser);
            }
        }
    }
}