package com.crossborder.erp.user.vo;

import com.crossborder.erp.user.entity.Role;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色视图对象
 *
 * <p>前端 {@code Role} 接口字段：id / roleCode / roleName / description /
 * status / sort / createTime / permissions。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class RoleVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 排序号（实体列名 sort_order，前端字段名 sort） */
    private Integer sort;

    private LocalDateTime createTime;

    /** 已分配权限 */
    private List<PermissionVO> permissions = new ArrayList<>();

    public static RoleVO from(Role role) {
        if (role == null) {
            return null;
        }
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setSort(role.getSortOrder());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }
}
