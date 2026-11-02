package com.crossborder.erp.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crossborder.erp.common.exception.BizException;
import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.user.entity.Permission;
import com.crossborder.erp.user.entity.Role;
import com.crossborder.erp.user.entity.RolePermission;
import com.crossborder.erp.user.entity.UserRole;
import com.crossborder.erp.user.mapper.PermissionMapper;
import com.crossborder.erp.user.mapper.RoleMapper;
import com.crossborder.erp.user.mapper.RolePermissionMapper;
import com.crossborder.erp.user.mapper.UserRoleMapper;
import com.crossborder.erp.user.service.PermissionService;
import com.crossborder.erp.user.service.RoleService;
import com.crossborder.erp.user.vo.PermissionVO;
import com.crossborder.erp.user.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;

    @Override
    public List<RoleVO> listRoles(Integer status) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(status != null, Role::getStatus, status)
                .orderByAsc(Role::getSortOrder)
                .orderByAsc(Role::getId);
        return roleMapper.selectList(wrapper).stream().map(RoleVO::from).collect(Collectors.toList());
    }

    @Override
    public PageResult<RoleVO> pageRoles(int current, int size, String keyword, Integer status) {
        Page<Role> page = new Page<>(current, size);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Role::getRoleName, keyword)
                    .or().like(Role::getRoleCode, keyword));
        }
        if (status != null) {
            wrapper.eq(Role::getStatus, status);
        }
        wrapper.orderByAsc(Role::getSortOrder).orderByAsc(Role::getId);

        Page<Role> result = roleMapper.selectPage(page, wrapper);
        List<RoleVO> records = result.getRecords().stream().map(RoleVO::from).collect(Collectors.toList());
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public RoleVO getRoleDetail(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        RoleVO vo = RoleVO.from(role);
        List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, id))
                .stream().map(RolePermission::getPermissionId).toList();
        vo.setPermissions(permissionIds.isEmpty()
                ? Collections.emptyList()
                : permissionService.listByIds(permissionIds));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createRole(RoleVO roleVO) {
        if (roleVO == null || !StringUtils.hasText(roleVO.getRoleCode())) {
            throw new BizException("角色编码不能为空");
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleVO.getRoleCode()));
        if (count != null && count > 0) {
            throw new BizException("角色编码已存在");
        }
        Role role = new Role();
        role.setRoleCode(roleVO.getRoleCode());
        role.setRoleName(roleVO.getRoleName());
        role.setDescription(roleVO.getDescription());
        role.setStatus(roleVO.getStatus() == null ? 1 : roleVO.getStatus());
        role.setSortOrder(roleVO.getSort() == null ? 0 : roleVO.getSort());
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());

        roleMapper.insert(role);
        log.info("创建角色成功: roleCode={}", role.getRoleCode());
        return RoleVO.from(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO updateRole(RoleVO roleVO) {
        if (roleVO == null || roleVO.getId() == null) {
            throw new BizException("角色ID不能为空");
        }
        Role existing = roleMapper.selectById(roleVO.getId());
        if (existing == null) {
            throw new BizException("角色不存在");
        }
        Role role = new Role();
        role.setId(roleVO.getId());
        role.setRoleName(roleVO.getRoleName());
        role.setDescription(roleVO.getDescription());
        role.setStatus(roleVO.getStatus());
        role.setSortOrder(roleVO.getSort());
        role.setUpdateTime(LocalDateTime.now());
        // roleCode 不允许改：它是权限标识，改了会导致已有的授权关系失效
        roleMapper.updateById(role);
        return getRoleDetail(roleVO.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        roleMapper.deleteById(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        log.info("删除角色成功: roleId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        List<Long> validIds = permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getId).toList();
        for (Long permissionId : validIds) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreateTime(LocalDateTime.now());
            rolePermissionMapper.insert(relation);
        }
        log.info("分配权限: roleId={}, permissionIds={}", roleId, validIds);
    }
}
