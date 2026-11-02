package com.crossborder.tenant.repository;

import com.crossborder.tenant.entity.Tenant;
import com.crossborder.tenant.entity.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 租户仓储
 *
 * <p>⚠️ 之前 erp-tenant-service 只有 PlatformCredentialRepository 一个仓储，
 * Tenant 实体没有任何查询入口 —— 前端 erp-web/src/views/tenant/index.vue 调用
 * {@code GET /api/tenant/list} 直接 404，页面靠 catch 里的 mock 数据兜着，
 * 看起来正常、实际是假数据。</p>
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    List<Tenant> findAllByOrderByCreatedAtDesc();

    List<Tenant> findByStatus(TenantStatus status);

    long countByStatus(TenantStatus status);
}
