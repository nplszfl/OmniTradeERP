package com.crossborder.erp.user.service.impl;

import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.user.dto.LoginRequest;
import com.crossborder.erp.user.dto.LoginResponse;
import com.crossborder.erp.user.entity.User;
import com.crossborder.erp.user.mapper.UserMapper;
import com.crossborder.erp.user.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试
 *
 * <p>⚠️ 之前是 {@code assertTrue(true)} 的假测试 —— 登录是全系统入口，
 * 却完全没有覆盖（密码错误、用户禁用、Token 生成都没测过）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
class AuthServiceImplTest {

    private UserMapper userMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private AuthServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generateToken(anyLong(), anyString())).thenReturn("mock-token");
        when(jwtUtil.generateRefreshToken(anyLong())).thenReturn("mock-refresh");

        service = new AuthServiceImpl();
        service.setRedisTemplate(redisTemplate);
        service.setPasswordEncoder(passwordEncoder);
        service.setJwtUtil(jwtUtil);
        // 反射注入 baseMapper（ServiceImpl 内部字段）
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    private User user(String username, String rawPassword, int status) {
        User u = new User();
        u.setId(1L);
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setStatus(status);
        u.setRealName("测试用户");
        return u;
    }

    private LoginRequest request(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    @Test
    @DisplayName("登录成功：生成 token + refresh token + 更新最后登录时间")
    void loginSuccess() {
        // 📌 MyBatis-Plus 3.5.6: getOne(wrapper) 会转发到 selectOne(wrapper, throwEx) 两参重载，
        //    只 stub 单参版不生效（会走真实实现 → null）
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user("admin", "admin123", 1));

        LoginResponse response = service.login(request("admin", "admin123"));

        assertNotNull(response);
        assertEquals("mock-token", response.getToken());
        assertEquals("mock-refresh", response.getRefreshToken());
        assertEquals("admin", response.getUserInfo().getUsername());
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        // 最后登录时间必须回写
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("登录失败：密码错误")
    void loginWrongPassword() {
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user("admin", "admin123", 1));
        BizException e = assertThrows(BizException.class,
                () -> service.login(request("admin", "wrong")));
        assertTrue(e.getMessage().contains("密码错误"));
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("登录失败：用户被禁用")
    void loginDisabledUser() {
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user("admin", "admin123", 0));
        BizException e = assertThrows(BizException.class,
                () -> service.login(request("admin", "admin123")));
        assertTrue(e.getMessage().contains("禁用"));
    }

    @Test
    @DisplayName("登录失败：用户不存在")
    void loginUserNotFound() {
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> service.login(request("nobody", "x")));
        assertTrue(e.getMessage().contains("用户不存在"));
    }

    @Test
    @DisplayName("登出：删除 Redis 里的 token")
    void logoutDeletesToken() {
        when(jwtUtil.getUserIdFromToken("abc")).thenReturn(1L);
        service.logout("Bearer abc");
        verify(redisTemplate).delete("token:1");
    }
}
