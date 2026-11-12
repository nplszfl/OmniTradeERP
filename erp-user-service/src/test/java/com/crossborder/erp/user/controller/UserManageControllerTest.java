package com.crossborder.erp.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.crossborder.erp.user.util.JwtUtil;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理接口契约测试（MockMvc + 真实 H2）
 *
 * <p>⚠️ 这组测试锁死的是<b>响应形态</b>，不是业务逻辑 ——
 * 前端 {@code erp-web/src/api/user.ts} 里有的读 {@code res.data.records}，
 * 有的读 {@code res || []}，有的读 {@code res.roles}。
 * 后端一旦把 Result 包装加错或漏掉，前端<b>不会报错</b>，
 * 只会静默显示空白 —— 所以必须用测试钉死。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserManageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    /** 管理员的真实 token（不是 mock principal）—— 走完整链路：filter 验签 → 查库取角色 → 授权判定 */
    private String adminToken;

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

        jdbcTemplate.update("INSERT INTO t_role (id, role_code, role_name, status, sort_order) VALUES (1, 'ADMIN', '管理员', 1, 1)");
        jdbcTemplate.update("INSERT INTO t_role (id, role_code, role_name, status, sort_order) VALUES (2, 'OPS', '运营', 1, 2)");
        jdbcTemplate.update("INSERT INTO t_permission (id, parent_id, permission_code, permission_name, permission_type, url, sort_order) VALUES (1, 0, 'DASHBOARD', '看板', 'menu', '/dashboard', 1)");
        jdbcTemplate.update("INSERT INTO t_permission (id, parent_id, permission_code, permission_name, permission_type, url, sort_order) VALUES (2, 1, 'DASHBOARD_VIEW', '看板查看', 'button', '/dashboard', 1)");
        jdbcTemplate.update("INSERT INTO t_department (id, dept_code, dept_name, parent_id, sort_order, status) VALUES (1, 'HQ', '总公司', 0, 1, 1)");
        jdbcTemplate.update("INSERT INTO t_department (id, dept_code, dept_name, parent_id, sort_order, status) VALUES (2, 'OPS', '运营部', 1, 2, 1)");
        jdbcTemplate.update("INSERT INTO t_user (id, username, password, real_name, email, phone, status) VALUES (1, 'admin', 'x', '系统管理员', 'a@b.com', '13800138000', 1)");
        // 📌 **给 admin 挂上 ADMIN 角色** —— 授权判定查的是库里的真实角色，不是 token 里的快照
        jdbcTemplate.update("INSERT INTO t_user_role (id, user_id, role_id) VALUES (1, 1, 1)");

        adminToken = jwtUtil.generateToken(1L, "admin");
    }

    /** 给请求带上管理员 token */
    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + adminToken);
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mockMvc.perform(authed((get(url))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("GET /user/list 必须返回 Result 包装的分页（前端读 res.data.records）")
    void userListIsWrappedPage() throws Exception {
        JsonNode json = getJson("/user/list?current=1&size=10");

        assertEquals(200, json.get("code").asInt());
        assertTrue(json.has("data"), "分页接口必须包 Result，前端读的是 res.data.records");
        JsonNode data = json.get("data");
        assertTrue(data.has("records"));
        assertTrue(data.has("total"));
        assertEquals(1, data.get("total").asInt());
        assertEquals("admin", data.get("records").get(0).get("username").asText());
    }

    @Test
    @DisplayName("用户列表里不能出现 password 字段")
    void userListNeverLeaksPassword() throws Exception {
        JsonNode json = getJson("/user/list?current=1&size=10");
        JsonNode record = json.get("data").get("records").get(0);
        assertTrue(record.get("password") == null || record.get("password").isNull(),
                "密码绝不能随响应回传");
    }

    @Test
    @DisplayName("GET /user/{id} 返回裸对象（前端读 res.username）")
    void userDetailIsBareObject() throws Exception {
        JsonNode json = getJson("/user/1");
        assertEquals("admin", json.get("username").asText());
        assertTrue(json.get("code") == null, "单个对象不应包 Result");
    }

    @Test
    @DisplayName("GET /role/list 不带分页参数 → 裸数组（前端 res || []）")
    void roleListWithoutPagingIsBareArray() throws Exception {
        JsonNode json = getJson("/role/list");
        assertTrue(json.isArray(), "不带 current/size 时必须返回裸数组");
        assertEquals(2, json.size());
        assertEquals("ADMIN", json.get(0).get("roleCode").asText());
    }

    @Test
    @DisplayName("GET /role/list 带分页参数 → Result 包装（前端 res.data.records）")
    void roleListWithPagingIsWrapped() throws Exception {
        JsonNode json = getJson("/role/list?current=1&size=10");
        assertTrue(json.has("data"), "带分页参数时必须返回 Result 包装");
        assertEquals(2, json.get("data").get("records").size());
    }

    @Test
    @DisplayName("GET /role/{id} 返回裸对象且带 permissions（前端 roleDetail.permissions）")
    void roleDetailIncludesPermissions() throws Exception {
        jdbcTemplate.update("INSERT INTO t_role_permission (id, role_id, permission_id) VALUES (1, 1, 1)");
        JsonNode json = getJson("/role/1");
        assertTrue(json.get("code") == null);
        assertTrue(json.has("permissions"), "角色详情必须带 permissions 数组");
        assertEquals(1, json.get("permissions").size());
    }

    @Test
    @DisplayName("GET /permission/tree 返回裸数组且带 children（前端 permissionTree.value = res || []）")
    void permissionTreeIsBareArray() throws Exception {
        JsonNode json = getJson("/permission/tree");
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        assertEquals("DASHBOARD", json.get(0).get("permissionCode").asText());
        assertEquals(1, json.get(0).get("children").size(), "子权限必须挂到 children");
        assertEquals("/dashboard", json.get(0).get("path").asText(), "前端读 path，实体列是 url");
    }

    @Test
    @DisplayName("GET /department/tree 返回裸数组且带 parentName")
    void departmentTreeIsBareArray() throws Exception {
        JsonNode json = getJson("/department/tree");
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        assertEquals("总公司", json.get(0).get("deptName").asText());
        assertEquals(1, json.get(0).get("children").size());
        assertEquals("总公司", json.get(0).get("children").get(0).get("parentName").asText());
    }

    @Test
    @DisplayName("GET /department/list 返回裸数组")
    void departmentListIsBareArray() throws Exception {
        JsonNode json = getJson("/department/list");
        assertTrue(json.isArray());
        assertEquals(2, json.size());
    }

    @Test
    @DisplayName("创建用户 → 密码加密存储，响应不含密码")
    void createUserHashesPassword() throws Exception {
        String body = "{\"username\":\"newuser\",\"password\":\"secret123\",\"realName\":\"新人\",\"email\":\"n@b.com\"}";
        String response = mockMvc.perform(authed((post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(response);
        assertEquals("newuser", json.get("username").asText());
        assertTrue(json.get("password") == null || json.get("password").isNull());

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE username = 'newuser'", String.class);
        assertTrue(stored != null && !stored.equals("secret123"), "密码必须加密存储");
        assertTrue(stored.startsWith("$2a$"), "应该是 BCrypt 散列，实际: " + stored);
    }

    @Test
    @DisplayName("用户名重复 → 400")
    void duplicateUsernameRejected() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"secret123\"}";
        mockMvc.perform(authed((post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("分配角色后，用户列表里的 roles 字段被填充")
    void assignRolesFillsUserRoles() throws Exception {
        mockMvc.perform(authed((post("/user/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1,2]}"))))
                .andExpect(status().isOk());

        JsonNode json = getJson("/user/list?current=1&size=10");
        JsonNode roles = json.get("data").get("records").get(0).get("roles");
        assertTrue(roles.isArray() && roles.size() == 2, "用户列表必须带角色数组");
    }

    @Test
    @DisplayName("分配权限后，角色详情的 permissions 被刷新")
    void assignPermissionsReplacesOldOnes() throws Exception {
        mockMvc.perform(authed((post("/role/1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[1,2]}"))))
                .andExpect(status().isOk());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_role_permission WHERE role_id = 1", Integer.class));

        // 整体覆盖：重新分配后旧关联应被清掉
        mockMvc.perform(authed((post("/role/1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[2]}"))))
                .andExpect(status().isOk());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_role_permission WHERE role_id = 1", Integer.class));
    }

    @Test
    @DisplayName("删除有子部门的部门 → 400")
    void deleteDepartmentWithChildrenRejected() throws Exception {
        mockMvc.perform(authed((delete("/department/1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("修改状态 → 数据库同步")
    void updateStatusPersists() throws Exception {
        mockMvc.perform(authed((put("/user/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))))
                .andExpect(status().isOk());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT status FROM t_user WHERE id = 1", Integer.class));
    }
}
