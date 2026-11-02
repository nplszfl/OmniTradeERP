package com.crossborder.erp.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门实体（树形）
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@TableName("t_department")
public class Department {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    /** 上级部门ID，根节点为 0 */
    @TableField("parent_id")
    private Long parentId;

    /** 负责人 */
    private String leader;

    private String phone;

    private String email;

    @TableField("sort_order")
    private Integer sortOrder;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
