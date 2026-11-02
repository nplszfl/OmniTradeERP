package com.crossborder.erp.user.vo;

import com.crossborder.erp.user.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户视图对象
 *
 * <p>📌 字段名严格对齐前端 {@code erp-web/src/api/user.ts} 的 {@code SysUser} 接口：
 * 前端表格直接读 {@code row.roles[].roleName}、{@code row.lastLoginTime}、
 * {@code row.status === 1}，字段名不一致就渲染不出来。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    private String email;

    private String phone;

    private String avatar;

    /** 状态(0禁用 1启用) */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 密码（仅写入，永不输出）
     *
     * <p>WRITE_ONLY：创建用户时要接收前端传的明文密码，但<b>绝不能</b>随响应回传
     * （否则列表接口会把 BCrypt 散列泄露到浏览器）。</p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 已分配角色 */
    private List<RoleVO> roles = new ArrayList<>();

    /** 所属部门 */
    private List<DepartmentVO> departments = new ArrayList<>();

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
