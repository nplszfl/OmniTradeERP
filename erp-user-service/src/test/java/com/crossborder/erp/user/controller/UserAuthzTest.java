package com.crossborder.erp.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crossborder.erp.user.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账户系统鉴权测试 —— 🔴 **安全回归的第一道防线**
 *
 * <p><b>这里锁死的不是业务逻辑，是「谁能做什么」</b>。
 * 2026-11-12 发现并修掉的缺陷（规则 100 / 105 同族）：
 * {@code SecurityConfig} 写的是 {@code anyRequest().permitAll()} →
 * <b>整个账户系统一个请求都不用带 token</b>；而
 * {@code POST /api/user/{id}/reset-password} 还接受调用方指定的 {@code defaultPassword}
 * → <b>任何人都能把任意用户的密码改成自己知道的值</b>
 * （再配合 permitAll 的 {@code POST /api/v1/auth/register} → 注册一个账号就能改管理员密码）
 * = <b>未认证即可完成账号接管</b>。</p>
 *
 * <p>📌 **这些测试必须用真实 token**（不是 mock principal）：
 * mock principal 只证明「有个认证上下文」，不证明
 * <b>过滤器验签 → 查库取角色 → 授权判定</b> 这条链路是通的。
 * 假绿比没测试更危险（规则 48 / 127）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-12
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAuthzTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM t_user_role");
        jdbcTemplate.update("DELETE FROM t_role_permission");
        jdbcTemplate.update("DELETE FROM t_user_department");
        jdbcTemplate.update("DELETE FROM t_user");
        jdbcTemplate.update("DELETE FROM t_role");
        jdbcTemplate.update("DELETE FROM t_permission");
        jdbcTemplate.update("DELETE FROM t_department");

        jdbcTemplate.update("INSERT INTO t_role (id, role_code, role_name, status, sort_order) VALUES (1, 'SUPER_ADMIN', '超级管理员', 1, 1)");
        jdbcTemplate.update("INSERT INTO t_role (id, role_code, role_name, status, sort_order) VALUES (2, 'OPS', '运营', 1, 2)");
        // 1 = admin（ADMIN 角色），2 = ops（只有 OPS 角色）
        jdbcTemplate.update("INSERT INTO t_user (id, username, password, real_name, email, phone, status) VALUES (1, 'admin', 'x', '系统管理员', 'a@b.com', '13800138000', 1)");
        jdbcTemplate.update("INSERT INTO t_user (id, username, password, real_name, email, phone, status) VALUES (2, 'ops', 'x', '运营员', 'o@b.com', '13800138001', 1)");
        jdbcTemplate.update("INSERT INTO t_user_role (id, user_id, role_id) VALUES (1, 1, 1)");
        jdbcTemplate.update("INSERT INTO t_user_role (id, user_id, role_id) VALUES (2, 2, 2)");
    }

    // ─────────────────────────────────────────────────────────
    // ① 匿名请求 → 必须被挡住
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 匿名重置别人密码 → 401（修复前是 200 = 未认证即可接管账号）")
    void anonymousResetPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/user/1/reset-password"))
                .andExpect(status().isUnauthorized());

        // 📌 **不只是状态码**：密码必须真的没被改
        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        assertEquals("x", stored, "未认证的请求**不许**改掉任何人的密码");
    }

    @Test
    @DisplayName("🔴 匿名带 defaultPassword（请求体）→ 仍然 401（参数不是通行证）")
    void anonymousResetPasswordWithParamIsRejected() throws Exception {
        mockMvc.perform(post("/user/1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultPassword\":\"attacker-known\"}"))
                .andExpect(status().isUnauthorized());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        assertEquals("x", stored, "未认证 + 自带 defaultPassword → 密码不许变");
    }

    @Test
    @DisplayName("🔴 口令走 URL 查询串 → **必须被忽略**（明文口令不许进访问日志）")
    void resetPasswordViaQueryStringIsIgnored() throws Exception {
        // 📌 2026-11-12 修：口令原来用 @RequestParam → 可以走 query string
        //    → nginx / ingress / 网关的访问日志里 `$request` = 完整请求行（含 query）
        //    → **明文口令进访问日志**。现在口令只认请求体。
        String adminToken = jwtUtil.generateToken(1L, "admin");
        mockMvc.perform(post("/user/1/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("defaultPassword", "leaked-in-url"))
                .andExpect(status().isOk());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        // 查询串里的口令**不许**生效 → 落库的应该是「默认口令」的 BCrypt，而不是 "leaked-in-url" 的
        assertTrue(stored != null && stored.startsWith("$2a$"), "必须 BCrypt 存储，实际: " + stored);
        assertTrue(!stored.equals("leaked-in-url"), "不能明文存密码");
        assertFalse(passwordEncoder.matches("leaked-in-url", stored),
                "❌ query string 里的口令居然生效了 → 它仍然能被写进访问日志");
    }

    @Test
    @DisplayName("🔴 匿名建用户 → 401（否则任何人都能造账号）")
    void anonymousCreateUserIsRejected() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"evil\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE username = 'evil'", Integer.class);
        assertEquals(0, count, "未认证不许创建用户");
    }

    @Test
    @DisplayName("🔴 匿名删用户 → 401")
    void anonymousDeleteUserIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/user/2"))
                .andExpect(status().isUnauthorized());
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user WHERE id = 2", Integer.class);
        assertEquals(1, count, "未认证不许删用户");
    }

    @Test
    @DisplayName("🔴 匿名改用户状态 → 401（禁用别人的账号也是攻击面）")
    void anonymousUpdateStatusIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/user/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("🔴 匿名读用户列表 → 401（否则用户表可以被随便枚举）")
    void anonymousUserListIsRejected() throws Exception {
        mockMvc.perform(get("/user/list?current=1&size=10"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────
    // ② 已登录但不是管理员 → 管理操作必须被挡住
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 非管理员重置别人密码 → 403（登录了也不等于能管人）")
    void nonAdminResetPasswordIsForbidden() throws Exception {
        String opsToken = jwtUtil.generateToken(2L, "ops");
        mockMvc.perform(post("/user/1/reset-password")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isForbidden());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        assertEquals("x", stored, "非管理员不许改别人的密码");
    }

    @Test
    @DisplayName("🔴 非管理员建角色 / 分配角色 → 403")
    void nonAdminCannotManageRoles() throws Exception {
        String opsToken = jwtUtil.generateToken(2L, "ops");
        mockMvc.perform(post("/user/1/roles")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1]}"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────
    // ③ 管理员 → 放行（证明上面的 401/403 不是「一律拒绝」）
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ 管理员重置密码 → 200（说明前两条不是「把门焊死了」）")
    void adminCanResetPassword() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin");
        mockMvc.perform(post("/user/1/reset-password")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        assertTrue(stored != null && !stored.equals("x"), "管理员重置后密码必须真的变了");
    }

    @Test
    @DisplayName("✅ 管理员带 defaultPassword（请求体）→ 200 且密码按参数落库")
    void adminCanResetPasswordToGivenValue() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin");
        mockMvc.perform(post("/user/1/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultPassword\":\"admin-set-pwd\"}"))
                .andExpect(status().isOk());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        assertTrue(stored != null && stored.startsWith("$2a$"), "必须 BCrypt 存储，实际: " + stored);
        assertTrue(!stored.equals("admin-set-pwd"), "不能明文存密码");
    }

    @Test
    @DisplayName("✅ 普通用户改**自己**的密码 → 200（自助改密不能被误伤）")
    void userCanChangeOwnPassword() throws Exception {
        String opsToken = jwtUtil.generateToken(2L, "ops");
        jdbcTemplate.update("UPDATE t_user SET password = ? WHERE id = 2",
                "{noop}x");
        // 用 BCrypt 存一个已知密码
        String bcrypt = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("oldpass");
        jdbcTemplate.update("UPDATE t_user SET password = ? WHERE id = 2", bcrypt);

        mockMvc.perform(post("/user/change-password")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"oldpass\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 带 token 但 body 里写别人的 userId → 400（身份只认 token）")
    void changePasswordRejectsForeignUserId() throws Exception {
        String opsToken = jwtUtil.generateToken(2L, "ops");
        mockMvc.perform(post("/user/change-password")
                        .header("Authorization", "Bearer " + opsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"1\",\"oldPassword\":\"x\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("🔴 无效 / 伪造 token → 401（不是靠「有 Authorization 头」就放行）")
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/user/list?current=1&size=10")
                        .header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("🔴 登录 / 注册仍然可以匿名访问（不能把门焊死）")
    void authEndpointsStayPublic() throws Exception {
        // 📌 **路径必须是 `/auth/login`**（网关 StripPrefix=1 之后的**服务侧真实路径**）。
        //    写错前缀 → 404 → 而「404 != 401」也会让「只断言 not 401」的弱断言**假绿**。
        //    → 所以这里同时断言 **不是 404**（映射必须存在）+ **不是 401**（没被鉴权拦住）。
        int status = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andReturn().getResponse().getStatus();
        assertTrue(status != 401, "登录接口必须允许匿名访问，实际: " + status);
        assertTrue(status != 404, "登录路径写错了（网关 StripPrefix=1 → 服务侧是 /auth/login），实际: " + status);
    }
}
