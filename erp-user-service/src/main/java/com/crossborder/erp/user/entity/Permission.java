package com.crossborder.erp.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限实体（树形：menu / button / api）
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@TableName("t_permission")
public class Permission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父权限ID，根节点为 0 */
    @TableField("parent_id")
    private Long parentId;

    @TableField("permission_code")
    private String permissionCode;

    @TableField("permission_name")
    private String permissionName;

    /** 权限类型(menu/button/api) */
    @TableField("permission_type")
    private String permissionType;

    /** 菜单URL / API路径 */
    private String url;

    /** 前端组件路径 */
    private String component;

    private String icon;

    @TableField("sort_order")
    private Integer sortOrder;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
