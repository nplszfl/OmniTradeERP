package com.crossborder.erp.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.user.entity.Department;
import com.crossborder.erp.user.entity.Role;
import com.crossborder.erp.user.entity.User;
import com.crossborder.erp.user.entity.UserDepartment;
import com.crossborder.erp.user.entity.UserRole;
import com.crossborder.erp.user.mapper.DepartmentMapper;
import com.crossborder.erp.user.mapper.RoleMapper;
import com.crossborder.erp.user.mapper.UserDepartmentMapper;
import com.crossborder.erp.user.mapper.UserMapper;
import com.crossborder.erp.user.mapper.UserRoleMapper;
import com.crossborder.erp.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户管理服务单元测试
 *
 * <p>⚠️ 之前的 UserServiceTest 是 {@code assertTrue(true)} 的假测试，
 * 而且它测的还是一个<b>从没被调用过</b>的 UserService（登录其实走 AuthService）。
 * 现在这个测试针对的是真正被前端调用的用户管理服务。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
class UserServiceImplTest {

    private UserMapper userMapper;
    private RoleMapper roleMapper;
    private DepartmentMapper departmentMapper;
    private UserRoleMapper userRoleMapper;
    private UserDepartmentMapper userDepartmentMapper;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        roleMapper = mock(RoleMapper.class);
        departmentMapper = mock(DepartmentMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        userDepartmentMapper = mock(UserDepartmentMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new UserServiceImpl(userMapper, roleMapper, departmentMapper,
                userRoleMapper, userDepartmentMapper, passwordEncoder);
    }

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setStatus(1);
        return user;
    }

    @Test
    @DisplayName("创建用户：密码加密存储 + 用户名查重")
    void createUserEncodesPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        UserVO input = new UserVO();
        input.setUsername("newbie");
        input.setPassword("secret123");
        input.setRealName("新人");

        // insert 后 MyBatis-Plus 回填 id，这里模拟
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return 1;
        });
        when(userMapper.selectById(100L)).thenAnswer(inv -> {
            User u = new User();
            u.setId(100L);
            u.setUsername("newbie");
            u.setPassword(passwordEncoder.encode("secret123"));
            u.setStatus(1);
            return u;
        });
        when(userRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userDepartmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        UserVO result = service.createUser(input);
        assertEquals(100L, result.getId());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        String stored = captor.getValue().getPassword();
        assertNotNull(stored);
        assertTrue(stored.startsWith("$2a$"), "密码必须是 BCrypt 散列，实际: " + stored);
        assertTrue(passwordEncoder.matches("secret123", stored));
    }

    @Test
    @DisplayName("创建用户：用户名重复 → 报错")
    void createUserRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        UserVO input = new UserVO();
        input.setUsername("admin");
        input.setPassword("x");
        BizException e = assertThrows(BizException.class, () -> service.createUser(input));
        assertTrue(e.getMessage().contains("用户名已存在"));
    }

    @Test
    @DisplayName("修改密码：原密码错 → 拒绝")
    void changePasswordRejectsWrongOldPassword() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        BizException e = assertThrows(BizException.class,
                () -> service.changePassword(1L, "wrong", "newpass123"));
        assertTrue(e.getMessage().contains("原密码错误"));
        verify(userMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("修改密码：新密码太短 → 拒绝")
    void changePasswordRejectsShortPassword() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        BizException e = assertThrows(BizException.class,
                () -> service.changePassword(1L, "admin123", "123"));
        assertTrue(e.getMessage().contains("6位"));
    }

    @Test
    @DisplayName("修改密码：正常路径 → 写入新散列")
    void changePasswordUpdatesHash() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        service.changePassword(1L, "admin123", "newpass123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(passwordEncoder.matches("newpass123", captor.getValue().getPassword()));
    }

    @Test
    @DisplayName("分配角色：整体覆盖 —— 先删旧关联再插新的")
    void assignRolesReplacesOldRelations() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        Role role = new Role();
        role.setId(5L);
        when(roleMapper.selectBatchIds(any())).thenReturn(Arrays.asList(role));

        service.assignRoles(1L, Arrays.asList(5L, 999L));

        verify(userRoleMapper, times(1)).delete(any());
        verify(userRoleMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("分配角色：传空列表 → 只清理不新增")
    void assignRolesWithEmptyListOnlyClears() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        service.assignRoles(1L, Collections.emptyList());
        verify(userRoleMapper, times(1)).delete(any());
        verify(userRoleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("删除用户：同时清理角色与部门关联（否则留下幽灵数据）")
    void deleteUserCleansRelations() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        service.deleteUser(1L);
        verify(userMapper, times(1)).deleteById(anyLong());
        verify(userRoleMapper, times(1)).delete(any());
        verify(userDepartmentMapper, times(1)).delete(any());
    }

    @Test
    @DisplayName("更新状态：只改 status，不动其他字段")
    void updateStatusOnlyTouchesStatus() {
        when(userMapper.selectById(1L)).thenReturn(existingUser());
        service.updateStatus(1L, 0);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getPassword());
    }

    @Test
    @DisplayName("分页查询：按角色过滤 —— 角色下无用户时直接返回空页（不查 DB）")
    void pageFiltersByRoleIdWithNoUsers() {
        when(userRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResult<UserVO> result = service.pageUsers(1, 10, null, null, 5L, null);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("分页查询：按角色过滤 —— 把角色下的用户ID作为 IN 条件传给 DB")
    void pageFiltersByRoleIdAppliesInCondition() {
        UserRole ur = new UserRole();
        ur.setUserId(1L);
        ur.setRoleId(5L);
        when(userRoleMapper.selectList(any())).thenReturn(Arrays.asList(ur));
        when(userMapper.selectPage(any(), any())).thenAnswer(inv -> {
            IPage<User> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(existingUser()));
            page.setTotal(1);
            return page;
        });
        when(userRoleMapper.selectList(any())).thenReturn(Arrays.asList(ur));
        when(userDepartmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResult<UserVO> result = service.pageUsers(1, 10, null, null, 5L, null);
        assertEquals(1L, result.getTotal());
        verify(userMapper, times(1)).selectPage(any(), any());
    }

    @Test
    @DisplayName("分页查询：关键字模糊匹配")
    void pageFiltersByKeyword() {
        when(userMapper.selectPage(any(), any())).thenAnswer(inv -> {
            IPage<User> page = inv.getArgument(0);
            page.setRecords(Collections.singletonList(existingUser()));
            page.setTotal(1);
            return page;
        });
        when(userRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userDepartmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResult<UserVO> result = service.pageUsers(1, 10, "adm", null, null, null);
        assertEquals(1L, result.getTotal());
        assertEquals("admin", result.getRecords().get(0).getUsername());
    }
}
