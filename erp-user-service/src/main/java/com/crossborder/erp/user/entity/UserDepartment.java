package com.crossborder.erp.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-部门关联
 *
 * @author 火球鼠
 * @since 2026-11-02
 */
@Data
@TableName("t_user_department")
public class UserDepartment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("department_id")
    private Long departmentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
