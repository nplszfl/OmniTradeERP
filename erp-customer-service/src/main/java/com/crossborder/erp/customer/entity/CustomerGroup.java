package com.crossborder.erp.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 客户分组实体
 */
@Data
@TableName("t_customer_group")
public class CustomerGroup {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 分组描述
     */
    private String description;

    /**
     * 分组成员数量（冗余统计字段，由服务层维护）
     */
    private Integer memberCount;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态（ACTIVE/INACTIVE）
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}