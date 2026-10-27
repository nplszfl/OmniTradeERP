package com.crossborder.erp.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 客户跟进记录实体
 */
@Data
@TableName("t_customer_follow_record")
public class CustomerFollowRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 跟进方式（PHONE/EMAIL/WECHAT/VISIT/OTHER）
     */
    private String followType;

    /**
     * 跟进内容
     */
    private String content;

    /**
     * 本次跟进时间
     */
    private LocalDateTime followTime;

    /**
     * 下次跟进时间
     */
    private LocalDateTime nextFollowTime;

    /**
     * 跟进结果（SUCCESS/PENDING/FAILED）
     */
    private String result;

    /**
     * 跟进人ID
     */
    private Long followUserId;

    /**
     * 跟进人姓名
     */
    private String followUserName;

    /**
     * 跟进人姓名（跟进人展示名）
     */
    private String followerName;

    /**
     * 备注
     */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}