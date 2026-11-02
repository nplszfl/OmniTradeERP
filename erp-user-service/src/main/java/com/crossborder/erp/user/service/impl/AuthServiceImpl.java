package com.crossborder.erp.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.user.dto.LoginRequest;
import com.crossborder.erp.user.dto.LoginResponse;
import com.crossborder.erp.user.dto.RegisterRequest;
import com.crossborder.erp.user.entity.User;
import com.crossborder.erp.user.mapper.UserMapper;
import com.crossborder.erp.user.service.AuthService;
import com.crossborder.erp.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthServiceImpl extends ServiceImpl<UserMapper, User> implements AuthService {

    private StringRedisTemplate redisTemplate;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Autowired
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired
    public void setJwtUtil(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = getOne(wrapper);

        if (user == null) {
            throw new BizException("用户不存在");
        }

        if (user.getStatus() == 0) {
            throw new BizException("用户已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("密码错误");
        }

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 生成Refresh Token
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 存储到Redis
        String redisKey = "token:" + user.getId();
        redisTemplate.opsForValue().set(redisKey, token, jwtExpiration, TimeUnit.MILLISECONDS);

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        updateById(user);

        log.info("用户登录成功: {}", user.getUsername());

        return new LoginResponse(
                token,
                refreshToken,
                new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRealName(), user.getEmail(), user.getAvatar())
        );
    }

    @Override
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId != null) {
            String redisKey = "token:" + userId;
            redisTemplate.delete(redisKey);
            log.info("用户登出: userId={}", userId);
        }
    }

    @Override
    public void register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (getOne(wrapper) != null) {
            throw new BizException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        save(user);
        log.info("用户注册成功: {}", user.getUsername());
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        if (userId == null) {
            throw new BizException("Refresh Token无效");
        }

        User user = getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        String newToken = jwtUtil.generateToken(userId, user.getUsername());
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        String redisKey = "token:" + userId;
        redisTemplate.opsForValue().set(redisKey, newToken, jwtExpiration, TimeUnit.MILLISECONDS);

        return new LoginResponse(
                newToken,
                newRefreshToken,
                new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRealName(), user.getEmail(), user.getAvatar())
        );
    }
}
