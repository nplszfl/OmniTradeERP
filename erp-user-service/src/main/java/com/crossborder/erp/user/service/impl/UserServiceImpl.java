package com.crossborder.erp.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.crossborder.erp.user.service.UserService;
import com.crossborder.erp.user.vo.DepartmentVO;
import com.crossborder.erp.user.vo.RoleVO;
import com.crossborder.erp.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_PASSWORD = "admin123";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final DepartmentMapper departmentMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> pageUsers(int current, int size, String keyword,
                                        Integer status, Long roleId, Long departmentId) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        // 按角色过滤：先查该角色的用户ID，再作为 IN 条件
        if (roleId != null) {
            List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getRoleId, roleId))
                    .stream().map(UserRole::getUserId).toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(Long.valueOf(current), Long.valueOf(size), 0L, Collections.emptyList());
            }
            wrapper.in(User::getId, userIds);
        }
        // 按部门过滤
        if (departmentId != null) {
            List<Long> userIds = userDepartmentMapper.selectList(new LambdaQueryWrapper<UserDepartment>()
                            .eq(UserDepartment::getDepartmentId, departmentId))
                    .stream().map(UserDepartment::getUserId).toList();
            if (userIds.isEmpty()) {
                return new PageResult<>(Long.valueOf(current), Long.valueOf(size), 0L, Collections.emptyList());
            }
            wrapper.in(User::getId, userIds);
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> result = userMapper.selectPage(page, wrapper);
        List<UserVO> records = result.getRecords().stream().map(UserVO::from).collect(Collectors.toList());
        fillRelations(records);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public UserVO getUserDetail(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        UserVO vo = UserVO.from(user);
        fillRelations(Collections.singletonList(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserVO userVO) {
        if (userVO == null || !StringUtils.hasText(userVO.getUsername())) {
            throw new BizException("用户名不能为空");
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, userVO.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        User user = new User();
        user.setUsername(userVO.getUsername());
        user.setRealName(userVO.getRealName());
        user.setEmail(userVO.getEmail());
        user.setPhone(userVO.getPhone());
        user.setAvatar(userVO.getAvatar());
        user.setStatus(userVO.getStatus() == null ? 1 : userVO.getStatus());
        String rawPassword = StringUtils.hasText(userVO.getPassword()) ? userVO.getPassword() : DEFAULT_PASSWORD;
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        log.info("创建用户成功: username={}", user.getUsername());
        return getUserDetail(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(UserVO userVO) {
        if (userVO == null || userVO.getId() == null) {
            throw new BizException("用户ID不能为空");
        }
        User existing = userMapper.selectById(userVO.getId());
        if (existing == null) {
            throw new BizException("用户不存在");
        }
        User user = new User();
        user.setId(userVO.getId());
        user.setRealName(userVO.getRealName());
        user.setEmail(userVO.getEmail());
        user.setPhone(userVO.getPhone());
        user.setAvatar(userVO.getAvatar());
        user.setUpdateTime(LocalDateTime.now());
        // ⚠️ 密码和状态不走这个接口：密码有专门的修改/重置接口，状态有专门的启停接口
        userMapper.updateById(user);
        return getUserDetail(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        userMapper.deleteById(id);
        // 清理关联，否则用户的角色/部门会变成「幽灵数据」
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        userDepartmentMapper.delete(new LambdaQueryWrapper<UserDepartment>().eq(UserDepartment::getUserId, id));
        log.info("删除用户成功: userId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String defaultPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        String raw = StringUtils.hasText(defaultPassword) ? defaultPassword : DEFAULT_PASSWORD;
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(raw));
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        log.info("重置用户密码: userId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new BizException("新密码长度不能少于6位");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException("原密码错误");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        log.info("用户修改密码: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        log.info("修改用户状态: userId={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> validRoleIds = roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getId).toList();
        for (Long roleId : validRoleIds) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
        log.info("分配角色: userId={}, roleIds={}", userId, validRoleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDepartments(Long userId, List<Long> departmentIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        userDepartmentMapper.delete(new LambdaQueryWrapper<UserDepartment>().eq(UserDepartment::getUserId, userId));
        if (departmentIds == null || departmentIds.isEmpty()) {
            return;
        }
        List<Long> validDeptIds = departmentMapper.selectBatchIds(departmentIds).stream()
                .map(Department::getId).toList();
        for (Long deptId : validDeptIds) {
            UserDepartment relation = new UserDepartment();
            relation.setUserId(userId);
            relation.setDepartmentId(deptId);
            relation.setCreateTime(LocalDateTime.now());
            userDepartmentMapper.insert(relation);
        }
        log.info("分配部门: userId={}, deptIds={}", userId, validDeptIds);
    }

    /**
     * 批量填充角色 / 部门（避免 N+1 查询）
     */
    private void fillRelations(List<UserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(UserVO::getId).toList();

        Map<Long, List<Role>> rolesByUser = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId, Collectors.toList())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> roleMapper.selectBatchIds(e.getValue()).stream().collect(Collectors.toList())));

        Map<Long, List<Department>> deptsByUser = userDepartmentMapper.selectList(new LambdaQueryWrapper<UserDepartment>()
                        .in(UserDepartment::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(UserDepartment::getUserId,
                        Collectors.mapping(UserDepartment::getDepartmentId, Collectors.toList())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> departmentMapper.selectBatchIds(e.getValue()).stream().collect(Collectors.toList())));

        for (UserVO vo : users) {
            vo.setRoles(rolesByUser.getOrDefault(vo.getId(), Collections.emptyList())
                    .stream().map(RoleVO::from).collect(Collectors.toList()));
            vo.setDepartments(deptsByUser.getOrDefault(vo.getId(), Collections.emptyList())
                    .stream().map(DepartmentVO::from).collect(Collectors.toList()));
        }
    }
}
