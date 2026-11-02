package com.crossborder.erp.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String password;

    @TableField("real_name")
    private String realName;

    private String email;

    private String phone;

    private String avatar;

    private Integer status;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /** 最后登录IP（前端 SysUser 接口有这个字段） */
    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
