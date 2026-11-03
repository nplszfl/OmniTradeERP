package com.crossborder.tenant.controller;

import com.crossborder.tenant.entity.Tenant;
import com.crossborder.tenant.entity.TenantPlan;
import com.crossborder.tenant.entity.TenantStatus;
import com.crossborder.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户列表接口测试
 *
 * <p>⚠️ 这个接口之前不存在 —— 前端 erp-web/src/views/tenant/index.vue 调用
 * {@code GET /api/tenant/list} 直接 404，页面靠 catch 分支的 mock 数据撑着。</p>
 *
 * <p>注意：网关路由是 {@code Path=/api/tenant/**} + {@code StripPrefix=0}，
 * 所以服务收到的路径是 {@code /api/tenant/list}，控制器根路径必须是 /api/tenant。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();
        tenantRepository.save(tenant("深圳跨境电商有限公司", TenantPlan.ENTERPRISE, TenantStatus.ACTIVE));
        tenantRepository.save(tenant("ShopSmart Global", TenantPlan.PROFESSIONAL, TenantStatus.ACTIVE));
        tenantRepository.save(tenant("过期租户", TenantPlan.STARTER, TenantStatus.EXPIRED));
    }

    private Tenant tenant(String name, TenantPlan plan, TenantStatus status) {
        return Tenant.builder()
                .name(name)
                .domain(name.hashCode() + ".example.com")
                .plan(plan)
                .status(status)
                .region("cn-shenzhen")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/tenant/list 返回租户列表（裸数组）")
    void listReturnsTenants() throws Exception {
        mockMvc.perform(get("/api/tenant/list").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].plan").exists())
                .andExpect(jsonPath("$[0].status").exists());
    }

    @Test
    @DisplayName("按状态过滤")
    void listFiltersByStatus() throws Exception {
        mockMvc.perform(get("/api/tenant/list").param("status", "EXPIRED").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("过期租户"));
    }

    @Test
    @DisplayName("非法状态过滤 → 空列表而不是 500")
    void listHandlesInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/tenant/list").param("status", "NOT_A_STATUS").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("管理端列表不要求登录（与其它 ERP 服务一致，网关也不鉴权）")
    void listIsPermitAllForAdmin() throws Exception {
        // 📌 修复前的行为是 403：SecurityConfig 写了 anyRequest().authenticated()，
        //    却没有任何认证 provider / JWT 过滤器往 SecurityContext 放 Authentication
        //    → 除 /api/v1/auth/** 外所有接口一律 403 **空 body**，
        //    前端租户页面整页空白。
        //    现在 /api/tenant/** 显式 permitAll（管理端），
        //    /api/v1/tenant/** 保持 authenticated（租户自助）。
        mockMvc.perform(get("/api/tenant/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("租户自助接口未带 token → 401 + JSON 报文（不是空 body）")
    void selfServiceRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/tenant"))
                .andExpect(status().isUnauthorized())
                // 📌 Spring Security 默认在未认证时返回 403 **空 body**，
                //    前端拿不到任何信息 → 显式配了 AuthenticationEntryPoint 返回 401 + JSON。
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("仓储层可按状态统计")
    void repositoryCountsByStatus() {
        assertEquals(2L, tenantRepository.countByStatus(TenantStatus.ACTIVE));
        assertEquals(1L, tenantRepository.countByStatus(TenantStatus.EXPIRED));
        assertTrue(tenantRepository.findAllByOrderByCreatedAtDesc().size() >= 3);
    }
}
