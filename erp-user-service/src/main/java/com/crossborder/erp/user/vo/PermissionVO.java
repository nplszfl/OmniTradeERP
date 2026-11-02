package com.crossborder.erp.user.vo;

import com.crossborder.erp.user.entity.Permission;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限视图对象（树形）
 *
 * <p>📌 前端字段名是 {@code path}，实体列名是 {@code url} —— 这里做一次改名，
 * 否则权限树里的菜单链接全是 undefined。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class PermissionVO {

    private Long id;

    private Long parentId;

    private String permissionCode;

    private String permissionName;

    /** 权限类型(menu/button/api) */
    private String permissionType;

    /** 菜单URL / API路径（实体列名 url） */
    private String path;

    private String component;

    private String icon;

    /** 排序号（实体列名 sort_order） */
    private Integer sort;

    private Integer status;

    /** 子权限 */
    private List<PermissionVO> children = new ArrayList<>();

    public static PermissionVO from(Permission permission) {
        if (permission == null) {
            return null;
        }
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setParentId(permission.getParentId());
        vo.setPermissionCode(permission.getPermissionCode());
        vo.setPermissionName(permission.getPermissionName());
        vo.setPermissionType(permission.getPermissionType());
        vo.setPath(permission.getUrl());
        vo.setComponent(permission.getComponent());
        vo.setIcon(permission.getIcon());
        vo.setSort(permission.getSortOrder());
        vo.setStatus(permission.getStatus());
        return vo;
    }
}
