package com.crossborder.erp.user.service;

import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.user.vo.RoleVO;

import java.util.List;

/**
 * 角色管理服务
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
public interface RoleService {

    /**
     * 查询角色列表（不分页）
     *
     * @param status 状态（null=全部）
     */
    List<RoleVO> listRoles(Integer status);

    /**
     * 分页查询角色
     */
    PageResult<RoleVO> pageRoles(int current, int size, String keyword, Integer status);

    /**
     * 角色详情（含已分配权限）
     */
    RoleVO getRoleDetail(Long id);

    /**
     * 创建角色
     */
    RoleVO createRole(RoleVO roleVO);

    /**
     * 更新角色
     */
    RoleVO updateRole(RoleVO roleVO);

    /**
     * 删除角色（同时清理角色-权限、用户-角色关联）
     */
    void deleteRole(Long id);

    /**
     * 分配权限（整体覆盖）
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}
