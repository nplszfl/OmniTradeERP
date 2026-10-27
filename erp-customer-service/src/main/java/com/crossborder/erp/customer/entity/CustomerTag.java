package com.crossborder.erp.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 客户标签实体
 */
@Data
@TableName("t_customer_tag")
public class CustomerTag {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签颜色（如 #FF5722）
     */
    private String color;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态（ACTIVE/INACTIVE）
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}