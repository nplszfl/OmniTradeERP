package com.crossborder.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crossborder.erp.finance.enums.CategoryType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 财务分类
 */
@Data
@TableName("fin_category")
public class FinanceCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类类型：INCOME / EXPENSE */
    private CategoryType type;

    /** 父分类 ID */
    private Long parentId;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否启用 */
    private Integer enabled;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
