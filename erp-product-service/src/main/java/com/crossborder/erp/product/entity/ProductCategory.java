package com.crossborder.erp.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类实体
 *
 * <p>📌 <b>字段与 {@code t_product_category} 表结构一一对应</b>：
 * 之前实体用的是 {@code name / level / sort / icon}，
 * 而库里实际是 {@code category_name / category_name_en / category_code / sort_order}，
 * 且<b>根本没有</b> name / level / sort / icon 这些列。
 * MyBatis-Plus 默认驼峰转下划线映射 →
 * 实体字段 {@code name} 会映射到列 {@code name}，该列不存在 →
 * 一旦有人调用这个 Mapper，SQL 直接报
 * {@code Unknown column 'name' in 'field list'}。
 * 另外 {@code category_name} 是 NOT NULL 且无默认值 → INSERT 也会失败。</p>
 *
 * <p>📌 以 <b>DDL + 种子数据</b>为准（database/init.sql、database/seed-data.sql），
 * 它们是线上真正生效的结构。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@Data
@TableName("t_product_category")
public class ProductCategory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 父分类ID（0为顶级分类）
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 分类名称
     */
    @TableField("category_name")
    private String categoryName;

    /**
     * 分类英文名
     */
    @TableField("category_name_en")
    private String categoryNameEn;

    /**
     * 分类编码
     */
    @TableField("category_code")
    private String categoryCode;

    /**
     * 排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 状态（0禁用 1启用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
