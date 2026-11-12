package com.crossborder.erp.user.controller;

import com.crossborder.erp.common.result.PageResult;
import com.crossborder.erp.common.result.Result;
import com.crossborder.erp.user.service.RoleService;
import com.crossborder.erp.user.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 *
 * <p>📌 <b>{@code GET /role/list} 是双形态接口</b>，因为前端两种用法都有：</p>
 * <pre>
 * roleApi.getRoles()            → 期望裸数组      allRoles.value = res || []
 * roleApi.getRoleList({current,size}) → 期望分页对象  roleList.value = res.data.records
 * </pre>
 * 规则：<b>带 current/size → 返回包了 Result 的分页对象；不带 → 返回裸数组</b>。
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Slf4j
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 角色列表 / 分页
     */
    @GetMapping("/list")
    public Object list(@RequestParam(required = false) Integer current,
                       @RequestParam(required = false) Integer size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer status) {
        if (current != null || size != null) {
            int c = current == null ? 1 : current;
            int s = size == null ? 20 : size;
            return Result.success(roleService.pageRoles(c, s, keyword, status));
        }
        return roleService.listRoles(status);
    }

    /**
     * 角色详情（含权限）
     */
    @GetMapping("/{id}")
    public RoleVO detail(@PathVariable Long id) {
        return roleService.getRoleDetail(id);
    }

    /**
     * 创建角色
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping
    public RoleVO create(@RequestBody RoleVO role) {
        return roleService.createRole(role);
    }

    /**
     * 更新角色
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping
    public RoleVO update(@RequestBody RoleVO role) {
        return roleService.updateRole(role);
    }

    /**
     * 删除角色
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        roleService.deleteRole(id);
    }

    /**
     * 分配权限（整体覆盖）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping("/{roleId}/permissions")
    public void assignPermissions(@PathVariable Long roleId, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(roleId, body.get("permissionIds"));
    }
}
