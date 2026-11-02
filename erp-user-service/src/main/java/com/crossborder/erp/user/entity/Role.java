package com.crossborder.erp.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体
 *
 * <p>⚠️ 这张表在 {@code database/init.sql} 里早就建好了、种子数据也有，
 * 但 Java 侧一直没有实体 —— 前端 {@code /role/**} 的调用全是 404。</p>
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@TableName("t_role")
public class Role {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("role_code")
    private String roleCode;

    @TableField("role_name")
    private String roleName;

    private String description;

    /** 状态(0禁用 1启用) */
    private Integer status;

    /** 排序号，前端表格有「排序」列 */
    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
